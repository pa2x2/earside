package eu.darken.capod.rules.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.DeviceRulesRepo
import eu.darken.capod.rules.core.RemovedRule
import eu.darken.capod.rules.core.RuleHandlers
import eu.darken.capod.rules.core.RuleId
import eu.darken.capod.rules.ui.DeviceRuleItem
import eu.darken.capod.rules.ui.DeviceRuleItems
import eu.darken.capod.rules.ui.RemovedRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DeviceRulesViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val repo: DeviceRulesRepo,
    private val items: DeviceRuleItems,
    private val handlers: RuleHandlers,
    private val profilesRepo: DeviceProfilesRepo,
    private val removedRules: RemovedRules,
    private val timeSource: TimeSource,
) : ViewModel4(dispatcherProvider) {

    private val profileId = MutableStateFlow<ProfileId?>(null)

    fun initialize(profileId: ProfileId) {
        this.profileId.value = profileId
    }

    sealed interface Event {
        data class RuleRemoved(val removed: RemovedRule) : Event
    }

    val events = SingleEventFlow<Event>()

    data class State(
        val deviceLabel: String,
        val model: PodModel,
        val rules: List<DeviceRuleItem>,
        val notify: Boolean,
        /** False when this model supports none of the actions yet. */
        val hasAvailableActions: Boolean,
        val now: Instant,
    )

    val state = profileId.filterNotNull().flatMapLatest { id ->
        combine(
            profilesRepo.profiles.map { profiles -> profiles.firstOrNull { it.id == id } },
            items.observe(id),
            repo.notifyProfiles,
        ) { profile, rules, notify ->
            val model = profile?.model ?: PodModel.UNKNOWN
            State(
                deviceLabel = profile?.label.orEmpty(),
                model = model,
                rules = rules,
                notify = id in notify,
                hasAvailableActions = handlers.allActions.any { it.isSupported(model.features) },
                now = timeSource.now(),
            )
        }
    }.asLiveState()

    /** A rule deleted in the editor gets its Undo here, once the list is showing again. */
    fun onResumed() {
        removedRules.take()?.let { events.tryEmit(Event.RuleRemoved(it)) }
    }

    fun addRule() {
        val id = profileId.value ?: return
        navTo(Nav.Main.DeviceRuleEditor(profileId = id))
    }

    fun openRule(ruleId: RuleId) {
        val id = profileId.value ?: return
        navTo(Nav.Main.DeviceRuleEditor(profileId = id, ruleId = ruleId))
    }

    fun setEnabled(ruleId: RuleId, enabled: Boolean) = launch {
        val id = profileId.value ?: return@launch
        val rule = state.first().rules.firstOrNull { it.id == ruleId }?.rule ?: return@launch
        log(TAG) { "setEnabled($ruleId, $enabled)" }
        repo.updateRule(id, rule.copy(enabled = enabled))
    }

    /** Only for rules this version can't read; the others are deleted from their editor. */
    fun deleteRule(ruleId: RuleId) = launch {
        val id = profileId.value ?: return@launch
        repo.removeRule(id, ruleId)?.let { events.emit(Event.RuleRemoved(it)) }
    }

    fun undoRemove(removed: RemovedRule) = launch {
        repo.restoreRule(removed)
    }

    fun setNotify(enabled: Boolean) = launch {
        val id = profileId.value ?: return@launch
        repo.setNotify(id, enabled)
    }

    companion object {
        private val TAG = logTag("Rules", "List", "ViewModel")
    }
}
