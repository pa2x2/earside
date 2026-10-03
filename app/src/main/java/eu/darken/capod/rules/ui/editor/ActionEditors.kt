package eu.darken.capod.rules.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.main.ui.components.icon
import eu.darken.capod.main.ui.components.shortLabelRes
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.action.SetAncModeHandler
import javax.inject.Inject

class SetAncModeEditor @Inject constructor(
    private val handler: SetAncModeHandler,
) : RuleActionEditor<RuleAction.SetAncMode> {

    override val type = RuleAction.SetAncMode::class
    override val icon = Icons.TwoTone.Headphones
    override val label = R.string.rules_action_anc_label

    @Composable
    override fun Settings(
        current: RuleAction.SetAncMode?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetAncMode?) -> Unit,
    ) {
        val modes = device?.ancMode?.supported ?: modelAncModes(model.features)
        Column {
            modes.forEach { mode ->
                ChoiceRow(
                    title = stringResource(mode.shortLabelRes()),
                    icon = mode.icon(),
                    selected = current?.mode == mode,
                    onClick = { onChange(RuleAction.SetAncMode(mode)) },
                )
            }
            // Offered anyway: the user may enable the mode later. The rule then reports it couldn't apply.
            val reason = current?.let { action ->
                device?.let { handler.unavailableReason(LocalContext.current, it, action) }
            }
            if (reason != null) {
                SettingsInfoBox(
                    text = reason,
                    type = InfoBoxType.WARNING,
                )
            }
        }
    }
}

// Before the AirPods have reported their modes; the same set the model's listening mode controls show.
private fun modelAncModes(features: PodModel.Features): List<AapSetting.AncMode.Value> = buildList {
    add(AapSetting.AncMode.Value.OFF)
    add(AapSetting.AncMode.Value.ON)
    add(AapSetting.AncMode.Value.TRANSPARENCY)
    if (features.hasAdaptiveAnc) add(AapSetting.AncMode.Value.ADAPTIVE)
}

class SetConversationalAwarenessEditor @Inject constructor() : RuleActionEditor<RuleAction.SetConversationalAwareness> {

    override val type = RuleAction.SetConversationalAwareness::class
    override val icon = Icons.TwoTone.Hearing
    override val label = R.string.rules_action_ca_label

    @Composable
    override fun Settings(
        current: RuleAction.SetConversationalAwareness?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleAction.SetConversationalAwareness?) -> Unit,
    ) {
        Column {
            listOf(true, false).forEach { enabled ->
                ChoiceRow(
                    title = stringResource(if (enabled) R.string.rules_value_on else R.string.rules_value_off),
                    icon = null,
                    selected = current?.enabled == enabled,
                    onClick = { onChange(RuleAction.SetConversationalAwareness(enabled)) },
                )
            }
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
