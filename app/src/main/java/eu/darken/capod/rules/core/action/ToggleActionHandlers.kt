package eu.darken.capod.rules.core.action

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

/** An action that switches one AAP setting on or off, which the device accepts in any state. */
abstract class ToggleActionHandler<A : RuleAction> : RuleActionHandler<A> {

    /** The setting's name, as Device Settings shows it. */
    @get:StringRes protected abstract val settingLabel: Int

    protected abstract fun A.enabled(): Boolean

    protected abstract fun create(enabled: Boolean): A

    /** Null while the device hasn't reported the setting. */
    protected abstract fun PodDevice.reported(): Boolean?

    protected abstract suspend fun send(address: BluetoothAddress, enabled: Boolean): DeviceControls.Result

    override fun isReady(device: PodDevice): Boolean = device.isAapReady && device.address != null

    override fun summary(context: Context, action: A): String = context.getString(
        R.string.rules_action_setting_summary,
        context.getString(settingLabel),
        context.getString(if (action.enabled()) R.string.rules_value_on else R.string.rules_value_off),
    )

    override fun unavailableReason(context: Context, device: PodDevice, action: A): String? = null

    override fun current(device: PodDevice, action: A): A? = device.reported()?.let { create(it) }

    override suspend fun execute(device: PodDevice, action: A): DeviceControls.Result =
        send(device.address!!, action.enabled())
}

class SetVolumeSwipeHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : ToggleActionHandler<RuleAction.SetVolumeSwipe>() {
    override val type = RuleAction.SetVolumeSwipe::class
    override val settingLabel = R.string.device_settings_volume_swipe_label
    override fun isSupported(features: PodModel.Features): Boolean = features.hasVolumeSwipe
    override fun RuleAction.SetVolumeSwipe.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetVolumeSwipe(enabled)
    override fun PodDevice.reported() = volumeSwipe?.enabled
    override suspend fun send(address: BluetoothAddress, enabled: Boolean) = deviceControls.setVolumeSwipe(address, enabled)
}

class SetPersonalizedVolumeHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : ToggleActionHandler<RuleAction.SetPersonalizedVolume>() {
    override val type = RuleAction.SetPersonalizedVolume::class
    override val settingLabel = R.string.device_settings_personalized_volume_label
    override fun isSupported(features: PodModel.Features): Boolean = features.hasPersonalizedVolume
    override fun RuleAction.SetPersonalizedVolume.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetPersonalizedVolume(enabled)
    override fun PodDevice.reported() = personalizedVolume?.enabled
    override suspend fun send(address: BluetoothAddress, enabled: Boolean) =
        deviceControls.setPersonalizedVolume(address, enabled)
}

class SetNcWithOneAirPodHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : ToggleActionHandler<RuleAction.SetNcWithOneAirPod>() {
    override val type = RuleAction.SetNcWithOneAirPod::class
    override val settingLabel = R.string.device_settings_nc_one_airpod_label
    override fun isSupported(features: PodModel.Features): Boolean = features.hasNcOneAirpod
    override fun RuleAction.SetNcWithOneAirPod.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetNcWithOneAirPod(enabled)
    override fun PodDevice.reported() = ncWithOneAirPod?.enabled
    override suspend fun send(address: BluetoothAddress, enabled: Boolean) =
        deviceControls.setNcWithOneAirPod(address, enabled)
}

class SetSleepDetectionHandler @Inject constructor(
    private val deviceControls: DeviceControls,
) : ToggleActionHandler<RuleAction.SetSleepDetection>() {
    override val type = RuleAction.SetSleepDetection::class
    override val settingLabel = R.string.device_settings_sleep_detection_label
    override fun isSupported(features: PodModel.Features): Boolean = features.hasSleepDetection
    override fun RuleAction.SetSleepDetection.enabled() = enabled
    override fun create(enabled: Boolean) = RuleAction.SetSleepDetection(enabled)
    override fun PodDevice.reported() = sleepDetection?.enabled
    override suspend fun send(address: BluetoothAddress, enabled: Boolean) =
        deviceControls.setSleepDetection(address, enabled)
}
