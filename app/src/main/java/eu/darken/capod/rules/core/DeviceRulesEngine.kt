package eu.darken.capod.rules.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs device rules while the monitor service runs. Two loops share the persisted [RuleRunState]s:
 * one folds every trigger change into them ([observe]), the other runs the rules that are waiting
 * once their device has a ready AAP session. Keeping them apart means a trigger change is recorded
 * even while the AirPods are away, and the rule runs whenever they come back.
 */
@Singleton
class DeviceRulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DeviceRulesRepo,
    private val settings: DeviceRulesSettings,
    private val handlers: RuleHandlers,
    private val deviceMonitor: DeviceMonitor,
    private val notifications: DeviceRulesNotifications,
    private val timeSource: TimeSource,
) {

    private data class ActiveRule(val profileId: ProfileId, val index: Int, val rule: DeviceRule)

    private val enabledRules: Flow<List<ActiveRule>> = repo.rules
        .map { byProfile ->
            byProfile.flatMap { (profileId, entries) ->
                entries.mapIndexedNotNull { index, entry ->
                    val rule = (entry as? RuleEntry.Known)?.rule ?: return@mapIndexedNotNull null
                    if (rule.enabled) ActiveRule(profileId, index, rule) else null
                }
            }
        }
        .distinctUntilChanged()

    fun monitor(): Flow<Unit> = merge(observeTriggers(), runDueRules())

    private fun observeTriggers(): Flow<Unit> = enabledRules
        .map { rules -> rules.map { it.rule } }
        .distinctUntilChanged()
        .flatMapLatest { rules ->
            // A disabled or deleted rule forgets what it saw, so enabling it again doesn't fire it.
            val ids = rules.map { it.id }.toSet()
            settings.runStates.update { it.copy(states = it.states.filterKeys { id -> id in ids }) }
            if (rules.isEmpty()) return@flatMapLatest emptyFlow()

            // Recorded inside, so a removed rule's last observation can't land after the cleanup above.
            rules.map { rule -> conditionOf(rule.trigger).onEach { record(rule, it) } }.merge()
        }
        .map { }

    private fun <T : RuleTrigger> conditionOf(trigger: T): Flow<TriggerCondition> =
        handlers.forTrigger(trigger)?.condition(trigger) ?: flowOf(TriggerCondition.Unknown)

    private suspend fun record(rule: DeviceRule, condition: TriggerCondition) {
        val now = timeSource.now()
        val (old, new) = settings.runStates.update { stored ->
            stored.copy(states = stored.states + (rule.id to stored.states[rule.id].observe(rule.trigger, condition, now)))
        }
        val before = old.states[rule.id]?.pendingSince
        val after = new.states[rule.id]?.pendingSince
        if (before != after) log(TAG, INFO) { "Rule ${rule.id} ($condition): pending $before -> $after" }
    }

    private fun runDueRules(): Flow<Unit> = combine(
        enabledRules,
        settings.runStates.flow,
        deviceMonitor.devices,
    ) { rules, runStates, devices ->
        val ready = devices
            .filter { it.isAapReady && it.address != null }
            .mapNotNull { device -> device.profileId?.let { it to device } }
            .toMap()
        rules
            .mapNotNull { active ->
                if (active.profileId !in ready) return@mapNotNull null
                val since = runStates.states[active.rule.id]?.pendingSince ?: return@mapNotNull null
                DueRule(active.profileId, active.index, active.rule, since)
            }
            .inRunOrder()
            .map { it to ready.getValue(it.profileId) }
    }
        .conflate()
        .onEach { due -> due.forEach { (rule, device) -> run(rule, device) } }
        .map { }

    private suspend fun run(due: DueRule, device: PodDevice) {
        val rule = due.rule
        // An earlier rule in this batch took time; the occurrence may have ended or been handled.
        if (settings.runStates.value().states[rule.id]?.pendingSince != due.pendingSince) return

        val handler = handlers.forAction(rule.action)
        val address = device.address
        if (handler == null || address == null) {
            log(TAG, WARN) { "Rule ${rule.id}: no handler or address for ${rule.action}" }
            finish(rule.id, due.pendingSince, RuleRunState.Outcome.FAILED)
            return
        }

        val unavailable = handler.unavailableReason(context, device, rule.action)
        if (unavailable != null) {
            log(TAG, INFO) { "Rule ${rule.id}: ${rule.action} not available: $unavailable" }
            finish(rule.id, due.pendingSince, RuleRunState.Outcome.NOT_AVAILABLE, unavailable)
            return
        }

        log(TAG, INFO) { "Rule ${rule.id}: running ${rule.action} on $address (pending since ${due.pendingSince})" }
        val outcome = when (val result = handler.execute(address, rule.action)) {
            DeviceControls.Result.Sent -> RuleRunState.Outcome.APPLIED
            // Someone changed the setting while the rule's request waited; their choice stands.
            DeviceControls.Result.Superseded -> null
            is DeviceControls.Result.Failed -> RuleRunState.Outcome.FAILED
        }
        log(TAG, if (outcome == RuleRunState.Outcome.FAILED) WARN else VERBOSE) { "Rule ${rule.id}: outcome $outcome" }

        if (!finish(rule.id, due.pendingSince, outcome)) return
        if (outcome == RuleRunState.Outcome.APPLIED && due.profileId in repo.notifyProfiles.first()) {
            val triggerSummary = handlers.forTrigger(rule.trigger)?.summary(context, rule.trigger).orEmpty()
            notifications.showApplied(
                profileId = due.profileId,
                deviceLabel = device.label ?: device.getLabel(context),
                actionSummary = handler.summary(context, rule.action),
                triggerSummary = triggerSummary,
            )
        }
    }

    /**
     * Ends the occurrence that started at [pendingSince]. A newer occurrence that began while the
     * action ran is left waiting. Returns false if the state moved on.
     */
    private suspend fun finish(
        ruleId: RuleId,
        pendingSince: Instant,
        outcome: RuleRunState.Outcome?,
        detail: String? = null,
    ): Boolean {
        val now = timeSource.now()
        var finished = false
        settings.runStates.update { stored ->
            val state = stored.states[ruleId]
            if (state?.pendingSince != pendingSince) return@update stored
            finished = true
            val updated = if (outcome == null) {
                state.copy(pendingSince = null)
            } else {
                state.copy(pendingSince = null, lastOutcome = outcome, lastOutcomeAt = now, lastOutcomeDetail = detail)
            }
            stored.copy(states = stored.states + (ruleId to updated))
        }
        return finished
    }

    companion object {
        private val TAG = logTag("Rules", "Engine")
    }
}
