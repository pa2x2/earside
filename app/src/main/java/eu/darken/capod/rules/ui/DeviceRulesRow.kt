package eu.darken.capod.rules.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.AutoMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.settings.SettingsBaseItem

/** The Device settings entry to a device's rules; the subtitle sums them up. */
@Composable
fun DeviceRulesRow(
    rules: List<DeviceRuleItem>,
    onClick: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val subtitle = if (rules.isEmpty()) {
        stringResource(R.string.rules_entry_description)
    } else {
        buildList {
            add(resources.getQuantityString(R.plurals.rules_entry_count, rules.size, rules.size))
            val on = rules.count { it.rule?.enabled == true }
            when {
                on == 0 -> add(resources.getQuantityString(R.plurals.rules_entry_all_off, rules.size))
                on < rules.size -> add(resources.getString(R.string.rules_entry_on, on))
            }
            val waiting = rules.count { it.status == RuleStatus.Waiting }
            if (waiting > 0) add(resources.getQuantityString(R.plurals.rules_entry_waiting, waiting, waiting))
            val attention = rules.count { it.status.needsAttention }
            if (attention > 0) add(resources.getQuantityString(R.plurals.rules_entry_attention, attention, attention))
        }.joinToString(" · ")
    }
    SettingsBaseItem(
        title = stringResource(R.string.rules_title),
        subtitle = subtitle,
        icon = Icons.TwoTone.AutoMode,
        onClick = onClick,
        trailingContent = { Icon(Icons.AutoMirrored.TwoTone.KeyboardArrowRight, contentDescription = null) },
    )
}
