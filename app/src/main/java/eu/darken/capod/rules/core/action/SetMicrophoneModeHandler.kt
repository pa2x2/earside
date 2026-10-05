package eu.darken.capod.rules.core.action

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetMicrophoneModeHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetMicrophoneMode> {

    override val type = RuleAction.SetMicrophoneMode::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasMicrophoneMode

    override fun isReady(device: PodDevice): Boolean = device.isAapReady && device.address != null && device.microphoneMode != null

    override fun summary(context: Context, action: RuleAction.SetMicrophoneMode): String = context.getString(
        R.string.rules_action_setting_summary,
        context.getString(R.string.device_settings_microphone_mode_label),
        context.getString(action.mode.labelRes),
    )

    override fun unavailableReason(
        context: Context,
        device: PodDevice,
        action: RuleAction.SetMicrophoneMode,
    ): String? = null

    override fun current(device: PodDevice, action: RuleAction.SetMicrophoneMode): RuleAction.SetMicrophoneMode? =
        device.microphoneMode?.let { RuleAction.SetMicrophoneMode(it.mode) }

    override suspend fun execute(device: PodDevice, action: RuleAction.SetMicrophoneMode): DeviceControls.Result =
        deviceControls.setMicrophoneMode(device.address!!, action.mode)
}

@get:StringRes
val AapSetting.MicrophoneMode.Mode.labelRes: Int
    get() = when (this) {
        AapSetting.MicrophoneMode.Mode.AUTO -> R.string.device_settings_microphone_mode_auto
        AapSetting.MicrophoneMode.Mode.ALWAYS_LEFT -> R.string.device_settings_microphone_mode_left
        AapSetting.MicrophoneMode.Mode.ALWAYS_RIGHT -> R.string.device_settings_microphone_mode_right
    }
