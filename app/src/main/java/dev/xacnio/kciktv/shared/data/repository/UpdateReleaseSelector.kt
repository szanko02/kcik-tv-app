package dev.xacnio.kciktv.shared.data.repository

import dev.xacnio.kciktv.shared.data.model.GithubRelease
import kotlin.math.max

object UpdateReleaseSelector {
    private val DEBUG_SUFFIX_REGEX = Regex("(?i)-debug$")
    private val GIT_HASH_ONLY_REGEX = Regex("(?i)^[0-9a-f]{7,40}$")

    private data class ParsedVersion(
        val core: List<Int>,
        val suffix: String
    )

    fun selectLatest(releases: List<GithubRelease>, channel: String): GithubRelease? {
        val filtered = releases.filter { it.safeTagName.isNotBlank() }
        val candidates = if (channel == "stable") filtered.filter { !it.prerelease } else filtered
        return candidates.maxWithOrNull { a, b ->
            val verCmp = compareVersions(a.safeTagName, b.safeTagName)
            if (verCmp != 0) verCmp else a.safePublishedAt.compareTo(b.safePublishedAt)
        }
    }

    private fun compareVersions(aTag: String, bTag: String): Int {
        val aNewer = isNewerRelease(bTag, aTag)
        val bNewer = isNewerRelease(aTag, bTag)
        return when {
            aNewer && !bNewer -> 1
            bNewer && !aNewer -> -1
            else -> 0
        }
    }

    fun normalizeVersion(tagOrVersion: String): String =
        tagOrVersion.trim().removePrefix("v").removePrefix("V")

    fun isNewerRelease(currentVersion: String, releaseTag: String): Boolean {
        val cleanRelease = cleanComparableVersion(releaseTag)
        if (cleanRelease.isEmpty()) return false
        val cleanCurrent = cleanComparableVersion(currentVersion)
        if (cleanCurrent.isEmpty()) return true
        if (cleanRelease.equals(cleanCurrent, ignoreCase = true)) return false

        val currentParsed = parseVersion(cleanCurrent) ?: return !cleanRelease.equals(cleanCurrent, ignoreCase = true)
        val releaseParsed = parseVersion(cleanRelease) ?: return false

        // 1. Compare numeric major.minor.patch(.build) segments first
        val coreComparison = compareCoreNumbers(releaseParsed.core, currentParsed.core)
        if (coreComparison > 0) return true
        if (coreComparison < 0) return false

        // 2. Equal core version: handle stable vs pre-release vs local <base>-<gitSha> builds
        val currentSuffix = currentParsed.suffix
        val releaseSuffix = releaseParsed.suffix

        if (currentSuffix.equals(releaseSuffix, ignoreCase = true)) return false

        val currentIsHashOnly = GIT_HASH_ONLY_REGEX.matches(currentSuffix)
        val releaseIsHashOnly = GIT_HASH_ONLY_REGEX.matches(releaseSuffix)

        // Local untagged build `2.5.1-<sha>` is built from `2.5.1` source -> official `2.5.1` is not newer
        if (currentIsHashOnly && releaseSuffix.isEmpty()) return false
        // Official stable `2.5.1` is newer than pre-release (`2.5.1-beta.1`), and not older than pre-release
        if (releaseSuffix.isEmpty()) return currentSuffix.isNotEmpty()
        if (currentSuffix.isEmpty()) return false

        // Both have suffixes on the same core version:
        // Two untagged `<base>-<sha>` builds on the same base version don't establish ordering
        if (currentIsHashOnly && releaseIsHashOnly) {
            return false
        }

        return comparePrereleaseSuffix(releaseSuffix, currentSuffix)
    }

    private fun cleanComparableVersion(raw: String): String {
        val noPrefix = normalizeVersion(raw).substringBefore('+')
        return noPrefix.replace(DEBUG_SUFFIX_REGEX, "").trim()
    }

    private fun parseVersion(cleaned: String): ParsedVersion? {
        val dashIdx = cleaned.indexOf('-')
        val corePart = if (dashIdx >= 0) cleaned.substring(0, dashIdx) else cleaned
        val suffixPart = if (dashIdx >= 0) cleaned.substring(dashIdx + 1) else ""
        if (corePart.isEmpty()) return null

        val numbers = corePart.split('.')
        if (numbers.isEmpty()) return null
        val parsedCore = ArrayList<Int>(numbers.size)
        for (segment in numbers) {
            val value = segment.toIntOrNull() ?: return null
            parsedCore.add(value)
        }
        return ParsedVersion(parsedCore, suffixPart)
    }

    private fun compareCoreNumbers(a: List<Int>, b: List<Int>): Int {
        val maxLen = max(a.size, b.size)
        for (i in 0 until maxLen) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    /**
     * Returns true when [releaseSuffix] represents a newer pre-release than [currentSuffix].
     * Handles both standard SemVer identifiers (`alpha.1 < beta.1 < beta.2 < beta.10 < rc.1`)
     * and upstream git-hash pre-releases (`beta-756b8a6` -> `beta-d96786c` or `beta-d96786c` -> `beta-756b8a6`).
     */
    private fun comparePrereleaseSuffix(releaseSuffix: String, currentSuffix: String): Boolean {
        val releaseBaseAndHash = splitPrereleaseHash(releaseSuffix)
        val currentBaseAndHash = splitPrereleaseHash(currentSuffix)

        if (releaseBaseAndHash != null && currentBaseAndHash != null) {
            val (releaseBase, releaseHash) = releaseBaseAndHash
            val (currentBase, currentHash) = currentBaseAndHash
            if (releaseBase.equals(currentBase, ignoreCase = true)) {
                return !releaseHash.equals(currentHash, ignoreCase = true)
            }
        }

        val releaseTokens = releaseSuffix.split('.', '-').filter { it.isNotEmpty() }
        val currentTokens = currentSuffix.split('.', '-').filter { it.isNotEmpty() }
        val minLen = minOf(releaseTokens.size, currentTokens.size)

        for (i in 0 until minLen) {
            val rt = releaseTokens[i]
            val ct = currentTokens[i]
            if (rt.equals(ct, ignoreCase = true)) continue

            val rNum = rt.toLongOrNull()
            val cNum = ct.toLongOrNull()
            return when {
                rNum != null && cNum != null -> rNum > cNum
                rNum != null && cNum == null -> false // SemVer: numeric has lower precedence than non-numeric
                rNum == null && cNum != null -> true
                else -> compareStageToken(rt, ct) > 0
            }
        }

        return releaseTokens.size > currentTokens.size
    }

    private fun splitPrereleaseHash(suffix: String): Pair<String, String>? {
        val lastDash = suffix.lastIndexOf('-')
        if (lastDash <= 0 || lastDash >= suffix.length - 1) return null
        val base = suffix.substring(0, lastDash)
        val tail = suffix.substring(lastDash + 1)
        return if (GIT_HASH_ONLY_REGEX.matches(tail)) base to tail else null
    }

    private fun compareStageToken(a: String, b: String): Int {
        val rankA = stageRank(a)
        val rankB = stageRank(b)
        if (rankA != null && rankB != null && rankA != rankB) {
            return rankA.compareTo(rankB)
        }
        return a.lowercase().compareTo(b.lowercase())
    }

    private fun stageRank(token: String): Int? = when (token.lowercase()) {
        "dev", "canary", "nightly" -> 0
        "alpha", "a" -> 1
        "beta", "b" -> 2
        "pre", "preview" -> 3
        "rc" -> 4
        else -> null
    }
}
