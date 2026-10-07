package eu.darken.capod.main.ui.devicesettings.cards

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.main.ui.overview.cards.components.CompactBatterySummary
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.monitor.core.battery.BatteryHealth
import eu.darken.capod.pods.core.apple.ble.formatBatteryDurationShort
import eu.darken.capod.pods.core.apple.ble.isKnownBattery

internal val PodDevice.hasKnownBattery: Boolean
    get() = isKnownBattery(batteryLeft) || isKnownBattery(batteryRight) ||
        isKnownBattery(batteryHeadset) || isKnownBattery(batteryCase)

/**
 * The battery block at the bottom of [DeviceInfoCard]: the overview's compact level row, then a
 * line with each pod's time left (in the row's pod order) and a line with how much of the rated
 * listening time the pods still reach. Callers show it only for a live device.
 */
@Composable
internal fun InfoCardBattery(
    device: PodDevice,
    estimate: BatteryEstimate?,
    health: BatteryHealth.PerPod?,
    healthPending: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        CompactBatterySummary(device = device)
        formatTimeLeft(context, device, estimate)?.let { BatteryLine(it) }
        formatListeningTime(context, health, healthPending)?.let { BatteryLine(it) }
    }
}

@Composable
private fun BatteryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

private class ShownSlot(val pod: BatteryEstimate.Pod, val charging: Boolean)

/**
 * "~2h 41m · ~2h 27m left" while both pods run; a charging pod shows its time until full instead
 * ("~2h 41m left · full in 35m"), or nothing during an Optimized Charging hold. "(rated)" marks
 * figures that still rest on the model's published battery life.
 */
private fun formatTimeLeft(context: Context, device: PodDevice, estimate: BatteryEstimate?): String? {
    if (estimate == null) return null
    val slots = if (device.hasDualPods) {
        listOf(
            estimate.left?.let { ShownSlot(it, device.isLeftPodCharging == true) },
            estimate.right?.let { ShownSlot(it, device.isRightPodCharging == true) },
        )
    } else {
        listOf(estimate.headset?.let { ShownSlot(it, device.isHeadsetBeingCharged == true) })
    }.filterNotNull()

    fun approx(minutes: Int) = context.getString(
        R.string.device_settings_battery_approx,
        formatBatteryDurationShort(context, minutes),
    )

    val anyCharging = slots.any { it.charging }
    val parts = slots.mapNotNull { slot ->
        when {
            slot.charging -> slot.pod.minutesUntilCharged?.let {
                context.getString(R.string.device_settings_battery_time_until_full, formatBatteryDurationShort(context, it))
            }
            anyCharging -> context.getString(R.string.device_settings_battery_time_left, approx(slot.pod.minutesRemaining))
            else -> approx(slot.pod.minutesRemaining)
        }
    }
    if (parts.isEmpty()) return null

    var text = parts.joinToString(" · ")
    if (!anyCharging) text = context.getString(R.string.device_settings_battery_time_left, text)
    if (slots.any { !it.charging && it.pod.isProvisional }) {
        text = context.getString(R.string.device_settings_battery_time_rated, text)
    }
    return text.replaceFirstChar { it.uppercase() }
}

private fun formatListeningTime(context: Context, health: BatteryHealth.PerPod?, pending: Boolean): String? {
    fun approx(reading: BatteryHealth.Reading) =
        context.getString(R.string.device_settings_battery_approx, "${reading.percent}%")

    val left = health?.left
    val right = health?.right
    val headset = health?.headset
    return when {
        left != null && right != null -> context.getString(
            R.string.device_settings_battery_listening_time,
            "${approx(left)} · ${approx(right)}",
        )
        left != null -> context.getString(R.string.device_settings_battery_listening_time_left, left.percent)
        right != null -> context.getString(R.string.device_settings_battery_listening_time_right, right.percent)
        headset != null -> context.getString(R.string.device_settings_battery_listening_time, approx(headset))
        pending -> context.getString(R.string.device_settings_battery_listening_time_pending)
        else -> null
    }
}

@Preview2
@Composable
private fun InfoCardBatteryPreview() = PreviewWrapper {
    InfoCardBattery(
        device = MockPodDataProvider.dualPodFullyLoaded(),
        estimate = BatteryEstimate(
            left = BatteryEstimate.Pod(161, 0.2f, BatteryEstimate.Source.LIVE),
            right = BatteryEstimate.Pod(147, 0.2f, BatteryEstimate.Source.LIVE),
        ),
        health = BatteryHealth.PerPod(
            left = BatteryHealth.Reading(92, isPromotable = true),
            right = BatteryHealth.Reading(88, isPromotable = true),
        ),
        healthPending = false,
    )
}
