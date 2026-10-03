package eu.darken.capod.rules.core.trigger.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.rules.core.trigger.RuleRequirement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Android only shares the Wi-Fi name with apps holding precise location access, from the
 * background only with "Allow all the time", and never while location is switched off.
 */
@Singleton
class LocationAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val recheckTrigger = MutableStateFlow(UUID.randomUUID())

    /** For screens on resume, so a grant shows at once instead of on the next periodic check. */
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

    // Android announces location being switched on or off, but not a permission being granted, so
    // access restored in system settings is only noticed by checking again. Revoking kills the app.
    private val periodicRecheck: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1.minutes)
        }
    }

    val missing: Flow<List<RuleRequirement>> = combine(recheckTrigger, locationModeChanges, periodicRecheck) { _, _, _ ->
        check()
    }
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

    // Not PermissionChecker: it also checks the app-op, which "While using the app" leaves in
    // foreground mode, and it reports that as denied even with the permission granted.
    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val TAG = logTag("Rules", "LocationAccess")
    }
}
