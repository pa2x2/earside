package eu.darken.capod.rules.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.rules.core.action.RuleActionHandler
import eu.darken.capod.rules.core.action.SetAncModeHandler
import eu.darken.capod.rules.core.action.SetConversationalAwarenessHandler
import eu.darken.capod.rules.core.trigger.RuleTriggerHandler
import eu.darken.capod.rules.core.trigger.wifi.WifiConnectedHandler
import eu.darken.capod.rules.core.trigger.wifi.WifiDisconnectedHandler
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Finds the handler for a trigger or action by its type. */
@Singleton
class RuleHandlers @Inject constructor(
    triggerHandlers: Set<@JvmSuppressWildcards RuleTriggerHandler<*>>,
    actionHandlers: Set<@JvmSuppressWildcards RuleActionHandler<*>>,
) {

    private val triggers = triggerHandlers.associateBy { it.type }
    private val actions = actionHandlers.associateBy { it.type }

    val allTriggers: Collection<RuleTriggerHandler<*>> get() = triggers.values

    val allActions: Collection<RuleActionHandler<*>> get() = actions.values

    @Suppress("UNCHECKED_CAST")
    fun <T : RuleTrigger> forTrigger(trigger: T): RuleTriggerHandler<T>? = triggers[trigger::class] as RuleTriggerHandler<T>?

    fun forTriggerType(type: KClass<out RuleTrigger>): RuleTriggerHandler<*>? = triggers[type]

    fun forActionType(type: KClass<out RuleAction>): RuleActionHandler<*>? = actions[type]

    @Suppress("UNCHECKED_CAST")
    fun <A : RuleAction> forAction(action: A): RuleActionHandler<A>? = actions[action::class] as RuleActionHandler<A>?
}

@InstallIn(SingletonComponent::class)
@Module
abstract class RuleHandlersModule {
    @Binds @IntoSet abstract fun wifiConnected(handler: WifiConnectedHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun wifiDisconnected(handler: WifiDisconnectedHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun setAncMode(handler: SetAncModeHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setConversationalAwareness(handler: SetConversationalAwarenessHandler): RuleActionHandler<*>
}
