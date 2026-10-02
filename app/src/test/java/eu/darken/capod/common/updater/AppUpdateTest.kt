package eu.darken.capod.common.updater

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppUpdateTest : BaseTest() {

    private fun release(
        tag: String,
        body: String? = "Notes for $tag",
        isDraft: Boolean = false,
        isPrerelease: Boolean = false,
        hasApk: Boolean = true,
    ) = GitHubRelease(
        tagName = tag,
        isDraft = isDraft,
        isPrerelease = isPrerelease,
        body = body,
        pageUrl = "https://github.com/pa2x2/earside/releases/tag/$tag",
        assets = if (hasApk) {
            listOf(
                GitHubRelease.Asset(
                    name = "earside-$tag.apk",
                    downloadUrl = "https://github.com/pa2x2/earside/releases/download/$tag/earside-$tag.apk",
                    size = 9_800_000,
                    digest = "sha256:" + "AB".repeat(32),
                ),
            )
        } else {
            emptyList()
        },
    )

    private fun version(input: String) = AppVersion.parse(input).shouldNotBeNull()

    @Test
    fun `versions order by SemVer precedence, not as strings`() {
        (version("1.10.0") > version("1.9.0")) shouldBe true
        (version("v1.2.0") > version("1.2.0-beta1")) shouldBe true
        (version("1.2.0-beta.11") > version("1.2.0-beta.2")) shouldBe true
        (version("1.2.0-beta.1") > version("1.2.0-1")) shouldBe true
        (version("1.2.0-rc1") > version("1.2.0-beta1")) shouldBe true
        version("v1.2.0") shouldBe version("1.2.0")
        AppVersion.parse("1.2").shouldBeNull()
        AppVersion.parse("r1234").shouldBeNull()
    }

    @Test
    fun `stable channel offers the newest stable release with the notes of every version since the installed one`() {
        val releases = listOf(
            release("v1.4.0", hasApk = false),
            release("v1.3.0-beta1"),
            release("v1.3.0-rc1", isPrerelease = true),
            release("v1.2.0"),
            release("v1.1.2", isDraft = true),
            release("v1.1.1"),
            release("v1.1.0"),
            release("v1.0.0"),
        )

        val update = selectUpdate(releases, UpdateChannel.STABLE, installed = version("1.1.0")).shouldNotBeNull()

        update.version shouldBe version("1.2.0")
        update.isPrerelease shouldBe false
        update.notes shouldBe "## 1.2.0\n\nNotes for v1.2.0\n\n## 1.1.1\n\nNotes for v1.1.1"
        update.apk.name shouldBe "earside-v1.2.0.apk"
        update.apk.sha256 shouldBe "ab".repeat(32)

        selectUpdate(releases, UpdateChannel.STABLE, installed = version("1.2.0")).shouldBeNull()
    }

    @Test
    fun `pre-release channel offers a newer pre-release, and a single release's notes have no version heading`() {
        val releases = listOf(
            release("v1.3.0-beta1"),
            release("v1.2.0"),
        )

        val update = selectUpdate(releases, UpdateChannel.PRERELEASE, installed = version("1.2.0")).shouldNotBeNull()

        update.version shouldBe version("1.3.0-beta1")
        update.isPrerelease shouldBe true
        update.notes shouldBe "Notes for v1.3.0-beta1"

        // Installed pre-release on the stable channel: the final release replaces it.
        selectUpdate(releases.reversed(), UpdateChannel.STABLE, installed = version("1.2.0-beta1"))
            .shouldNotBeNull().version shouldBe version("1.2.0")
    }
}
