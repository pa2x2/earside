package eu.darken.capod.main.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader

@Composable
fun SettingsScreenHost(vm: SettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    SettingsScreen(
        onNavigateUp = { vm.navUp() },
        onGeneralSettings = { vm.navTo(Nav.Settings.General) },
        onDeviceManager = { vm.navTo(Nav.Main.DeviceManager) },
        onSupport = { vm.navTo(Nav.Settings.Support) },
        onChangelog = { vm.openUrl("https://github.com/pa2x2/earside/releases") },
        onAcknowledgements = { vm.navTo(Nav.Settings.Acknowledgements) },
    )
}

@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onGeneralSettings: () -> Unit,
    onDeviceManager: () -> Unit,
    onSupport: () -> Unit,
    onChangelog: () -> Unit,
    onAcknowledgements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_label))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_general_label),
                    subtitle = stringResource(R.string.settings_general_description),
                    icon = Icons.TwoTone.Settings,
                    onClick = onGeneralSettings,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_devices_label),
                    subtitle = stringResource(R.string.settings_devices_description),
                    icon = Icons.TwoTone.DevicesOther,
                    onClick = onDeviceManager,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_support_label),
                    subtitle = stringResource(R.string.settings_support_description),
                    icon = Icons.TwoTone.SupportAgent,
                    onClick = onSupport,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.changelog_label),
                    subtitle = BuildConfigWrap.VERSION_DESCRIPTION,
                    iconPainter = painterResource(R.drawable.ic_changelog_onsurface),
                    onClick = onChangelog,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_acknowledgements_label),
                    subtitle = stringResource(R.string.general_thank_you_label),
                    icon = Icons.TwoTone.Favorite,
                    onClick = onAcknowledgements,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsScreenPreview() = PreviewWrapper {
    SettingsScreen(
        onNavigateUp = {},
        onGeneralSettings = {},
        onDeviceManager = {},
        onSupport = {},
        onChangelog = {},
        onAcknowledgements = {},
    )
}
