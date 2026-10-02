package eu.darken.capod.monitor.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.ui.tile.AncTileSendCoordinator
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class NotificationControlsReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var aapManager: AapConnectionManager

    // Shared with the QS tile so taps from either surface debounce against each other instead of
    // stacking SetAncMode commands on the AAP session.
    @Inject lateinit var ancSendCoordinator: AncTileSendCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS)
        if (address == null) {
            log(TAG, WARN) { "onReceive: no address in $intent" }
            return
        }
        when (intent.action) {
            ACTION_SET_ANC_MODE -> {
                val mode = intent.getStringExtra(EXTRA_ANC_MODE)
                    ?.let { name -> AapSetting.AncMode.Value.entries.firstOrNull { it.name == name } }
                if (mode == null) {
                    log(TAG, WARN) { "onReceive: invalid ANC mode in $intent" }
                    return
                }
                log(TAG, VERBOSE) { "onReceive: SetAncMode($mode) for $address" }
                ancSendCoordinator.scheduleSetAncMode(address, mode, debounce = 300.milliseconds)
            }

            // No debounce needed: the AAP queue keeps only the newest command of a type, so rapid
            // taps collapse to the last value.
            ACTION_SET_CONVERSATIONAL_AWARENESS -> {
                if (!intent.hasExtra(EXTRA_ENABLED)) {
                    log(TAG, WARN) { "onReceive: no target state in $intent" }
                    return
                }
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
                log(TAG, VERBOSE) { "onReceive: SetConversationalAwareness($enabled) for $address" }
                val pending = goAsync()
                appScope.launch {
                    try {
                        aapManager.sendCommand(address, AapCommand.SetConversationalAwareness(enabled))
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "SetConversationalAwareness failed: ${e.asLog()}" }
                    } finally {
                        pending.finish()
                    }
                }
            }

            else -> log(TAG, WARN) { "onReceive: unknown action in $intent" }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Notification", "Controls")
        private const val ACTION_SET_ANC_MODE = "eu.darken.capod.notification.SET_ANC_MODE"
        private const val ACTION_SET_CONVERSATIONAL_AWARENESS =
            "eu.darken.capod.notification.SET_CONVERSATIONAL_AWARENESS"
        private const val EXTRA_ADDRESS = "extra.address"
        private const val EXTRA_ANC_MODE = "extra.mode"
        private const val EXTRA_ENABLED = "extra.enabled"

        fun setAncModeIntent(context: Context, address: BluetoothAddress, mode: AapSetting.AncMode.Value): Intent =
            Intent(context, NotificationControlsReceiver::class.java).apply {
                action = ACTION_SET_ANC_MODE
                data = controlUri("anc", address, mode.name)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_ANC_MODE, mode.name)
            }

        // Carries the target state rather than "toggle", so a tap on a stale notification can't
        // flip the setting the wrong way.
        fun setConversationalAwarenessIntent(context: Context, address: BluetoothAddress, enabled: Boolean): Intent =
            Intent(context, NotificationControlsReceiver::class.java).apply {
                action = ACTION_SET_CONVERSATIONAL_AWARENESS
                data = controlUri("ca", address, if (enabled) "on" else "off")
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_ENABLED, enabled)
            }

        // PendingIntent identity ignores extras. The data makes each device and value its own
        // PendingIntent, so one button can't retarget another's.
        private fun controlUri(control: String, address: BluetoothAddress, value: String): Uri = Uri.Builder()
            .scheme("earside")
            .authority(control)
            .appendPath(address)
            .appendPath(value)
            .build()
    }
}
