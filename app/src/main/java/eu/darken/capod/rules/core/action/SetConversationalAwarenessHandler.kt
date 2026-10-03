package eu.darken.capod.rules.core.action

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetConversationalAwarenessHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetConversationalAwareness> {

    override val type = RuleAction.SetConversationalAwareness::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasConversationAwareness

    override suspend fun execute(
        address: BluetoothAddress,
        action: RuleAction.SetConversationalAwareness,
    ): DeviceControls.Result = deviceControls.setConversationalAwareness(address, action.enabled)
}
