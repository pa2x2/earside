package eu.darken.capod.rules.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.profiles.core.ProfileId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRulesNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.rules_notification_channel_label),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    fun showApplied(
        profileId: ProfileId,
        deviceLabel: String,
        ruleName: String?,
        actionSummary: String,
        triggerSummary: String,
    ) {
        if (!notificationManager.areNotificationsEnabled()) {
            log(TAG, WARN) { "Notifications disabled, rule-applied notification suppressed" }
            return
        }
        // PendingIntent identity ignores extras, so each device gets its own request code; a shared
        // one would send every rule notification to the device that applied a rule last.
        val openPi = PendingIntent.getActivity(
            context,
            profileId.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DEVICE_RULES_PROFILE_ID, profileId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
        )
        val title = if (ruleName != null) {
            context.getString(R.string.rules_notification_title_named, deviceLabel, ruleName)
        } else {
            context.getString(R.string.rules_notification_title, deviceLabel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.device_earbuds_generic_both)
            .setContentTitle(title)
            .setContentText(actionSummary)
            .setSubText(triggerSummary)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // One per device: a newer rule replaces the older notice instead of stacking.
        notificationManager.notify("$TAG_PREFIX$profileId", NOTIFICATION_ID, notification)
    }

    companion object {
        private val TAG = logTag("Rules", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.rules"
        private const val NOTIFICATION_ID = 5
        private const val TAG_PREFIX = "rules:"
    }
}
