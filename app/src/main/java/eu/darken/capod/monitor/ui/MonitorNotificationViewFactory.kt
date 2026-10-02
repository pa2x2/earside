package eu.darken.capod.monitor.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.components.iconDrawableRes
import eu.darken.capod.main.ui.components.shortLabel
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.monitor.core.battery.CaseCharges
import eu.darken.capod.monitor.core.battery.caseCharges
import eu.darken.capod.monitor.core.battery.displayFraction
import eu.darken.capod.monitor.core.battery.displayMinutes
import eu.darken.capod.monitor.core.batteryCaseReading
import eu.darken.capod.monitor.core.receiver.NotificationControlsReceiver
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.formatBatteryDurationShort
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.pods.core.apple.ble.getBatteryDrawable
import eu.darken.capod.pods.core.apple.ble.isKnownBattery
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt


class MonitorNotificationViewFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createContentView(device: PodDevice): RemoteViews = when {
        device.hasDualPods -> createDualPods(device)
        device.model != PodModel.UNKNOWN -> createSinglePod(device)
        else -> createUnknownDevice(device)
    }

    private fun createDualPods(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_small
    ).apply {
        // Left
        val leftPercent = device.batteryLeft
        setImageViewResource(R.id.pod_left_icon, device.leftPodIcon)
        setTextViewText(R.id.pod_left_label, formatBatteryPercent(context, leftPercent))
        val isLeftPodCharging = device.isLeftPodCharging ?: false
        setViewVisibility(R.id.pod_left_charging, if (isLeftPodCharging) View.VISIBLE else View.GONE)
        val isLeftPodInEar = device.isLeftInEar ?: false
        setViewVisibility(R.id.pod_left_ear, if (isLeftPodInEar) View.VISIBLE else View.GONE)

        // Case
        setViewVisibility(R.id.pod_case_charging, if (device.hasCase) View.VISIBLE else View.GONE)
        if (device.hasCase) {
            setImageViewResource(R.id.pod_case_icon, device.caseIcon)
            val casePercent = device.batteryCase
            setTextViewText(R.id.pod_case_label, formatBatteryPercent(context, casePercent))
            setViewVisibility(R.id.pod_case_charging, if (device.isCaseCharging == true) View.VISIBLE else View.GONE)
        }

        // Right
        val rightPercent = device.batteryRight
        setImageViewResource(R.id.pod_right_icon, device.rightPodIcon)
        setTextViewText(R.id.pod_right_label, formatBatteryPercent(context, rightPercent))
        val isRightPodCharging = device.isRightPodCharging ?: false
        setViewVisibility(R.id.pod_right_charging, if (isRightPodCharging) View.VISIBLE else View.GONE)
        val isRightPodInEar = device.isRightInEar ?: false
        setViewVisibility(R.id.pod_right_ear, if (isRightPodInEar) View.VISIBLE else View.GONE)
    }

    private fun createSinglePod(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_single_pods_small
    ).apply {
        val headsetPercent = device.batteryHeadset
        setTextViewText(R.id.headphones_label, device.getLabel(context))
        setImageViewResource(R.id.headphones_icon, device.iconRes)
        setImageViewResource(R.id.headphones_battery_icon, getBatteryDrawable(headsetPercent))
        setTextViewText(R.id.headphones_battery_label, formatBatteryPercent(context, headsetPercent))
        if (device.hasEarDetection) {
            setViewVisibility(R.id.headphones_worn, if (device.isBeingWorn == true) View.VISIBLE else View.GONE)
        }
        setViewVisibility(
            R.id.headphones_charging,
            if (device.isHeadsetBeingCharged == true) View.VISIBLE else View.GONE
        )
    }

    private fun createUnknownDevice(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_unknown_device_small
    ).apply {
        setTextViewText(R.id.device, device.getLabel(context))
    }

    fun createBigContentView(device: PodDevice, estimate: BatteryEstimate? = null): RemoteViews = when {
        device.hasDualPods -> createDualPodsBig(device, estimate)
        device.model != PodModel.UNKNOWN -> createSinglePodBig(device, estimate)
        else -> createUnknownDeviceBig(device)
    }

    private fun createDualPodsBig(device: PodDevice, estimate: BatteryEstimate?): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_dual_pods_big
    ).apply {
        // Left
        val leftPercent = device.batteryLeft
        setImageViewResource(R.id.pod_left_icon, device.leftPodIcon)
        setProgressBar(R.id.pod_left_progress, 100, percentToInt(leftPercent), false)
        setTextViewText(
            R.id.pod_left_label,
            formatBatteryPercent(context, leftPercent) +
                estimateSuffix(estimate?.left, device.isLeftPodCharging == true)
        )
        val isLeftPodCharging = device.isLeftPodCharging ?: false
        setViewVisibility(R.id.pod_left_charging, if (isLeftPodCharging) View.VISIBLE else View.GONE)
        val isLeftPodInEar = device.isLeftInEar ?: false
        setViewVisibility(R.id.pod_left_ear, if (isLeftPodInEar) View.VISIBLE else View.GONE)

        // Case
        setViewVisibility(R.id.pod_case_container, if (device.hasCase) View.VISIBLE else View.GONE)
        if (device.hasCase) {
            setImageViewResource(R.id.pod_case_icon, device.caseIcon)
            val casePercent = device.batteryCase
            setProgressBar(R.id.pod_case_progress, 100, percentToInt(casePercent), false)
            setTextViewText(
                R.id.pod_case_label,
                formatBatteryPercent(context, casePercent) + caseChargesSuffix(device)
            )
            setViewVisibility(R.id.pod_case_charging, if (device.isCaseCharging == true) View.VISIBLE else View.GONE)
        }

        // Right
        val rightPercent = device.batteryRight
        setImageViewResource(R.id.pod_right_icon, device.rightPodIcon)
        setProgressBar(R.id.pod_right_progress, 100, percentToInt(rightPercent), false)
        setTextViewText(
            R.id.pod_right_label,
            formatBatteryPercent(context, rightPercent) +
                estimateSuffix(estimate?.right, device.isRightPodCharging == true)
        )
        val isRightPodCharging = device.isRightPodCharging ?: false
        setViewVisibility(R.id.pod_right_charging, if (isRightPodCharging) View.VISIBLE else View.GONE)
        val isRightPodInEar = device.isRightInEar ?: false
        setViewVisibility(R.id.pod_right_ear, if (isRightPodInEar) View.VISIBLE else View.GONE)

        bindControls(device)
    }

    private fun createSinglePodBig(device: PodDevice, estimate: BatteryEstimate?): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_single_pods_big
    ).apply {
        val headsetPercent = device.batteryHeadset
        setTextViewText(R.id.headphones_label, device.getLabel(context))
        setImageViewResource(R.id.headphones_icon, device.iconRes)
        setProgressBar(R.id.headphones_battery_progress, 100, percentToInt(headsetPercent), false)
        setTextViewText(
            R.id.headphones_battery_label,
            formatBatteryPercent(context, headsetPercent) +
                estimateSuffix(estimate?.headset, device.isHeadsetBeingCharged == true)
        )
        if (device.hasEarDetection) {
            setViewVisibility(R.id.headphones_worn, if (device.isBeingWorn == true) View.VISIBLE else View.GONE)
        }
        setViewVisibility(
            R.id.headphones_charging,
            if (device.isHeadsetBeingCharged == true) View.VISIBLE else View.GONE
        )

        bindControls(device)
    }

    private fun createUnknownDeviceBig(device: PodDevice): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_unknown_device_big
    ).apply {
        setTextViewText(R.id.device, device.getLabel(context))
    }

    // Only offered over a ready AAP session: without one a tap has nothing to send through.
    private fun RemoteViews.bindControls(device: PodDevice) {
        val address = device.address
        if (!device.isAapReady || address == null) {
            setViewVisibility(R.id.controls_container, View.GONE)
            return
        }

        val ancButtons = createAncButtons(device, address)
        val caButton = createConversationAwarenessButton(device, address)
        if (ancButtons.isEmpty() && caButton == null) {
            setViewVisibility(R.id.controls_container, View.GONE)
            return
        }

        removeAllViews(R.id.controls_container)
        ancButtons.forEach { addView(R.id.controls_container, it) }
        if (caButton != null) {
            // CA is an independent switch, the divider keeps it from reading as another listening mode
            if (ancButtons.isNotEmpty()) {
                addView(
                    R.id.controls_container,
                    RemoteViews(context.packageName, R.layout.monitor_notification_control_divider)
                )
            }
            addView(R.id.controls_container, caButton)
        }
        setViewVisibility(R.id.controls_container, View.VISIBLE)
    }

    private fun createAncButtons(device: PodDevice, address: BluetoothAddress): List<RemoteViews> {
        val current = device.ancMode?.current
        val modes = device.visibleAncModes
        if (!device.hasAncControl || current == null) return emptyList()

        val selected = device.pendingAncMode?.takeIf { it in modes } ?: current
        return modes.map { mode ->
            createControlButton(
                iconRes = mode.iconDrawableRes(),
                label = mode.shortLabel(context),
                isActive = mode == selected,
                activeDescriptionRes = R.string.monitor_notification_control_selected,
                inactiveDescriptionRes = null,
                intent = NotificationControlsReceiver.setAncModeIntent(context, address, mode),
            )
        }
    }

    private fun createConversationAwarenessButton(device: PodDevice, address: BluetoothAddress): RemoteViews? {
        val enabled = device.conversationalAwareness?.enabled
        if (!device.hasConversationAwareness || enabled == null) return null

        return createControlButton(
            iconRes = R.drawable.ic_baseline_hearing_24,
            label = context.getString(R.string.conversation_awareness_label),
            isActive = enabled,
            activeDescriptionRes = R.string.monitor_notification_control_on,
            inactiveDescriptionRes = R.string.monitor_notification_control_off,
            intent = NotificationControlsReceiver.setConversationalAwarenessIntent(context, address, !enabled),
        )
    }

    private fun createControlButton(
        @DrawableRes iconRes: Int,
        label: String,
        isActive: Boolean,
        @StringRes activeDescriptionRes: Int,
        @StringRes inactiveDescriptionRes: Int?,
        intent: Intent,
    ): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.monitor_notification_control_button
    ).apply {
        setImageViewResource(R.id.control_button_icon, iconRes)
        // Icon-only buttons: the description is all TalkBack has, so it carries the state too
        val descriptionRes = if (isActive) activeDescriptionRes else inactiveDescriptionRes
        setContentDescription(R.id.control_button, descriptionRes?.let { context.getString(it, label) } ?: label)
        if (isActive) {
            setInt(R.id.control_button, "setBackgroundResource", R.drawable.notification_control_button_bg_selected)
            setInt(R.id.control_button_icon, "setColorFilter", Color.WHITE)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
        )
        setOnClickPendingIntent(R.id.control_button, pendingIntent)
    }

    private fun percentToInt(percent: Float): Int =
        if (isKnownBattery(percent)) (percent * 100).roundToInt().coerceIn(0, 100) else 0

    private fun estimateSuffix(pod: BatteryEstimate.Pod?, charging: Boolean): String =
        pod?.displayMinutes(charging)?.let { " · ${formatBatteryDurationShort(context, it)}" } ?: ""

    /**
     * How many more full pair charges the case holds, e.g. " · 0.8 charges". Empty when the model
     * publishes no case figure, so the row keeps showing the percentage alone.
     */
    private fun caseChargesSuffix(device: PodDevice): String {
        val spec = device.model.caseSpec ?: return ""
        val charges = caseCharges(spec, device.batteryCaseReading) ?: return ""
        val amount = String.format(Locale.getDefault(), "%.1f", charges.displayFraction)
        // An empty case has nothing for an open ended spec to undersell, so it drops the "+".
        val resId =
            if (spec.isLowerBound && charges.display != CaseCharges.Display.EMPTY) {
                R.string.battery_case_charges_short_at_least
            } else {
                R.string.battery_case_charges_short
            }
        return " · ${context.getString(resId, amount)}"
    }

}
