package eu.darken.capod.rules.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatteryAlert
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.Earbuds
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.device.BATTERY_LOW_MARGIN
import eu.darken.capod.rules.core.trigger.device.BATTERY_LOW_THRESHOLDS
import javax.inject.Inject

class AirPodsConnectedEditor @Inject constructor() : RuleTriggerEditor<RuleTrigger.AirPodsConnected> {

    override val type = RuleTrigger.AirPodsConnected::class
    override val icon = Icons.TwoTone.BluetoothConnected
    override val label = R.string.rules_trigger_airpods_connected_label

    @Composable
    override fun Settings(
        current: RuleTrigger.AirPodsConnected?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.AirPodsConnected?) -> Unit,
    ) {
        // Nothing to set, so picking the type completes the trigger.
        LaunchedEffect(current) { if (current == null) onChange(RuleTrigger.AirPodsConnected) }
    }
}

class WearingEditor @Inject constructor() : RuleTriggerEditor<RuleTrigger.Wearing> {

    override val type = RuleTrigger.Wearing::class
    override val icon = Icons.TwoTone.Earbuds
    override val label = R.string.rules_trigger_wearing_label

    @Composable
    override fun Settings(
        current: RuleTrigger.Wearing?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.Wearing?) -> Unit,
    ) {
        LaunchedEffect(current) { if (current == null) onChange(RuleTrigger.Wearing(RuleTrigger.Wearing.State.BOTH_IN)) }
        val options = if (model.features.hasDualPods) {
            listOf(
                RuleTrigger.Wearing.State.BOTH_IN to R.string.rules_wearing_both_option,
                RuleTrigger.Wearing.State.ONE_IN to R.string.rules_wearing_one_option,
                RuleTrigger.Wearing.State.NONE_IN to R.string.rules_wearing_none_option,
            )
        } else {
            listOf(
                RuleTrigger.Wearing.State.BOTH_IN to R.string.rules_wearing_worn_option,
                RuleTrigger.Wearing.State.NONE_IN to R.string.rules_wearing_not_worn_option,
            )
        }
        TriggerChoices(
            options = options.map { (state, label) -> state to stringResource(label) },
            selected = current?.state,
            onSelect = { onChange(RuleTrigger.Wearing(it)) },
            hint = R.string.rules_wearing_hint,
        )
    }
}

class BatteryLowEditor @Inject constructor() : RuleTriggerEditor<RuleTrigger.BatteryLow> {

    override val type = RuleTrigger.BatteryLow::class
    override val icon = Icons.TwoTone.BatteryAlert
    override val label = R.string.rules_trigger_battery_low_label

    @Composable
    override fun Settings(
        current: RuleTrigger.BatteryLow?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.BatteryLow?) -> Unit,
    ) {
        LaunchedEffect(current) { if (current == null) onChange(RuleTrigger.BatteryLow(DEFAULT_BATTERY_THRESHOLD)) }
        TriggerChoices(
            options = BATTERY_LOW_THRESHOLDS.map { it to stringResource(R.string.rules_battery_threshold_option, it) },
            selected = current?.thresholdPercent,
            onSelect = { onChange(RuleTrigger.BatteryLow(it)) },
            hint = R.string.rules_battery_low_hint,
            hintArg = BATTERY_LOW_MARGIN,
        )
    }

    companion object {
        private const val DEFAULT_BATTERY_THRESHOLD = 20
    }
}

@Composable
private fun <T> TriggerChoices(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    @StringRes hint: Int,
    hintArg: Any? = null,
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label) },
                )
            }
        }
        Text(
            text = if (hintArg != null) stringResource(hint, hintArg) else stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
