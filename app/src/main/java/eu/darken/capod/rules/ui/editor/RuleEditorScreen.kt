package eu.darken.capod.rules.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Label
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.ui.RuleRequirementSteps
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

    RuleEditorScreen(
        state = current,
        onClose = { vm.navUp() },
        onDelete = { vm.delete() },
        onTriggerType = { vm.selectTriggerType(it) },
        onTrigger = { vm.setTrigger(it) },
        onActionType = { vm.toggleActionType(it) },
        onAction = { type, action -> vm.setAction(type, action) },
        onUndoWhenEnds = { vm.setUndoWhenEnds(it) },
        onName = { vm.setName(it) },
        onRequirementReturned = { vm.recheckRequirements() },
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
    onAction: (KClass<out RuleAction>, RuleAction?) -> Unit,
    onUndoWhenEnds: (Boolean) -> Unit,
    onName: (String) -> Unit,
    onRequirementReturned: () -> Unit,
    onSave: () -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (state.hasChanges) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = requestClose)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.rules_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.rules_discard_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.rules_keep_editing_action)) }
            },
        )
    }

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
                    IconButton(onClick = requestClose) {
                        Icon(Icons.TwoTone.Close, contentDescription = stringResource(R.string.general_close_action))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.rules_delete_action))
                        }
                    }
                },
            )
        },
        bottomBar = { SaveBar(enabled = state.canSave, onSave = onSave) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            WhenSection(state, onTriggerType, onTrigger)
            RuleRequirementSteps(missing = state.missing, onReturned = onRequirementReturned)
            Spacer(Modifier.height(8.dp))
            ThenSection(state, onActionType, onAction)
            Conflicts(state.conflicts)
            SettingsSwitchItem(
                icon = Icons.AutoMirrored.TwoTone.Undo,
                title = stringResource(R.string.rules_undo_label),
                subtitle = state.undoText ?: stringResource(R.string.rules_undo_description),
                checked = state.draft.undoWhenEnds,
                onCheckedChange = onUndoWhenEnds,
            )
            NameField(initial = state.draft.name, onName = onName)
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun WhenSection(
    state: RuleEditorViewModel.State,
    onTriggerType: (KClass<out RuleTrigger>) -> Unit,
    onTrigger: (RuleTrigger?) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.rules_editor_when_section)) {
        state.triggerOptions.forEachIndexed { index, editor ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            val selected = state.draft.triggerType == editor.type
            ChoiceRow(
                title = stringResource(editor.label),
                icon = editor.icon,
                selected = selected,
                onClick = { onTriggerType(editor.type) },
            )
            if (selected) {
                editor as RuleTriggerEditor<RuleTrigger>
                // Keyed by type: the new type's settings start from what the draft carried over.
                key(editor.type) {
                    editor.Settings(current = state.draft.trigger?.takeIf { editor.type.isInstance(it) }, onChange = onTrigger)
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ThenSection(
    state: RuleEditorViewModel.State,
    onActionType: (KClass<out RuleAction>) -> Unit,
    onAction: (KClass<out RuleAction>, RuleAction?) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.rules_editor_then_section)) {
        state.actionOptions.forEachIndexed { index, option ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            val editor = option.editor
            val checked = editor.type in state.draft.actions
            CheckRow(
                title = stringResource(editor.label),
                subtitle = if (option.supported) null else stringResource(R.string.rules_action_not_supported, state.model.label),
                icon = editor.icon,
                checked = checked,
                // A rule saved before the model changed can still drop the action it no longer supports.
                enabled = option.supported || checked,
                onClick = { onActionType(editor.type) },
            )
            if (checked) {
                editor as RuleActionEditor<RuleAction>
                key(editor.type) {
                    editor.Settings(
                        current = state.draft.actions[editor.type],
                        model = state.model,
                        device = state.device,
                        onChange = { onAction(editor.type, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Conflicts(conflicts: List<String>) {
    if (conflicts.isEmpty()) return
    SettingsInfoBox(
        title = stringResource(R.string.rules_conflict_title),
        text = conflicts.joinToString("\n") + "\n\n" + stringResource(R.string.rules_conflict_explanation),
        type = InfoBoxType.WARNING,
    )
}

@Composable
private fun NameField(initial: String, onName: (String) -> Unit) {
    // Held here: echoing each keystroke back through the ViewModel's state flow arrives late and
    // moves the cursor, scrambling typed text.
    var name by rememberSaveable { mutableStateOf(initial) }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SaveBar(enabled: Boolean, onSave: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onSave, enabled = enabled) {
                Text(stringResource(R.string.general_save_action))
            }
        }
    }
}
