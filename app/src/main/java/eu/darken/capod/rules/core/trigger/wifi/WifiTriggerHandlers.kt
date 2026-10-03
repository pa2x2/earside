package eu.darken.capod.rules.core.trigger.wifi

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.core.trigger.RuleTriggerHandler
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WifiConnectedHandler @Inject constructor(
    private val wifi: WifiNetworkSource,
    locationAccess: LocationAccess,
) : RuleTriggerHandler<RuleTrigger.WifiConnected> {

    override val type = RuleTrigger.WifiConnected::class

    override fun summary(context: Context, trigger: RuleTrigger.WifiConnected): String =
        context.getString(R.string.rules_trigger_wifi_connected_summary, trigger.ssid)

    override fun holdsNowText(context: Context, trigger: RuleTrigger.WifiConnected): String =
        context.getString(R.string.rules_apply_now_wifi_connected, trigger.ssid)

    override fun undoText(context: Context, trigger: RuleTrigger.WifiConnected): String =
        context.getString(R.string.rules_undo_wifi_connected, trigger.ssid)

    override val missingRequirements: Flow<List<RuleRequirement>> = locationAccess.missing

    override fun condition(profileId: ProfileId, trigger: RuleTrigger.WifiConnected): Flow<TriggerCondition> = wifi.state
        .map { it.onNetwork(trigger.ssid) }
        .distinctUntilChanged()
}

class WifiDisconnectedHandler @Inject constructor(
    private val wifi: WifiNetworkSource,
    locationAccess: LocationAccess,
) : RuleTriggerHandler<RuleTrigger.WifiDisconnected> {

    override val type = RuleTrigger.WifiDisconnected::class

    override fun summary(context: Context, trigger: RuleTrigger.WifiDisconnected): String =
        context.getString(R.string.rules_trigger_wifi_disconnected_summary, trigger.ssid)

    override fun holdsNowText(context: Context, trigger: RuleTrigger.WifiDisconnected): String =
        context.getString(R.string.rules_apply_now_wifi_disconnected, trigger.ssid)

    override fun undoText(context: Context, trigger: RuleTrigger.WifiDisconnected): String =
        context.getString(R.string.rules_undo_wifi_disconnected, trigger.ssid)

    override val missingRequirements: Flow<List<RuleRequirement>> = locationAccess.missing

    override fun condition(profileId: ProfileId, trigger: RuleTrigger.WifiDisconnected): Flow<TriggerCondition> = wifi.state
        .map { state ->
            when (val on = state.onNetwork(trigger.ssid)) {
                TriggerCondition.Unknown -> on
                is TriggerCondition.Known -> TriggerCondition.Known(holds = !on.holds)
            }
        }
        .distinctUntilChanged()
}

/**
 * Whether the phone is on [ssid]. A network whose name Android hides could be [ssid], so then the
 * answer is unknown rather than "no"; otherwise losing location access would look like leaving.
 */
internal fun WifiState.onNetwork(ssid: String): TriggerCondition {
    networks.firstOrNull { it.ssid == ssid }?.let { return TriggerCondition.Known(holds = true, occurrence = "wifi:${it.handle}") }
    if (networks.any { it.ssid == null }) return TriggerCondition.Unknown
    return TriggerCondition.Known(holds = false)
}
