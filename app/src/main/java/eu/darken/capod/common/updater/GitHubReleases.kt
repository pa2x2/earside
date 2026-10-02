package eu.darken.capod.common.updater

import dagger.Reusable
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@Reusable
class GitHubReleases @Inject constructor(
    @SerializationCapod private val json: Json,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun fetch(): List<GitHubRelease> = withContext(dispatcherProvider.IO) {
        log(TAG) { "fetch()" }
        val connection = open(RELEASES_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw UpdateException(R.string.updates_error_unreachable, cause = e)
            }
            if ((status == 403 || status == 429) && connection.getHeaderField("x-ratelimit-remaining") == "0") {
                throw UpdateException(R.string.updates_error_rate_limited, detail = "HTTP $status, rate limited")
            }
            if (status !in 200..299) {
                throw UpdateException(R.string.updates_error_server, listOf(status), detail = "HTTP $status")
            }
            val body = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw UpdateException(R.string.updates_error_unreachable, cause = e)
            }
            try {
                json.decodeFromString<List<GitHubRelease>>(body)
            } catch (e: SerializationException) {
                throw UpdateException(R.string.updates_error_invalid_response, cause = e)
            } catch (e: IllegalArgumentException) {
                throw UpdateException(R.string.updates_error_invalid_response, cause = e)
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads [apk] into [directory] and returns the file. A complete earlier download of the same
     * file is reused, any other file in [directory] is deleted first. [onProgress] gets a fraction,
     * or null while the size is unknown.
     */
    suspend fun download(
        apk: AppUpdate.Apk,
        directory: File,
        onProgress: (Float?) -> Unit,
    ): File = withContext(dispatcherProvider.IO) {
        directory.mkdirs()
        directory.listFiles()?.filter { it.name != apk.name }?.forEach { it.delete() }

        val target = File(directory, apk.name)
        if (target.length() == apk.size) {
            log(TAG) { "download(): reusing $target" }
            onProgress(1f)
            return@withContext target
        }

        log(TAG) { "download(): ${apk.url} -> $target" }
        val partial = File(directory, "${apk.name}.part")
        val connection = open(apk.url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw UpdateException(R.string.updates_error_download, detail = "HTTP $status")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: apk.size.takeIf { it > 0 }
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var written = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(total?.let { (written.toFloat() / it).coerceAtMost(1f) })
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Failed to move $partial to $target")
            target
        } catch (e: IOException) {
            log(TAG, WARN) { "download() failed: ${e.asLog()}" }
            throw UpdateException(R.string.updates_error_download, cause = e)
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "Earside/${BuildConfigWrap.VERSION_NAME}")
    }

    companion object {
        private const val REPOSITORY = "pa2x2/earside"
        private const val RELEASES_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"
        private val TAG = logTag("Updater", "GitHub")
    }
}
