package eu.darken.capod.monitor.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.ui.tile.AncTileSendCoordinator
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class AncNotificationReceiver : BroadcastReceiver() {

    // Shared with the QS tile so taps from either surface debounce against each other instead of
    // stacking SetAncMode commands on the AAP session.
    @Inject lateinit var sendCoordinator: AncTileSendCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS)
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { name -> AapSetting.AncMode.Value.entries.firstOrNull { it.name == name } }
        if (address == null || mode == null) {
            log(TAG, WARN) { "onReceive: invalid intent $intent" }
            return
        }
        log(TAG, VERBOSE) { "onReceive: SetAncMode($mode) for $address" }
        sendCoordinator.scheduleSetAncMode(address, mode, debounce = 300.milliseconds)
    }

    companion object {
        private val TAG = logTag("Monitor", "Notification", "Anc")
        private const val EXTRA_ADDRESS = "extra.address"
        private const val EXTRA_MODE = "extra.mode"

        fun intent(context: Context, address: BluetoothAddress, mode: AapSetting.AncMode.Value): Intent =
            Intent(context, AncNotificationReceiver::class.java).apply {
                // PendingIntent identity ignores extras. The data makes each device and mode its own
                // PendingIntent, so one button can't retarget another's.
                data = Uri.Builder()
                    .scheme("earside")
                    .authority("anc")
                    .appendPath(address)
                    .appendPath(mode.name)
                    .build()
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_MODE, mode.name)
            }
    }
}
