package eu.darken.capod.main.ui.equalizer

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceMonitor: DeviceMonitor,
    private val aapManager: AapConnectionManager,
) : ViewModel4(dispatcherProvider) {

    private val targetProfileId = MutableStateFlow<ProfileId?>(null)
    private var initialized = false

    fun initialize(profileId: ProfileId) {
        if (initialized && targetProfileId.value == profileId) return
        initialized = true
        targetProfileId.value = profileId
    }

    sealed interface Event {
        data class SendFailed(val message: String?) : Event
        data object NotConfirmedByDevice : Event
    }

    val events = SingleEventFlow<Event>()

    /**
     * The curve the user last asked for, shown in place of the device's until it has been written.
     * Writes are throttled while a fader is dragged, so the device state trails the finger; showing
     * it would make the fader jump back to an older position.
     */
    private val requested = MutableStateFlow<AapSetting.CustomEq?>(null)

    init {
        // StateFlow conflates, so whatever was requested during the pause is what goes out next,
        // and the final position of a drag is always written.
        launch {
            requested.filterNotNull().collect { eq ->
                write(eq)
                requested.compareAndSet(eq, null)
                delay(WRITE_INTERVAL_MS)
            }
        }
        launch {
            aapManager.settingRejectedEvents.collect { (address, command) ->
                if (command is AapCommand.SetCustomEq && address == currentAddress()) {
                    events.tryEmit(Event.NotConfirmedByDevice)
                }
            }
        }
    }

    val state = targetProfileId.flatMapLatest { profileId ->
        if (profileId == null) return@flatMapLatest flowOf(State())
        combine(deviceForProfile(profileId), requested) { device, requestedEq ->
            State(
                device = device,
                eq = requestedEq ?: device?.customEq,
                isAapReady = device?.isAapReady == true,
                isWaitingForEar = device?.aap?.isEitherPodInEar == false,
            )
        }
    }.asLiveState()

    private fun deviceForProfile(profileId: ProfileId): Flow<PodDevice?> =
        deviceMonitor.devices.flatMapLatest { devices ->
            val live = devices.firstOrNull { it.profileId == profileId }
            flow<PodDevice?> { emit(live ?: deviceMonitor.getDeviceForProfile(profileId)) }
        }

    data class State(
        val device: PodDevice? = null,
        val eq: AapSetting.CustomEq? = null,
        val isAapReady: Boolean = false,
        val isWaitingForEar: Boolean = false,
    )

    /** Takes the whole curve rather than one band, so fast successive edits can't race each other. */
    fun setEq(eq: AapSetting.CustomEq) {
        requested.value = eq
    }

    private suspend fun currentAddress(): String? {
        val profileId = targetProfileId.value ?: return null
        return deviceMonitor.getDeviceForProfile(profileId)?.address
    }

    private suspend fun write(eq: AapSetting.CustomEq) {
        val address = currentAddress() ?: return
        try {
            aapManager.sendCommand(address, AapCommand.SetCustomEq(eq))
            log(TAG, INFO) { "Sent $eq to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send $eq: ${e.message}" }
            events.emit(Event.SendFailed(e.message))
        }
    }

    companion object {
        private val TAG = logTag("Equalizer", "VM")

        /** About 7 writes a second while dragging: enough to hear the change follow the finger. */
        private const val WRITE_INTERVAL_MS = 150L
    }
}
