package dev.xacnio.kciktv.shared.data.repository

import dev.xacnio.kciktv.shared.data.model.GithubRelease

enum class UpdateSource(val key: String, val owner: String, val repo: String) {
    FORK("fork", "szanko02", "kcik-tv-app"),
    UPSTREAM("upstream", "xacnio", "kcik-tv-app");

    companion object {
        fun fromKey(key: String?): UpdateSource =
            if (key == UPSTREAM.key) UPSTREAM else FORK
    }
}

object UpdateReleaseSelector {
    fun selectLatest(releases: List<GithubRelease>, channel: String): GithubRelease? {
        val sorted = releases.sortedByDescending { it.publishedAt }
        return if (channel == "stable") {
            sorted.firstOrNull { !it.prerelease }
        } else {
            sorted.firstOrNull()
        }
    }

    fun normalizeVersion(tagOrVersion: String): String =
        tagOrVersion.trim().removePrefix("v").removePrefix("V")

    fun isNewerRelease(currentVersion: String, releaseTag: String): Boolean {
        val normalizedRelease = normalizeVersion(releaseTag)
        if (normalizedRelease.isEmpty()) return false
        val normalizedCurrent = normalizeVersion(currentVersion)
        return !normalizedRelease.equals(normalizedCurrent, ignoreCase = true)
    }
}
