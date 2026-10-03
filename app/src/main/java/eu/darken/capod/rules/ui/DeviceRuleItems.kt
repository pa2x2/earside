package eu.darken.capod.rules.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.ui.graphics.vector.ImageVector
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.DeviceRule
import eu.darken.capod.rules.core.DeviceRulesRepo
import eu.darken.capod.rules.core.RuleEntry
import eu.darken.capod.rules.core.RuleHandlers
import eu.darken.capod.rules.core.RuleId
import eu.darken.capod.rules.core.RuleRunState
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.ui.editor.RuleEditors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

sealed interface RuleStatus {
    data object None : RuleStatus
    data object NeedsAccess : RuleStatus
    data object LocationOff : RuleStatus
    data object Waiting : RuleStatus
    data class Applied(val at: Instant) : RuleStatus
    data class CouldNotApply(val at: Instant, val detail: String?) : RuleStatus

    val needsAttention: Boolean get() = this is NeedsAccess || this is LocationOff || this is CouldNotApply
}

/** A switched-off rule shows nothing; what's missing on the phone outranks what the rule last did. */
fun ruleStatus(rule: DeviceRule, state: RuleRunState?, missing: List<RuleRequirement>): RuleStatus = when {
    !rule.enabled -> RuleStatus.None
    missing.any { it != RuleRequirement.LOCATION_SERVICES } -> RuleStatus.NeedsAccess
    RuleRequirement.LOCATION_SERVICES in missing -> RuleStatus.LocationOff
    state?.pendingSince != null -> RuleStatus.Waiting
    else -> {
        val at = state?.lastOutcomeAt
        when (state?.lastOutcome) {
            null -> RuleStatus.None
            RuleRunState.Outcome.APPLIED -> at?.let { RuleStatus.Applied(it) } ?: RuleStatus.None
            RuleRunState.Outcome.NOT_AVAILABLE, RuleRunState.Outcome.FAILED ->
                RuleStatus.CouldNotApply(at ?: Instant.EPOCH, state.lastOutcomeDetail)
        }
    }
}

fun RuleStatus.text(context: Context, now: Instant): String? = when (this) {
    RuleStatus.None -> null
    RuleStatus.NeedsAccess -> context.getString(R.string.rules_status_needs_access)
    RuleStatus.LocationOff -> context.getString(R.string.rules_status_location_off)
    RuleStatus.Waiting -> context.getString(R.string.rules_status_waiting)
    is RuleStatus.Applied -> if (Duration.between(at, now) < Duration.ofMinutes(1)) {
        context.getString(R.string.rules_status_applied_just_now)
    } else {
        context.getString(R.string.rules_status_applied, relative(at, now))
    }
    is RuleStatus.CouldNotApply -> detail
        ?.let { context.getString(R.string.rules_status_not_applied_reason, it) }
        ?: context.getString(R.string.rules_status_not_applied)
}

private fun relative(at: Instant, now: Instant): CharSequence =
    DateUtils.getRelativeTimeSpanString(at.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS)

data class DeviceRuleItem(
    val id: RuleId,
    /** Null for a rule saved by a newer Earside; it can only be deleted. */
    val rule: DeviceRule?,
    val whenText: String = "",
    val whenIcon: ImageVector? = null,
    val thenText: String = "",
    val thenIcon: ImageVector? = null,
    val status: RuleStatus = RuleStatus.None,
    /** What the phone still has to allow for this rule's trigger. */
    val missing: List<RuleRequirement> = emptyList(),
)

/** One device's rules as shown in its rule list and summed up in Device settings. */
class DeviceRuleItems @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DeviceRulesRepo,
    private val handlers: RuleHandlers,
    private val editors: RuleEditors,
) {

    fun observe(profileId: ProfileId): Flow<List<DeviceRuleItem>> = repo.rulesFor(profileId).flatMapLatest { entries ->
        val triggerTypes = entries.mapNotNull { (it as? RuleEntry.Known)?.rule?.trigger?.let { t -> t::class } }.distinct()
        val missingFlows = triggerTypes.map { type ->
            handlers.forTriggerType(type)?.missingRequirements ?: flowOf(emptyList())
        }
        val missingByType: Flow<Map<Any, List<RuleRequirement>>> = if (missingFlows.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(missingFlows) { lists -> triggerTypes.zip(lists.toList()).toMap<Any, List<RuleRequirement>>() }
        }
        combine(missingByType, repo.runStates) { missing, states ->
            entries.map { entry -> entry.toItem(missing, states) }
        }
    }

    private fun RuleEntry.toItem(missing: Map<Any, List<RuleRequirement>>, states: Map<RuleId, RuleRunState>) = when (this) {
        is RuleEntry.Unsupported -> DeviceRuleItem(id = id, rule = null)

        is RuleEntry.Known -> {
            val missingHere = missing[rule.trigger::class].orEmpty()
            DeviceRuleItem(
                id = id,
                rule = rule,
                whenText = triggerSummary(rule),
                whenIcon = editors.forTrigger(rule.trigger::class)?.icon,
                thenText = actionSummary(rule),
                thenIcon = editors.forAction(rule.action::class)?.icon,
                status = ruleStatus(rule, states[id], missingHere),
                missing = missingHere,
            )
        }
    }

    fun triggerSummary(rule: DeviceRule): String = handlers.forTrigger(rule.trigger)?.summary(context, rule.trigger).orEmpty()

    fun actionSummary(rule: DeviceRule): String = handlers.forAction(rule.action)?.summary(context, rule.action).orEmpty()

    fun holdsNowText(rule: DeviceRule): String = handlers.forTrigger(rule.trigger)?.holdsNowText(context, rule.trigger).orEmpty()
}
