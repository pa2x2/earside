package eu.darken.capod.rules.ui.editor

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.rules.core.DeviceRule
import eu.darken.capod.rules.core.DeviceRulesRepo
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.RuleEntry
import eu.darken.capod.rules.core.RuleHandlers
import eu.darken.capod.rules.core.RuleId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.core.trigger.RuleRequirement
import eu.darken.capod.rules.core.trigger.wifi.LocationAccess
import eu.darken.capod.rules.ui.DeviceRuleItems
import eu.darken.capod.rules.ui.RuleEditorResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val repo: DeviceRulesRepo,
    private val handlers: RuleHandlers,
    private val editors: RuleEditors,
    private val items: DeviceRuleItems,
    private val profilesRepo: DeviceProfilesRepo,
    private val deviceMonitor: DeviceMonitor,
    private val locationAccess: LocationAccess,
    private val results: RuleEditorResults,
) : ViewModel4(dispatcherProvider) {

    data class Draft(
        val triggerType: KClass<out RuleTrigger>? = null,
        /** Null while the trigger's settings are incomplete. */
        val trigger: RuleTrigger? = null,
        /** The chosen action types; a value is null while that action's settings are incomplete. */
        val actions: Map<KClass<out RuleAction>, RuleAction?> = emptyMap(),
        val undoWhenEnds: Boolean = false,
        val name: String = "",
    )

    private data class Session(
        val profileId: ProfileId,
        /** The rule being edited, or null for a new one. */
        val original: DeviceRule?,
        /** What the editor opened with, to tell whether closing loses anything. */
        val initial: Draft,
        val draft: Draft,
    )

    data class ActionOption(val editor: RuleActionEditor<*>, val supported: Boolean)

    data class State(
        val isNew: Boolean,
        val draft: Draft,
        val deviceLabel: String,
        val model: PodModel,
        val device: PodDevice?,
        val triggerOptions: List<RuleTriggerEditor<*>>,
        val actionOptions: List<ActionOption>,
        /** Still missing for the chosen trigger type. */
        val missing: List<RuleRequirement>,
        /** Actions of other rules on the same event that set one of this rule's settings to something else. */
        val conflicts: List<String>,
        /** Null until the trigger is complete. */
        val undoText: String?,
        val hasChanges: Boolean,
    ) {
        val canSave: Boolean
            get() = draft.trigger != null &&
                draft.actions.isNotEmpty() &&
                draft.actions.values.none { it == null } &&
                missing.isEmpty()
    }

    private val session = MutableStateFlow<Session?>(null)

    fun initialize(profileId: ProfileId, ruleId: RuleId?) {
        if (session.value != null) return
        launch {
            val original = ruleId?.let { id ->
                val entry = repo.rulesFor(profileId).first().firstOrNull { it.id == id }
                (entry as? RuleEntry.Known)?.rule ?: run {
                    log(TAG, WARN) { "Rule $id not found or unreadable, closing editor" }
                    navUp()
                    return@launch
                }
            }
            val initial = original?.let {
                Draft(
                    triggerType = it.trigger::class,
                    trigger = it.trigger,
                    actions = it.actions.associateBy { action -> action::class },
                    undoWhenEnds = it.undoWhenEnds,
                    name = it.name.orEmpty(),
                )
            } ?: Draft()
            session.value = Session(profileId = profileId, original = original, initial = initial, draft = initial)
        }
    }

    private val missingForDraft: Flow<List<RuleRequirement>> = session
        .map { it?.draft?.triggerType }
        .flatMapLatest { type -> type?.let { handlers.forTriggerType(it)?.missingRequirements } ?: flowOf(emptyList()) }

    val state = session.filterNotNull().flatMapLatest { s ->
        combine(
            profilesRepo.profiles.map { profiles -> profiles.firstOrNull { it.id == s.profileId } },
            deviceMonitor.devices.map { devices -> devices.firstOrNull { it.profileId == s.profileId } },
            repo.rulesFor(s.profileId),
            missingForDraft,
        ) { profile, device, entries, missing ->
            val model = profile?.model ?: device?.model ?: PodModel.UNKNOWN
            val draft = s.draft
            State(
                isNew = s.original == null,
                draft = draft,
                deviceLabel = profile?.label.orEmpty(),
                model = model,
                device = device,
                triggerOptions = editors.triggers,
                actionOptions = editors.actions.map { editor ->
                    ActionOption(editor, handlers.forActionType(editor.type)?.isSupported(model.features) == true)
                },
                missing = missing,
                conflicts = conflicts(draft, s.original, entries),
                undoText = draft.trigger?.let { items.undoText(it) },
                hasChanges = draft.normalized() != s.initial.normalized(),
            )
        }
    }.asLiveState()

    // Rules on different events never run together; on the same event, the same value is harmless.
    private fun conflicts(draft: Draft, original: DeviceRule?, entries: List<RuleEntry>): List<String> {
        val trigger = draft.trigger ?: return emptyList()
        val actions = draft.actions.values.filterNotNull()
        return entries
            .mapNotNull { (it as? RuleEntry.Known)?.rule }
            .filter { it.id != original?.id && it.enabled && it.trigger == trigger }
            .flatMap { other ->
                other.actions
                    .filter { theirs -> actions.any { it::class == theirs::class && it != theirs } }
                    .map { theirs ->
                        val summary = items.actionSummary(theirs)
                        other.name?.let { "$it: $summary" } ?: summary
                    }
            }
    }

    private fun Draft.normalized() = copy(name = name.trim())

    private fun updateDraft(transform: (Draft) -> Draft) = session.update { it?.copy(draft = transform(it.draft)) }

    fun selectTriggerType(type: KClass<out RuleTrigger>) = updateDraft { draft ->
        if (draft.triggerType == type) return@updateDraft draft
        val carried = draft.trigger?.let { editors.forTrigger(type)?.carryOver(it) }
        draft.copy(triggerType = type, trigger = carried)
    }

    fun setTrigger(trigger: RuleTrigger?) = updateDraft { it.copy(trigger = trigger) }

    fun toggleActionType(type: KClass<out RuleAction>) = launch {
        if (session.value?.draft?.actions?.containsKey(type) == true) {
            updateDraft { it.copy(actions = it.actions - type) }
            return@launch
        }
        val current = state.first()
        val initial = editors.forAction(type)?.initial(current.model, current.device)
        updateDraft { it.copy(actions = it.actions + (type to initial)) }
    }

    fun setAction(type: KClass<out RuleAction>, action: RuleAction?) = updateDraft { draft ->
        if (type in draft.actions) draft.copy(actions = draft.actions + (type to action)) else draft
    }

    fun setUndoWhenEnds(enabled: Boolean) = updateDraft { it.copy(undoWhenEnds = enabled) }

    fun setName(name: String) = updateDraft { it.copy(name = name) }

    fun recheckRequirements() = locationAccess.recheck()

    fun save() = launch {
        val s = session.value ?: return@launch
        val current = state.first()
        if (!current.canSave) return@launch
        val trigger = s.draft.trigger ?: return@launch
        // In the order the editor lists them.
        val actions = editors.actions.mapNotNull { s.draft.actions[it.type] }
        val name = s.draft.name.trim().takeIf { it.isNotEmpty() }
        val saved = if (s.original == null) {
            DeviceRule(name = name, trigger = trigger, actions = actions, undoWhenEnds = s.draft.undoWhenEnds)
                .also { repo.addRule(s.profileId, it) }
        } else {
            s.original.copy(name = name, trigger = trigger, actions = actions, undoWhenEnds = s.draft.undoWhenEnds)
                .also { repo.updateRule(s.profileId, it) }
        }
        results.offer(RuleEditorResults.Result.Saved(s.profileId, saved.id))
        navUp()
    }

    fun delete() = launch {
        val s = session.value ?: return@launch
        val original = s.original ?: return@launch
        repo.removeRule(s.profileId, original.id)?.let { results.offer(RuleEditorResults.Result.Removed(it)) }
        navUp()
    }

    companion object {
        private val TAG = logTag("Rules", "Editor", "ViewModel")
    }
}
