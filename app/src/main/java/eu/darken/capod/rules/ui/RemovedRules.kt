package eu.darken.capod.rules.ui

import eu.darken.capod.rules.core.RemovedRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/** Hands a rule deleted in the editor to the rule list, which offers Undo after the editor closes. */
@Singleton
class RemovedRules @Inject constructor() {

    private val _latest = MutableStateFlow<RemovedRule?>(null)
    val latest: StateFlow<RemovedRule?> = _latest.asStateFlow()

    fun offer(removed: RemovedRule) {
        _latest.value = removed
    }

    fun take(): RemovedRule? = _latest.getAndUpdate { null }
}
