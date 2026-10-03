package eu.darken.capod.monitor.core.controls

import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceControlsTest : BaseTest() {

    private val address = "00:11:22:33:44:55"
    private val tx = AapSetting.AncMode.Value.TRANSPARENCY
    private val ad = AapSetting.AncMode.Value.ADAPTIVE

    @Test
    fun `replacing pending send dispatches only latest target`() = runTest {
        val aapManager = mockk<AapConnectionManager>(relaxed = true)
        val controls = controls(aapManager)

        controls.setAncMode(address, tx, debounce = 1.seconds)
        controls.pendingAncModes.value[address] shouldBe tx
        advanceTimeBy(999)
        runCurrent()
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(tx)) }
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(ad)) }

        controls.setAncMode(address, ad, debounce = 1.seconds)
        controls.pendingAncModes.value[address] shouldBe ad
        advanceTimeBy(999)
        runCurrent()
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(tx)) }
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(ad)) }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(tx)) }
        coVerify(exactly = 1) { aapManager.sendCommand(address, AapCommand.SetAncMode(ad)) }
    }

    @Test
    fun `pending target clears after timeout without confirmation`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds, timeout = 5.seconds)
        controls.pendingAncModes.value[address] shouldBe tx

        advanceTimeBy(5_000)
        runCurrent()

        controls.pendingAncModes.value[address] shouldBe null
    }

    @Test
    fun `send failure clears pending target`() = runTest {
        val aapManager = mockk<AapConnectionManager>(relaxed = true)
        coEvery {
            aapManager.sendCommand(address, AapCommand.SetAncMode(tx))
        } throws IllegalStateException("not connected")
        val controls = controls(aapManager)

        controls.setAncMode(address, tx, debounce = 1.seconds)
        controls.pendingAncModes.value[address] shouldBe tx

        advanceTimeBy(1_000)
        runCurrent()

        controls.pendingAncModes.value[address] shouldBe null
    }

    @Test
    fun `a replaced or failed request still reports its result`() = runTest {
        val aapManager = mockk<AapConnectionManager>(relaxed = true)
        coEvery {
            aapManager.sendCommand(address, AapCommand.SetAncMode(ad))
        } throws IllegalStateException("not connected")
        val controls = controls(aapManager)

        val replaced = controls.setAncMode(address, tx, debounce = 1.seconds)
        val failing = controls.setAncMode(address, ad)
        runCurrent()

        replaced.isCompleted shouldBe true
        replaced.getCompleted() shouldBe DeviceControls.Result.Superseded
        failing.getCompleted().shouldBeInstanceOf<DeviceControls.Result.Failed>()
    }

    private fun TestScope.controls(
        aapManager: AapConnectionManager = mockk(relaxed = true),
    ): DeviceControls = DeviceControls(
        appScope = backgroundScope,
        aapManager = aapManager,
    )
}
