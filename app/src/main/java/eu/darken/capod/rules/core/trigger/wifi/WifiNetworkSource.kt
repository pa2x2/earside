package eu.darken.capod.rules.core.trigger.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** [ssid] is null when Android hides the name from us. */
data class WifiNetwork(val handle: Long, val ssid: String?)

/** The Wi-Fi networks the phone is on right now; empty when it's on none. */
data class WifiState(val networks: List<WifiNetwork>)

@Singleton
class WifiNetworkSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope appScope: CoroutineScope,
    locationAccess: LocationAccess,
) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)

    // Android doesn't re-deliver network capabilities when location access changes, so register anew
    // to get the name un-redacted. The debounce covers registration: existing networks arrive just
    // after an initial empty state, and that blip must not look like leaving the network.
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val state: Flow<WifiState> = locationAccess.missing
        .flatMapLatest { observeNetworks() }
        .debounce(2.seconds)
        .distinctUntilChanged()
        .onEach { log(TAG) { "Wi-Fi state: $it" } }
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun observeNetworks(): Flow<WifiState> = callbackFlow {
        val networks = mutableMapOf<Network, WifiNetwork>()
        fun publish() {
            trySend(WifiState(networks.values.sortedBy { it.handle }))
        }

        val callback = object : ConnectivityManager.NetworkCallback(
            if (hasApiLevel(31)) FLAG_INCLUDE_LOCATION_INFO else 0
        ) {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                networks[network] = WifiNetwork(network.networkHandle, readSsid(capabilities))
                publish()
            }

            override fun onLost(network: Network) {
                networks.remove(network)
                publish()
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        publish()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun readSsid(capabilities: NetworkCapabilities): String? = if (hasApiLevel(29)) {
        parseSsid((capabilities.transportInfo as? WifiInfo)?.ssid)
    } else {
        @Suppress("DEPRECATION")
        parseSsid(wifiManager?.connectionInfo?.ssid)
    }

    companion object {
        private val TAG = logTag("Rules", "Wifi")
    }
}

/**
 * Android quotes UTF-8 names (`"Home"`) and reports [WifiManager.UNKNOWN_SSID] when it hides the
 * name. Names that aren't UTF-8 arrive as unquoted hex; they're kept as is and won't match a typed name.
 */
internal fun parseSsid(raw: String?): String? = when {
    raw.isNullOrEmpty() || raw == WifiManager.UNKNOWN_SSID -> null
    raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"') -> raw.substring(1, raw.length - 1).ifEmpty { null }
    else -> raw
}
