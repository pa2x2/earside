package eu.darken.capod.rules.core.action

import android.content.Context
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.PodDevice
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

    /** E.g. "Listening mode: Off". */
    fun summary(context: Context, action: A): String

    /** Why [device] can't take [action] right now, or null if it can. Checked just before running. */
    fun unavailableReason(context: Context, device: PodDevice, action: A): String?

    /** Must go through [DeviceControls], so a rule changes a setting exactly like the other controls do. */
    suspend fun execute(address: BluetoothAddress, action: A): DeviceControls.Result
}
