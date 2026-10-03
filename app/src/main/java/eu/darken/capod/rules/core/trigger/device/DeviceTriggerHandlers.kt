package eu.darken.capod.rules.core.trigger.device

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.core.trigger.RuleTriggerHandler
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AirPodsConnectedHandler @Inject constructor(
    private val profilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
) : RuleTriggerHandler<RuleTrigger.AirPodsConnected> {

    override val type = RuleTrigger.AirPodsConnected::class

    override fun isSupported(features: PodModel.Features): Boolean = true

    override fun summary(context: Context, trigger: RuleTrigger.AirPodsConnected): String =
        context.getString(R.string.rules_trigger_airpods_connected_summary)

    override fun holdsNowText(context: Context, trigger: RuleTrigger.AirPodsConnected): String =
        context.getString(R.string.rules_apply_now_airpods_connected)

    override fun undoText(context: Context, trigger: RuleTrigger.AirPodsConnected): String =
        context.getString(R.string.rules_undo_airpods_connected)

    override val missingRequirements: Flow<List<RuleRequirement>> = flowOf(emptyList())

    // PodDevice.isSystemConnected, read from its sources: DeviceMonitor reports nothing connected
    // until the phone's list arrives, and that first "disconnected" would end the occurrence and
    // start a new one on every service start. The Bluetooth connection rather than the AAP session,
    // which can drop and come back on its own, re-applying the rule over a mode picked by hand.
    override fun condition(profileId: ProfileId, trigger: RuleTrigger.AirPodsConnected): Flow<TriggerCondition> = combine(
        profilesRepo.profiles.map { profiles -> profiles.firstOrNull { it.id == profileId }?.address },
        bluetoothManager.connectedDevices,
    ) { address, connected ->
        TriggerCondition.Known(holds = address != null && connected.any { it.address == address })
    }.distinctUntilChanged()

    override val maxWait: Duration = MAX_WAIT

    companion object {
        /** The phone connects first; the AAP session most actions need comes up some seconds later. */
        val MAX_WAIT = 1.minutes
    }
}

class WearingHandler @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
) : RuleTriggerHandler<RuleTrigger.Wearing> {

    override val type = RuleTrigger.Wearing::class

    override fun isSupported(features: PodModel.Features): Boolean = features.hasEarDetection

    override fun summary(context: Context, trigger: RuleTrigger.Wearing): String = context.getString(
        when (trigger.state) {
            RuleTrigger.Wearing.State.BOTH_IN -> R.string.rules_trigger_wearing_both_summary
            RuleTrigger.Wearing.State.ONE_IN -> R.string.rules_trigger_wearing_one_summary
            RuleTrigger.Wearing.State.NONE_IN -> R.string.rules_trigger_wearing_none_summary
        }
    )

    override fun holdsNowText(context: Context, trigger: RuleTrigger.Wearing): String = context.getString(
        when (trigger.state) {
            RuleTrigger.Wearing.State.BOTH_IN -> R.string.rules_apply_now_wearing_both
            RuleTrigger.Wearing.State.ONE_IN -> R.string.rules_apply_now_wearing_one
            RuleTrigger.Wearing.State.NONE_IN -> R.string.rules_apply_now_wearing_none
        }
    )

    override fun undoText(context: Context, trigger: RuleTrigger.Wearing): String = context.getString(
        when (trigger.state) {
            RuleTrigger.Wearing.State.BOTH_IN -> R.string.rules_undo_wearing_both
            RuleTrigger.Wearing.State.ONE_IN -> R.string.rules_undo_wearing_one
            RuleTrigger.Wearing.State.NONE_IN -> R.string.rules_undo_wearing_none
        }
    )

    override val missingRequirements: Flow<List<RuleRequirement>> = flowOf(emptyList())

    override fun condition(profileId: ProfileId, trigger: RuleTrigger.Wearing): Flow<TriggerCondition> = deviceMonitor.devices
        .map { devices -> devices.firstOrNull { it.profileId == profileId }?.wearingState() }
        .distinctUntilChanged()
        .map { state -> state?.let { TriggerCondition.Known(holds = it == trigger.state) } ?: TriggerCondition.Unknown }
}

/**
 * Which [RuleTrigger.Wearing.State] the device is in, or null if that's unknown. AAP only: BLE
 * reports phantom "in ear" for AirPods resting in the case, so it would fire "both in" from the
 * case. With ear detection switched off on the AirPods their readings mean nothing either.
 */
internal fun PodDevice.wearingState(): RuleTrigger.Wearing.State? {
    if (!hasAapEarDetection || earDetectionEnabled?.enabled == false) return null
    // The aggregates, not isLeftInEar/isRightInEar: those also need to know which pod is primary.
    val both = isBeingWorn ?: return null
    val either = isEitherPodInEar ?: return null
    return when {
        both || (either && !hasDualPods) -> RuleTrigger.Wearing.State.BOTH_IN
        either -> RuleTrigger.Wearing.State.ONE_IN
        else -> RuleTrigger.Wearing.State.NONE_IN
    }
}

class BatteryLowHandler @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
) : RuleTriggerHandler<RuleTrigger.BatteryLow> {

    override val type = RuleTrigger.BatteryLow::class

    override fun isSupported(features: PodModel.Features): Boolean = true

    override fun summary(context: Context, trigger: RuleTrigger.BatteryLow): String =
        context.getString(R.string.rules_trigger_battery_low_summary, trigger.thresholdPercent)

    override fun holdsNowText(context: Context, trigger: RuleTrigger.BatteryLow): String =
        context.getString(R.string.rules_apply_now_battery_low)

    override fun undoText(context: Context, trigger: RuleTrigger.BatteryLow): String =
        context.getString(R.string.rules_undo_battery_low, trigger.thresholdPercent + BATTERY_LOW_MARGIN)

    override val missingRequirements: Flow<List<RuleRequirement>> = flowOf(emptyList())

    override fun condition(profileId: ProfileId, trigger: RuleTrigger.BatteryLow): Flow<TriggerCondition> = deviceMonitor.devices
        .map { devices -> devices.firstOrNull { it.profileId == profileId }?.liveBatteries()?.lowestInUse() }
        .distinctUntilChanged()
        .batteryLowCondition(trigger.thresholdPercent)
}

/** The choices offered. BLE reports the battery in steps of 10. */
val BATTERY_LOW_THRESHOLDS = listOf(10, 20, 30)

/** How far above the threshold the battery must be back before it no longer counts as low. */
const val BATTERY_LOW_MARGIN = 10

/** [disconnected]: AAP says the pod isn't connected to the AirPods, e.g. in a closed case. */
internal data class PodBattery(val percent: Float, val charging: Boolean?, val disconnected: Boolean = false)

/**
 * Live readings of each pod, or of single-battery headphones; never the case. Never the cache
 * either: a stale number must not start a rule, nor end one.
 */
internal fun PodDevice.liveBatteries(): List<PodBattery> = listOfNotNull(
    podBattery(
        aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent,
        aap?.isLeftCharging ?: (ble as? HasChargeDetectionDual)?.isLeftPodCharging,
        aap?.leftChargingState,
    ),
    podBattery(
        aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent,
        aap?.isRightCharging ?: (ble as? HasChargeDetectionDual)?.isRightPodCharging,
        aap?.rightChargingState,
    ),
    podBattery(
        aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent,
        aap?.isHeadsetCharging ?: (ble as? HasChargeDetection)?.isHeadsetBeingCharged,
        aap?.headsetChargingState,
    ),
)

private fun podBattery(percent: Float?, charging: Boolean?, aapState: AapPodState.ChargingState?): PodBattery? =
    percent?.takeIf { it >= 0f }?.let {
        PodBattery(it, charging, disconnected = aapState == AapPodState.ChargingState.DISCONNECTED)
    }

/**
 * The lowest battery of the pods in use, in whole percent; null if none is. A pod that's charging
 * sits in the case (or headphones are plugged in) and one AAP reports as disconnected is in a
 * closed case: neither is what's running low.
 */
internal fun List<PodBattery>.lowestInUse(): Int? =
    filter { it.charging != true && !it.disconnected }.minOfOrNull { (it.percent * 100).roundToInt() }

/**
 * Low at [thresholdPercent] or below, no longer low only from [BATTERY_LOW_MARGIN] above it, so a
 * reading wavering around the threshold doesn't start the rule over and over. In between it stays
 * at [last], and is unknown (null) without one.
 */
internal fun isBatteryLow(percent: Int?, thresholdPercent: Int, last: Boolean?): Boolean? = when {
    percent == null -> null
    percent <= thresholdPercent -> true
    percent >= thresholdPercent + BATTERY_LOW_MARGIN -> false
    else -> last
}

/**
 * What this flow last reported stands in for the margin's "in between". Starting there it reports
 * unknown, so the engine keeps its persisted observation and the margin holds across restarts.
 */
internal fun Flow<Int?>.batteryLowCondition(thresholdPercent: Int): Flow<TriggerCondition> = flow {
    var last: Boolean? = null
    collect { percent ->
        val low = isBatteryLow(percent, thresholdPercent, last)
        if (low != null) last = low
        emit(low?.let { TriggerCondition.Known(holds = it) } ?: TriggerCondition.Unknown)
    }
}
