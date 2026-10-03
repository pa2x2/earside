package eu.darken.capod.rules.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.darken.capod.R
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.rules.core.trigger.RuleRequirement

/**
 * The next thing the phone still has to allow, one step at a time with one button. Results come
 * back through [onReturned] and on resume, where the caller rechecks.
 */
@Composable
fun RuleRequirementSteps(
    missing: List<RuleRequirement>,
    onReturned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Once Android stops showing its dialog, only the app's settings page can grant access.
    var preciseBlocked by rememberSaveable { mutableStateOf(false) }
    var backgroundBlocked by rememberSaveable { mutableStateOf(false) }

    fun blocked(permission: String) =
        activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    var preciseDenials by rememberSaveable { mutableIntStateOf(0) }
    val preciseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            preciseDenials++
            // With approximate granted the rationale flag reads false, yet Android still offers the
            // upgrade to precise, so only give up on the dialog once that was turned down as well.
            val approximateOnly = it[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            preciseBlocked = blocked(Manifest.permission.ACCESS_FINE_LOCATION) && (!approximateOnly || preciseDenials >= 2)
        }
        onReturned()
    }
    // From Android 11 this request opens the app's location settings page instead of a dialog.
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) backgroundBlocked = blocked(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        onReturned()
    }

    fun openAppSettings() = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    val next = missing.firstOrNull() ?: return

    Column(modifier = modifier) {
        if (next != RuleRequirement.LOCATION_SERVICES) {
            Text(
                text = stringResource(R.string.rules_requirement_location_explanation),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val (title, text, button) = when (next) {
            RuleRequirement.PRECISE_LOCATION -> Triple(
                R.string.rules_requirement_precise_title,
                R.string.rules_requirement_precise_text,
                if (preciseBlocked) R.string.rules_requirement_open_settings else R.string.rules_requirement_allow,
            )

            RuleRequirement.BACKGROUND_LOCATION -> Triple(
                R.string.rules_requirement_background_title,
                R.string.rules_requirement_background_text,
                if (backgroundBlocked) R.string.rules_requirement_open_settings else R.string.rules_requirement_allow,
            )

            RuleRequirement.LOCATION_SERVICES -> Triple(
                R.string.rules_requirement_location_off_title,
                R.string.rules_requirement_location_off_text,
                R.string.rules_requirement_turn_on,
            )
        }
        SettingsInfoBox(
            title = stringResource(title),
            text = stringResource(text),
            type = InfoBoxType.WARNING,
            action = {
                TextButton(
                    onClick = {
                        when (next) {
                            RuleRequirement.PRECISE_LOCATION -> if (preciseBlocked) {
                                openAppSettings()
                            } else {
                                preciseLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }

                            RuleRequirement.BACKGROUND_LOCATION -> if (backgroundBlocked) {
                                openAppSettings()
                            } else {
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }

                            RuleRequirement.LOCATION_SERVICES -> context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        }
                    },
                ) { Text(stringResource(button)) }
            },
        )
    }
}
