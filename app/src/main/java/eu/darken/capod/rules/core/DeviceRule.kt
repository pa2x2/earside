package eu.darken.capod.rules.core

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

typealias RuleId = String

/**
 * One "when [trigger], do [actions]" rule, owned by a device profile.
 *
 * Rules are pure data. What a trigger or action does lives in its handler, looked up by type, so a
 * new trigger or action is a new subclass plus a handler; the engine, storage and screens don't
 * change. The `@SerialName`s here and on every subtype are the stored format: never rename them.
 */
@Serializable
data class DeviceRule(
    @SerialName("id") val id: RuleId = UUID.randomUUID().toString(),
    @SerialName("enabled") val enabled: Boolean = true,
    /** Optional label; without one the rule is described by its trigger and actions. */
    @SerialName("name") val name: String? = null,
    @SerialName("trigger") val trigger: RuleTrigger,
    /** Run in this order. Never empty, and at most one of each type, so they never fight each other. */
    @SerialName("actions") val actions: List<RuleAction>,
    /** Put back the values [actions] replaced once the trigger's condition stops holding. */
    @SerialName("undo") val undoWhenEnds: Boolean = false,
)

@Serializable
sealed interface RuleTrigger {

    /** Network names match exactly, including case. */
    @Serializable
    @SerialName("wifi.connected")
    data class WifiConnected(@SerialName("ssid") val ssid: String) : RuleTrigger

    @Serializable
    @SerialName("wifi.disconnected")
    data class WifiDisconnected(@SerialName("ssid") val ssid: String) : RuleTrigger
}

@Serializable
sealed interface RuleAction {

    @Serializable
    @SerialName("anc.set")
    data class SetAncMode(
        @SerialName("mode") val mode: AapSetting.AncMode.Value,
    ) : RuleAction

    @Serializable
    @SerialName("conversation_awareness.set")
    data class SetConversationalAwareness(@SerialName("enabled") val enabled: Boolean) : RuleAction
}
