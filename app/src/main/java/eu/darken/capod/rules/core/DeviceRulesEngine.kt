package eu.darken.capod.rules.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
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
import eu.darken.capod.rules.core.action.RuleActionHandler
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
 * once their actions are ready to run on their device ([RuleActionHandler.isReady]). Keeping them
 * apart means a trigger change is recorded even while the AirPods are away, and the rule runs
 * whenever they come back, unless its trigger's
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
     * Whether [rule]'s condition holds right now and its actions are ready to run on its device, so
     * [applyNow] would run it at once. A saved or re-enabled rule otherwise waits for the next
     * occurrence, since the first observation never starts one.
     */
    suspend fun canApplyNow(profileId: ProfileId, rule: DeviceRule): Boolean {
        if (!rule.enabled) return false
        val actionHandlers = rule.actions.map { handlers.forAction(it) ?: return false }
        val ready = deviceMonitor.devices.first().any { device ->
            device.profileId == profileId && actionHandlers.all { it.isReady(device) }
        }
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
            stored.copy(states = stored.states + (rule.id to state.copy(pendingSince = now, restoreSince = null)))
        }
    }

    private fun observeTriggers(): Flow<Unit> = enabledRules
        .map { rules -> rules.map { it.rule } }
        .distinctUntilChanged()
        .flatMapLatest { rules ->
            // A disabled or deleted rule forgets what it saw, so enabling it again doesn't fire it.
            // One with undo switched off forgets what to put back, so switching it on again can't
            // restore a value from long ago.
            val byId = rules.associateBy { it.id }
            settings.runStates.update { stored ->
                val states = stored.states.mapNotNull { (id, state) ->
                    val rule = byId[id] ?: return@mapNotNull null
                    id to if (rule.undoWhenEnds) state else state.copy(restore = emptyList(), restoreSince = null)
                }
                stored.copy(states = states.toMap())
            }
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
        val before = old.states[rule.id]
        val after = new.states[rule.id]
        if (before?.pendingSince != after?.pendingSince) {
            log(TAG, INFO) { "Rule ${rule.id} ($condition): pending ${before?.pendingSince} -> ${after?.pendingSince}" }
        }
        if (before?.restoreSince != after?.restoreSince) {
            log(TAG, INFO) { "Rule ${rule.id} ($condition): restore ${before?.restoreSince} -> ${after?.restoreSince}" }
        }
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
        val due = rules
            .flatMap { active ->
                val state = runStates.states[active.rule.id] ?: return@flatMap emptyList()
                // Without a handler there's nothing to wait for; the run then records the failure.
                fun readyDevice(actions: List<RuleAction>) = devices.firstOrNull { device ->
                    device.profileId == active.profileId && actions.all { handlers.forAction(it)?.isReady(device) != false }
                }

                val run = state.pendingSince
                    // dropWhenStale may not have caught up yet, e.g. right after a restart.
                    ?.takeUnless { since -> waitEnd(active.rule.trigger, since)?.let { it <= now } == true }
                    ?.let { since ->
                        readyDevice(active.rule.actions)?.let { DueRule(active.profileId, active.index, active.rule, since) to it }
                    }
                // A restore waits until the device has reported each setting; without it, there's no
                // telling whether the rule's value is still in place.
                val restore = state.restoreSince?.let { since ->
                    readyDevice(state.restore.map { it.previous })
                        ?.takeIf { device -> state.restore.all { currentOf(device, it.set) != null } }
                        ?.let { DueRule(active.profileId, active.index, active.rule, since, restore = true) to it }
                }
                listOfNotNull(run, restore)
            }
            .toMap()
        due.keys.toList().inRunOrder().map { it to due.getValue(it) }
    }
        .conflate()
        .onEach { due -> due.forEach { (rule, device) -> if (rule.restore) restore(rule, device) else run(rule, device) } }
        .map { }

    private fun currentOf(device: PodDevice, action: RuleAction): RuleAction? =
        handlers.forAction(action)?.current(device, action)

    private suspend fun run(due: DueRule, device: PodDevice) {
        val rule = due.rule
        log(TAG, INFO) { "Rule ${rule.id}: running on ${device.address} (pending since ${due.pendingSince})" }
        val results = mutableListOf<ActionResult>()
        val restore = mutableListOf<RuleRunState.Restore>()
        for (action in rule.actions) {
            // An earlier rule or action took time; the occurrence may have ended or been handled.
            val state = settings.runStates.value().states[rule.id]
            if (state?.pendingSince != due.pendingSince) return
            // Read before running, so it's the value the action replaces.
            val current = if (rule.undoWhenEnds) currentOf(device, action) else null
            val result = runAction(rule.id, action, device)
            results += result
            if (rule.undoWhenEnds) {
                // An action that didn't apply replaced nothing; any unfinished restore for it stays.
                val unfinished = state.restore.firstOrNull { it.set::class == action::class }
                val next = if (result is ActionResult.Applied) unfinished.forRun(action, current) else unfinished
                next?.let { restore += it }
            }
        }
        val (outcome, detail) = results.outcome()
        log(TAG, if (outcome == RuleRunState.Outcome.FAILED) WARN else VERBOSE) { "Rule ${rule.id}: outcome $outcome" }

        if (!finish(rule.id, due.pendingSince, outcome, detail, restore.takeIf { rule.undoWhenEnds })) return
        val applied = results.filterIsInstance<ActionResult.Applied>()
        // Also when another action didn't apply: the notice is about the settings that did change.
        if (applied.isNotEmpty()) notifyIfWanted(due, device, applied.joinToString(", ") { it.summary })
    }

    private suspend fun restore(due: DueRule, device: PodDevice) {
        val rule = due.rule
        val restores = settings.runStates.value().states[rule.id]
            ?.takeIf { it.restoreSince == due.pendingSince }
            ?.restore
            ?: return
        val restored = mutableListOf<String>()
        for (restore in restores) {
            // Back on the trigger before every setting went back: the rule's values are wanted again.
            if (settings.runStates.value().states[rule.id]?.restoreSince != due.pendingSince) return
            val handler = handlers.forAction(restore.previous)
            val current = handler?.current(device, restore.set)
            val unavailable = handler?.unavailableReason(context, device, restore.previous)
            when {
                handler == null -> log(TAG, WARN) { "Rule ${rule.id}: no handler to restore ${restore.previous}" }
                current != restore.set -> log(TAG, INFO) {
                    "Rule ${rule.id}: setting changed since ($current), not restoring ${restore.previous}"
                }
                unavailable != null -> log(TAG, INFO) { "Rule ${rule.id}: ${restore.previous} not available: $unavailable" }
                else -> {
                    log(TAG, INFO) {
                        "Rule ${rule.id}: restoring ${restore.previous} on ${device.address} (ended at ${due.pendingSince})"
                    }
                    val result = handler.execute(device, restore.previous)
                    log(TAG, if (result is DeviceControls.Result.Failed) WARN else VERBOSE) { "Rule ${rule.id}: restore $result" }
                    if (result == DeviceControls.Result.Sent) restored += handler.summary(context, restore.previous)
                }
            }
        }
        if (finishRestore(rule.id, due.pendingSince) && restored.isNotEmpty()) {
            notifyIfWanted(due, device, context.getString(R.string.rules_notification_restored, restored.joinToString(", ")))
        }
    }

    /** Ends the restore that started at [restoreSince], whatever came of it; one try only, like a run. */
    private suspend fun finishRestore(ruleId: RuleId, restoreSince: Instant): Boolean {
        var finished = false
        settings.runStates.update { stored ->
            val state = stored.states[ruleId]
            if (state?.restoreSince != restoreSince) return@update stored
            finished = true
            stored.copy(states = stored.states + (ruleId to state.copy(restore = emptyList(), restoreSince = null)))
        }
        return finished
    }

    private suspend fun notifyIfWanted(due: DueRule, device: PodDevice, actionSummary: String) {
        if (due.profileId !in repo.notifyProfiles.first()) return
        notifications.showApplied(
            profileId = due.profileId,
            deviceLabel = device.label ?: device.getLabel(context),
            ruleName = due.rule.name,
            actionSummary = actionSummary,
            triggerSummary = handlers.forTrigger(due.rule.trigger)?.summary(context, due.rule.trigger).orEmpty(),
        )
    }

    private suspend fun runAction(
        ruleId: RuleId,
        action: RuleAction,
        device: PodDevice,
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
        return when (handler.execute(device, action)) {
            DeviceControls.Result.Sent -> ActionResult.Applied(summary)
            DeviceControls.Result.Superseded -> ActionResult.Superseded
            is DeviceControls.Result.Failed -> ActionResult.Failed(summary)
        }
    }

    /**
     * Ends the occurrence that started at [pendingSince]. A newer occurrence that began while the
     * actions ran is left waiting. Returns false if the state moved on. [restore], if given, replaces
     * the stored one.
     */
    private suspend fun finish(
        ruleId: RuleId,
        pendingSince: Instant,
        outcome: RuleRunState.Outcome?,
        detail: String? = null,
        restore: List<RuleRunState.Restore>? = null,
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
            stored.copy(states = stored.states + (ruleId to updated.copy(restore = restore ?: state.restore)))
        }
        return finished
    }

    companion object {
        private val TAG = logTag("Rules", "Engine")
    }
}
