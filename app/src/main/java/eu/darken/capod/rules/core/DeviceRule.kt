package eu.darken.capod.rules.core

import eu.darken.capod.common.serialization.LocalTimeIsoSerializer
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime
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

    /**
     * Wall-clock times, so the window follows DST and time zone changes. An [end] before [start]
     * runs into the next day, and the window belongs to the day it starts on: [days] are start days.
     * [start] == [end] or no [days] is a window that never holds; the editor doesn't save one.
     */
    @Serializable
    @SerialName("time.window")
    data class TimeWindow(
        @SerialName("start") @Serializable(with = LocalTimeIsoSerializer::class) val start: LocalTime,
        @SerialName("end") @Serializable(with = LocalTimeIsoSerializer::class) val end: LocalTime,
        @SerialName("days") val days: Set<DayOfWeek>,
    ) : RuleTrigger
    /** While anything silences notifications: Do Not Disturb itself, or a schedule or mode. */
    @Serializable
    @SerialName("dnd.on")
    data object DoNotDisturbOn : RuleTrigger

    /** While a phone or app call is established; ringing doesn't count. */
    @Serializable
    @SerialName("call.active")
    data object InCall : RuleTrigger
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
