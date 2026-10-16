package eu.darken.capod.main.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EqualizerScreenHost(
    profileId: String,
    vm: EqualizerViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)

    LaunchedEffect(profileId) { vm.initialize(profileId) }

    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailedTemplate = stringResource(R.string.device_settings_send_failed, "%1\$s")
    val notConfirmedMessage = stringResource(R.string.equalizer_not_confirmed_message)

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is EqualizerViewModel.Event.SendFailed ->
                    snackbarHostState.showSnackbar(sendFailedTemplate.format(event.message ?: ""))

                EqualizerViewModel.Event.NotConfirmedByDevice ->
                    snackbarHostState.showSnackbar(notConfirmedMessage)
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    val isAapConnected = currentState.device?.isAapConnected == true
    var hasSeenAapConnected by rememberSaveable(profileId) { mutableStateOf(false) }
    var didAutoNavigate by rememberSaveable(profileId) { mutableStateOf(false) }
    LaunchedEffect(profileId, isAapConnected) {
        if (isAapConnected) {
            hasSeenAapConnected = true
        } else if (hasSeenAapConnected && !didAutoNavigate) {
            didAutoNavigate = true
            vm.navUp()
        }
    }

    EqualizerScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onEqChange = { vm.setEq(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    state: EqualizerViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onEqChange: (AapSetting.CustomEq) -> Unit = {},
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_settings_eq_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.equalizer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.isWaitingForEar) {
                SettingsInfoBox(text = stringResource(R.string.equalizer_waiting_for_ear))
            }

            val eq = state.eq ?: return@Column
            val bandsEnabled = state.isAapReady && eq.custom

            ModeSelector(
                custom = eq.custom,
                enabled = state.isAapReady,
                onSelected = { onEqChange(eq.copy(custom = it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BandFader(
                    label = stringResource(R.string.equalizer_band_low),
                    value = eq.low,
                    enabled = bandsEnabled,
                    onChange = { onEqChange(eq.copy(low = it)) },
                )
                BandFader(
                    label = stringResource(R.string.equalizer_band_mid),
                    value = eq.mid,
                    enabled = bandsEnabled,
                    onChange = { onEqChange(eq.copy(mid = it)) },
                )
                BandFader(
                    label = stringResource(R.string.equalizer_band_high),
                    value = eq.high,
                    enabled = bandsEnabled,
                    onChange = { onEqChange(eq.copy(high = it)) },
                )
            }

            val flat = AapSetting.CustomEq.FLAT
            OutlinedButton(
                onClick = { onEqChange(eq.copy(low = flat, mid = flat, high = flat)) },
                enabled = bandsEnabled && !eq.isFlat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.equalizer_reset_action))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeSelector(
    custom: Boolean,
    enabled: Boolean,
    onSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        stringResource(R.string.equalizer_mode_recommended) to false,
        stringResource(R.string.equalizer_mode_custom) to true,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = value == custom,
                onClick = { if (value != custom) onSelected(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BandFader(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    // Local while the finger is down, so the fader never waits on the device round trip.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: value.toFloat()
    val colors = SliderDefaults.colors()
    val interactionSource = remember { MutableInteractionSource() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Slider(
            value = shown,
            onValueChange = {
                dragValue = it
                // The slider reports sub-step moves too, starting with the touch itself; each one
                // reported would be another write of an unchanged curve.
                val stepped = it.roundToInt()
                if (stepped != value) onChange(stepped)
            },
            onValueChangeFinished = { dragValue = null },
            valueRange = AapSetting.CustomEq.BAND_RANGE.first.toFloat()..AapSetting.CustomEq.BAND_RANGE.last.toFloat(),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled) },
            track = { sliderState -> CentredTrack(sliderState.coercedValueAsFraction, colors, enabled) },
            modifier = Modifier
                .height(FADER_LENGTH)
                .vertical()
                .semantics { contentDescription = label },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = formatBandOffset(shown.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fills from the flat midpoint towards the value, so a boost and a cut read differently. */
@Composable
private fun CentredTrack(fraction: Float, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val marker = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        drawRect(inactive)
        val mid = size.width / 2
        val x = size.width * fraction
        drawRect(active, topLeft = Offset(min(mid, x), 0f), size = Size(abs(x - mid), size.height))
        drawCircle(marker, radius = 2.dp.toPx(), center = Offset(mid, size.height / 2))
    }
}

/**
 * Turns a horizontal slider into a bottom-to-top one. material3 1.4's VerticalSlider is internal,
 * so the stock slider is laid out with swapped constraints and rotated; touch input follows the
 * rotation.
 */
private fun Modifier.vertical(): Modifier = this
    .graphicsLayer {
        rotationZ = 270f
        transformOrigin = TransformOrigin(0f, 0f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
    }

/** The pods' 0..100 shown as a signed step from flat; the dB scale behind it is unknown. */
internal fun formatBandOffset(value: Int): String {
    val offset = value - AapSetting.CustomEq.FLAT
    return when {
        offset > 0 -> "+$offset"
        offset < 0 -> "−${-offset}"
        else -> "0"
    }
}

private val FADER_LENGTH = 200.dp

private fun previewEqualizerState(eq: AapSetting.CustomEq): EqualizerViewModel.State {
    val device = PodDevice(
        profileId = "preview",
        label = "My AirPods",
        ble = MockPodDataProvider.airPodsProWithKeys(),
        aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(AapSetting.CustomEq::class to eq),
        ),
    )
    return EqualizerViewModel.State(device = device, eq = eq, isAapReady = true)
}

@Preview2
@Composable
private fun EqualizerScreenCustomPreview() = PreviewWrapper {
    EqualizerScreen(
        state = previewEqualizerState(AapSetting.CustomEq(custom = true, low = 70, mid = 50, high = 36)),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun EqualizerScreenRecommendedPreview() = PreviewWrapper {
    EqualizerScreen(
        state = previewEqualizerState(AapSetting.CustomEq(custom = false, low = 70, mid = 50, high = 36)),
        onNavigateUp = {},
    )
}
