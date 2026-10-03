package eu.darken.capod.rules.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothAddress
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * Runs device rules while the monitor service runs. Two loops share the persisted [RuleRunState]s:
 * one folds every trigger change into them ([observe]), the other runs the rules that are waiting
 * once their device has a ready AAP session. Keeping them apart means a trigger change is recorded
 * even while the AirPods are away, and the rule runs whenever they come back, unless its trigger's
 * [maxWait][eu.darken.capod.rules.core.trigger.RuleTriggerHandler.maxWait] runs out first.
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

    /**
     * Whether [rule]'s condition holds right now and its device has a ready AAP session, so
     * [applyNow] would run it at once. A saved or re-enabled rule otherwise waits for the next
     * occurrence, since the first observation never starts one.
     */
    suspend fun canApplyNow(profileId: ProfileId, rule: DeviceRule): Boolean {
        if (!rule.enabled) return false
        val ready = deviceMonitor.devices.first().any { it.profileId == profileId && it.isAapReady && it.address != null }
        if (!ready) return false
        // The Wi-Fi source debounces a fresh registration for 2 s before its first state.
        val condition = withTimeoutOrNull(5.seconds) { conditionOf(rule.trigger).first { it is TriggerCondition.Known } }
        return (condition as? TriggerCondition.Known)?.holds == true
    }

    /** Starts an occurrence for [rule] now; the run loop then applies it like any other. */
    suspend fun applyNow(rule: DeviceRule) {
        val now = timeSource.now()
        log(TAG, INFO) { "Rule ${rule.id}: applying now ($now)" }
        settings.runStates.update { stored ->
            val state = stored.states[rule.id]?.takeIf { it.trigger == rule.trigger } ?: RuleRunState(rule.trigger)
            stored.copy(states = stored.states + (rule.id to state.copy(pendingSince = now)))
        }
    }

    private fun observeTriggers(): Flow<Unit> = enabledRules
        .map { rules -> rules.map { it.rule } }
        .distinctUntilChanged()
        .flatMapLatest { rules ->
            // A disabled or deleted rule forgets what it saw, so enabling it again doesn't fire it.
            val ids = rules.map { it.id }.toSet()
            settings.runStates.update { it.copy(states = it.states.filterKeys { id -> id in ids }) }
            if (rules.isEmpty()) return@flatMapLatest emptyFlow()

            // Recorded inside, so a removed rule's last observation can't land after the cleanup above.
            rules.map { rule -> merge(conditionOf(rule.trigger).onEach { record(rule, it) }, dropWhenStale(rule)) }.merge()
        }
        .map { }

    // A rule listed as needing access stays out of play until it has it, even when part of its
    // condition is visible without it (no Wi-Fi at all still shows without location access).
    private fun <T : RuleTrigger> conditionOf(trigger: T): Flow<TriggerCondition> {
        val handler = handlers.forTrigger(trigger) ?: return flowOf(TriggerCondition.Unknown)
        return combine(handler.missingRequirements, handler.condition(trigger)) { missing, condition ->
            if (missing.isEmpty()) condition else TriggerCondition.Unknown
        }.distinctUntilChanged()
    }

    private suspend fun record(rule: DeviceRule, condition: TriggerCondition) {
        val now = timeSource.now()
        val (old, new) = settings.runStates.update { stored ->
            stored.copy(states = stored.states + (rule.id to stored.states[rule.id].observe(rule.trigger, condition, now)))
        }
        val before = old.states[rule.id]?.pendingSince
        val after = new.states[rule.id]?.pendingSince
        if (before != after) log(TAG, INFO) { "Rule ${rule.id} ($condition): pending $before -> $after" }
    }

    /** When an occurrence of [trigger] that started at [since] stops waiting; null if it waits as long as its condition holds. */
    private fun waitEnd(trigger: RuleTrigger, since: Instant): Instant? =
        handlers.forTrigger(trigger)?.maxWait?.let { since + it.toJavaDuration() }

    // Clears the stored wait rather than only skipping the run, so the rule stops showing as waiting.
    // After a restart, an occurrence that went stale meanwhile is dropped at once.
    private fun dropWhenStale(rule: DeviceRule): Flow<Unit> {
        if (handlers.forTrigger(rule.trigger)?.maxWait == null) return emptyFlow()
        return settings.runStates.flow
            .map { it.states[rule.id]?.pendingSince }
            .distinctUntilChanged()
            .mapLatest { since ->
                val end = since?.let { waitEnd(rule.trigger, it) } ?: return@mapLatest
                delay(java.time.Duration.between(timeSource.now(), end).toKotlinDuration())
                if (finish(rule.id, since, outcome = null)) log(TAG, INFO) { "Rule ${rule.id}: waited since $since, dropped" }
            }
    }

    private fun runDueRules(): Flow<Unit> = combine(
        enabledRules,
        settings.runStates.flow,
        deviceMonitor.devices,
    ) { rules, runStates, devices ->
        val now = timeSource.now()
        val ready = devices
            .filter { it.isAapReady && it.address != null }
            .mapNotNull { device -> device.profileId?.let { it to device } }
            .toMap()
        rules
            .mapNotNull { active ->
                if (active.profileId !in ready) return@mapNotNull null
                val since = runStates.states[active.rule.id]?.pendingSince ?: return@mapNotNull null
                // dropWhenStale may not have caught up yet, e.g. right after a restart.
                if (waitEnd(active.rule.trigger, since)?.let { it <= now } == true) return@mapNotNull null
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
        val address = device.address
        if (address == null) {
            log(TAG, WARN) { "Rule ${rule.id}: no address for ${device.profileId}" }
            finish(rule.id, due.pendingSince, RuleRunState.Outcome.FAILED)
            return
        }

        log(TAG, INFO) { "Rule ${rule.id}: running on $address (pending since ${due.pendingSince})" }
        val results = mutableListOf<ActionResult>()
        for (action in rule.actions) {
            // An earlier rule or action took time; the occurrence may have ended or been handled.
            if (settings.runStates.value().states[rule.id]?.pendingSince != due.pendingSince) return
            results += runAction(rule.id, action, device, address)
        }
        val (outcome, detail) = results.outcome()
        log(TAG, if (outcome == RuleRunState.Outcome.FAILED) WARN else VERBOSE) { "Rule ${rule.id}: outcome $outcome" }

        if (!finish(rule.id, due.pendingSince, outcome, detail)) return
        val applied = results.filterIsInstance<ActionResult.Applied>()
        // Also when another action didn't apply: the notice is about the settings that did change.
        if (applied.isNotEmpty() && due.profileId in repo.notifyProfiles.first()) {
            val triggerSummary = handlers.forTrigger(rule.trigger)?.summary(context, rule.trigger).orEmpty()
            notifications.showApplied(
                profileId = due.profileId,
                deviceLabel = device.label ?: device.getLabel(context),
                ruleName = rule.name,
                actionSummary = applied.joinToString(", ") { it.summary },
                triggerSummary = triggerSummary,
            )
        }
    }

    private suspend fun runAction(
        ruleId: RuleId,
        action: RuleAction,
        device: PodDevice,
        address: BluetoothAddress,
    ): ActionResult {
        val handler = handlers.forAction(action)
        if (handler == null) {
            log(TAG, WARN) { "Rule $ruleId: no handler for $action" }
            return ActionResult.Failed(null)
        }

        val unavailable = handler.unavailableReason(context, device, action)
        if (unavailable != null) {
            log(TAG, INFO) { "Rule $ruleId: $action not available: $unavailable" }
            return ActionResult.NotAvailable(unavailable)
        }

        log(TAG, INFO) { "Rule $ruleId: running $action" }
        val summary = handler.summary(context, action)
        return when (handler.execute(address, action)) {
            DeviceControls.Result.Sent -> ActionResult.Applied(summary)
            DeviceControls.Result.Superseded -> ActionResult.Superseded
            is DeviceControls.Result.Failed -> ActionResult.Failed(summary)
        }
    }

    /**
     * Ends the occurrence that started at [pendingSince]. A newer occurrence that began while the
     * actions ran is left waiting. Returns false if the state moved on.
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
