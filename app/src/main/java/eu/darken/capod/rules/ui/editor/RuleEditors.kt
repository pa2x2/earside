package eu.darken.capod.rules.ui.editor

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.RuleTrigger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * The editor's part of one [RuleTrigger] type: how it's offered on the When step and its own
 * settings. Requirements come from the type's core handler. A new trigger type adds one of these.
 */
interface RuleTriggerEditor<T : RuleTrigger> {

    val type: KClass<T>

    val icon: ImageVector

    /** E.g. "Phone joins a Wi-Fi network". */
    @get:StringRes val label: Int

    /** The settings of [previous] that carry over when the user switches to this type, e.g. the network. */
    fun carryOver(previous: RuleTrigger): T? = null

    /**
     * [current] is null for a new rule. Reports a complete trigger, or null while the settings are incomplete.
     * [device] is null while the AirPods are away; [model] is always known from the profile.
     */
    @Composable
    fun Settings(current: T?, model: PodModel, device: PodDevice?, onChange: (T?) -> Unit)
}

/** The editor's part of one [RuleAction] type, offered on the Then step. */
interface RuleActionEditor<A : RuleAction> {

    val type: KClass<A>

    val icon: ImageVector

    /** E.g. "Set listening mode". */
    @get:StringRes val label: Int

    /** Preselected when the user picks this action, so its values never show without a choice. */
    fun initial(model: PodModel, device: PodDevice?): A

    /** [device] is null while the AirPods are away; [model] is always known from the profile. */
    @Composable
    fun Settings(current: A?, model: PodModel, device: PodDevice?, onChange: (A?) -> Unit)
}

@Singleton
class RuleEditors @Inject constructor(
    triggerEditors: Set<@JvmSuppressWildcards RuleTriggerEditor<*>>,
    actionEditors: Set<@JvmSuppressWildcards RuleActionEditor<*>>,
) {
    // Sorted by type so the order doesn't depend on set iteration.
    val triggers: List<RuleTriggerEditor<*>> = triggerEditors.sortedBy { it.type.qualifiedName }
    val actions: List<RuleActionEditor<*>> = actionEditors.sortedBy { it.type.qualifiedName }

    fun forTrigger(type: KClass<*>): RuleTriggerEditor<*>? = triggers.firstOrNull { it.type == type }

    fun forAction(type: KClass<*>): RuleActionEditor<*>? = actions.firstOrNull { it.type == type }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class RuleEditorsModule {
    @Binds @IntoSet abstract fun wifiConnected(editor: WifiConnectedEditor): RuleTriggerEditor<*>
    @Binds @IntoSet abstract fun wifiDisconnected(editor: WifiDisconnectedEditor): RuleTriggerEditor<*>
    @Binds @IntoSet abstract fun setAncMode(editor: SetAncModeEditor): RuleActionEditor<*>
    @Binds @IntoSet abstract fun setConversationalAwareness(editor: SetConversationalAwarenessEditor): RuleActionEditor<*>
    @Binds @IntoSet abstract fun timeWindow(editor: TimeWindowEditor): RuleTriggerEditor<*>
    @Binds @IntoSet abstract fun doNotDisturbOn(editor: DoNotDisturbOnEditor): RuleTriggerEditor<*>
    @Binds @IntoSet abstract fun inCall(editor: InCallEditor): RuleTriggerEditor<*>
}
