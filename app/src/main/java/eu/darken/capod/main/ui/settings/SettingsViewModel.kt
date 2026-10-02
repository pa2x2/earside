package eu.darken.capod.main.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag

import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.updater.UpdateManager
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val webpageTool: WebpageTool,
    private val updateManager: UpdateManager,
) : ViewModel4(dispatcherProvider) {

    data class State(
        /** Null in builds that don't update themselves, which hides the row. */
        val updateCheck: UpdateManager.Check?,
    )

    val state = updateManager.state
        .map { State(updateCheck = if (updateManager.isSupported) it.check else null) }
        .asLiveState()

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun checkForUpdates() {
        if (updateManager.state.value.check is UpdateManager.Check.Available) {
            updateManager.openPrompt()
        } else {
            updateManager.check(manual = true)
        }
    }

    companion object {
        private val TAG = logTag("Settings", "VM")
    }
}
