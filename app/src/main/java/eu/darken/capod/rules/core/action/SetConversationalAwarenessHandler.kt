package eu.darken.capod.rules.core.action

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetConversationalAwarenessHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetConversationalAwareness> {

    override val type = RuleAction.SetConversationalAwareness::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasConversationAwareness

    override fun isReady(device: PodDevice): Boolean = device.isAapReady && device.address != null && device.conversationalAwareness != null

    override fun summary(context: Context, action: RuleAction.SetConversationalAwareness): String =
        context.getString(if (action.enabled) R.string.rules_action_ca_summary_on else R.string.rules_action_ca_summary_off)

    override fun unavailableReason(
        context: Context,
        device: PodDevice,
        action: RuleAction.SetConversationalAwareness,
    ): String? = null

    override fun current(
        device: PodDevice,
        action: RuleAction.SetConversationalAwareness,
    ): RuleAction.SetConversationalAwareness? =
        device.conversationalAwareness?.let { RuleAction.SetConversationalAwareness(it.enabled) }

    override suspend fun execute(
        device: PodDevice,
        action: RuleAction.SetConversationalAwareness,
    ): DeviceControls.Result = deviceControls.setConversationalAwareness(device.address!!, action.enabled)
}
