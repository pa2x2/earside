package eu.darken.capod.rules.core.trigger.device

import eu.darken.capod.rules.core.trigger.TriggerCondition
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BatteryLowTest : BaseTest() {

    @Test
    fun `a pod charging in the case or disconnected doesn't count, so it can't fire for the one in use`() {
        listOf(
            PodBattery(0.8f, charging = false),
            PodBattery(0.05f, charging = true),
            PodBattery(0.1f, charging = false, disconnected = true),
        ).lowestInUse() shouldBe 80

        // 0.29f times 100 is 28.99…, which must not drop to the next lower percent.
        listOf(PodBattery(0.29f, charging = false), PodBattery(0.6f, charging = null)).lowestInUse() shouldBe 29
        listOf(PodBattery(0.1f, charging = true)).lowestInUse() shouldBe null
    }

    @Test
    fun `a level wavering around the threshold fires once, and a start inside the margin keeps what the engine saw`() = runTest {
        val low = TriggerCondition.Known(holds = true)
        val notLow = TriggerCondition.Known(holds = false)

        flowOf(25, 20, 25, 29, 30, 25, null, 25, 20).batteryLowCondition(20).toList() shouldBe listOf(
            TriggerCondition.Unknown,
            low,
            low,
            low,
            notLow,
            notLow,
            TriggerCondition.Unknown,
            notLow,
            low,
        )
    }
}
