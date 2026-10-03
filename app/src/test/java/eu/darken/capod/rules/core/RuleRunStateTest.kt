package eu.darken.capod.rules.core

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.core.trigger.TriggerCondition.Known
import eu.darken.capod.rules.core.trigger.TriggerCondition.Unknown
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class RuleRunStateTest : BaseTest() {

    private val home = RuleTrigger.WifiConnected("Home")
    private val t1 = Instant.parse("2026-10-03T08:00:00Z")
    private val t2 = Instant.parse("2026-10-03T09:00:00Z")
    private val t3 = Instant.parse("2026-10-03T10:00:00Z")

    private fun RuleRunState.applied() = copy(pendingSince = null)

    @Test
    fun `a rule runs once per stay on a network and again after rejoining it`() {
        val arrived = null.observe(home, Known(false), t1).observe(home, Known(true, "wifi:100"), t2)
        arrived.pendingSince shouldBe t2

        // AirPods reconnect, or the service restarts, while still on the same network session.
        val reconnected = arrived.applied().observe(home, Known(true, "wifi:100"), t3)
        reconnected.pendingSince shouldBe null

        // Left and rejoined while Earside wasn't watching: only the network handle changed.
        reconnected.observe(home, Known(true, "wifi:101"), t3).pendingSince shouldBe t3
    }

    @Test
    fun `losing sight of Wi-Fi neither starts nor ends an occurrence`() {
        val applied = null.observe(home, Known(false), t1).observe(home, Known(true, "wifi:100"), t1).applied()

        // Location revoked or switched off, then back.
        val lost = applied.observe(home, Unknown, t2)
        lost shouldBe applied
        lost.observe(home, Known(true, "wifi:100"), t3).pendingSince shouldBe null

        val waiting = null.observe(home, Known(false), t1).observe(home, Known(true, "wifi:100"), t1)
        waiting.observe(home, Unknown, t2).pendingSince shouldBe t1
    }

    @Test
    fun `a rule waiting for the AirPods stops waiting once its condition ends`() {
        val waiting = null.observe(home, Known(false), t1).observe(home, Known(true, "wifi:100"), t1)

        waiting.observe(home, Known(false), t2).pendingSince shouldBe null
    }

    @Test
    fun `a new or edited rule does not run for a condition that already holds`() {
        null.observe(home, Known(true, "wifi:100"), t1).pendingSince shouldBe null

        val onHome = null.observe(home, Known(false), t1).observe(home, Known(true, "wifi:100"), t1)
        val office = RuleTrigger.WifiConnected("Office")
        onHome.observe(office, Known(true, "wifi:100"), t2).pendingSince shouldBe null
    }

    @Test
    fun `Apply now on a just-saved rule survives its first observation`() {
        // DeviceRulesEngine.applyNow can land before the engine has observed the new rule once.
        val applied = RuleRunState(home, pendingSince = t1)

        applied.observe(home, Known(true, "wifi:100"), t2).pendingSince shouldBe t1
        applied.observe(home, Known(false), t2).pendingSince shouldBe null
    }

    @Test
    fun `waiting rules run in the order their events happened, then in list order`() {
        fun due(index: Int, since: Instant) = DueRule(
            profileId = "p1",
            index = index,
            rule = DeviceRule(
                id = "r$index",
                trigger = home,
                actions = listOf(RuleAction.SetAncMode(AapSetting.AncMode.Value.OFF)),
            ),
            pendingSince = since,
        )

        val order = listOf(due(0, t2), due(1, t1), due(3, t1), due(2, t1)).inRunOrder().map { it.index }

        order shouldBe listOf(1, 2, 3, 0)
    }

    @Test
    fun `a rule is applied only if each of its actions was taken, and names the ones that were not`() {
        val mixed = listOf(
            ActionResult.Applied("Conversation Awareness: On"),
            ActionResult.NotAvailable("Adaptive isn't enabled in Listening modes"),
            ActionResult.Failed("Volume: 30%"),
        ).outcome()
        mixed shouldBe RunOutcome(
            RuleRunState.Outcome.FAILED,
            "Adaptive isn't enabled in Listening modes · Volume: 30%",
        )

        listOf(ActionResult.Applied("Listening mode: Off"), ActionResult.Superseded).outcome() shouldBe
            RunOutcome(RuleRunState.Outcome.APPLIED, null)

        // The user changed every setting while the rule waited: no new outcome, the last one stays.
        listOf(ActionResult.Superseded, ActionResult.Superseded).outcome() shouldBe RunOutcome(null, null)
    }
}
