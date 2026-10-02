package eu.darken.capod.common.updater.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.updater.UpdateManager
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val updateManager: UpdateManager,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    val state = updateManager.state

    fun update() = updateManager.startUpdate()

    fun skip() = updateManager.skipVersion()

    fun close() = updateManager.closePrompt()

    fun cancelDownload() = updateManager.cancelDownload()

    fun allowInstalls() = updateManager.openInstallSettings()

    fun openReleasePage(url: String) {
        webpageTool.open(url)
    }
}
