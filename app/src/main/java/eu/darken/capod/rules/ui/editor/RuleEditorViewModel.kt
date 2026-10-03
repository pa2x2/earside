package eu.darken.capod.rules.ui.editor

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import eu.darken.capod.rules.ui.RemovedRules
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
    @ApplicationContext private val context: Context,
    private val repo: DeviceRulesRepo,
    private val handlers: RuleHandlers,
    private val editors: RuleEditors,
    private val items: DeviceRuleItems,
    private val profilesRepo: DeviceProfilesRepo,
    private val deviceMonitor: DeviceMonitor,
    private val locationAccess: LocationAccess,
    private val removedRules: RemovedRules,
) : ViewModel4(dispatcherProvider) {

    enum class Step { WHEN, THEN, REVIEW }

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
        val step: Step,
        /** A step opened from Review goes back to Review. */
        val fromReview: Boolean,
        val draft: Draft,
    )

    data class ActionOption(val editor: RuleActionEditor<*>, val supported: Boolean)

    data class State(
        val step: Step,
        val isNew: Boolean,
        val fromReview: Boolean,
        val draft: Draft,
        val deviceLabel: String,
        val model: PodModel,
        val device: PodDevice?,
        val triggerOptions: List<RuleTriggerEditor<*>>,
        val actionOptions: List<ActionOption>,
        /** Still missing for the chosen trigger type. */
        val missing: List<RuleRequirement>,
        /** Other rules on this device that change the same setting. */
        val conflicts: List<String>,
        val whenSummary: String?,
        val thenSummary: String?,
    ) {
        val canLeaveWhen: Boolean get() = draft.trigger != null && missing.isEmpty()
        val canLeaveThen: Boolean get() = draft.action != null
        val canSave: Boolean get() = canLeaveWhen && canLeaveThen
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
            session.value = Session(
                profileId = profileId,
                original = original,
                step = if (original == null) Step.WHEN else Step.REVIEW,
                fromReview = false,
                draft = original?.let {
                    Draft(
                        triggerType = it.trigger::class,
                        trigger = it.trigger,
                        actionType = it.action::class,
                        action = it.action,
                        name = it.name.orEmpty(),
                    )
                } ?: Draft(),
            )
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
            val conflicts = draft.actionType?.let { type ->
                entries
                    .mapNotNull { (it as? RuleEntry.Known)?.rule }
                    .filter { it.id != s.original?.id && it.enabled && it.action::class == type }
                    .map { items.triggerSummary(it) + " → " + items.actionSummary(it) }
            }.orEmpty()
            State(
                step = s.step,
                isNew = s.original == null,
                fromReview = s.fromReview,
                draft = draft,
                deviceLabel = profile?.label.orEmpty(),
                model = model,
                device = device,
                triggerOptions = editors.triggers,
                actionOptions = editors.actions.map { editor ->
                    ActionOption(editor, handlers.forActionType(editor.type)?.isSupported(model.features) == true)
                },
                missing = missing,
                conflicts = conflicts,
                whenSummary = draft.trigger?.let { handlers.forTrigger(it)?.summary(context, it) },
                thenSummary = draft.action?.let { handlers.forAction(it)?.summary(context, it) },
            )
        }
    }.asLiveState()

    private fun updateDraft(transform: (Draft) -> Draft) = session.update { it?.copy(draft = transform(it.draft)) }

    fun selectTriggerType(type: KClass<out RuleTrigger>) = updateDraft {
        if (it.triggerType == type) it else it.copy(triggerType = type, trigger = null)
    }

    fun setTrigger(trigger: RuleTrigger?) = updateDraft { it.copy(trigger = trigger) }

    fun selectActionType(type: KClass<out RuleAction>) = updateDraft {
        if (it.actionType == type) it else it.copy(actionType = type, action = null)
    }

    fun setAction(action: RuleAction?) = updateDraft { it.copy(action = action) }

    fun setName(name: String) = updateDraft { it.copy(name = name) }

    fun recheckRequirements() = locationAccess.recheck()

    fun openStep(step: Step) = session.update { it?.copy(step = step, fromReview = step != Step.REVIEW) }

    /** The step's main button: on to the next step, or back to Review when opened from there. */
    fun next() = session.update { s ->
        s ?: return@update null
        val next = when {
            s.fromReview -> Step.REVIEW
            s.step == Step.WHEN -> Step.THEN
            else -> Step.REVIEW
        }
        s.copy(step = next, fromReview = false)
    }

    /** Returns false when there is no earlier step and the editor should close. */
    fun back(): Boolean {
        val s = session.value ?: return false
        val previous = when {
            s.fromReview -> Step.REVIEW
            s.step == Step.REVIEW && s.original == null -> Step.THEN
            s.step == Step.THEN && s.original == null -> Step.WHEN
            else -> return false
        }
        session.value = s.copy(step = previous, fromReview = false)
        return true
    }

    fun save() = launch {
        val s = session.value ?: return@launch
        val current = state.first()
        if (!current.canSave) return@launch
        val trigger = s.draft.trigger ?: return@launch
        val action = s.draft.action ?: return@launch
        val name = s.draft.name.trim().takeIf { it.isNotEmpty() }
        if (s.original == null) {
            repo.addRule(s.profileId, DeviceRule(name = name, trigger = trigger, action = action))
        } else {
            repo.updateRule(s.profileId, s.original.copy(name = name, trigger = trigger, action = action))
        }
        navUp()
    }

    fun delete() = launch {
        val s = session.value ?: return@launch
        val original = s.original ?: return@launch
        repo.removeRule(s.profileId, original.id)?.let { removedRules.offer(it) }
        navUp()
    }

    companion object {
        private val TAG = logTag("Rules", "Editor", "ViewModel")
    }
}
