package eu.darken.capod.common.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The slice of GitHub's release JSON the updater reads. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("draft") val isDraft: Boolean = false,
    @SerialName("prerelease") val isPrerelease: Boolean = false,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val pageUrl: String,
    @SerialName("assets") val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        @SerialName("name") val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        @SerialName("size") val size: Long,
        /** `sha256:<hex>` on assets uploaded since GitHub started computing digests, null before. */
        @SerialName("digest") val digest: String? = null,
    )
}

data class AppUpdate(
    val version: AppVersion,
    val isPrerelease: Boolean,
    /** Markdown notes of every offered release newer than the installed version, newest first. */
    val notes: String,
    val pageUrl: String,
    val apk: Apk,
) {
    data class Apk(
        val name: String,
        val url: String,
        val size: Long,
        /** Lowercase hex, null when GitHub has no digest for the asset. */
        val sha256: String?,
    )
}

/**
 * The newest release on [channel] that is newer than [installed] and has an APK, or null when the app
 * is up to date. Drafts never reach the app: GitHub hides them from anonymous calls.
 */
fun selectUpdate(
    releases: List<GitHubRelease>,
    channel: UpdateChannel,
    installed: AppVersion,
): AppUpdate? {
    val newer = releases
        .filter { !it.isDraft }
        .mapNotNull { release -> AppVersion.parse(release.tagName)?.let { it to release } }
        .filter { (version, release) ->
            channel == UpdateChannel.PRERELEASE || !(release.isPrerelease || version.isPrerelease)
        }
        .filter { (version, _) -> version > installed }
        .sortedByDescending { (version, _) -> version }

    val (version, release, apk) = newer.firstNotNullOfOrNull { (version, release) ->
        release.apk()?.let { Triple(version, release, it) }
    } ?: return null

    val sections = newer
        .filter { (other, _) -> other <= version }
        .mapNotNull { (other, release) -> release.body?.trim()?.takeIf { it.isNotEmpty() }?.let { other to it } }
    val notes = sections.singleOrNull()?.second
        ?: sections.joinToString("\n\n") { (other, body) -> "## $other\n\n$body" }

    return AppUpdate(
        version = version,
        isPrerelease = release.isPrerelease || version.isPrerelease,
        notes = notes,
        pageUrl = release.pageUrl,
        apk = apk,
    )
}

// release.yml uploads exactly one asset per release, named after the tag.
private fun GitHubRelease.apk(): AppUpdate.Apk? {
    val asset = assets.find { it.name == "earside-$tagName.apk" } ?: return null
    return AppUpdate.Apk(
        name = asset.name,
        url = asset.downloadUrl,
        size = asset.size,
        sha256 = asset.digest
            ?.let { Regex("^sha256:([0-9a-fA-F]{64})$").matchEntire(it) }
            ?.groupValues?.get(1)?.lowercase(),
    )
}
