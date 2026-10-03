package eu.darken.capod.rules.core.trigger.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.rules.core.trigger.RuleRequirement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android only shares the Wi-Fi name with apps holding precise location access, from the
 * background only with "Allow all the time", and never while location is switched off.
 */
@Singleton
class LocationAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val recheckTrigger = MutableStateFlow(UUID.randomUUID())

    /** Permissions come back from system dialogs and settings pages without a callback; screens call this on resume. */
    fun recheck() {
        recheckTrigger.value = UUID.randomUUID()
    }

    private val locationModeChanges: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    val missing: Flow<List<RuleRequirement>> = combine(recheckTrigger, locationModeChanges) { _, _ -> check() }
        .distinctUntilChanged()
        .onEach { log(TAG) { "Missing location requirements: $it" } }

    private fun check(): List<RuleRequirement> = buildList {
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) add(RuleRequirement.PRECISE_LOCATION)
        if (hasApiLevel(29) && !isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            add(RuleRequirement.BACKGROUND_LOCATION)
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            add(RuleRequirement.LOCATION_SERVICES)
        }
    }

    private fun isGranted(permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    companion object {
        private val TAG = logTag("Rules", "LocationAccess")
    }
}
