package eu.darken.capod.rules.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.rules.core.action.RuleActionHandler
import eu.darken.capod.rules.core.action.SetAncModeHandler
import eu.darken.capod.rules.core.action.SetConversationalAwarenessHandler
import javax.inject.Inject
import javax.inject.Singleton

/** Finds the handler for an action by its type. */
@Singleton
class RuleHandlers @Inject constructor(
    actionHandlers: Set<@JvmSuppressWildcards RuleActionHandler<*>>,
) {

    private val actions = actionHandlers.associateBy { it.type }

    val allActions: Collection<RuleActionHandler<*>> get() = actions.values

    @Suppress("UNCHECKED_CAST")
    fun <A : RuleAction> forAction(action: A): RuleActionHandler<A>? = actions[action::class] as RuleActionHandler<A>?
}

@InstallIn(SingletonComponent::class)
@Module
abstract class RuleHandlersModule {
    @Binds @IntoSet abstract fun setAncMode(handler: SetAncModeHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setConversationalAwareness(handler: SetConversationalAwarenessHandler): RuleActionHandler<*>
}
