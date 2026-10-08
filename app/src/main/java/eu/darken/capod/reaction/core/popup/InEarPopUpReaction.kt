package eu.darken.capod.reaction.core.popup

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the in-ear popup when the user puts the pods on. Only AAP ear detection counts: BLE reports
 * phantom "in ear" for pods resting in the case, and the popup's listening-mode row needs the AAP
 * session anyway. A reading has to hold for [EAR_SETTLE] first: a pod brushing a finger on its way
 * into the case reports "in ear" for under 100 ms.
 */
@Singleton
class InEarPopUpReaction @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val timeSource: TimeSource,
) {

    @Volatile private var lastShownAt: Instant? = null
    @Volatile private var lastTakenOffAt: Instant? = null

    internal data class Observation(
        val profileId: ProfileId?,
        val eligible: Boolean,
        val keepPill: Boolean,
        /** From AAP ear detection; null while the session hasn't reported where the pods are. */
        val inEar: Boolean?,
        /** When the pods' audio connection to this phone appeared; null while not connected. */
        val connectedAt: Instant?,
    )

    enum class Decision { SHOW, HIDE, NONE }

    @OptIn(FlowPreview::class)
    fun monitor(): Flow<Event> = combine(
        deviceMonitor.primaryDeviceByTier,
        bluetoothManager.connectedDevices,
    ) { device, connected ->
        val address = device?.address
        Observation(
            profileId = device?.profileId,
            eligible = device?.reactions?.showPopUpOnEarIn == true,
            keepPill = device?.reactions?.showInEarPill == true,
            // With ear detection switched off on the pods, their readings mean nothing.
            inEar = device?.takeIf { it.earDetectionEnabled?.enabled != false }?.aap?.isEitherPodInEar,
            connectedAt = connected.firstOrNull { it.address == address }?.seenFirstAt,
        )
    }
        .distinctUntilChanged()
        .debounce(EAR_SETTLE)
        .distinctUntilChanged()
        .withPrevious()
        .mapNotNull { (previous, current) ->
            val now = timeSource.now()
            val (decision, reason) = evaluate(previous, current, lastShownAt, lastTakenOffAt, now)
            log(TAG, if (decision == Decision.NONE) VERBOSE else DEBUG) {
                "In-ear popup decision: $decision ($reason) for $current"
            }
            when (decision) {
                Decision.SHOW -> {
                    lastShownAt = now
                    Event.Show(current.profileId!!, current.keepPill)
                }

                Decision.HIDE -> {
                    if (current.inEar == false) lastTakenOffAt = now
                    Event.Hide
                }
                Decision.NONE -> null
            }
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    sealed interface Event {
        data class Show(val profileId: ProfileId, val keepPill: Boolean) : Event
        data object Hide : Event
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp", "InEar")

        private val EAR_SETTLE = 500.milliseconds

        /**
         * How long after the audio connection appears a session that opens with the pods already in
         * still counts as "just put on". Pods taken from the case and put in quickly are in the ear
         * before the session's first report.
         */
        internal val FRESH_CONNECTION_WINDOW: Duration = Duration.ofSeconds(30)

        /**
         * How long after the pods were taken off putting them on doesn't count. A pod held in the
         * hand reads "in ear" for about a second, longer than [EAR_SETTLE].
         */
        internal val TAKEN_OFF_COOLDOWN: Duration = Duration.ofSeconds(3)

        internal fun evaluate(
            previous: Observation?,
            current: Observation,
            lastShownAt: Instant?,
            lastTakenOffAt: Instant?,
            now: Instant,
        ): Pair<Decision, String> {
            val sameDevice = previous != null && previous.profileId == current.profileId
            if (previous?.eligible == true && (!current.eligible || !sameDevice)) {
                return Decision.HIDE to "eligibility lost or another device took over"
            }
            if (!current.eligible || current.profileId == null) return Decision.NONE to "not eligible"

            val wasInEar = if (sameDevice) previous?.inEar else null
            // The cached device stays in the flow after it disconnects, so nothing else would ever
            // take the pill down. An AAP drop alone doesn't count: it reconnects under live audio.
            val wasConnected = sameDevice && (previous?.inEar != null || previous?.connectedAt != null)
            if (wasConnected && current.inEar == null && current.connectedAt == null) {
                return Decision.HIDE to "disconnected"
            }
            return when {
                current.inEar == true && wasInEar == false -> {
                    if (lastTakenOffAt != null && Duration.between(lastTakenOffAt, now) < TAKEN_OFF_COOLDOWN) {
                        return Decision.NONE to "put on right after being taken off"
                    }
                    Decision.SHOW to "put on"
                }
                current.inEar == true && wasInEar == null -> {
                    val connectedAt = current.connectedAt
                        ?: return Decision.NONE to "worn when the session came up, no audio connection"
                    if (Duration.between(connectedAt, now) > FRESH_CONNECTION_WINDOW) {
                        return Decision.NONE to "worn when the session came up on an older connection"
                    }
                    if (lastShownAt != null && Duration.between(lastShownAt, now) <= FRESH_CONNECTION_WINDOW) {
                        return Decision.NONE to "already shown for this connection"
                    }
                    Decision.SHOW to "worn when a fresh connection came up"
                }

                current.inEar == false && wasInEar == true -> Decision.HIDE to "taken off"
                else -> Decision.NONE to "no change"
            }
        }
    }
}
