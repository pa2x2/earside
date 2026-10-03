package eu.darken.capod.rules.core.trigger.time

import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.TriggerCondition.Known
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

class TimeWindowTest : BaseTest() {

    private fun berlin(text: String) = ZonedDateTime.parse("$text[Europe/Berlin]")

    @Test
    fun `a window past midnight belongs to the day it starts on`() {
        val fridayNights = RuleTrigger.TimeWindow(LocalTime.of(22, 0), LocalTime.of(7, 0), setOf(DayOfWeek.FRIDAY))

        // Friday morning is the end of Thursday's window, which isn't selected.
        fridayNights.conditionAt(berlin("2026-10-02T06:00+02:00")) shouldBe Known(false)
        fridayNights.nextEdge(berlin("2026-10-02T06:00+02:00"))!!.toInstant() shouldBe Instant.parse("2026-10-02T20:00:00Z")

        fridayNights.conditionAt(berlin("2026-10-02T23:00+02:00")) shouldBe Known(true, "time:2026-10-02")
        fridayNights.conditionAt(berlin("2026-10-03T06:59+02:00")) shouldBe Known(true, "time:2026-10-02")
        fridayNights.conditionAt(berlin("2026-10-03T07:00+02:00")) shouldBe Known(false)
        fridayNights.conditionAt(berlin("2026-10-03T23:00+02:00")) shouldBe Known(false)
        fridayNights.nextEdge(berlin("2026-10-03T07:00+02:00"))!!.toInstant() shouldBe Instant.parse("2026-10-09T20:00:00Z")
    }

    @Test
    fun `window edges follow the wall clock across DST changes`() {
        val nights = RuleTrigger.TimeWindow(LocalTime.of(22, 0), LocalTime.of(7, 0), DayOfWeek.entries.toSet())
        // Clocks go back at 03:00 that night: the window lasts 10 hours and still ends at 07:00.
        nights.nextEdge(berlin("2026-10-24T22:30+02:00"))!!.toInstant() shouldBe Instant.parse("2026-10-25T06:00:00Z")
        nights.conditionAt(berlin("2026-10-25T06:30+01:00")) shouldBe Known(true, "time:2026-10-24")

        // Clocks jump from 02:00 to 03:00: a start at 02:30 begins at 03:00, not an hour after it.
        val early = RuleTrigger.TimeWindow(LocalTime.of(2, 30), LocalTime.of(4, 0), DayOfWeek.entries.toSet())
        early.nextEdge(berlin("2026-03-29T01:00+01:00"))!!.toInstant() shouldBe Instant.parse("2026-03-29T01:00:00Z")
        early.conditionAt(berlin("2026-03-29T03:10+02:00")) shouldBe Known(true, "time:2026-03-29")

        // A window wholly inside the skipped hour doesn't happen that day.
        val skipped = RuleTrigger.TimeWindow(LocalTime.of(2, 15), LocalTime.of(2, 45), DayOfWeek.entries.toSet())
        skipped.nextEdge(berlin("2026-03-29T01:00+01:00"))!!.toInstant() shouldBe Instant.parse("2026-03-30T00:15:00Z")
    }
}
