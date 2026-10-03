package eu.darken.capod.rules.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.Mic
import androidx.compose.material.icons.twotone.Nightlight
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.action.labelRes
import javax.inject.Inject
import kotlin.math.roundToInt

abstract class ToggleActionEditor<A : RuleAction> : RuleActionEditor<A> {

    /** Device Settings warns while the setting is on that it isn't tested on all devices yet. */
    protected open val experimental: Boolean = false

    protected abstract fun A.enabled(): Boolean

    protected abstract fun create(enabled: Boolean): A

    override fun initial(model: PodModel, device: PodDevice?): A = create(enabled = true)

    @Composable
    override fun Settings(current: A?, model: PodModel, device: PodDevice?, onChange: (A?) -> Unit) {
        Column {
            OptionRow(
                options = listOf(true to R.string.rules_value_on, false to R.string.rules_value_off),
                selected = current?.enabled(),
                onSelect = { onChange(create(it)) },
            )
            if (experimental && current?.enabled() == true) {
                SettingsInfoBox(
                    title = stringResource(R.string.device_settings_experimental_title),
                    text = stringResource(R.string.device_settings_experimental_description),
                    type = InfoBoxType.WARNING,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

class SetVolumeSwipeEditor @Inject constructor() : ToggleActionEditor<RuleAction.SetVolumeSwipe>() {
    override val type = RuleAction.SetVolumeSwipe::class
    override val icon = Icons.TwoTone.Swipe
    override val label = R.string.rules_action_volume_swipe_label
    override fun RuleAction.SetVolumeSwipe.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetVolumeSwipe(enabled)
}

class SetPersonalizedVolumeEditor @Inject constructor() : ToggleActionEditor<RuleAction.SetPersonalizedVolume>() {
    override val type = RuleAction.SetPersonalizedVolume::class
    override val icon = Icons.AutoMirrored.TwoTone.VolumeUp
    override val label = R.string.rules_action_personalized_volume_label
    override val experimental = true
    override fun RuleAction.SetPersonalizedVolume.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetPersonalizedVolume(enabled)
}

class SetNcWithOneAirPodEditor @Inject constructor() : ToggleActionEditor<RuleAction.SetNcWithOneAirPod>() {
    override val type = RuleAction.SetNcWithOneAirPod::class
    override val icon = Icons.TwoTone.LooksOne
    override val label = R.string.rules_action_nc_one_airpod_label
    override fun RuleAction.SetNcWithOneAirPod.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetNcWithOneAirPod(enabled)
}

class SetSleepDetectionEditor @Inject constructor() : ToggleActionEditor<RuleAction.SetSleepDetection>() {
    override val type = RuleAction.SetSleepDetection::class
    override val icon = Icons.TwoTone.Nightlight
    override val label = R.string.rules_action_sleep_detection_label
    override val experimental = true
    override fun RuleAction.SetSleepDetection.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetSleepDetection(enabled)
}

// Device Settings' slider range; the AAP encoder clamps to it too.
private val TONE_VOLUME_RANGE = 15..100

class SetToneVolumeEditor @Inject constructor() : RuleActionEditor<RuleAction.SetToneVolume> {

    override val type = RuleAction.SetToneVolume::class
    override val icon = Icons.TwoTone.NotificationsActive
    override val label = R.string.rules_action_tone_volume_label

    override fun initial(model: PodModel, device: PodDevice?) =
        RuleAction.SetToneVolume(device?.toneVolume?.level?.coerceIn(TONE_VOLUME_RANGE) ?: TONE_VOLUME_RANGE.last)

    @Composable
    override fun Settings(
        current: RuleAction.SetToneVolume?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetToneVolume?) -> Unit,
    ) {
        PercentSlider(
            level = current?.level ?: TONE_VOLUME_RANGE.last,
            range = TONE_VOLUME_RANGE,
            onChange = { onChange(RuleAction.SetToneVolume(it)) },
        )
    }
}

private val ADAPTIVE_AUDIO_NOISE_RANGE = 0..100

class SetAdaptiveAudioNoiseEditor @Inject constructor() : RuleActionEditor<RuleAction.SetAdaptiveAudioNoise> {

    override val type = RuleAction.SetAdaptiveAudioNoise::class
    override val icon = Icons.TwoTone.GraphicEq
    override val label = R.string.rules_action_adaptive_noise_label

    override fun initial(model: PodModel, device: PodDevice?) =
        RuleAction.SetAdaptiveAudioNoise(device?.adaptiveAudioNoise?.level ?: 50)

    @Composable
    override fun Settings(
        current: RuleAction.SetAdaptiveAudioNoise?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetAdaptiveAudioNoise?) -> Unit,
    ) {
        Column {
            PercentSlider(
                level = current?.level ?: 50,
                range = ADAPTIVE_AUDIO_NOISE_RANGE,
                onChange = { onChange(RuleAction.SetAdaptiveAudioNoise(it)) },
            )
            SettingsInfoBox(
                text = stringResource(R.string.rules_action_adaptive_noise_hint),
                type = InfoBoxType.INFO,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

class SetMicrophoneModeEditor @Inject constructor() : RuleActionEditor<RuleAction.SetMicrophoneMode> {

    override val type = RuleAction.SetMicrophoneMode::class
    override val icon = Icons.TwoTone.Mic
    override val label = R.string.rules_action_microphone_mode_label

    override fun initial(model: PodModel, device: PodDevice?) =
        RuleAction.SetMicrophoneMode(AapSetting.MicrophoneMode.Mode.AUTO)

    @Composable
    override fun Settings(
        current: RuleAction.SetMicrophoneMode?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetMicrophoneMode?) -> Unit,
    ) {
        // Device Settings' order.
        val modes = listOf(
            AapSetting.MicrophoneMode.Mode.AUTO,
            AapSetting.MicrophoneMode.Mode.ALWAYS_LEFT,
            AapSetting.MicrophoneMode.Mode.ALWAYS_RIGHT,
        )
        OptionRow(
            options = modes.map { it to it.labelRes },
            selected = current?.mode,
            onSelect = { onChange(RuleAction.SetMicrophoneMode(it)) },
        )
    }
}

@Composable
private fun <T> OptionRow(options: List<Pair<T, Int>>, selected: T?, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun PercentSlider(level: Int, range: IntRange, onChange: (Int) -> Unit) {
    // Reported on release, like Device Settings' sliders; each step echoed back through the
    // ViewModel's state would arrive late and make the thumb jump.
    var value by remember(level) { mutableFloatStateOf(level.toFloat()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${value.roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(42.dp),
        )
    }
}
