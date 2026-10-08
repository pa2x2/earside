package eu.darken.capod.reaction.ui.popup

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pill the in-ear popup collapses into: a black island around the front camera that shows the
 * worn battery until the pods come out. [InEarPopUpWindow] decides when it shows; this class places
 * and draws it, and can only do so while [OverlayAccessibilityService] is connected.
 *
 * The window is exactly the pill's size, since all of it takes touches away from the status bar
 * underneath. The pill grows out of the camera hole and shrinks back into it.
 *
 * While an app hides the status bar, as fullscreen video does, the pill would cover that app instead
 * of sitting in the bar, so it shrinks away and lets touches through until the bar is back.
 */
@Singleton
class InEarPillWindow @Inject constructor(
    private val generalSettings: GeneralSettings,
    private val deviceMonitor: DeviceMonitor,
) {

    private var service: AccessibilityService? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var pillState: PillState? = null

    val isAvailable: Boolean get() = service != null

    /** Its window manager may add accessibility overlays; null while [OverlayAccessibilityService] is off. */
    val accessibilityContext: Context? get() = service

    class Actions(
        val onExpand: () -> Unit,
        val onOpen: () -> Unit,
        val onDismiss: () -> Unit,
    )

    private class PillState(
        val profileId: ProfileId,
        placement: PillPlacement,
        val actions: Actions,
        statusBarVisible: Boolean,
    ) {
        var placement by mutableStateOf(placement)
        var requested by mutableStateOf(true)
        var statusBarVisible by mutableStateOf(statusBarVisible)
        val visibility = MutableTransitionState(false)
    }

    fun onServiceConnected(service: AccessibilityService) {
        this.service = service
    }

    fun onServiceDisconnected(service: AccessibilityService) {
        if (this.service !== service) return
        teardown()
        this.service = null
    }

    // Rotation, display size and cutout changes all move the camera hole. Not every one of them is a
    // configuration change, but each changes the display.
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val state = pillState ?: return
            val service = service ?: return
            val view = composeView?.takeIf { it.parent != null } ?: return
            val placement = measurePlacement(service)
            if (placement == state.placement) return
            log(TAG) { "Display changed, moving the pill to $placement" }
            state.placement = placement
            try {
                service.windowManager.updateViewLayout(view, createLayoutParams(placement, touchable = state.statusBarVisible))
            } catch (e: Exception) {
                log(TAG, ERROR) { "Moving the pill failed: ${e.asLog()}" }
            }
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    fun show(profileId: ProfileId, actions: Actions) {
        val service = service ?: run {
            log(TAG, WARN) { "show($profileId): accessibility service not connected" }
            return
        }
        try {
            log(TAG, INFO) { "show($profileId)" }

            val existing = pillState
            if (composeView?.parent != null && existing?.profileId == profileId) {
                existing.requested = true
                return
            }

            teardown()

            val placement = measurePlacement(service)
            log(TAG) { "Placing the pill at $placement" }
            val state = PillState(profileId, placement, actions, isStatusBarVisible(service))
            pillState = state

            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val view = ComposeView(service).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    // Dark whatever the app theme: the pill is black to blend into the camera hole.
                    CapodTheme(state = generalSettings.currentThemeState.copy(mode = ThemeMode.DARK)) {
                        InEarPillOverlay(state)
                    }
                }
            }
            composeView = view

            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            service.windowManager.addView(view, createLayoutParams(placement, touchable = state.statusBarVisible))
            service.getSystemService(DisplayManager::class.java)
                ?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            log(TAG, ERROR) { "show() failed: ${e.asLog()}" }
        }
    }

    // The pill's window sits above the status bar, so Android leaves the bar out of that window's
    // insets. The window metrics still report whether it's showing.
    private fun isStatusBarVisible(context: Context): Boolean {
        if (!hasApiLevel(Build.VERSION_CODES.R)) return true
        return context.windowManager.currentWindowMetrics.windowInsets.isVisible(WindowInsets.Type.statusBars())
    }

    private fun onStatusBarVisibilityChanged(state: PillState, visible: Boolean) {
        if (pillState !== state || state.statusBarVisible == visible) return
        log(TAG) { "Status bar visible: $visible" }
        state.statusBarVisible = visible
        val view = composeView?.takeIf { it.parent != null } ?: return
        try {
            service?.windowManager?.updateViewLayout(view, createLayoutParams(state.placement, touchable = visible))
        } catch (e: Exception) {
            log(TAG, ERROR) { "Updating the pill's touchability failed: ${e.asLog()}" }
        }
    }

    fun close() {
        val state = pillState ?: return
        log(TAG, INFO) { "close()" }
        state.requested = false
    }

    private fun teardown() = try {
        service?.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        lifecycleOwner?.let { existing ->
            existing.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            existing.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            existing.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        val view = composeView
        if (view?.parent != null) service?.windowManager?.removeView(view)
        composeView = null
        lifecycleOwner = null
        pillState = null
    } catch (e: Exception) {
        log(TAG, ERROR) { "teardown() failed: ${e.asLog()}" }
    }

    private val Context.windowManager: WindowManager
        get() = getSystemService(WindowManager::class.java)

    private fun measurePlacement(context: Context): PillPlacement {
        val windowManager = context.windowManager
        val screenWidth: Int
        val statusBarHeight: Int
        if (hasApiLevel(Build.VERSION_CODES.R)) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            statusBarHeight = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        } else {
            @Suppress("DEPRECATION")
            screenWidth = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.widthPixels
            statusBarHeight = 0
        }
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        return placePill(
            screenWidth = screenWidth,
            statusBarHeight = statusBarHeight,
            cutout = display?.topCutoutBounds(),
            density = context.resources.displayMetrics.density,
        )
    }

    // Gravity.LEFT on purpose: x is a display coordinate taken from the cutout, not a start offset.
    @SuppressLint("RtlHardcoded")
    private fun createLayoutParams(placement: PillPlacement, touchable: Boolean) = WindowManager.LayoutParams(
        placement.width,
        placement.height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = placement.x
        y = placement.y
        if (hasApiLevel(Build.VERSION_CODES.R)) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (hasApiLevel(Build.VERSION_CODES.P)) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun InEarPillOverlay(state: PillState) {
        val profileId = state.profileId
        val device by remember(profileId) {
            deviceMonitor.primaryDeviceByTier.map { it?.takeIf { device -> device.profileId == profileId } }
        }.collectAsState(initial = null)

        LaunchedEffect(device?.reactions?.showInEarPill) {
            if (device?.reactions?.showInEarPill != false) return@LaunchedEffect
            log(TAG) { "Pill switched off for $profileId" }
            state.actions.onDismiss()
        }

        LaunchedEffect(state.requested, device != null, state.statusBarVisible) {
            state.visibility.targetState = state.requested && device != null && state.statusBarVisible
        }

        // Nothing calls back when another app hides or shows the status bar, so check it while the
        // pill is up.
        LaunchedEffect(state) {
            if (!hasApiLevel(Build.VERSION_CODES.R)) return@LaunchedEffect
            while (true) {
                delay(STATUS_BAR_POLL_MS)
                val service = service ?: return@LaunchedEffect
                onStatusBarVisibilityChanged(state, isStatusBarVisible(service))
            }
        }

        LaunchedEffect(state) {
            snapshotFlow {
                !state.requested && state.visibility.isIdle && !state.visibility.currentState
            }.collect { exited ->
                if (!exited) return@collect
                // Posted, since removing the view tears down the composition running this.
                composeView?.post { if (pillState === state && !state.requested) teardown() }
            }
        }

        val growth by rememberTransition(state.visibility, label = "pill").animateFloat(
            transitionSpec = { tween(GROW_MS, easing = FastOutSlowInEasing) },
            label = "growth",
        ) { visible -> if (visible) 1f else 0f }

        val placement = state.placement
        val hasHole = placement.holeWidth > 0
        val density = LocalDensity.current
        val dragThreshold = with(density) { DRAG_DP.dp.toPx() }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val current = device ?: return@Box
            // Starts as the camera hole, which hides it; without a hole it starts small and fades in.
            val startWidth = if (hasHole) placement.holeWidth else placement.height / 2
            val startHeight = if (hasHole) placement.holeHeight else placement.height / 2
            val width = lerp(startWidth.toFloat(), placement.width.toFloat(), growth)
            val height = lerp(startHeight.toFloat(), placement.height.toFloat(), growth)

            Box(
                modifier = Modifier
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .graphicsLayer { alpha = if (hasHole) 1f else growth }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black)
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = stringResource(R.string.popup_pill_expand_action),
                        onLongClickLabel = stringResource(R.string.popup_pill_open_action),
                        onLongClick = state.actions.onOpen,
                        onClick = state.actions.onExpand,
                    )
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                when {
                                    dragged > dragThreshold -> state.actions.onExpand()
                                    dragged < -dragThreshold -> state.actions.onDismiss()
                                }
                            },
                        ) { change, delta ->
                            change.consume()
                            dragged += delta
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                InEarPillContent(
                    device = current,
                    modifier = Modifier
                        .requiredSize(
                            with(density) { placement.width.toDp() },
                            with(density) { placement.height.toDp() },
                        )
                        .graphicsLayer { alpha = ((growth - 0.5f) * 2f).coerceIn(0f, 1f) },
                )
            }
        }
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp", "InEar", "Pill")
        private const val GROW_MS = 350
        private const val DRAG_DP = 12
        private const val STATUS_BAR_POLL_MS = 1000L
    }
}

@Composable
private fun InEarPillContent(device: PodDevice, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val description = "${device.label ?: device.getLabel(context)}, ${formatBatteryPercent(context, device.wornBattery)}"
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = PILL_EDGE_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(device.iconRes),
            contentDescription = null,
            modifier = Modifier.size(PILL_ITEM_DP.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        BatteryRing(
            percent = device.wornBattery,
            size = PILL_ITEM_DP.dp,
            strokeWidth = 3.dp,
            showText = false,
        )
    }
}

/**
 * The pill's window in display pixels. [holeWidth] and [holeHeight] are the camera hole it is
 * centered on, 0 when there is none to center on.
 */
internal data class PillPlacement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val holeWidth: Int,
    val holeHeight: Int,
)

/**
 * Centers the pill on a cutout at the top middle of the screen, a hole or a notch, with the device
 * image on its left and the battery on its right. Anywhere else (a hole in a corner, a cutout on the
 * side in landscape, no cutout at all) the pill sits at the top middle of the status bar instead.
 */
internal fun placePill(
    screenWidth: Int,
    statusBarHeight: Int,
    cutout: RectF?,
    density: Float,
): PillPlacement {
    val side = (PILL_EDGE_DP + PILL_ITEM_DP + PILL_GAP_DP) * density
    val minHeight = PILL_MIN_HEIGHT_DP * density
    val hole = cutout?.takeIf {
        !it.isEmpty &&
            it.width() < screenWidth / 2f &&
            abs(it.centerX() - screenWidth / 2f) < screenWidth * CENTER_TOLERANCE
    }

    val width: Float
    val height: Float
    val centerX: Float
    val centerY: Float
    if (hole != null) {
        width = hole.width() + 2 * side
        height = max(hole.height() + 2 * PILL_HOLE_MARGIN_DP * density, minHeight)
        centerX = hole.centerX()
        // A notch starts at the screen edge, so its pill does too and runs off the top.
        centerY = hole.centerY()
    } else {
        width = 2 * side
        height = minHeight
        centerX = screenWidth / 2f
        centerY = max(statusBarHeight / 2f, height / 2f)
    }
    return PillPlacement(
        x = (centerX - width / 2).roundToInt(),
        y = (centerY - height / 2).roundToInt(),
        width = width.roundToInt(),
        height = height.roundToInt(),
        holeWidth = hole?.width()?.roundToInt() ?: 0,
        holeHeight = hole?.height()?.roundToInt() ?: 0,
    )
}

/**
 * The cutout at the top of the screen in the current rotation. Its bounding rect is padded out to the
 * status bar's height, so on Android 12+ the exact shape is used: the path holds every cutout on the
 * display, and only its part inside the top rect counts.
 */
private fun Display.topCutoutBounds(): RectF? {
    if (!hasApiLevel(Build.VERSION_CODES.Q)) return null
    val cutout = cutout ?: return null
    val top = RectF(cutout.boundingRectTop.takeUnless { it.isEmpty } ?: return null)
    if (hasApiLevel(Build.VERSION_CODES.S)) {
        val path = cutout.cutoutPath
        val topPart = Path().apply { addRect(top, Path.Direction.CW) }
        if (path != null && topPart.op(path, Path.Op.INTERSECT)) {
            val exact = RectF().also { topPart.computeBounds(it, true) }
            if (!exact.isEmpty) return exact
        }
    }
    return top
}

private const val PILL_EDGE_DP = 10
private const val PILL_ITEM_DP = 22
private const val PILL_GAP_DP = 10
private const val PILL_HOLE_MARGIN_DP = 6
private const val PILL_MIN_HEIGHT_DP = 34

/** How far, as a share of the screen width, a cutout's center may sit from the middle to get the pill. */
private const val CENTER_TOLERANCE = 0.1f
