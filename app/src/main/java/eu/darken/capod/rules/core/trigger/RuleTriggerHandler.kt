package eu.darken.capod.rules.core.trigger

import android.content.Context
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.RuleTrigger
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Everything one kind of [RuleTrigger] does. Bound into a set with `@IntoSet` and looked up by
 * [type]; a new trigger is a new handler, nothing else changes.
 */
interface RuleTriggerHandler<T : RuleTrigger> {

    val type: KClass<T>

    /** E.g. "When connected to Home". */
    fun summary(context: Context, trigger: T): String

    /** E.g. "You're on Home now", offering to apply a rule whose condition already holds. */
    fun holdsNowText(context: Context, trigger: T): String

    /** E.g. "Back to the previous setting when you leave Home", explaining undo for this trigger. */
    fun undoText(context: Context, trigger: T): String

    /** What the phone still has to allow before this trigger can be observed, in the order to ask for it. */
    val missingRequirements: Flow<List<RuleRequirement>>

    /**
     * Whether [trigger]'s condition holds right now for a rule on [profileId]'s device, so a trigger
     * can watch that device. Emits on every change.
     */
    fun condition(profileId: ProfileId, trigger: T): Flow<TriggerCondition>

    /**
     * How long an occurrence may wait for the device before it's dropped; null waits as long as the
     * condition holds. Set it when the condition marks a moment rather than a lasting state: a volume
     * set "because you connected" shouldn't land hours later.
     */
    val maxWait: Duration? get() = null
}

sealed interface TriggerCondition {

    /** Can't tell right now, e.g. location access is missing. Never counts as a change either way. */
    data object Unknown : TriggerCondition

    /**
     * [occurrence] tells apart two separate times the condition held, when the trigger can (a Wi-Fi
     * session's network handle), so rejoining while Earside wasn't watching still counts as new.
     */
    data class Known(val holds: Boolean, val occurrence: String? = null) : TriggerCondition
}

/** A step the user must take before a trigger can work. Shared by triggers so steps are asked once. */
enum class RuleRequirement {
    PRECISE_LOCATION,
    BACKGROUND_LOCATION,
    LOCATION_SERVICES,
}
