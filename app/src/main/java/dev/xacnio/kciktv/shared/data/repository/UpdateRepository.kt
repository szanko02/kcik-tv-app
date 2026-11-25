/**
 * File: UpdateRepository.kt
 *
 * Description: Implementation of Update Repository functionality.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.data.repository

import dev.xacnio.kciktv.shared.data.api.GithubApiService
import dev.xacnio.kciktv.shared.data.api.RetrofitClient
import dev.xacnio.kciktv.shared.data.model.GithubRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

class UpdateRepository(
    private val githubService: GithubApiService = RetrofitClient.githubService,
    private val httpClient: OkHttpClient = RetrofitClient.githubHttpClient,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry(
        val releases: List<GithubRelease>,
        val fetchedAtMs: Long
    )

    companion object {
        private const val TAG = "UpdateRepository"
        const val REPO_OWNER = "szanko02"
        const val REPO_NAME = "kcik-tv-app"
        internal const val CACHE_TTL_MS = 30_000L

        @Volatile
        private var sharedCache: CacheEntry? = null
        private val fetchMutex = Mutex()

        internal fun clearCacheForTesting() {
            sharedCache = null
        }
    }

    suspend fun getLatestRelease(
        channel: String,
        forceRefresh: Boolean = false
    ): Result<GithubRelease?> = withContext(Dispatchers.IO) {
        try {
            val releases = fetchReleasesWithCacheAndFallback(forceRefresh)
            Result.success(UpdateReleaseSelector.selectLatest(releases, channel))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeLogE("getLatestRelease failed for $REPO_OWNER/$REPO_NAME ($channel)", e)
            Result.failure(e)
        }
    }

    suspend fun getAllReleases(
        forceRefresh: Boolean = false
    ): Result<List<GithubRelease>?> = withContext(Dispatchers.IO) {
        try {
            val releases = fetchReleasesWithCacheAndFallback(forceRefresh)
            Result.success(releases)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            safeLogE("getAllReleases failed for $REPO_OWNER/$REPO_NAME", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchReleasesWithCacheAndFallback(
        forceRefresh: Boolean
    ): List<GithubRelease> = fetchMutex.withLock {
        val now = clockMs()
        if (!forceRefresh) {
            val cached = sharedCache
            if (cached != null && now - cached.fetchedAtMs in 0 until CACHE_TTL_MS) {
                return@withLock cached.releases
            }
        }

        val apiFailureSummary: String = try {
            val response = githubService.getReleases(REPO_OWNER, REPO_NAME)
            if (response.isSuccessful) {
                val releases = response.body().orEmpty()
                sharedCache = CacheEntry(releases, clockMs())
                return@withLock releases
            }
            "API HTTP ${response.code()}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "API ${e.javaClass.simpleName}: ${e.message ?: "error"}"
        }

        safeLogW("GitHub REST releases failed ($apiFailureSummary) for $REPO_OWNER/$REPO_NAME; falling back to releases.atom")

        val fallbackReleases = try {
            fetchViaAtomFallback()
        } catch (fallbackError: Exception) {
            throw IOException("$apiFailureSummary; Atom fallback ${fallbackError.message ?: fallbackError.javaClass.simpleName}", fallbackError)
        }

        sharedCache = CacheEntry(fallbackReleases, clockMs())
        fallbackReleases
    }

    private fun fetchViaAtomFallback(): List<GithubRelease> {
        val atomUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases.atom"
        val atomRequest = Request.Builder()
            .url(atomUrl)
            .header("Accept", "application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            .build()

        val atomXml = httpClient.newCall(atomRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }

        val initialReleases = GithubReleaseFallbackParser.parseAtomFeed(atomXml, REPO_OWNER, REPO_NAME)
        if (initialReleases.isEmpty()) return emptyList()

        // Enrich top releases (overall latest + latest stable) via expanded_assets so exact APK
        // filenames are resolved without extra GitHub REST API rate-limit cost.
        val tagsToEnrich = LinkedHashSet<String>()
        initialReleases.take(2).forEach { release ->
            if (release.safeTagName.isNotBlank()) tagsToEnrich.add(release.safeTagName)
        }
        initialReleases.firstOrNull { !it.prerelease && it.safeTagName.isNotBlank() }?.let { stable ->
            tagsToEnrich.add(stable.safeTagName)
        }

        return initialReleases.map { release ->
            val tag = release.safeTagName
            if (tag in tagsToEnrich) {
                val expandedHtml = fetchExpandedAssetsHtml(REPO_OWNER, REPO_NAME, tag)
                val resolvedAssets = GithubReleaseFallbackParser.resolveAssetsOrFallback(
                    expandedAssetsHtml = expandedHtml,
                    owner = REPO_OWNER,
                    repo = REPO_NAME,
                    tag = tag
                )
                release.copy(assets = resolvedAssets)
            } else {
                release
            }
        }
    }

    private fun fetchExpandedAssetsHtml(owner: String, repo: String, tag: String): String? {
        return runCatching {
            val encodedTag = URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
            val url = "https://github.com/$owner/$repo/releases/expanded_assets/$encodedTag"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html, */*;q=0.8")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun safeLogW(msg: String) {
        runCatching { android.util.Log.w(TAG, msg) }
    }

    private fun safeLogE(msg: String, tr: Throwable) {
        runCatching { android.util.Log.e(TAG, msg, tr) }
    }
}
