package eu.darken.capod.reaction.ui.popup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject

/**
 * Exists only so [InEarPillWindow] can sit over the status bar: an accessibility overlay is the one
 * window type an app may draw there and take touches in. The service subscribes to no events and
 * reads no window content.
 */
@AndroidEntryPoint
class OverlayAccessibilityService : AccessibilityService() {

    @Inject lateinit var pillWindow: InEarPillWindow

    override fun onServiceConnected() {
        super.onServiceConnected()
        log(TAG, INFO) { "onServiceConnected()" }
        pillWindow.onServiceConnected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        log(TAG, INFO) { "onUnbind()" }
        pillWindow.onServiceDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private val TAG = logTag("Reaction", "PopUp", "InEar", "AccessibilityService")

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == context.packageName && info.name == OverlayAccessibilityService::class.java.name
            }
        }
    }
}
