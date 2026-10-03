package eu.darken.capod.rules.core.trigger.phone

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.rules.core.trigger.TriggerCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Whether notifications are silenced. Android folds Do Not Disturb, its schedules and, from
 * Android 15, every active mode into one interruption filter. A mode set to allow all notifications
 * (one that only dims or greys the screen, say) leaves the filter at "all" and doesn't count.
 */
@Singleton
class DoNotDisturbSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope appScope: CoroutineScope,
) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    val condition: Flow<TriggerCondition> = callbackFlow {
        fun publish() {
            trySend(silencing(notificationManager.currentInterruptionFilter))
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = publish()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        publish()
        awaitClose { context.unregisterReceiver(receiver) }
    }
        .distinctUntilChanged()
        .onEach { log(TAG) { "Do Not Disturb: $it" } }
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    companion object {
        private val TAG = logTag("Rules", "DoNotDisturb")
    }
}

private fun silencing(filter: Int): TriggerCondition = when (filter) {
    NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> TriggerCondition.Unknown
    NotificationManager.INTERRUPTION_FILTER_ALL -> TriggerCondition.Known(holds = false)
    else -> TriggerCondition.Known(holds = true)
}

/**
 * Whether a call is established, from the audio mode: it needs no permission and also covers app
 * calls, which use [AudioManager.MODE_IN_COMMUNICATION].
 */
@Singleton
class CallSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    val inCall: Flow<Boolean> = (if (hasApiLevel(31)) modeChanges() else legacyChanges())
        .distinctUntilChanged()
        .onEach { log(TAG) { "In call: $it" } }
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @RequiresApi(31)
    private fun modeChanges(): Flow<Boolean> = callbackFlow {
        // Reads the mode anew instead of taking the one passed in: a callback queued before the
        // first read below could otherwise land after it with an older mode.
        val listener = AudioManager.OnModeChangedListener { trySend(isCallMode(audioManager.mode)) }
        audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener)
        trySend(isCallMode(audioManager.mode))
        awaitClose { audioManager.removeOnModeChangedListener(listener) }
    }

    // Below Android 12 nothing announces audio mode changes. The call state announces phone calls
    // without a permission there (from 12 on it needs READ_PHONE_STATE), and app calls are caught
    // by checking the mode every few seconds. The listener must be created on a thread with a looper.
    @Suppress("DEPRECATION")
    private fun legacyChanges(): Flow<Boolean> = callbackFlow {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        var callState = TelephonyManager.CALL_STATE_IDLE
        fun publish() {
            // Off the hook can come a moment before the mode switches to the call.
            trySend(callState == TelephonyManager.CALL_STATE_OFFHOOK || isCallMode(audioManager.mode))
        }

        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                callState = state
                publish()
            }
        }
        telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        publish()
        launch {
            while (true) {
                delay(5.seconds)
                publish()
            }
        }
        awaitClose { telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }.flowOn(dispatchers.Main)

    companion object {
        private val TAG = logTag("Rules", "Call")
    }
}

// Ringing doesn't count, so a declined or missed call changes nothing. Call screening doesn't
// either (the assistant is on the line, not you), nor a call whose audio is redirected to another
// device, since it doesn't reach the AirPods.
private fun isCallMode(mode: Int): Boolean =
    mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
