package eu.darken.capod.rules.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.automirrored.twotone.Label
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.ui.RuleRequirementSteps
import eu.darken.capod.rules.ui.editor.RuleEditorViewModel.Step
import kotlin.reflect.KClass

@Composable
fun RuleEditorScreenHost(
    profileId: String,
    ruleId: String?,
    vm: RuleEditorViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(profileId, ruleId) { vm.initialize(profileId, ruleId) }
    // Permissions and location come back from system pages without a callback.
    LifecycleResumeEffect(Unit) {
        vm.recheckRequirements()
        onPauseOrDispose { }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val current = state ?: return

    BackHandler { if (!vm.back()) vm.navUp() }

    RuleEditorScreen(
        state = current,
        onClose = { vm.navUp() },
        onDelete = { vm.delete() },
        onTriggerType = { vm.selectTriggerType(it) },
        onTrigger = { vm.setTrigger(it) },
        onActionType = { vm.selectActionType(it) },
        onAction = { vm.setAction(it) },
        onName = { vm.setName(it) },
        onRequirementReturned = { vm.recheckRequirements() },
        onOpenStep = { vm.openStep(it) },
        onBack = { if (!vm.back()) vm.navUp() },
        onNext = { vm.next() },
        onSave = { vm.save() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    state: RuleEditorViewModel.State,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onTriggerType: (KClass<out RuleTrigger>) -> Unit,
    onTrigger: (RuleTrigger?) -> Unit,
    onActionType: (KClass<out RuleAction>) -> Unit,
    onAction: (RuleAction?) -> Unit,
    onName: (String) -> Unit,
    onRequirementReturned: () -> Unit,
    onOpenStep: (Step) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (state.isNew) R.string.rules_editor_new_title else R.string.rules_editor_edit_title))
                        if (state.deviceLabel.isNotEmpty()) {
                            Text(
                                text = state.deviceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.TwoTone.Close, contentDescription = stringResource(R.string.general_close_action))
                    }
                },
                actions = {
                    if (!state.isNew && state.step == Step.REVIEW) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.rules_delete_action))
                        }
                    }
                },
            )
        },
        bottomBar = {
            StepButtons(state = state, onBack = onBack, onNext = onNext, onSave = onSave)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            StepHeader(state.step, showCounter = state.isNew)
            when (state.step) {
                Step.WHEN -> WhenStep(state, onTriggerType, onTrigger, onRequirementReturned)
                Step.THEN -> ThenStep(state, onActionType, onAction)
                Step.REVIEW -> ReviewStep(state, onName, onOpenStep, onRequirementReturned)
            }
        }
    }
}

@Composable
private fun StepHeader(step: Step, showCounter: Boolean) {
    val (title, index) = when (step) {
        Step.WHEN -> R.string.rules_editor_when_title to 1
        Step.THEN -> R.string.rules_editor_then_title to 2
        Step.REVIEW -> R.string.rules_editor_review_title to 3
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Editing jumps between steps from Review, so a position in the sequence means nothing there.
        if (showCounter) {
            Text(
                text = stringResource(R.string.rules_editor_step_counter, index, 3),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(text = stringResource(title), style = MaterialTheme.typography.titleLarge)
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun WhenStep(
    state: RuleEditorViewModel.State,
    onTriggerType: (KClass<out RuleTrigger>) -> Unit,
    onTrigger: (RuleTrigger?) -> Unit,
    onRequirementReturned: () -> Unit,
) {
    SettingsSection {
        state.triggerOptions.forEach { editor ->
            ChoiceRow(
                title = stringResource(editor.label),
                icon = editor.icon,
                selected = state.draft.triggerType == editor.type,
                onClick = { onTriggerType(editor.type) },
            )
        }
    }
    val selected = state.triggerOptions.firstOrNull { it.type == state.draft.triggerType } ?: return
    selected as RuleTriggerEditor<RuleTrigger>
    // Keyed by type: switching types starts that type's settings fresh.
    key(selected.type) {
        selected.Settings(current = state.draft.trigger?.takeIf { selected.type.isInstance(it) }, onChange = onTrigger)
    }
    RuleRequirementSteps(missing = state.missing, onReturned = onRequirementReturned)
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ThenStep(
    state: RuleEditorViewModel.State,
    onActionType: (KClass<out RuleAction>) -> Unit,
    onAction: (RuleAction?) -> Unit,
) {
    SettingsSection {
        state.actionOptions.forEach { option ->
            ChoiceRow(
                title = stringResource(option.editor.label),
                subtitle = if (option.supported) null else stringResource(R.string.rules_action_not_supported, state.model.label),
                icon = option.editor.icon,
                selected = state.draft.actionType == option.editor.type,
                enabled = option.supported,
                onClick = { onActionType(option.editor.type) },
            )
        }
    }
    val selected = state.actionOptions.firstOrNull { it.editor.type == state.draft.actionType }?.editor ?: return
    selected as RuleActionEditor<RuleAction>
    SettingsSection {
        key(selected.type) {
            selected.Settings(
                current = state.draft.action?.takeIf { selected.type.isInstance(it) },
                model = state.model,
                device = state.device,
                onChange = onAction,
            )
        }
    }
    Conflicts(state.conflicts)
}

@Composable
private fun Conflicts(conflicts: List<String>) {
    if (conflicts.isEmpty()) return
    SettingsInfoBox(
        title = stringResource(R.string.rules_conflict_title),
        text = conflicts.joinToString("\n") + "\n\n" + stringResource(R.string.rules_conflict_order),
    )
}

@Composable
private fun ReviewStep(
    state: RuleEditorViewModel.State,
    onName: (String) -> Unit,
    onOpenStep: (Step) -> Unit,
    onRequirementReturned: () -> Unit,
) {
    SettingsSection {
        val whenEditor = state.triggerOptions.firstOrNull { it.type == state.draft.triggerType }
        SettingsBaseItem(
            title = state.whenSummary.orEmpty(),
            icon = whenEditor?.icon,
            onClick = { onOpenStep(Step.WHEN) },
            trailingContent = { Icon(Icons.TwoTone.Edit, contentDescription = null) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        val thenEditor = state.actionOptions.firstOrNull { it.editor.type == state.draft.actionType }?.editor
        SettingsBaseItem(
            title = state.thenSummary.orEmpty(),
            icon = thenEditor?.icon,
            onClick = { onOpenStep(Step.THEN) },
            trailingContent = { Icon(Icons.TwoTone.Edit, contentDescription = null) },
        )
    }
    RuleRequirementSteps(missing = state.missing, onReturned = onRequirementReturned)
    Conflicts(state.conflicts)
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Held here: echoing each keystroke back through the ViewModel's state flow arrives late
        // and moves the cursor, scrambling typed text.
        var name by rememberSaveable { mutableStateOf(state.draft.name) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onName(it)
            },
            label = { Text(stringResource(R.string.rules_name_label)) },
            placeholder = { Text(stringResource(R.string.rules_name_placeholder)) },
            leadingIcon = { Icon(Icons.AutoMirrored.TwoTone.Label, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepButtons(
    state: RuleEditorViewModel.State,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val showBack = state.fromReview || (state.isNew && state.step != Step.WHEN)
            if (showBack) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.rules_editor_back)) }
            } else {
                Spacer(Modifier)
            }
            when (state.step) {
                Step.WHEN -> Button(onClick = onNext, enabled = state.canLeaveWhen) {
                    Text(stringResource(if (state.fromReview) R.string.general_done_action else R.string.rules_editor_next))
                }

                Step.THEN -> Button(onClick = onNext, enabled = state.canLeaveThen) {
                    Text(stringResource(if (state.fromReview) R.string.general_done_action else R.string.rules_editor_next))
                }

                Step.REVIEW -> Button(onClick = onSave, enabled = state.canSave) {
                    Text(stringResource(R.string.general_save_action))
                }
            }
        }
    }
}
