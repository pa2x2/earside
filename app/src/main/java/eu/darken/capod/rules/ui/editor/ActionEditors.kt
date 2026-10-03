package eu.darken.capod.rules.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.main.ui.overview.cards.components.AncModeSelector
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.action.SetAncModeHandler
import eu.darken.capod.rules.core.action.SetMediaVolumeHandler
import javax.inject.Inject
import kotlin.math.roundToInt

class SetAncModeEditor @Inject constructor(
    private val handler: SetAncModeHandler,
) : RuleActionEditor<RuleAction.SetAncMode> {

    override val type = RuleAction.SetAncMode::class
    override val icon = Icons.TwoTone.Headphones
    override val label = R.string.rules_action_anc_label

    override fun initial(model: PodModel, device: PodDevice?) = RuleAction.SetAncMode(ancModes(model, device).first())

    @Composable
    override fun Settings(
        current: RuleAction.SetAncMode?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetAncMode?) -> Unit,
    ) {
        val modes = ancModes(model, device)
        Column {
            // The same control as the Overview's, so the modes look like they do everywhere else.
            AncModeSelector(
                currentMode = current?.mode ?: modes.first(),
                supportedModes = modes,
                onModeSelected = { onChange(RuleAction.SetAncMode(it)) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            // Offered anyway: the user may enable the mode later. The rule then reports it couldn't apply.
            val reason = current?.let { action ->
                device?.let { handler.unavailableReason(LocalContext.current, it, action) }
            }
            if (reason != null) {
                SettingsInfoBox(
                    text = reason,
                    type = InfoBoxType.WARNING,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

// Before the AirPods have reported their modes: the same set the model's listening mode controls show.
private fun ancModes(model: PodModel, device: PodDevice?): List<AapSetting.AncMode.Value> =
    device?.ancMode?.supported?.takeIf { it.isNotEmpty() } ?: buildList {
        add(AapSetting.AncMode.Value.OFF)
        add(AapSetting.AncMode.Value.ON)
        add(AapSetting.AncMode.Value.TRANSPARENCY)
        if (model.features.hasAdaptiveAnc) add(AapSetting.AncMode.Value.ADAPTIVE)
    }

class SetConversationalAwarenessEditor @Inject constructor() : RuleActionEditor<RuleAction.SetConversationalAwareness> {

    override val type = RuleAction.SetConversationalAwareness::class
    override val icon = Icons.TwoTone.Hearing
    override val label = R.string.rules_action_ca_label

    override fun initial(model: PodModel, device: PodDevice?) = RuleAction.SetConversationalAwareness(enabled = true)

    @Composable
    override fun Settings(
        current: RuleAction.SetConversationalAwareness?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetConversationalAwareness?) -> Unit,
    ) {
        val options = listOf(true to R.string.rules_value_on, false to R.string.rules_value_off)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            options.forEachIndexed { index, (enabled, label) ->
                SegmentedButton(
                    selected = current?.enabled == enabled,
                    onClick = { onChange(RuleAction.SetConversationalAwareness(enabled)) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

class SetMediaVolumeEditor @Inject constructor(
    private val handler: SetMediaVolumeHandler,
) : RuleActionEditor<RuleAction.SetMediaVolume> {

    override val type = RuleAction.SetMediaVolume::class
    override val icon = Icons.AutoMirrored.TwoTone.VolumeUp
    override val label = R.string.rules_action_media_volume_label

    override fun initial(model: PodModel, device: PodDevice?) = RuleAction.SetMediaVolume(handler.currentPercent())

    @Composable
    override fun Settings(
        current: RuleAction.SetMediaVolume?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetMediaVolume?) -> Unit,
    ) {
        val percent = current?.percent ?: 0
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { onChange(RuleAction.SetMediaVolume(it.roundToInt())) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.rules_action_media_volume_value, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                )
            }
            Text(
                text = stringResource(R.string.rules_action_media_volume_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ChoiceRow(
    title: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsBaseItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        trailingContent = { RadioButton(selected = selected, onClick = onClick, enabled = enabled) },
    )
}

@Composable
internal fun CheckRow(
    title: String,
    icon: ImageVector?,
    checked: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsBaseItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onClick() }, enabled = enabled) },
    )
}
