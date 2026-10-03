package eu.darken.capod.rules.ui

import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.RemovedRule
import eu.darken.capod.rules.core.RuleId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands what the editor did to the rule list, which reacts once the editor has closed: Undo for a
 * deleted rule, Apply now for a saved one.
 */
@Singleton
class RuleEditorResults @Inject constructor() {

    sealed interface Result {
        data class Removed(val removed: RemovedRule) : Result
        data class Saved(val profileId: ProfileId, val ruleId: RuleId) : Result
    }

    private val latest = MutableStateFlow<Result?>(null)

    fun offer(result: Result) {
        latest.value = result
    }

    fun take(): Result? = latest.getAndUpdate { null }
}
