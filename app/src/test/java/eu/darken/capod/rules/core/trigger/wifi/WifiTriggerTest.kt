package eu.darken.capod.rules.core.trigger.wifi

import eu.darken.capod.rules.core.trigger.TriggerCondition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WifiTriggerTest : BaseTest() {

    @Test
    fun `quoted names match and Android's hidden-name placeholder is not a network name`() {
        parseSsid("\"Home\"") shouldBe "Home"
        parseSsid("<unknown ssid>") shouldBe null
        parseSsid("\"\"") shouldBe null
        parseSsid(null) shouldBe null
    }

    @Test
    fun `a network whose name is hidden counts as unknown, not as having left`() {
        WifiState(listOf(WifiNetwork(handle = 7, ssid = null))).onNetwork("Home") shouldBe TriggerCondition.Unknown

        WifiState(listOf(WifiNetwork(handle = 7, ssid = "Home"))).onNetwork("Home") shouldBe
            TriggerCondition.Known(holds = true, occurrence = "wifi:7")
        WifiState(listOf(WifiNetwork(handle = 7, ssid = "Office"))).onNetwork("Home") shouldBe
            TriggerCondition.Known(holds = false)
        WifiState(emptyList()).onNetwork("Home") shouldBe TriggerCondition.Known(holds = false)
    }
}
