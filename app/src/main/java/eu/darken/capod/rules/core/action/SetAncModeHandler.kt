package eu.darken.capod.rules.core.action

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.main.ui.components.shortLabel
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetAncModeHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetAncMode> {

    override val type = RuleAction.SetAncMode::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasAncControl

    override fun isReady(device: PodDevice): Boolean = device.isAapReady && device.address != null && device.ancMode != null

    override fun summary(context: Context, action: RuleAction.SetAncMode): String =
        context.getString(R.string.rules_action_anc_summary, action.mode.shortLabel(context))

    // Before the device reports its modes there's nothing to check against; the device then decides.
    override fun unavailableReason(context: Context, device: PodDevice, action: RuleAction.SetAncMode): String? {
        if (device.ancMode == null || action.mode in device.visibleAncModes) return null
        return context.getString(R.string.rules_action_anc_unavailable_mode, action.mode.shortLabel(context))
    }

    override fun current(device: PodDevice, action: RuleAction.SetAncMode): RuleAction.SetAncMode? =
        device.ancMode?.let { RuleAction.SetAncMode(it.current) }

    override suspend fun execute(device: PodDevice, action: RuleAction.SetAncMode): DeviceControls.Result =
        deviceControls.setAncMode(device.address!!, action.mode).await()
}
