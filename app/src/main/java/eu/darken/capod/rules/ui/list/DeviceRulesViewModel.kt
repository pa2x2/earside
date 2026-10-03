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
import eu.darken.capod.rules.core.DeviceRulesEngine
import eu.darken.capod.rules.core.DeviceRulesRepo
import eu.darken.capod.rules.core.RemovedRule
import eu.darken.capod.rules.core.RuleEntry
import eu.darken.capod.rules.core.RuleHandlers
import eu.darken.capod.rules.core.RuleId
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.core.trigger.wifi.LocationAccess
import eu.darken.capod.rules.ui.DeviceRuleItem
import eu.darken.capod.rules.ui.DeviceRuleItems
import eu.darken.capod.rules.ui.RuleEditorResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class DeviceRulesViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val repo: DeviceRulesRepo,
    private val items: DeviceRuleItems,
    private val handlers: RuleHandlers,
    private val engine: DeviceRulesEngine,
    private val profilesRepo: DeviceProfilesRepo,
    private val editorResults: RuleEditorResults,
    private val locationAccess: LocationAccess,
    private val timeSource: TimeSource,
) : ViewModel4(dispatcherProvider) {

    private val profileId = MutableStateFlow<ProfileId?>(null)

    fun initialize(profileId: ProfileId) {
        this.profileId.value = profileId
    }

    sealed interface Event {
        data class RuleRemoved(val removed: RemovedRule) : Event

        /** [text] says the rule's condition holds now, e.g. "You're on Home now". */
        data class OfferApply(val ruleId: RuleId, val text: String) : Event
    }

    val events = SingleEventFlow<Event>()

    data class State(
        val deviceLabel: String,
        val model: PodModel,
        val rules: List<DeviceRuleItem>,
        /** What the phone still has to allow for the switched-on rules, in the order to ask for it. */
        val missing: List<RuleRequirement>,
        val notify: Boolean,
        /** False when this model supports none of the actions yet. */
        val hasAvailableActions: Boolean,
        val now: Instant,
    )

    // Keeps "Applied 5 minutes ago" current while the list is open.
    private val clock = flow {
        while (true) {
            emit(timeSource.now())
            delay(30.seconds)
        }
    }

    val state = profileId.filterNotNull().flatMapLatest { id ->
        combine(
            profilesRepo.profiles.map { profiles -> profiles.firstOrNull { it.id == id } },
            items.observe(id),
            repo.notifyProfiles,
            clock,
        ) { profile, rules, notify, now ->
            val model = profile?.model ?: PodModel.UNKNOWN
            State(
                deviceLabel = profile?.label.orEmpty(),
                model = model,
                rules = rules,
                missing = rules.filter { it.rule?.enabled == true }.flatMap { it.missing }.distinct().sorted(),
                notify = id in notify,
                hasAvailableActions = handlers.allActions.any { it.isSupported(model.features) },
                now = now,
            )
        }
    }.asLiveState()

    /** Access may have been granted in system settings; the editor may have left a result to react to. */
    fun onResumed() {
        locationAccess.recheck()
        when (val result = editorResults.take()) {
            is RuleEditorResults.Result.Removed -> events.tryEmit(Event.RuleRemoved(result.removed))
            is RuleEditorResults.Result.Saved -> launch { offerApply(result.profileId, result.ruleId) }
            null -> Unit
        }
    }

    fun recheckRequirements() = locationAccess.recheck()

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
        if (enabled) offerApply(id, ruleId)
    }

    // A saved or re-enabled rule only reacts to the next occurrence; offer to apply it for this one.
    private suspend fun offerApply(profileId: ProfileId, ruleId: RuleId) {
        val rule = knownRule(profileId, ruleId) ?: return
        if (!engine.canApplyNow(profileId, rule)) return
        events.emit(Event.OfferApply(ruleId, items.holdsNowText(rule)))
    }

    fun applyNow(ruleId: RuleId) = launch {
        val id = profileId.value ?: return@launch
        val rule = knownRule(id, ruleId) ?: return@launch
        engine.applyNow(rule)
    }

    private suspend fun knownRule(profileId: ProfileId, ruleId: RuleId) = repo.rulesFor(profileId).first()
        .firstNotNullOfOrNull { entry -> (entry as? RuleEntry.Known)?.rule?.takeIf { it.id == ruleId } }

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
