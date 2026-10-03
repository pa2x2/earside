package eu.darken.capod.rules.core.action

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import eu.darken.capod.R
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import javax.inject.Inject

/**
 * Sets the phone's media volume. Android keeps a volume per output, so the action waits until the
 * AirPods play the phone's media; otherwise it would set the speaker's volume.
 */
class SetMediaVolumeHandler @Inject constructor(
    private val mediaControl: MediaControl,
    private val audioManager: AudioManager,
) : RuleActionHandler<RuleAction.SetMediaVolume> {

    override val type = RuleAction.SetMediaVolume::class

    override fun isSupported(features: PodModel.Features): Boolean = true

    override fun summary(context: Context, action: RuleAction.SetMediaVolume): String =
        context.getString(R.string.rules_action_media_volume_summary, action.percent)

    override fun isReady(device: PodDevice): Boolean = device.isSystemConnected && playsMedia(device.address)

    override fun unavailableReason(context: Context, device: PodDevice, action: RuleAction.SetMediaVolume): String? =
        if (mediaControl.isVolumeFixed) context.getString(R.string.rules_action_media_volume_unavailable_fixed) else null

    override fun current(device: PodDevice, action: RuleAction.SetMediaVolume): RuleAction.SetMediaVolume =
        action.atIndex(mediaControl.currentMusicVolume(), mediaControl.musicVolumeRange())

    override suspend fun execute(device: PodDevice, action: RuleAction.SetMediaVolume): DeviceControls.Result {
        val prior = mediaControl.currentMusicVolume()
        val target = mediaVolumeIndex(action.percent, mediaControl.musicVolumeRange())
        val applied = mediaControl.setMusicVolume(target)
            ?: return DeviceControls.Result.Failed(SecurityException("Media volume change denied"))
        // ColorOS accepts the write from the background and leaves the volume as it was.
        if (applied == prior && target != prior) {
            return DeviceControls.Result.Failed(IllegalStateException("Media volume stayed at $applied, wanted $target"))
        }
        return DeviceControls.Result.Sent
    }

    fun currentPercent(): Int = mediaVolumePercent(mediaControl.currentMusicVolume(), mediaControl.musicVolumeRange())

    private fun playsMedia(address: BluetoothAddress?): Boolean {
        if (address == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            return audioManager.getAudioDevicesForAttributes(media).any {
                it.type in BLUETOOTH_MEDIA_TYPES && isAddressOf(it.address, address)
            }
        }
        // No way to ask for the active media route here: any Bluetooth media output for the AirPods.
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in BLUETOOTH_MEDIA_TYPES &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || isAddressOf(it.address, address))
        }
    }

    companion object {
        private val BLUETOOTH_MEDIA_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )

        /**
         * Without Bluetooth access, Android 14+ reports "XX:XX:XX:XX:EE:FF"; some ROMs report no address
         * at all, which then can't rule the output out.
         */
        private fun isAddressOf(reported: String, address: BluetoothAddress): Boolean = when {
            reported.isBlank() -> true
            reported.startsWith("XX:") -> reported.takeLast(5).equals(address.takeLast(5), ignoreCase = true)
            else -> reported.equals(address, ignoreCase = true)
        }
    }
}

/** The index in [range] that [percent] sets, rounded to the nearest step. */
internal fun mediaVolumeIndex(percent: Int, range: IntRange): Int {
    val steps = range.last - range.first
    return range.first + (steps * percent.coerceIn(0, 100) + 50) / 100
}

/** Inverse of [mediaVolumeIndex]: with up to 100 steps, every index maps back to itself. */
internal fun mediaVolumePercent(index: Int, range: IntRange): Int {
    val steps = range.last - range.first
    if (steps <= 0) return 100
    return ((index.coerceIn(range) - range.first) * 200 + steps) / (2 * steps)
}

/**
 * The phone at [index] as the action that sets it. Several percentages can land on the same step, so
 * it's this action whenever the phone is at its step: otherwise a rule's "30%" would read back as
 * "33%", look like a change by hand, and never be put back.
 */
internal fun RuleAction.SetMediaVolume.atIndex(index: Int, range: IntRange): RuleAction.SetMediaVolume =
    if (mediaVolumeIndex(percent, range) == index) this else RuleAction.SetMediaVolume(mediaVolumePercent(index, range))
