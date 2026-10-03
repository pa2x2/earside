package eu.darken.capod.main.ui.tile

import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.seconds

class AncTilePendingTargetTest : BaseTest() {

    private val address = "00:11:22:33:44:55"
    private val off = AapSetting.AncMode.Value.OFF
    private val on = AapSetting.AncMode.Value.ON
    private val tx = AapSetting.AncMode.Value.TRANSPARENCY
    private val ad = AapSetting.AncMode.Value.ADAPTIVE
    private val visible = listOf(off, on, tx, ad)

    @Test
    fun `pending target is visible immediately and survives service restart state`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds)

        val rendered = controls.applyPendingTarget(active(current = off, pending = null))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe tx
        pickNextMode(rendered.visible, rendered.current, rendered.pending) shouldBe ad
    }

    @Test
    fun `applying device pending confirmation is pure and keeps rendered pending mode`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds)

        val rendered = controls.applyPendingTarget(active(current = off, pending = tx))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe tx
        controls.pendingAncModes.value[address] shouldBe tx
    }

    @Test
    fun `acknowledging device pending confirmation clears process target`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds)

        controls.acknowledgeDeviceState(active(current = off, pending = tx))

        controls.pendingAncModes.value[address] shouldBe null
    }

    @Test
    fun `applying device current confirmation is pure`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds)

        val rendered = controls.applyPendingTarget(active(current = tx, pending = null))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe null
        controls.pendingAncModes.value[address] shouldBe tx
    }

    @Test
    fun `acknowledging device current confirmation clears process target`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds)

        controls.acknowledgeDeviceState(active(current = tx, pending = null))

        controls.pendingAncModes.value[address] shouldBe null
    }

    @Test
    fun `target matching current is kept while device reports different pending mode`() = runTest {
        val controls = controls()

        controls.setAncMode(address, tx, debounce = 1.seconds)

        val rendered = controls.applyPendingTarget(active(current = tx, pending = off))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe tx
        controls.pendingAncModes.value[address] shouldBe tx
    }

    @Test
    fun `applying target filtered out of visible modes is pure`() = runTest {
        val controls = controls()

        controls.setAncMode(address, off, debounce = 1.seconds)

        val rendered = controls.applyPendingTarget(active(current = on, pending = null, visible = listOf(on, tx, ad)))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe null
        controls.pendingAncModes.value[address] shouldBe off
    }

    @Test
    fun `acknowledging target filtered out of visible modes clears process target`() = runTest {
        val controls = controls()

        controls.setAncMode(address, off, debounce = 1.seconds)

        controls.acknowledgeDeviceState(active(current = on, pending = null, visible = listOf(on, tx, ad)))

        controls.pendingAncModes.value[address] shouldBe null
    }

    private fun TestScope.controls(
        aapManager: AapConnectionManager = mockk(relaxed = true),
    ): DeviceControls = DeviceControls(
        appScope = backgroundScope,
        aapManager = aapManager,
    )

    private fun active(
        current: AapSetting.AncMode.Value,
        pending: AapSetting.AncMode.Value?,
        visible: List<AapSetting.AncMode.Value> = this.visible,
    ) = AncTileState.Active(
        current = current,
        pending = pending,
        visible = visible,
        deviceLabel = "Pods",
        deviceAddress = address,
    )
}
