package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.controls.DeviceControls
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Overlay banner at the top of the screen, shown by [eu.darken.capod.reaction.core.popup.InEarPopUpReaction].
 * It follows the device live while visible and collapses [AUTO_HIDE_MS] after the last touch, into
 * the [InEarPillWindow] for a device that keeps one, which then stays until [close]. Closing only
 * starts the exit animation; the window is removed once that finishes.
 */
@Singleton
class InEarPopUpWindow @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val generalSettings: GeneralSettings,
    private val deviceMonitor: DeviceMonitor,
    private val deviceControls: DeviceControls,
    private val pillWindow: InEarPillWindow,
) {

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var overlayState: OverlayState? = null

    /** The worn device whose pill stands in for the banner until [close]; null when it keeps none. */
    private var pillProfileId: ProfileId? = null

    var isMainActivityVisible: Boolean = false
        set(value) {
            field = value
            if (value) {
                overlayState?.requested = false
                pillWindow.close()
            } else {
                showPill()
            }
        }

    private class OverlayState(profileId: ProfileId) {
        var profileId by mutableStateOf(profileId)
        var requested by mutableStateOf(true)

        /** Bumped by every [show], so a repeated show restarts the auto-hide countdown. */
        var generation by mutableIntStateOf(0)
        val visibility = MutableTransitionState(false)
    }

    fun show(profileId: ProfileId, keepPill: Boolean) {
        pillProfileId = profileId.takeIf { keepPill }
        pillWindow.close()
        if (isMainActivityVisible) {
            log(TAG) { "Suppressing in-ear popup, MainActivity is visible" }
            return
        }
        try {
            log(TAG, INFO) { "show($profileId)" }

            val existing = overlayState
            if (composeView?.parent != null && existing != null) {
                existing.profileId = profileId
                existing.requested = true
                existing.generation++
                return
            }

            teardown()

            val state = OverlayState(profileId)
            overlayState = state

            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val view = ComposeView(appContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    CapodTheme(state = generalSettings.currentThemeState) {
                        InEarPopUpOverlay(state)
                    }
                }
            }
            composeView = view

            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            // With the accessibility service on, the banner is an accessibility overlay like the pill,
            // so it also shows where application overlays don't: on the lock screen and over Settings.
            val accessibilityContext = pillWindow.accessibilityContext
            val manager = (accessibilityContext ?: appContext).getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager = manager
            manager.addView(view, createLayoutParams(accessibility = accessibilityContext != null))
        } catch (e: Exception) {
            log(TAG, ERROR) { "show() failed: ${e.asLog()}" }
        }
    }

    /** The pods came out: the banner and the pill both go. */
    fun close() {
        log(TAG, INFO) { "close()" }
        pillProfileId = null
        overlayState?.requested = false
        pillWindow.close()
    }

    private fun collapse(state: OverlayState) {
        state.requested = false
        showPill()
    }

    private fun showPill() {
        val profileId = pillProfileId ?: return
        if (isMainActivityVisible) return
        if (!pillWindow.isAvailable) {
            log(TAG) { "No pill for $profileId, the accessibility service is off" }
            return
        }
        pillWindow.show(
            profileId = profileId,
            actions = InEarPillWindow.Actions(
                onExpand = { show(profileId, keepPill = true) },
                onOpen = {
                    pillWindow.close()
                    openDeviceSettings(profileId)
                },
                onDismiss = {
                    pillProfileId = null
                    pillWindow.close()
                },
            ),
        )
    }

    private fun teardown() = try {
        lifecycleOwner?.let { existing ->
            existing.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            existing.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            existing.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        if (composeView?.parent != null) {
            windowManager?.removeView(composeView)
        }
        composeView = null
        windowManager = null
        lifecycleOwner = null
        overlayState = null
    } catch (e: Exception) {
        log(TAG, ERROR) { "teardown() failed: ${e.asLog()}" }
    }

    private fun createLayoutParams(accessibility: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (accessibility) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Laid out below the status bar, as both window types are by default. That matters for an
        // application overlay: the status bar sits above it and would take the touches.
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    private fun openDeviceSettings(profileId: ProfileId) {
        log(TAG, INFO) { "openDeviceSettings($profileId)" }
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_DEVICE_SETTINGS_PROFILE_ID, profileId)
        }
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            log(TAG, ERROR) { "openDeviceSettings() failed: ${e.asLog()}" }
        }
    }

    @Composable
    private fun InEarPopUpOverlay(state: OverlayState) {
        val profileId = state.profileId
        val device by remember(profileId) {
            deviceMonitor.primaryDeviceByTier.map { it?.takeIf { device -> device.profileId == profileId } }
        }.collectAsState(initial = null)

        LaunchedEffect(state.requested, device != null) {
            state.visibility.targetState = state.requested && device != null
        }

        var touches by remember { mutableIntStateOf(0) }
        LaunchedEffect(state.generation, touches) {
            delay(AUTO_HIDE_MS)
            if (!state.requested) return@LaunchedEffect
            log(TAG) { "Auto-hiding after ${AUTO_HIDE_MS}ms without a touch" }
            collapse(state)
        }

        LaunchedEffect(state) {
            snapshotFlow {
                !state.requested && state.visibility.isIdle && !state.visibility.currentState
            }.collect { exited ->
                if (!exited) return@collect
                // Posted, since removing the view tears down the composition running this. A show()
                // landing before the post runs keeps the window.
                composeView?.post { if (overlayState === state && !state.requested) teardown() }
            }
        }

        val scope = rememberCoroutineScope()
        val dragOffset = remember { Animatable(0f) }
        LaunchedEffect(state.generation) { dragOffset.snapTo(0f) }
        val dismissDistance = with(LocalDensity.current) { DISMISS_DRAG_DP.dp.toPx() }

        AnimatedVisibility(
            visibleState = state.visibility,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val current = device ?: return@AnimatedVisibility
            InEarPopUpContent(
                device = current,
                onAncModeChange = { mode ->
                    val address = current.address ?: return@InEarPopUpContent
                    deviceControls.setAncMode(address, mode)
                },
                onOpen = {
                    state.requested = false
                    openDeviceSettings(profileId)
                },
                modifier = Modifier
                    .widthIn(max = MAX_WIDTH_DP.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                touches++
                            }
                        }
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtMost(0f)) }
                        },
                        onDragStopped = { velocity ->
                            if (dragOffset.value < -dismissDistance || velocity < -DISMISS_FLING_VELOCITY) {
                                collapse(state)
                            } else {
                                dragOffset.animateTo(0f)
                            }
                        },
                    ),
            )
        }
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp", "InEar", "Window")
        private const val AUTO_HIDE_MS = 4_500L
        private const val MAX_WIDTH_DP = 440
        private const val DISMISS_DRAG_DP = 24
        private const val DISMISS_FLING_VELOCITY = 1_000f
    }
}
