package eu.darken.capod.rules.core.trigger.phone

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.core.trigger.RuleTriggerHandler
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DoNotDisturbOnHandler @Inject constructor(
    private val doNotDisturb: DoNotDisturbSource,
) : RuleTriggerHandler<RuleTrigger.DoNotDisturbOn> {

    override val type = RuleTrigger.DoNotDisturbOn::class

    override fun isSupported(features: PodModel.Features): Boolean = true

    override fun summary(context: Context, trigger: RuleTrigger.DoNotDisturbOn): String =
        context.getString(R.string.rules_trigger_dnd_summary)

    override fun holdsNowText(context: Context, trigger: RuleTrigger.DoNotDisturbOn): String =
        context.getString(R.string.rules_apply_now_dnd)

    override fun undoText(context: Context, trigger: RuleTrigger.DoNotDisturbOn): String =
        context.getString(R.string.rules_undo_dnd)

    override val missingRequirements: Flow<List<RuleRequirement>> = flowOf(emptyList())

    override fun condition(profileId: ProfileId, trigger: RuleTrigger.DoNotDisturbOn): Flow<TriggerCondition> =
        doNotDisturb.condition
}

class InCallHandler @Inject constructor(
    private val calls: CallSource,
) : RuleTriggerHandler<RuleTrigger.InCall> {

    override val type = RuleTrigger.InCall::class

    override fun isSupported(features: PodModel.Features): Boolean = true

    override fun summary(context: Context, trigger: RuleTrigger.InCall): String =
        context.getString(R.string.rules_trigger_call_summary)

    override fun holdsNowText(context: Context, trigger: RuleTrigger.InCall): String =
        context.getString(R.string.rules_apply_now_call)

    override fun undoText(context: Context, trigger: RuleTrigger.InCall): String =
        context.getString(R.string.rules_undo_call)

    override val missingRequirements: Flow<List<RuleRequirement>> = flowOf(emptyList())

    override fun condition(profileId: ProfileId, trigger: RuleTrigger.InCall): Flow<TriggerCondition> =
        calls.inCall.map { TriggerCondition.Known(holds = it) }
}
