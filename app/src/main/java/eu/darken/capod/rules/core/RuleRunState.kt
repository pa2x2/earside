package eu.darken.capod.rules.core

import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * What a rule has seen and done, persisted so a service restart or an AirPods reconnect inside the
 * same occurrence doesn't fire the rule again.
 *
 * An occurrence starts when the trigger's condition starts holding, or keeps holding with a new
 * occurrence id (rejoined the network). The rule then waits until the device can take the action
 * (no longer than [RuleTriggerHandler.maxWait][eu.darken.capod.rules.core.trigger.RuleTriggerHandler.maxWait]),
 * runs once, and stays quiet until the next occurrence; a manual change in between is left alone.
 */
@Serializable
data class RuleRunState(
    /** The trigger this state was observed for. An edited trigger starts over. */
    @SerialName("trigger") val trigger: RuleTrigger,
    /**
     * Last known condition; null until the first one, which never starts an occurrence (the rule
     * saw no change). It keeps one started by [DeviceRulesEngine.applyNow], though.
     */
    @SerialName("observed") val observed: ObservedCondition? = null,
    /** When the current occurrence started, while the rule waits to run. */
    @SerialName("pendingSince") @Serializable(with = InstantEpochMillisSerializer::class)
    val pendingSince: Instant? = null,
    @SerialName("lastOutcome") val lastOutcome: Outcome? = null,
    @SerialName("lastOutcomeAt") @Serializable(with = InstantEpochMillisSerializer::class)
    val lastOutcomeAt: Instant? = null,
    /** Why the last run didn't apply in full, as shown to the user. */
    @SerialName("lastOutcomeDetail") val lastOutcomeDetail: String? = null,
) {

    @Serializable
    data class ObservedCondition(
        @SerialName("holds") val holds: Boolean,
        @SerialName("occurrence") val occurrence: String? = null,
    )

    @Serializable
    enum class Outcome {
        @SerialName("applied") APPLIED,
        /** The device couldn't take an action, e.g. the mode isn't in its listening-mode cycle. */
        @SerialName("not_available") NOT_AVAILABLE,
        @SerialName("failed") FAILED,
    }
}

/** Folds a new trigger observation into this state. [Unknown][TriggerCondition.Unknown] changes nothing. */
fun RuleRunState?.observe(trigger: RuleTrigger, condition: TriggerCondition, now: Instant): RuleRunState {
    val current = this?.takeIf { it.trigger == trigger } ?: RuleRunState(trigger)
    if (condition !is TriggerCondition.Known) return current

    val previous = current.observed
    val pendingSince = when {
        !condition.holds -> null
        previous == null -> current.pendingSince
        !previous.holds -> now
        condition.occurrence != null && condition.occurrence != previous.occurrence -> now
        else -> current.pendingSince
    }
    return current.copy(
        observed = RuleRunState.ObservedCondition(condition.holds, condition.occurrence),
        pendingSince = pendingSince,
    )
}

data class DueRule(
    val profileId: ProfileId,
    /** Position in the device's rule list. */
    val index: Int,
    val rule: DeviceRule,
    val pendingSince: Instant,
)

/**
 * Waiting rules run in the order their events happened, so the result matches what the AirPods
 * would have ended up with had they been connected all along. Same moment: list order.
 */
fun List<DueRule>.inRunOrder(): List<DueRule> = sortedWith(compareBy({ it.pendingSince }, { it.index }))

/** What running one of a rule's actions came to. */
sealed interface ActionResult {
    data class Applied(val summary: String) : ActionResult

    /** Someone changed the setting while the rule's request waited; their choice stands. */
    data object Superseded : ActionResult

    data class NotAvailable(val reason: String) : ActionResult

    /** [summary] is null when the action has no handler. */
    data class Failed(val summary: String?) : ActionResult
}

data class RunOutcome(val outcome: RuleRunState.Outcome?, val detail: String?)

/**
 * A rule counts as applied only if every action it ran was taken; the detail names those that
 * weren't. A superseded action counts neither way, so a rule whose actions were all superseded keeps
 * its last outcome.
 */
fun List<ActionResult>.outcome(): RunOutcome {
    val outcome = when {
        any { it is ActionResult.Failed } -> RuleRunState.Outcome.FAILED
        any { it is ActionResult.NotAvailable } -> RuleRunState.Outcome.NOT_AVAILABLE
        any { it is ActionResult.Applied } -> RuleRunState.Outcome.APPLIED
        else -> null
    }
    val detail = mapNotNull {
        when (it) {
            is ActionResult.NotAvailable -> it.reason
            is ActionResult.Failed -> it.summary
            else -> null
        }
    }
    return RunOutcome(outcome, detail.joinToString(" · ").ifEmpty { null })
}
