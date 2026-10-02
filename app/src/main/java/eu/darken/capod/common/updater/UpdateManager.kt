package eu.darken.capod.common.updater

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the update flow: the check against GitHub, whether the prompt is open, and the download and
 * install of the offered APK. Lives for the process, so a download keeps going when the prompt closes.
 *
 * Debug builds never update: they install as a separate `.dev` app that a release APK can't replace.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val settings: UpdateSettings,
    private val releases: GitHubReleases,
    private val installer: UpdateInstaller,
) {

    sealed interface Check {
        data object Idle : Check
        data object Checking : Check
        data object UpToDate : Check
        data class Available(val update: AppUpdate) : Check
        data class Failed(val error: UpdateException) : Check
    }

    sealed interface Install {
        data object Idle : Install

        /** [progress] is a fraction, null while the size is unknown. */
        data class Downloading(val progress: Float?) : Install
        data object NeedsPermission : Install
        data object Installing : Install
        data class Failed(val error: UpdateException) : Install
    }

    data class State(
        val check: Check = Check.Idle,
        val install: Install = Install.Idle,
        val isPromptOpen: Boolean = false,
    )

    val isSupported: Boolean = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.DEV

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val downloads = File(context.cacheDir, "updates")
    private var checkJob: Job? = null
    private var updateJob: Job? = null

    fun checkOnLaunch() {
        if (!isSupported) return
        appScope.launch {
            if (settings.checkOnLaunch.value()) check(manual = false)
        }
    }

    /**
     * A manual check opens the prompt for any update it finds. The launch check stays quiet for a
     * skipped version and failures, which then only show in Settings.
     */
    fun check(manual: Boolean) {
        if (!isSupported) return
        if (checkJob?.isActive == true || updateJob?.isActive == true) return
        log(TAG, INFO) { "check(manual=$manual)" }
        _state.update { it.copy(check = Check.Checking) }
        checkJob = appScope.launch {
            val check = try {
                val installed = AppVersion.parse(BuildConfigWrap.VERSION_NAME)
                    ?: throw IllegalStateException("Unparsable version ${BuildConfigWrap.VERSION_NAME}")
                val update = selectUpdate(releases.fetch(), settings.channel.value(), installed)
                if (update == null) Check.UpToDate else Check.Available(update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Update check failed: ${e.asLog()}" }
                Check.Failed(e.asUpdateException())
            }
            log(TAG) { "check(manual=$manual): $check" }

            if (check is Check.UpToDate) downloads.deleteRecursively()
            val prompt = check is Check.Available && (manual || !isSkipped(check.update.version))
            _state.update {
                it.copy(
                    check = check,
                    install = Install.Idle,
                    isPromptOpen = it.isPromptOpen || prompt,
                )
            }
        }
    }

    fun openPrompt() {
        if (_state.value.check is Check.Available) _state.update { it.copy(isPromptOpen = true) }
    }

    fun closePrompt() {
        _state.update { it.copy(isPromptOpen = false) }
    }

    fun skipVersion() {
        val update = (_state.value.check as? Check.Available)?.update ?: return
        log(TAG, INFO) { "skipVersion(${update.version})" }
        closePrompt()
        appScope.launch { settings.skippedVersion.value(update.version.toString()) }
    }

    fun startUpdate() {
        val update = (_state.value.check as? Check.Available)?.update ?: return
        if (updateJob?.isActive == true) return
        log(TAG, INFO) { "startUpdate(${update.version})" }
        updateJob = appScope.launch {
            try {
                if (!installer.canRequestInstalls()) {
                    _state.update { it.copy(install = Install.NeedsPermission) }
                    return@launch
                }
                _state.update { it.copy(install = Install.Downloading(null)) }
                val apk = releases.download(update.apk, downloads) { progress ->
                    _state.update { it.copy(install = Install.Downloading(progress)) }
                }
                _state.update { it.copy(install = Install.Installing) }
                val outcome = installer.install(apk, update.apk.sha256)
                log(TAG) { "Install outcome: $outcome" }
                _state.update { it.copy(install = Install.Idle) }
            } catch (e: CancellationException) {
                _state.update { it.copy(install = Install.Idle) }
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Update failed: ${e.asLog()}" }
                _state.update { it.copy(install = Install.Failed(e.asUpdateException())) }
            }
        }
    }

    fun cancelDownload() {
        if (_state.value.install !is Install.Downloading) return
        log(TAG, INFO) { "cancelDownload()" }
        updateJob?.cancel()
    }

    fun openInstallSettings() {
        installer.openInstallSettings()
    }

    /** Continues an install that waited for the "install unknown apps" permission. */
    fun onActivityResumed(activity: Activity) {
        installer.onActivityResumed(activity)
        if (_state.value.install == Install.NeedsPermission && installer.canRequestInstalls()) {
            log(TAG) { "Install permission granted, continuing" }
            startUpdate()
        }
    }

    fun onActivityPaused(activity: Activity) {
        installer.onActivityPaused(activity)
    }

    private suspend fun isSkipped(version: AppVersion): Boolean {
        val skipped = settings.skippedVersion.value()?.let { AppVersion.parse(it) } ?: return false
        return version <= skipped
    }

    private fun Exception.asUpdateException(): UpdateException =
        this as? UpdateException ?: UpdateException(R.string.updates_error_unexpected, cause = this)

    companion object {
        private val TAG = logTag("Updater", "Manager")
    }
}
