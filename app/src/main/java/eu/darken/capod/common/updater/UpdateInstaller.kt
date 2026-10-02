package eu.darken.capod.common.updater

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs an update APK of this app through a [PackageInstaller] session, so the update never leaves
 * the app for a browser or file manager.
 *
 * On Android 12+ the session needs no confirmation once Earside is the installer of record, which it
 * is after its first self-update. Otherwise Android hands back a confirmation screen; one that arrives
 * while no activity is resumed waits until [onActivityResumed].
 *
 * A successful self-update kills the process, so [install] usually only returns on cancellation.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    enum class Outcome { INSTALLED, CANCELLED }

    private val statusAction = "${context.packageName}.UPDATE_INSTALLER_STATUS"
    private val packageInstaller get() = context.packageManager.packageInstaller

    private val lock = Any()

    // The install in flight, guarded by lock.
    private var sessionId: Int? = null
    private var pending: CompletableDeferred<Outcome>? = null
    private var deferredConfirmation: Intent? = null
    private var confirmationShown = false
    private var resumedActivity: WeakReference<Activity>? = null

    init {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onStatus(intent)
            },
            IntentFilter(statusAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        val activity = synchronized(lock) { resumedActivity?.get() }
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    suspend fun install(file: File, sha256: String?): Outcome {
        log(TAG) { "install($file, sha256=$sha256)" }
        withContext(dispatcherProvider.IO) { verify(file, sha256) }

        val deferred = CompletableDeferred<Outcome>()
        // One install at a time: drop the previous caller and any session an earlier attempt or an
        // earlier process left behind.
        synchronized(lock) {
            pending?.complete(Outcome.CANCELLED)
            clearPending()
        }
        packageInstaller.mySessions.forEach { runCatching { packageInstaller.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        withContext(dispatcherProvider.IO) {
            var id: Int? = null
            try {
                id = packageInstaller.createSession(params)
                packageInstaller.openSession(id).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("base.apk", 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    synchronized(lock) {
                        sessionId = id
                        pending = deferred
                    }
                    session.commit(statusReceiver(id).intentSender)
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to start the install: ${e.asLog()}" }
                id?.let { runCatching { packageInstaller.abandonSession(it) } }
                synchronized(lock) { if (pending === deferred) clearPending() }
                throw UpdateException(R.string.updates_error_install_failed, cause = e)
            }
        }
        return deferred.await()
    }

    fun onActivityResumed(activity: Activity) = synchronized(lock) {
        resumedActivity = WeakReference(activity)
        val id = sessionId ?: return@synchronized
        val confirmation = deferredConfirmation
        if (confirmation != null) {
            deferredConfirmation = null
            confirmationShown = true
            activity.startActivity(confirmation)
            return@synchronized
        }
        // Back from the confirmation screen with the session gone and no status broadcast: some
        // Android builds drop it on cancel.
        if (confirmationShown && packageInstaller.getSessionInfo(id) == null) {
            log(TAG) { "Session $id is gone after its confirmation screen, treating as cancelled" }
            settle(Outcome.CANCELLED)
        }
    }

    fun onActivityPaused(activity: Activity) = synchronized(lock) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    private fun onStatus(intent: Intent) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        log(TAG) { "onStatus(session=$id, status=$status, message=$message)" }

        synchronized(lock) {
            if (id != sessionId) return
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    if (confirmation == null) {
                        fail(R.string.updates_error_install_failed, "Confirmation requested without an intent")
                        return
                    }
                    val activity = resumedActivity?.get()
                    if (activity != null) {
                        confirmationShown = true
                        activity.startActivity(confirmation)
                    } else {
                        deferredConfirmation = confirmation
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> settle(Outcome.INSTALLED)
                PackageInstaller.STATUS_FAILURE_ABORTED -> settle(Outcome.CANCELLED)
                PackageInstaller.STATUS_FAILURE_BLOCKED -> fail(R.string.updates_error_install_blocked, message)
                PackageInstaller.STATUS_FAILURE_CONFLICT -> fail(R.string.updates_error_install_conflict, message)
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> fail(R.string.updates_error_install_incompatible, message)
                PackageInstaller.STATUS_FAILURE_STORAGE -> fail(R.string.updates_error_install_storage, message)
                else -> fail(R.string.updates_error_install_failed, message)
            }
        }
    }

    // Call with lock held.
    private fun settle(outcome: Outcome) {
        val deferred = pending
        clearPending()
        deferred?.complete(outcome)
    }

    // Call with lock held.
    private fun fail(messageRes: Int, detail: String?) {
        val deferred = pending
        clearPending()
        deferred?.completeExceptionally(UpdateException(messageRes, detail = detail))
    }

    private fun clearPending() {
        sessionId = null
        pending = null
        deferredConfirmation = null
        confirmationShown = false
    }

    private fun statusReceiver(id: Int): PendingIntent {
        val intent = Intent(statusAction).setPackage(context.packageName)
        // Mutable: PackageInstaller fills in the status extras.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, id, intent, flags)
    }

    /**
     * Checks the APK is intact and a newer build of this app signed with the same key, so a wrong file
     * fails here with a clear reason instead of after Android's confirmation screen.
     */
    private fun verify(file: File, sha256: String?) {
        if (sha256 != null) {
            val actual = sha256Of(file)
            if (!actual.equals(sha256, ignoreCase = true)) {
                file.delete()
                throw UpdateException(R.string.updates_error_checksum, detail = "SHA-256 $actual, expected $sha256")
            }
        }

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(file.path, flags)
            ?: throw UpdateException(R.string.updates_error_invalid_apk)
        if (archive.packageName != context.packageName) {
            throw UpdateException(R.string.updates_error_wrong_package, detail = archive.packageName)
        }
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveVersion = PackageInfoCompat.getLongVersionCode(archive)
        val installedVersion = PackageInfoCompat.getLongVersionCode(installed)
        if (archiveVersion <= installedVersion) {
            throw UpdateException(
                R.string.updates_error_not_newer,
                detail = "versionCode $archiveVersion, installed $installedVersion",
            )
        }
        if (!sameSigner(archive, installed)) {
            throw UpdateException(R.string.updates_error_signature)
        }
    }

    private fun sameSigner(archive: PackageInfo, installed: PackageInfo): Boolean {
        // Below Android 9 there is no signing info to compare; Android itself rejects a mismatch.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val current = installed.signingInfo?.apkContentsSigners ?: return true
        val incoming = archive.signingInfo ?: return true
        // The history covers a rotated key, whose lineage includes the old one.
        val accepted = if (incoming.hasMultipleSigners()) {
            incoming.apkContentsSigners
        } else {
            incoming.signingCertificateHistory
        }
        return current.any { signer -> accepted.orEmpty().any { it == signer } }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val TAG = logTag("Updater", "Installer")
    }
}
