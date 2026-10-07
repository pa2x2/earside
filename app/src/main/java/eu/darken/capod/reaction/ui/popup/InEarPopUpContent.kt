package eu.darken.capod.reaction.ui.popup

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.theming.fillColor
import eu.darken.capod.common.theming.textColorOrNull
import eu.darken.capod.main.ui.overview.cards.components.AncModeSelector
import eu.darken.capod.main.ui.overview.cards.components.batteryTierState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryTier
import eu.darken.capod.monitor.core.battery.batteryTier
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.BATTERY_UNKNOWN
import eu.darken.capod.pods.core.apple.ble.batteryProgress
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.pods.core.apple.ble.isKnownBattery

@Composable
fun InEarPopUpContent(
    device: PodDevice,
    onAncModeChange: (AapSetting.AncMode.Value) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpen)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(device.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.label ?: device.getLabel(context),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (device.label != null) {
                        Text(
                            text = device.getLabel(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                BatteryRing(percent = device.wornBattery)
            }

            val ancMode = device.ancMode
            if (device.isAapConnected && device.hasAncControl && ancMode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AncModeSelector(
                    currentMode = ancMode.current,
                    supportedModes = device.visibleAncModes,
                    onModeSelected = onAncModeChange,
                    pendingMode = device.pendingAncMode,
                )
            }
        }
    }
}

@Composable
internal fun BatteryRing(
    percent: Float,
    size: Dp = 52.dp,
    strokeWidth: Dp = 5.dp,
    showText: Boolean = true,
) {
    val context = LocalContext.current
    val tier = batteryTier(percent)
    val progress by animateFloatAsState(
        targetValue = batteryProgress(percent),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "inEarBatteryProgress",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .batteryTierState(tier),
    ) {
        CircularProgressIndicator(
            progress = { if (tier == BatteryTier.UNKNOWN) 0f else progress },
            modifier = Modifier.size(size),
            color = tier.fillColor(),
            strokeWidth = strokeWidth,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        if (showText) {
            Text(
                text = formatBatteryPercent(context, percent),
                style = MaterialTheme.typography.labelLarge,
                color = tier.textColorOrNull() ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The battery the popup shows: the lower of the pods being worn, since that one runs out first. A
 * pod left in the case doesn't count; with no pod reported in the ear, both do.
 */
internal val PodDevice.wornBattery: Float
    get() {
        if (!hasDualPods) return batteryHeadset
        val pods = listOf(batteryLeft to isLeftInEar, batteryRight to isRightInEar)
            .filter { (percent, _) -> isKnownBattery(percent) }
        val worn = pods.filter { (_, inEar) -> inEar == true }.ifEmpty { pods }
        return worn.minOfOrNull { (percent, _) -> percent } ?: BATTERY_UNKNOWN
    }

@Preview2
@Composable
private fun InEarPopUpContentDualPodPreview() = PreviewWrapper {
    InEarPopUpContent(
        device = MockPodDataProvider.dualPodMonitoredMixed(),
        onAncModeChange = {},
        onOpen = {},
    )
}

@Preview2
@Composable
private fun InEarPopUpContentSinglePodPreview() = PreviewWrapper {
    InEarPopUpContent(
        device = MockPodDataProvider.singlePodMonitored(),
        onAncModeChange = {},
        onOpen = {},
    )
}
