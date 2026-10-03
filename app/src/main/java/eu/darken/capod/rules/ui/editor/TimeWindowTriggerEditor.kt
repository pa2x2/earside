package eu.darken.capod.rules.ui.editor

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.time.formatTime
import eu.darken.capod.rules.core.trigger.time.weekOf
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import javax.inject.Inject

class TimeWindowEditor @Inject constructor() : RuleTriggerEditor<RuleTrigger.TimeWindow> {

    override val type = RuleTrigger.TimeWindow::class
    override val icon = Icons.TwoTone.Schedule
    override val label = R.string.rules_trigger_time_window_label

    @Composable
    override fun Settings(
        current: RuleTrigger.TimeWindow?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.TimeWindow?) -> Unit,
    ) {
        TimeWindowFields(current, onChange)
    }
}

private enum class WindowEdge(@StringRes val label: Int) {
    START(R.string.rules_time_window_start_label),
    END(R.string.rules_time_window_end_label),
}

/** A new window starts with no times and every day; it's reported once both times are set and differ. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeWindowFields(
    initial: RuleTrigger.TimeWindow?,
    onChange: (RuleTrigger.TimeWindow?) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var start by rememberSaveable { mutableStateOf(initial?.start) }
    var end by rememberSaveable { mutableStateOf(initial?.end) }
    var days by rememberSaveable { mutableStateOf(initial?.days ?: DayOfWeek.entries.toSet()) }
    var picking by rememberSaveable { mutableStateOf<WindowEdge?>(null) }

    fun report() {
        val from = start
        val until = end
        onChange(
            if (from != null && until != null && from != until && days.isNotEmpty()) {
                RuleTrigger.TimeWindow(from, until, days)
            } else {
                null
            },
        )
    }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(WindowEdge.START, start, onClick = { picking = WindowEdge.START }, modifier = Modifier.weight(1f))
            TimeField(WindowEdge.END, end, onClick = { picking = WindowEdge.END }, modifier = Modifier.weight(1f))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            weekOf(locale).forEach { day ->
                FilterChip(
                    selected = day in days,
                    onClick = {
                        days = if (day in days) days - day else days + day
                        report()
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                )
            }
        }
        val from = start
        val until = end
        val (hint, isError) = when {
            from != null && from == until -> R.string.rules_time_window_same_time_error to true
            days.isEmpty() -> R.string.rules_time_window_no_days_error to true
            from != null && until != null && until < from -> R.string.rules_time_window_overnight_hint to false
            else -> null to false
        }
        if (hint != null) {
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    picking?.let { edge ->
        TimeDialog(
            edge = edge,
            initial = (if (edge == WindowEdge.START) start else end) ?: LocalTime.now().withMinute(0),
            onPick = { time ->
                if (edge == WindowEdge.START) start = time else end = time
                picking = null
                report()
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun TimeField(
    edge: WindowEdge,
    time: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(edge.label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = time?.let { formatTime(LocalContext.current, it) } ?: stringResource(R.string.rules_time_window_not_set),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun TimeDialog(
    edge: WindowEdge,
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        title = {
            Text(
                text = stringResource(edge.label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) {
                        TimePickerDisplayMode.Input
                    } else {
                        TimePickerDisplayMode.Picker
                    }
                },
                displayMode = displayMode,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel_action)) }
        },
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) TimePicker(state = state) else TimeInput(state = state)
    }
}
