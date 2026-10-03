package eu.darken.capod.rules.core.action

import eu.darken.capod.rules.core.RuleAction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MediaVolumeTest : BaseTest() {

    @Test
    fun `a rule's volume reads back as its own after the phone rounds it to a step, so undo still restores`() {
        val steps = 0..15
        val rule = RuleAction.SetMediaVolume(30)

        // 30% of 15 steps is 4.5, set as step 5, which is 33%.
        mediaVolumeIndex(rule.percent, steps) shouldBe 5
        rule.atIndex(5, steps) shouldBe rule
        // Changed by hand since: no longer the rule's value, so it stays.
        rule.atIndex(6, steps) shouldBe RuleAction.SetMediaVolume(40)
    }

    @Test
    fun `restoring the volume a rule replaced puts back the exact step it was on`() {
        for (min in 0..1) {
            for (steps in 1..100) {
                val range = min..min + steps
                for (index in range) {
                    val previous = RuleAction.SetMediaVolume(60).atIndex(index, range)
                    mediaVolumeIndex(previous.percent, range) shouldBe index
                }
            }
        }
    }
}
