package eu.darken.capod.rules.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.rules.core.action.RuleActionHandler
import eu.darken.capod.rules.core.action.SetAncModeHandler
import eu.darken.capod.rules.core.action.SetConversationalAwarenessHandler
import eu.darken.capod.rules.core.action.SetAdaptiveAudioNoiseHandler
import eu.darken.capod.rules.core.action.SetMicrophoneModeHandler
import eu.darken.capod.rules.core.action.SetNcWithOneAirPodHandler
import eu.darken.capod.rules.core.action.SetPersonalizedVolumeHandler
import eu.darken.capod.rules.core.action.SetSleepDetectionHandler
import eu.darken.capod.rules.core.action.SetToneVolumeHandler
import eu.darken.capod.rules.core.action.SetVolumeSwipeHandler
import eu.darken.capod.rules.core.action.SetMediaVolumeHandler
import eu.darken.capod.rules.core.trigger.RuleTriggerHandler
import eu.darken.capod.rules.core.trigger.time.TimeWindowHandler
import eu.darken.capod.rules.core.trigger.phone.DoNotDisturbOnHandler
import eu.darken.capod.rules.core.trigger.phone.InCallHandler
import eu.darken.capod.rules.core.trigger.device.AirPodsConnectedHandler
import eu.darken.capod.rules.core.trigger.device.BatteryLowHandler
import eu.darken.capod.rules.core.trigger.device.WearingHandler
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
    @Binds @IntoSet abstract fun timeWindow(handler: TimeWindowHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun doNotDisturbOn(handler: DoNotDisturbOnHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun inCall(handler: InCallHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun airPodsConnected(handler: AirPodsConnectedHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun wearing(handler: WearingHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun batteryLow(handler: BatteryLowHandler): RuleTriggerHandler<*>
    @Binds @IntoSet abstract fun setVolumeSwipe(handler: SetVolumeSwipeHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setAdaptiveAudioNoise(handler: SetAdaptiveAudioNoiseHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setToneVolume(handler: SetToneVolumeHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setPersonalizedVolume(handler: SetPersonalizedVolumeHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setNcWithOneAirPod(handler: SetNcWithOneAirPodHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setMicrophoneMode(handler: SetMicrophoneModeHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setSleepDetection(handler: SetSleepDetectionHandler): RuleActionHandler<*>
    @Binds @IntoSet abstract fun setMediaVolume(handler: SetMediaVolumeHandler): RuleActionHandler<*>
}
