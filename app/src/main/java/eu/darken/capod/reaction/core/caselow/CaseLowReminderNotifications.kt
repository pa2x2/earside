package eu.darken.capod.reaction.core.caselow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseLowReminderNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reaction_caselow_channel_label),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun show(profileId: String, deviceLabel: String, casePercent: Int) {
        if (!notificationManager.areNotificationsEnabled()) {
            log(TAG, WARN) { "Notifications disabled — case low reminder suppressed" }
            return
        }
        // PendingIntent identity ignores extras, so each device needs its own request code. It is keyed
        // on the tag because the rules notification already uses the bare profile ID's hash.
        val openPi = PendingIntent.getActivity(
            context,
            profileId.toTag().hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DEVICE_SETTINGS_PROFILE_ID, profileId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
        )
        val text = context.getString(R.string.reaction_caselow_notification_text, deviceLabel, casePercent)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.device_earbuds_generic_both)
            .setContentTitle(context.getString(R.string.reaction_caselow_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(profileId.toTag(), NOTIFICATION_ID, notification)
    }

    fun cancel(profileId: String) {
        notificationManager.cancel(profileId.toTag(), NOTIFICATION_ID)
    }

    fun cancelAllExcept(profileIds: Set<String>) {
        notificationManager.activeNotifications
            .filter { it.id == NOTIFICATION_ID }
            .mapNotNull { it.tag?.takeIf { tag -> tag.startsWith(TAG_PREFIX) } }
            .filter { it.removePrefix(TAG_PREFIX) !in profileIds }
            .forEach { notificationManager.cancel(it, NOTIFICATION_ID) }
    }

    private fun String.toTag() = "$TAG_PREFIX$this"

    companion object {
        private val TAG = logTag("Reaction", "CaseLow", "Notifications")
        internal val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.reaction.caselow"
        private const val NOTIFICATION_ID = 5
        private const val TAG_PREFIX = "caselow:"
    }
}
