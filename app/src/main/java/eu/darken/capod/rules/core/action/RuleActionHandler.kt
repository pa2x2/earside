package eu.darken.capod.rules.core.action

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import kotlin.reflect.KClass

/**
 * Everything one kind of [RuleAction] does. Bound into a set with `@IntoSet` and looked up by
 * [type]; a new action is a new handler, nothing else changes.
 */
interface RuleActionHandler<A : RuleAction> {

    val type: KClass<A>

    /** Whether this model can run the action at all. Unsupported actions are offered greyed out. */
    fun isSupported(features: PodModel.Features): Boolean

    /** Must go through [DeviceControls], so a rule changes a setting exactly like the other controls do. */
    suspend fun execute(address: BluetoothAddress, action: A): DeviceControls.Result
}
