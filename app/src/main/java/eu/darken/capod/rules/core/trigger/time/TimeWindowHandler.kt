package eu.darken.capod.rules.core.trigger.time

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.core.trigger.RuleTriggerHandler
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

class TimeWindowHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeSource: TimeSource,
) : RuleTriggerHandler<RuleTrigger.TimeWindow> {

    override val type = RuleTrigger.TimeWindow::class

    override fun isSupported(features: PodModel.Features): Boolean = true

    override fun summary(context: Context, trigger: RuleTrigger.TimeWindow): String {
        val start = formatTime(context, trigger.start)
        val end = formatTime(context, trigger.end)
        return if (trigger.days.size == DayOfWeek.entries.size) {
            context.getString(R.string.rules_trigger_time_window_summary_every_day, start, end)
        } else {
            context.getString(R.string.rules_trigger_time_window_summary, start, end, formatDays(context, trigger.days))
        }
    }

    override fun holdsNowText(context: Context, trigger: RuleTrigger.TimeWindow): String = context.getString(
        R.string.rules_apply_now_time_window,
        formatTime(context, trigger.start),
        formatTime(context, trigger.end),
    )

    override fun undoText(context: Context, trigger: RuleTrigger.TimeWindow): String =
        context.getString(R.string.rules_undo_time_window, formatTime(context, trigger.end))

    override val missingRequirements: Flow<List<RuleRequirement>> = flowOf(emptyList())

    /**
     * Checked again whenever the wall clock may have crossed an edge of the window: when the time or
     * time zone is changed, and on an alarm for the next edge. Not a coroutine delay: that runs on a
     * clock that stops in deep sleep, so a delay from 14:00 to 22:00 can end hours late. The alarm is
     * a non-wakeup one on the wall clock, delivered the next time the phone is awake after the edge.
     * Each collection sets its own listener alarm, so rules can't replace one another's.
     */
    override fun condition(profileId: ProfileId, trigger: RuleTrigger.TimeWindow): Flow<TriggerCondition> = flow {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val recheck = Channel<Unit>(Channel.CONFLATED)
        val alarm = AlarmManager.OnAlarmListener { recheck.trySend(Unit) }
        val clockChanges = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                recheck.trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, clockChanges, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            recheck.trySend(Unit)
            for (unused in recheck) {
                val now = ZonedDateTime.ofInstant(timeSource.now(), ZoneId.systemDefault())
                val condition = trigger.conditionAt(now)
                val next = trigger.nextEdge(now)
                log(TAG) { "$trigger at $now: $condition, next check at $next" }
                // Replaces this listener's previous alarm.
                next?.let { alarmManager.set(AlarmManager.RTC, it.toInstant().toEpochMilli(), TAG, alarm, null) }
                emit(condition)
            }
        } finally {
            alarmManager.cancel(alarm)
            context.unregisterReceiver(clockChanges)
        }
    }.distinctUntilChanged()

    companion object {
        private val TAG = logTag("Rules", "TimeWindow")
    }
}

/** In the user's 12- or 24-hour format. */
internal fun formatTime(context: Context, time: LocalTime): String {
    val locale = context.resources.configuration.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/** The days of the week in the order the locale's calendar shows them, e.g. from Sunday in the US. */
internal fun weekOf(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

/** E.g. "Mon–Fri" or "Mon, Wed, Sat, Sun"; three or more days in a row become a range. */
private fun formatDays(context: Context, days: Set<DayOfWeek>): String {
    val locale = context.resources.configuration.locales[0]
    fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT, locale)

    val runs = mutableListOf<MutableList<DayOfWeek>>()
    var previousSelected = false
    for (day in weekOf(locale)) {
        val selected = day in days
        if (selected) {
            if (previousSelected) runs.last() += day else runs += mutableListOf(day)
        }
        previousSelected = selected
    }
    return runs.joinToString(", ") { run ->
        if (run.size >= 3) "${name(run.first())}–${name(run.last())}" else run.joinToString(", ") { name(it) }
    }
}
