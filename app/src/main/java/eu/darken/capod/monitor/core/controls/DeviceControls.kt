package eu.darken.capod.monitor.core.controls

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one place that changes listening mode and Conversation Awareness on a device. The QS tile,
 * notification, widget, Overview, Device Settings, stem presses and device rules all go through
 * here, so the same control behaves the same whatever triggered it.
 *
 * Listening mode sends are process-scoped and per device: a new request cancels one that hasn't
 * gone out yet. Without that, surfaces stacked separate `SetAncMode` commands (QS taps across
 * panel sessions, a tile tap racing a rule), overwhelming the AAP verification loop and triggering
 * "Rejected after retry" storms that left the device unresponsive until the app restarted.
 * Debouncing is a property of the input surface, so callers that see tap bursts pass one.
 */
@Singleton
class DeviceControls @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val aapManager: AapConnectionManager,
) {

    sealed interface Result {
        data object Sent : Result
        /** A newer request for the same device replaced this one before it went out. */
        data object Superseded : Result
        data class Failed(val error: Exception) : Result
    }

    private class PendingSend(val job: Job, val result: CompletableDeferred<Result>)

    private val lock = Any()
    private val pendingSends = mutableMapOf<BluetoothAddress, PendingSend>()
    private val timeoutJobs = mutableMapOf<BluetoothAddress, Job>()

    private val _pendingAncModes = MutableStateFlow<Map<BluetoothAddress, AapSetting.AncMode.Value>>(emptyMap())

    /** The listening mode each device was last asked for, until the device confirms it or [setAncMode]'s timeout ends. */
    val pendingAncModes: StateFlow<Map<BluetoothAddress, AapSetting.AncMode.Value>> = _pendingAncModes.asStateFlow()

    fun setAncMode(
        address: BluetoothAddress,
        mode: AapSetting.AncMode.Value,
        debounce: Duration = Duration.ZERO,
        timeout: Duration = 5.seconds,
    ): Deferred<Result> = synchronized(lock) {
        log(TAG, VERBOSE) {
            "setAncMode($mode, addr=$address, debounce=$debounce, replacingPending=${pendingSends[address]?.job?.isActive == true})"
        }
        _pendingAncModes.value = _pendingAncModes.value + (address to mode)

        dropPendingSend(address, Result.Superseded)
        timeoutJobs.remove(address)?.cancel()

        val result = CompletableDeferred<Result>()
        val job = appScope.launch {
            delay(debounce)
            log(TAG, VERBOSE) { "dispatching SetAncMode($mode) to AAP for $address" }
            try {
                aapManager.sendCommand(address, AapCommand.SetAncMode(mode))
                log(TAG, VERBOSE) { "sent SetAncMode($mode) to $address" }
                result.complete(Result.Sent)
            } catch (e: CancellationException) {
                // Also reached once the device already reports the mode, so don't call it superseded here.
                log(TAG, VERBOSE) { "send for $mode cancelled before it finished" }
                result.complete(Result.Superseded)
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "SetAncMode($mode) failed for $address: ${e.asLog()}" }
                // Before clearing: clearing completes an unfinished result as Sent.
                result.complete(Result.Failed(e))
                clearPendingAncMode(address, mode)
            }
        }
        pendingSends[address] = PendingSend(job, result)

        timeoutJobs[address] = appScope.launch {
            delay(timeout)
            if (clearPendingFromTimeout(address, mode)) {
                log(TAG, VERBOSE) { "pending target $mode timed out before device confirmation" }
            }
        }
        result
    }

    // No debounce or supersede needed: the AAP queue keeps only the newest command of a type.
    suspend fun setConversationalAwareness(address: BluetoothAddress, enabled: Boolean): Result = try {
        aapManager.sendCommand(address, AapCommand.SetConversationalAwareness(enabled))
        log(TAG, VERBOSE) { "sent SetConversationalAwareness($enabled) to $address" }
        Result.Sent
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, ERROR) { "SetConversationalAwareness($enabled) failed for $address: ${e.asLog()}" }
        Result.Failed(e)
    }

    /** Drops [address]'s pending target if it is still [expectedMode]; true if it did. */
    fun clearPendingAncMode(
        address: BluetoothAddress,
        expectedMode: AapSetting.AncMode.Value,
    ): Boolean = synchronized(lock) {
        val current = _pendingAncModes.value[address] ?: return@synchronized false
        if (current != expectedMode) return@synchronized false

        _pendingAncModes.value = _pendingAncModes.value - address
        // The device already reports the target, so an unsent request has nothing left to do.
        dropPendingSend(address, Result.Sent)
        timeoutJobs.remove(address)?.cancel()
        true
    }

    private fun clearPendingFromTimeout(
        address: BluetoothAddress,
        expectedMode: AapSetting.AncMode.Value,
    ): Boolean = synchronized(lock) {
        val current = _pendingAncModes.value[address] ?: return@synchronized false
        if (current != expectedMode) return@synchronized false

        _pendingAncModes.value = _pendingAncModes.value - address
        dropPendingSend(address, Result.Superseded)
        timeoutJobs.remove(address)
        true
    }

    // A job cancelled before it starts never reaches its catch block, so its result is completed
    // here; otherwise an awaiting caller would hang. complete() is a no-op once the job finished.
    private fun dropPendingSend(address: BluetoothAddress, outcome: Result) {
        val pending = pendingSends.remove(address) ?: return
        pending.job.cancel()
        pending.result.complete(outcome)
    }

    companion object {
        private val TAG = logTag("Monitor", "DeviceControls")
    }
}
