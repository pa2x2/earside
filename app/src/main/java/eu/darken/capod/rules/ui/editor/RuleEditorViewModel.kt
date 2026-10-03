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
        val actionType: KClass<out RuleAction>? = null,
        val action: RuleAction? = null,
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
        /** Other rules on the same event that set the same setting to something else. */
        val conflicts: List<String>,
        val hasChanges: Boolean,
    ) {
        val canSave: Boolean get() = draft.trigger != null && draft.action != null && missing.isEmpty()
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
                    actionType = it.action::class,
                    action = it.action,
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
                hasChanges = draft.normalized() != s.initial.normalized(),
            )
        }
    }.asLiveState()

    // Rules on different events never run together; on the same event, the same value is harmless.
    private fun conflicts(draft: Draft, original: DeviceRule?, entries: List<RuleEntry>): List<String> {
        val trigger = draft.trigger ?: return emptyList()
        val action = draft.action ?: return emptyList()
        return entries
            .mapNotNull { (it as? RuleEntry.Known)?.rule }
            .filter { it.id != original?.id && it.enabled && it.trigger == trigger }
            .filter { it.action::class == action::class && it.action != action }
            .map { other -> other.name?.let { "$it: ${items.actionSummary(other)}" } ?: items.actionSummary(other) }
    }

    private fun Draft.normalized() = copy(name = name.trim())

    private fun updateDraft(transform: (Draft) -> Draft) = session.update { it?.copy(draft = transform(it.draft)) }

    fun selectTriggerType(type: KClass<out RuleTrigger>) = updateDraft { draft ->
        if (draft.triggerType == type) return@updateDraft draft
        val carried = draft.trigger?.let { editors.forTrigger(type)?.carryOver(it) }
        draft.copy(triggerType = type, trigger = carried)
    }

    fun setTrigger(trigger: RuleTrigger?) = updateDraft { it.copy(trigger = trigger) }

    fun selectActionType(type: KClass<out RuleAction>) = launch {
        if (session.value?.draft?.actionType == type) return@launch
        val current = state.first()
        val initial = editors.forAction(type)?.initial(current.model, current.device)
        updateDraft { it.copy(actionType = type, action = initial) }
    }

    fun setAction(action: RuleAction?) = updateDraft { it.copy(action = action) }

    fun setName(name: String) = updateDraft { it.copy(name = name) }

    fun recheckRequirements() = locationAccess.recheck()

    fun save() = launch {
        val s = session.value ?: return@launch
        val current = state.first()
        if (!current.canSave) return@launch
        val trigger = s.draft.trigger ?: return@launch
        val action = s.draft.action ?: return@launch
        val name = s.draft.name.trim().takeIf { it.isNotEmpty() }
        val saved = if (s.original == null) {
            DeviceRule(name = name, trigger = trigger, action = action).also { repo.addRule(s.profileId, it) }
        } else {
            s.original.copy(name = name, trigger = trigger, action = action).also { repo.updateRule(s.profileId, it) }
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
