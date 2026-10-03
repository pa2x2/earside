package eu.darken.capod.rules.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material.icons.twotone.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.wifi.WifiNetworkSource
import javax.inject.Inject

class WifiConnectedEditor @Inject constructor(
    private val wifi: WifiNetworkSource,
) : RuleTriggerEditor<RuleTrigger.WifiConnected> {

    override val type = RuleTrigger.WifiConnected::class
    override val icon = Icons.TwoTone.Wifi
    override val label = R.string.rules_trigger_wifi_connected_label

    override fun carryOver(previous: RuleTrigger): RuleTrigger.WifiConnected? =
        previous.ssid?.let { RuleTrigger.WifiConnected(it) }

    @Composable
    override fun Settings(
        current: RuleTrigger.WifiConnected?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.WifiConnected?) -> Unit,
    ) {
        WifiNetworkField(wifi, current?.ssid) { ssid -> onChange(ssid?.let { RuleTrigger.WifiConnected(it) }) }
    }
}

class WifiDisconnectedEditor @Inject constructor(
    private val wifi: WifiNetworkSource,
) : RuleTriggerEditor<RuleTrigger.WifiDisconnected> {

    override val type = RuleTrigger.WifiDisconnected::class
    override val icon = Icons.TwoTone.WifiOff
    override val label = R.string.rules_trigger_wifi_disconnected_label

    override fun carryOver(previous: RuleTrigger): RuleTrigger.WifiDisconnected? =
        previous.ssid?.let { RuleTrigger.WifiDisconnected(it) }

    @Composable
    override fun Settings(
        current: RuleTrigger.WifiDisconnected?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.WifiDisconnected?) -> Unit,
    ) {
        WifiNetworkField(wifi, current?.ssid) { ssid -> onChange(ssid?.let { RuleTrigger.WifiDisconnected(it) }) }
    }
}

private val RuleTrigger.ssid: String?
    get() = (this as? RuleTrigger.WifiConnected)?.ssid ?: (this as? RuleTrigger.WifiDisconnected)?.ssid

/** A typed network name, with the networks the phone is on right now offered as one-tap picks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiNetworkField(
    wifi: WifiNetworkSource,
    initial: String?,
    onChange: (String?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    val wifiState by wifi.state.collectAsStateWithLifecycle(initialValue = null)
    val current = wifiState?.networks.orEmpty().mapNotNull { it.ssid }.distinct()

    fun update(value: String) {
        text = value
        // Not trimmed: spaces are part of a network name and must match exactly.
        onChange(value.takeIf { it.isNotBlank() })
    }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = ::update,
            label = { Text(stringResource(R.string.rules_wifi_network_label)) },
            supportingText = {
                Column {
                    Text(stringResource(R.string.rules_wifi_network_hint))
                    if (current.isEmpty()) Text(stringResource(R.string.rules_wifi_network_none_current))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (current.isNotEmpty()) {
            FlowRow {
                current.forEach { ssid ->
                    AssistChip(
                        onClick = { update(ssid) },
                        label = { Text(stringResource(R.string.rules_wifi_network_current, ssid)) },
                        leadingIcon = { Icon(Icons.TwoTone.Wifi, contentDescription = null) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}
