package eu.darken.capod.rules.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Upgrade
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material.icons.twotone.WifiOff
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.rules.core.DeviceRule
import eu.darken.capod.rules.core.RuleAction
import eu.darken.capod.rules.core.RuleId
import eu.darken.capod.rules.core.RuleTrigger
import eu.darken.capod.rules.ui.DeviceRuleItem
import eu.darken.capod.rules.ui.DeviceRuleLine
import eu.darken.capod.rules.ui.RuleRequirementSteps
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
    val applyLabel = stringResource(R.string.rules_apply_now_action)
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

                is DeviceRulesViewModel.Event.OfferApply -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = applyLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.applyNow(event.ruleId)
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
        onDelete = { vm.deleteRule(it) },
        onNotifyChange = { vm.setNotify(it) },
        onRequirementReturned = { vm.recheckRequirements() },
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
    onDelete: (RuleId) -> Unit = {},
    onNotifyChange: (Boolean) -> Unit = {},
    onRequirementReturned: () -> Unit = {},
) {
    var deleteCandidate by rememberSaveable { mutableStateOf<RuleId?>(null) }
    deleteCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            text = { Text(stringResource(R.string.rules_unsupported_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    onDelete(id)
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
            // Every action would be greyed out in the editor.
            if (state.hasAvailableActions) {
                ExtendedFloatingActionButton(
                    onClick = onAddRule,
                    icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.rules_add_action)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            if (!state.hasAvailableActions) {
                item("no_actions") {
                    SettingsInfoBox(
                        text = stringResource(R.string.rules_no_actions_for_model, state.model.label),
                    )
                }
            }

            // The rules share their trigger's requirements, so one card fixes all of them.
            if (state.missing.isNotEmpty()) {
                item("requirements") {
                    RuleRequirementSteps(missing = state.missing, onReturned = onRequirementReturned)
                }
            }

            if (state.rules.isEmpty()) {
                item("empty") { EmptyRules(showExplainer = state.hasAvailableActions) }
            } else {
                item("rules") {
                    SettingsSection(title = stringResource(R.string.rules_section_label)) {
                        state.rules.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            key(item.id) {
                                SwipeToDelete(onDelete = { onDelete(item.id) }) {
                                    if (item.rule != null) {
                                        RuleRow(
                                            item = item,
                                            rule = item.rule,
                                            now = state.now,
                                            onClick = { onOpenRule(item.id) },
                                            onEnabledChange = { onEnabledChange(item.id, it) },
                                        )
                                    } else {
                                        UnsupportedRuleRow(onDelete = { deleteCandidate = item.id })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item("notify") {
                SettingsSection(modifier = Modifier.padding(top = 8.dp)) {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.Notifications,
                        title = stringResource(R.string.rules_notify_label),
                        subtitle = stringResource(R.string.rules_notify_description),
                        checked = state.notify,
                        onCheckedChange = onNotifyChange,
                    )
                }
            }

            item("fab_spacer") { Spacer(Modifier.height(88.dp)) }
        }
    }
}

/** Deleting offers Undo, so a swipe needs no confirmation. */
@Composable
private fun SwipeToDelete(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        onDismiss = { onDelete() },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(2) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        // Opaque, so the delete background only shows while the row is being swiped.
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
    }
}

@Composable
private fun RuleRow(
    item: DeviceRuleItem,
    rule: DeviceRule,
    now: Instant,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val contentAlpha = if (rule.enabled) 1f else 0.6f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            rule.name?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            // Without a name, the When line leads the row.
            RuleLine(
                icon = item.whenIcon,
                text = item.whenText,
                style = if (rule.name == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                alpha = contentAlpha * if (rule.name == null) 1f else 0.7f,
            )
            item.thenLines.forEach { line ->
                RuleLine(
                    icon = line.icon,
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    alpha = contentAlpha * 0.7f,
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun RuleLine(
    icon: ImageVector?,
    text: String,
    style: TextStyle,
    alpha: Float,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * alpha),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}

@Composable
private fun UnsupportedRuleRow(onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDelete)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Upgrade,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.Top)
                .padding(top = 2.dp)
                .size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.rules_unsupported_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.rules_unsupported_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.rules_delete_action))
        }
    }
}

@Composable
private fun EmptyRules(showExplainer: Boolean) {
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
        // It promises a setting change; next to "No actions are available" there is none to make.
        if (showExplainer) {
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
    val named = DeviceRule(
        name = "At home",
        trigger = RuleTrigger.WifiConnected("Home"),
        actions = listOf(
            RuleAction.SetAncMode(AapSetting.AncMode.Value.TRANSPARENCY),
            RuleAction.SetConversationalAwareness(enabled = true),
        ),
    )
    val unnamed = DeviceRule(
        trigger = RuleTrigger.WifiDisconnected("Home"),
        actions = listOf(RuleAction.SetAncMode(AapSetting.AncMode.Value.ON)),
    )
    DeviceRulesScreen(
        state = DeviceRulesViewModel.State(
            deviceLabel = "My AirPods Pro",
            model = PodModel.AIRPODS_PRO2,
            rules = listOf(
                DeviceRuleItem(
                    id = named.id,
                    rule = named,
                    whenText = "When connected to Home",
                    whenIcon = Icons.TwoTone.Wifi,
                    thenLines = listOf(
                        DeviceRuleLine(Icons.TwoTone.Headphones, "Listening mode: Transparency"),
                        DeviceRuleLine(Icons.TwoTone.Hearing, "Conversation Awareness: On"),
                    ),
                    status = RuleStatus.Applied(Instant.now().minusSeconds(300)),
                ),
                DeviceRuleItem(
                    id = unnamed.id,
                    rule = unnamed,
                    whenText = "When disconnected from Home",
                    whenIcon = Icons.TwoTone.WifiOff,
                    thenLines = listOf(DeviceRuleLine(Icons.TwoTone.Headphones, "Listening mode: ANC")),
                    status = RuleStatus.Waiting,
                ),
            ),
            missing = emptyList(),
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
            missing = emptyList(),
            notify = false,
            hasAvailableActions = false,
            now = Instant.now(),
        ),
        onNavigateUp = {},
    )
}
