package eu.darken.capod.rules.core.action

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

class SetAdaptiveAudioNoiseHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : RuleActionHandler<RuleAction.SetAdaptiveAudioNoise> {

    override val type = RuleAction.SetAdaptiveAudioNoise::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasAdaptiveAudioNoise

    override fun isReady(device: PodDevice): Boolean = device.isAapReady && device.address != null && device.adaptiveAudioNoise != null

    override fun summary(context: Context, action: RuleAction.SetAdaptiveAudioNoise): String = context.getString(
        R.string.rules_action_setting_summary,
        context.getString(R.string.device_settings_adaptive_noise_label),
        "${action.level}%",
    )

    // Not refused outside Adaptive mode, although the level only matters in it: the engine checks every
    // action against the device as it was before the rule ran, so a rule that also switches to
    // Adaptive would have its level refused. Sent outside it, the level waits for Adaptive mode.
    override fun unavailableReason(
        context: Context,
        device: PodDevice,
        action: RuleAction.SetAdaptiveAudioNoise,
    ): String? = null

    override fun current(
        device: PodDevice,
        action: RuleAction.SetAdaptiveAudioNoise,
    ): RuleAction.SetAdaptiveAudioNoise? = device.adaptiveAudioNoise?.let { reported ->
        if (reportsAs(action.level, reported.level)) action else RuleAction.SetAdaptiveAudioNoise(reported.level)
    }

    // AirPods 5 keep only 0, 50 or 100 and report that back, so a rule's 30 reads back as 50. Without
    // this, undo would take that for a change by hand and leave the level alone.
    private fun reportsAs(level: Int, reported: Int): Boolean = reported == level || reported == (level + 25) / 50 * 50

    override suspend fun execute(
        device: PodDevice,
        action: RuleAction.SetAdaptiveAudioNoise,
    ): DeviceControls.Result = deviceControls.setAdaptiveAudioNoise(device.address!!, action.level)
}
