package dev.xacnio.kciktv.shared.data.repository

import dev.xacnio.kciktv.shared.data.model.GithubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseSelectorTest {

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        publishedAt: String = "2026-01-01T00:00:00Z"
    ) = GithubRelease(
        tagName = tag,
        name = tag,
        prerelease = prerelease,
        publishedAt = publishedAt,
        assets = emptyList(),
        body = ""
    )

    @Test
    fun updateSource_fromKey_mapsUpstreamAndFallsBackToFork() {
        assertEquals(UpdateSource.UPSTREAM, UpdateSource.fromKey("upstream"))
        assertEquals("xacnio", UpdateSource.UPSTREAM.owner)
        assertEquals("kcik-tv-app", UpdateSource.UPSTREAM.repo)

        assertEquals(UpdateSource.FORK, UpdateSource.fromKey("fork"))
        assertEquals("szanko02", UpdateSource.FORK.owner)
        assertEquals("kcik-tv-app", UpdateSource.FORK.repo)

        assertEquals(UpdateSource.FORK, UpdateSource.fromKey(null))
        assertEquals(UpdateSource.FORK, UpdateSource.fromKey(""))
        assertEquals(UpdateSource.FORK, UpdateSource.fromKey("unknown"))
        assertEquals(UpdateSource.FORK, UpdateSource.fromKey("UPSTREAM"))
    }

    @Test
    fun selectLatest_sortsByPublishedAtAndRespectsChannel() {
        val releases = listOf(
            release("v2.4.3", prerelease = false, publishedAt = "2026-01-01T10:00:00Z"),
            release("v2.4.5-beta-1v2b3c4", prerelease = true, publishedAt = "2026-01-03T10:00:00Z"),
            release("v2.4.4", prerelease = false, publishedAt = "2026-01-02T10:00:00Z")
        )

        val stable = UpdateReleaseSelector.selectLatest(releases, "stable")
        assertEquals("v2.4.4", stable?.tagName)

        val beta = UpdateReleaseSelector.selectLatest(releases, "beta")
        assertEquals("v2.4.5-beta-1v2b3c4", beta?.tagName)
    }

    @Test
    fun selectLatest_returnsNullWhenEmptyOrNoMatchingStable() {
        assertNull(UpdateReleaseSelector.selectLatest(emptyList(), "stable"))
        assertNull(UpdateReleaseSelector.selectLatest(emptyList(), "beta"))

        val onlyPrereleases = listOf(
            release("v2.4.5-beta-1", prerelease = true, publishedAt = "2026-01-03T10:00:00Z")
        )
        assertNull(UpdateReleaseSelector.selectLatest(onlyPrereleases, "stable"))
        assertEquals("v2.4.5-beta-1", UpdateReleaseSelector.selectLatest(onlyPrereleases, "beta")?.tagName)
    }

    @Test
    fun normalizeVersion_stripsOnlyLeadingPrefixAndPreservesInnerV() {
        assertEquals("2.4.5-beta-1v2b3c4", UpdateReleaseSelector.normalizeVersion("v2.4.5-beta-1v2b3c4"))
        assertEquals("2.4.5-beta-1v2b3c4", UpdateReleaseSelector.normalizeVersion("V2.4.5-beta-1v2b3c4"))
        assertEquals("2.4.5-beta-1v2b3c4", UpdateReleaseSelector.normalizeVersion("  v2.4.5-beta-1v2b3c4  "))
        assertEquals("2.4.5-beta-1v2b3c4", UpdateReleaseSelector.normalizeVersion("2.4.5-beta-1v2b3c4"))
        assertEquals("", UpdateReleaseSelector.normalizeVersion("v"))
        assertEquals("", UpdateReleaseSelector.normalizeVersion("   "))
    }

    @Test
    fun isNewerRelease_comparesNormalizedVersionsCaseInsensitively() {
        assertFalse(UpdateReleaseSelector.isNewerRelease("2.4.5-beta-1v2b3c4", "v2.4.5-beta-1v2b3c4"))
        assertFalse(UpdateReleaseSelector.isNewerRelease("v2.4.5-beta-1v2b3c4", "V2.4.5-BETA-1V2B3C4"))
        assertFalse(UpdateReleaseSelector.isNewerRelease("2.4.5", ""))
        assertFalse(UpdateReleaseSelector.isNewerRelease("2.4.5", "v"))

        assertTrue(UpdateReleaseSelector.isNewerRelease("2.4.5-beta-1v2b3c4", "v2.4.5-beta-9z8y7x6"))
        assertTrue(UpdateReleaseSelector.isNewerRelease("2.4.5", "v2.4.6"))
    }
}
