package eu.darken.capod.rules.core.trigger.time

import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.TriggerCondition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Whether [now] is inside one of the trigger's windows. Each day's window is its own occurrence,
 * named by the day it starts on, so the rule runs again the next day even if it was never seen to end.
 */
internal fun RuleTrigger.TimeWindow.conditionAt(now: ZonedDateTime): TriggerCondition.Known {
    // A window ends on the day it starts or the next, so only today's and yesterday's can hold.
    val day = listOf(now.toLocalDate(), now.toLocalDate().minusDays(1)).firstOrNull { day ->
        val (from, until) = windowOn(day, now.zone) ?: return@firstOrNull false
        !now.isBefore(from) && now.isBefore(until)
    }
    return if (day != null) TriggerCondition.Known(holds = true, occurrence = "time:$day") else TriggerCondition.Known(holds = false)
}

/** The first moment after [now] at which a window starts or ends; null if none ever does. */
internal fun RuleTrigger.TimeWindow.nextEdge(now: ZonedDateTime): ZonedDateTime? =
    // From yesterday, whose window may still be open, to the same weekday next week.
    (-1L..7L)
        .mapNotNull { windowOn(now.toLocalDate().plusDays(it), now.zone) }
        .flatMap { it.toList() }
        .filter { it.isAfter(now) }
        .minByOrNull { it.toInstant() }

/** The window starting on [day], or null if there's none that day. */
private fun RuleTrigger.TimeWindow.windowOn(day: LocalDate, zone: ZoneId): Pair<ZonedDateTime, ZonedDateTime>? {
    if (day.dayOfWeek !in days || start == end) return null
    val from = day.atTime(start).inZone(zone)
    val until = (if (end > start) day else day.plusDays(1)).atTime(end).inZone(zone)
    // A window that lies wholly in the hour skipped for DST doesn't happen that day.
    return (from to until).takeIf { from.isBefore(until) }
}

// A time the clock skips for DST counts from the moment it jumps (02:30 becomes 03:00, where
// java.time would make it 03:30). A time the clock shows twice counts the first time.
private fun LocalDateTime.inZone(zone: ZoneId): ZonedDateTime {
    val transition = zone.rules.getTransition(this)
    return if (transition?.isGap == true) ZonedDateTime.ofInstant(transition.instant, zone) else atZone(zone)
}
