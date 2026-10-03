package eu.darken.capod.rules.core

import eu.darken.capod.common.serialization.SerializationModule
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.datastore.FakeDataStoreValue

class DeviceRulesRepoTest : BaseTest() {

    private val json = SerializationModule().json()

    private val futureRule =
        """{"id":"future","enabled":true,"trigger":{"type":"location.arrived","place":"Gym"},"action":{"type":"anc.set","mode":"ON"}}"""

    // This version's stored format, plus a rule whose trigger only a newer version knows.
    private val stored = """
        {"profiles":{"p1":[
          {"id":"known","enabled":true,"trigger":{"type":"wifi.connected","ssid":"Home"},"action":{"type":"anc.set","mode":"OFF"}},
          $futureRule
        ]}}
    """

    @Test
    fun `a rule from a newer version neither breaks the others nor gets lost on save`() = runTest {
        val storage = FakeDataStoreValue(json.decodeFromString<DeviceRulesStorage>(stored))
        val settings = mockk<DeviceRulesSettings> { every { rules } returns storage.mock }
        val repo = DeviceRulesRepo(settings, json)

        val entries = repo.rulesFor("p1").first()
        entries[0] shouldBe RuleEntry.Known(
            DeviceRule(
                id = "known",
                trigger = RuleTrigger.WifiConnected("Home"),
                action = RuleAction.SetAncMode(AapSetting.AncMode.Value.OFF),
            )
        )
        entries[1].shouldBeInstanceOf<RuleEntry.Unsupported>().id shouldBe "future"

        val known = (entries[0] as RuleEntry.Known).rule
        repo.updateRule("p1", known.copy(enabled = false))

        storage.value.profiles.getValue("p1")[1] shouldBe json.parseToJsonElement(futureRule).jsonObject
        repo.rulesFor("p1").first()[0].shouldBeInstanceOf<RuleEntry.Known>().rule.enabled shouldBe false
    }
}
