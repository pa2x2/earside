package eu.darken.capod.rules.core.action

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetAncModeHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetAncMode> {

    override val type = RuleAction.SetAncMode::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasAncControl

    override suspend fun execute(address: BluetoothAddress, action: RuleAction.SetAncMode): DeviceControls.Result =
        deviceControls.setAncMode(address, action.mode).await()
}
