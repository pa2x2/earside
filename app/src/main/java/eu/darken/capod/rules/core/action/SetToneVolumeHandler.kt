package eu.darken.capod.rules.core.action

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetToneVolumeHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetToneVolume> {

    override val type = RuleAction.SetToneVolume::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasToneVolume

    override fun isReady(device: PodDevice): Boolean = device.isAapReady && device.address != null && device.toneVolume != null

    override fun summary(context: Context, action: RuleAction.SetToneVolume): String = context.getString(
        R.string.rules_action_setting_summary,
        context.getString(R.string.device_settings_tone_volume_label),
        "${action.level}%",
    )

    override fun unavailableReason(context: Context, device: PodDevice, action: RuleAction.SetToneVolume): String? = null

    override fun current(device: PodDevice, action: RuleAction.SetToneVolume): RuleAction.SetToneVolume? =
        device.toneVolume?.let { RuleAction.SetToneVolume(it.level) }

    override suspend fun execute(device: PodDevice, action: RuleAction.SetToneVolume): DeviceControls.Result =
        deviceControls.setToneVolume(device.address!!, action.level)
}
