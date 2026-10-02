package eu.darken.capod.common.updater.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.updater.AppUpdate
import eu.darken.capod.common.updater.AppVersion
import eu.darken.capod.common.updater.UpdateManager.Check
import eu.darken.capod.common.updater.UpdateManager.Install

/** The update prompt. Hosted by MainActivity above the navigation, so it shows over any screen. */
@Composable
fun UpdateSheetHost(vm: UpdateViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()
    val update = (state.check as? Check.Available)?.update
    if (state.isPromptOpen && update != null) {
        UpdateSheet(
            update = update,
            install = state.install,
            onUpdate = { vm.update() },
            onSkip = { vm.skip() },
            onClose = { vm.close() },
            onCancelDownload = { vm.cancelDownload() },
            onAllowInstalls = { vm.allowInstalls() },
            onOpenReleasePage = { vm.openReleasePage(update.pageUrl) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    update: AppUpdate,
    install: Install,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onCancelDownload: () -> Unit,
    onAllowInstalls: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        UpdateSheetContent(
            update = update,
            install = install,
            onUpdate = onUpdate,
            onSkip = onSkip,
            onClose = onClose,
            onCancelDownload = onCancelDownload,
            onAllowInstalls = onAllowInstalls,
            onOpenReleasePage = onOpenReleasePage,
        )
    }
}

@Composable
private fun UpdateSheetContent(
    update: AppUpdate,
    install: Install,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onCancelDownload: () -> Unit,
    onAllowInstalls: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.updates_available_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = listOfNotNull(
                stringResource(R.string.updates_version_label, update.version.toString()),
                Formatter.formatShortFileSize(context, update.apk.size),
                if (update.isPrerelease) stringResource(R.string.settings_updates_channel_prerelease) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (update.notes.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .weight(1f, fill = false),
            ) {
                Markdown(
                    content = update.notes,
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.titleLarge,
                        h2 = MaterialTheme.typography.titleLarge,
                        h3 = MaterialTheme.typography.titleMedium,
                        h4 = MaterialTheme.typography.titleSmall,
                        h5 = MaterialTheme.typography.titleSmall,
                        h6 = MaterialTheme.typography.titleSmall,
                        text = MaterialTheme.typography.bodyMedium,
                        paragraph = MaterialTheme.typography.bodyMedium,
                        bullet = MaterialTheme.typography.bodyMedium,
                        list = MaterialTheme.typography.bodyMedium,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (install) {
                Install.Idle -> {
                    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_update_action))
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_skip_action))
                    }
                    NotNowButton(onClose)
                }

                is Install.Downloading -> {
                    if (install.progress != null) {
                        LinearProgressIndicator(progress = { install.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = install.progress
                            ?.let { stringResource(R.string.updates_downloading_percent_label, (it * 100).toInt()) }
                            ?: stringResource(R.string.updates_downloading_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FilledTonalButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_cancel_download_action))
                    }
                }

                Install.NeedsPermission -> {
                    Note(stringResource(R.string.updates_permission_description))
                    Button(onClick = onAllowInstalls, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_allow_installs_action))
                    }
                    NotNowButton(onClose)
                }

                Install.Installing -> {
                    Note(stringResource(R.string.updates_installing_description))
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.updates_installing_label))
                        }
                    }
                }

                is Install.Failed -> {
                    Note(install.error.describe(context), isError = true)
                    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.general_retry_action))
                    }
                    TextButton(onClick = onOpenReleasePage, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_open_release_action))
                    }
                    NotNowButton(onClose)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun NotNowButton(onClose: () -> Unit) {
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.updates_not_now_action))
    }
}

private val previewUpdate = AppUpdate(
    version = AppVersion(1, 2, 0),
    isPrerelease = false,
    notes = """
        ## 1.2.0

        ### ✨ Added

        - Earside checks GitHub for new versions and installs them from inside the app.

        ## 1.1.1

        ### 🐛 Fixed

        - ANC buttons in the notification work again after a reconnect.
    """.trimIndent(),
    pageUrl = "https://github.com/pa2x2/earside/releases/tag/v1.2.0",
    apk = AppUpdate.Apk(name = "earside-v1.2.0.apk", url = "", size = 9_800_000, sha256 = null),
)

@Preview2
@Composable
private fun UpdateSheetContentPreview() = PreviewWrapper {
    UpdateSheetContent(
        update = previewUpdate,
        install = Install.Idle,
        onUpdate = {},
        onSkip = {},
        onClose = {},
        onCancelDownload = {},
        onAllowInstalls = {},
        onOpenReleasePage = {},
    )
}

@Preview2
@Composable
private fun UpdateSheetContentDownloadingPreview() = PreviewWrapper {
    UpdateSheetContent(
        update = previewUpdate,
        install = Install.Downloading(0.42f),
        onUpdate = {},
        onSkip = {},
        onClose = {},
        onCancelDownload = {},
        onAllowInstalls = {},
        onOpenReleasePage = {},
    )
}
