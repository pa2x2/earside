package eu.darken.capod.rules.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DoNotDisturbOn
import androidx.compose.material.icons.twotone.PhoneInTalk
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.rules.core.RuleTrigger
import javax.inject.Inject

class DoNotDisturbOnEditor @Inject constructor() : RuleTriggerEditor<RuleTrigger.DoNotDisturbOn> {

    override val type = RuleTrigger.DoNotDisturbOn::class
    override val icon = Icons.TwoTone.DoNotDisturbOn
    override val label = R.string.rules_trigger_dnd_label

    @Composable
    override fun Settings(
        current: RuleTrigger.DoNotDisturbOn?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.DoNotDisturbOn?) -> Unit,
    ) {
        FixedTrigger(RuleTrigger.DoNotDisturbOn, R.string.rules_trigger_dnd_explanation, onChange)
    }
}

class InCallEditor @Inject constructor() : RuleTriggerEditor<RuleTrigger.InCall> {

    override val type = RuleTrigger.InCall::class
    override val icon = Icons.TwoTone.PhoneInTalk
    override val label = R.string.rules_trigger_call_label

    @Composable
    override fun Settings(
        current: RuleTrigger.InCall?,
        model: PodModel,
        device: PodDevice?,
        onChange: (RuleTrigger.InCall?) -> Unit,
    ) {
        FixedTrigger(RuleTrigger.InCall, R.string.rules_trigger_call_explanation, onChange)
    }
}

/** A trigger without settings is complete as soon as its type is picked. */
@Composable
private fun <T : RuleTrigger> FixedTrigger(trigger: T, @StringRes explanation: Int, onChange: (T?) -> Unit) {
    LaunchedEffect(trigger) { onChange(trigger) }
    Text(
        text = stringResource(explanation),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}
