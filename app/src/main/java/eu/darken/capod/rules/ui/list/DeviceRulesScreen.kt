package eu.darken.capod.rules.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.AutoMode
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.DeviceRule
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.RuleId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.ui.DeviceRuleItem
import eu.darken.capod.rules.ui.RuleStatus
import eu.darken.capod.rules.ui.text
import java.time.Instant

@Composable
fun DeviceRulesScreenHost(
    profileId: String,
    vm: DeviceRulesViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(profileId) { vm.initialize(profileId) }
    LifecycleResumeEffect(Unit) {
        vm.onResumed()
        onPauseOrDispose { }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.rules_removed_message)
    val undoLabel = stringResource(R.string.rules_undo_action)
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DeviceRulesViewModel.Event.RuleRemoved -> {
                    val result = snackbarHostState.showSnackbar(
                        message = removedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoRemove(event.removed)
                }
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val current = state ?: return

    DeviceRulesScreen(
        state = current,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onAddRule = { vm.addRule() },
        onOpenRule = { vm.openRule(it) },
        onEnabledChange = { id, enabled -> vm.setEnabled(id, enabled) },
        onDeleteUnsupported = { vm.deleteRule(it) },
        onNotifyChange = { vm.setNotify(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceRulesScreen(
    state: DeviceRulesViewModel.State,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAddRule: () -> Unit = {},
    onOpenRule: (RuleId) -> Unit = {},
    onEnabledChange: (RuleId, Boolean) -> Unit = { _, _ -> },
    onDeleteUnsupported: (RuleId) -> Unit = {},
    onNotifyChange: (Boolean) -> Unit = {},
) {
    var deleteCandidate by rememberSaveable { mutableStateOf<RuleId?>(null) }
    deleteCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            text = { Text(stringResource(R.string.rules_unsupported_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    onDeleteUnsupported(id)
                }) { Text(stringResource(R.string.rules_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.rules_title))
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
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.rules_add_action)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item("description") {
                Text(
                    text = stringResource(R.string.rules_list_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (!state.hasAvailableActions) {
                item("no_actions") {
                    SettingsInfoBox(
                        text = stringResource(R.string.rules_no_actions_for_model, state.model.label),
                    )
                }
            }

            item("notify") {
                SettingsSection {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.Notifications,
                        title = stringResource(R.string.rules_notify_label),
                        subtitle = stringResource(R.string.rules_notify_description),
                        checked = state.notify,
                        onCheckedChange = onNotifyChange,
                    )
                }
            }

            if (state.rules.isEmpty()) {
                item("empty") { EmptyRules(showExample = state.hasAvailableActions) }
            } else {
                item("rules") {
                    SettingsSection(title = stringResource(R.string.rules_section_label)) {
                        state.rules.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            RuleRow(
                                item = item,
                                now = state.now,
                                onClick = { if (item.rule != null) onOpenRule(item.id) else deleteCandidate = item.id },
                                onEnabledChange = { onEnabledChange(item.id, it) },
                            )
                        }
                    }
                }
            }

            item("fab_spacer") { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun RuleRow(
    item: DeviceRuleItem,
    now: Instant,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val enabled = item.rule?.enabled ?: false
    val contentAlpha = if (item.rule == null || enabled) 1f else 0.6f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon ?: Icons.TwoTone.Upgrade,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha),
            modifier = Modifier
                .align(Alignment.Top)
                .padding(top = 2.dp)
                .size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
            item.status.text(context, now)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status.needsAttention) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (item.rule != null) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun EmptyRules(showExample: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.TwoTone.AutoMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.rules_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        // The example names actions; next to "No actions are available" it would promise them anyway.
        if (showExample) {
            Text(
                text = stringResource(R.string.rules_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview2
@Composable
private fun DeviceRulesScreenPreview() = PreviewWrapper {
    val rule = DeviceRule(
        trigger = RuleTrigger.WifiConnected("Home"),
        action = RuleAction.SetAncMode(AapSetting.AncMode.Value.OFF),
    )
    DeviceRulesScreen(
        state = DeviceRulesViewModel.State(
            deviceLabel = "My AirPods Pro",
            model = PodModel.AIRPODS_PRO2,
            rules = listOf(
                DeviceRuleItem(
                    id = rule.id,
                    rule = rule,
                    title = "When connected to Home",
                    subtitle = "Listening mode: Off",
                    icon = null,
                    status = RuleStatus.Waiting,
                ),
            ),
            notify = false,
            hasAvailableActions = true,
            now = Instant.now(),
        ),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun DeviceRulesScreenEmptyPreview() = PreviewWrapper {
    DeviceRulesScreen(
        state = DeviceRulesViewModel.State(
            deviceLabel = "My AirPods",
            model = PodModel.AIRPODS_GEN4,
            rules = emptyList(),
            notify = false,
            hasAvailableActions = false,
            now = Instant.now(),
        ),
        onNavigateUp = {},
    )
}
