package eu.darken.capod.common.updater

/**
 * A version as release.yml tags it: `X.Y.Z` or `X.Y.Z-prerelease`, optionally with a leading `v`.
 * Ordered by SemVer 2.0.0 precedence, so `1.2.0-beta.2` < `1.2.0-beta.11` < `1.2.0` < `1.10.0`.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Dot-separated pre-release identifiers, empty for a stable version. */
    val prerelease: List<String> = emptyList(),
) : Comparable<AppVersion> {

    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
            .let { if (it != 0) return it }
        // A stable version outranks any pre-release of the same X.Y.Z.
        if (!isPrerelease || !other.isPrerelease) return other.prerelease.size - prerelease.size
        prerelease.zip(other.prerelease).forEach { (a, b) ->
            compareIdentifiers(a, b).let { if (it != 0) return it }
        }
        return prerelease.size - other.prerelease.size
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (isPrerelease) append(prerelease.joinToString(".", prefix = "-"))
    }

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(input: String): AppVersion? {
            val match = PATTERN.matchEntire(input.trim()) ?: return null
            val (major, minor, patch, prerelease) = match.destructured
            return AppVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                prerelease = if (prerelease.isEmpty()) emptyList() else prerelease.split("."),
            )
        }

        private fun compareIdentifiers(a: String, b: String): Int {
            val aNumber = a.takeIf { it.all(Char::isDigit) }?.toBigIntegerOrNull()
            val bNumber = b.takeIf { it.all(Char::isDigit) }?.toBigIntegerOrNull()
            return when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                // Numeric identifiers sort before alphanumeric ones.
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
