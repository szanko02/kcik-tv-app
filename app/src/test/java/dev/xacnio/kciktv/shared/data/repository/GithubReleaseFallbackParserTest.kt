package dev.xacnio.kciktv.shared.data.repository

import com.google.gson.Gson
import dev.xacnio.kciktv.shared.data.api.GithubApiService
import dev.xacnio.kciktv.shared.data.model.GithubRelease
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class GithubReleaseFallbackParserTest {

    @Before
    fun setUp() {
        UpdateRepository.clearCacheForTesting()
    }

    @Test
    fun gsonNullFields_neverThrowNullPointerException() {
        val json = """
            {
              "tag_name": "v2.5.1",
              "name": null,
              "prerelease": false,
              "published_at": null,
              "assets": null,
              "body": null
            }
        """.trimIndent()

        val release = Gson().fromJson(json, GithubRelease::class.java)
        assertEquals("v2.5.1", release.safeTagName)
        assertEquals("v2.5.1", release.safeName)
        assertEquals("", release.safePublishedAt)
        assertEquals("", release.safeBody)
        assertTrue(release.safeAssets.isEmpty())
    }

    @Test
    fun parseAtomFeed_extractsTagTitleTimestampBodyPrereleaseAndFallbackAsset() {
        val atomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/1466604200/v2.5.2</id>
                <updated>2026-11-25T04:10:00Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/szanko02/kcik-tv-app/releases/tag/v2.5.2"/>
                <title>KCIKTV v2.5.2</title>
                <content type="html">&lt;h3&gt;Исправлено&lt;/h3&gt;
                &lt;ul&gt;
                  &lt;li&gt;&lt;strong&gt;OTA обновления:&lt;/strong&gt; Добавлен резервный канал &lt;code&gt;releases.atom&lt;/code&gt;.&lt;/li&gt;
                &lt;/ul&gt;</content>
              </entry>
              <entry>
                <id>tag:github.com,2008:Repository/999/v2.4.5-beta-d96786c</id>
                <updated>2026-11-20T12:00:00Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/xacnio/kcik-tv-app/releases/tag/v2.4.5-beta-d96786c"/>
                <title>2.4.5 Beta</title>
                <content type="html">&lt;p&gt;No content.&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val releases = GithubReleaseFallbackParser.parseAtomFeed(atomXml, "szanko02", "kcik-tv-app")
        assertEquals(2, releases.size)

        val stable = releases[0]
        assertEquals("v2.5.2", stable.safeTagName)
        assertEquals("KCIKTV v2.5.2", stable.safeName)
        assertFalse(stable.prerelease)
        assertEquals("2026-11-25T04:10:00Z", stable.safePublishedAt)
        assertTrue(stable.safeBody.contains("### Исправлено"))
        assertTrue(stable.safeBody.contains("- **OTA обновления:** Добавлен резервный канал `releases.atom`."))
        assertEquals(1, stable.safeAssets.size)
        assertEquals("KCIKTV-2.5.2.apk", stable.safeAssets[0].safeName)
        assertEquals(
            "https://github.com/szanko02/kcik-tv-app/releases/download/v2.5.2/KCIKTV-2.5.2.apk",
            stable.safeAssets[0].safeDownloadUrl
        )

        val pre = releases[1]
        assertEquals("v2.4.5-beta-d96786c", pre.safeTagName)
        assertTrue(pre.prerelease)
        assertEquals("", pre.safeBody)
    }

    @Test
    fun parseExpandedAssets_extractsExactApkLinksAndFallsBackDeterministically() {
        val html = """
            <div class="Box">
              <ul>
                <li class="Box-row">
                  <a href="/szanko02/kcik-tv-app/releases/download/v2.5.2/KCIKTV-2.5.2.apk" rel="nofollow">
                    <span class="text-bold">KCIKTV-2.5.2.apk</span>
                  </a>
                </li>
                <li class="Box-row">
                  <a href="/szanko02/kcik-tv-app/archive/refs/tags/v2.5.2.zip">Source code (zip)</a>
                </li>
              </ul>
            </div>
        """.trimIndent()

        val assets = GithubReleaseFallbackParser.parseExpandedAssets(html, "szanko02", "kcik-tv-app", "v2.5.2")
        assertEquals(1, assets.size)
        assertEquals("KCIKTV-2.5.2.apk", assets[0].safeName)
        assertEquals(
            "https://github.com/szanko02/kcik-tv-app/releases/download/v2.5.2/KCIKTV-2.5.2.apk",
            assets[0].safeDownloadUrl
        )

        val fallback = GithubReleaseFallbackParser.resolveAssetsOrFallback(null, "szanko02", "kcik-tv-app", "v2.5.1")
        assertEquals(1, fallback.size)
        assertEquals("KCIKTV-2.5.1.apk", fallback[0].safeName)
        assertEquals(
            "https://github.com/szanko02/kcik-tv-app/releases/download/v2.5.1/KCIKTV-2.5.1.apk",
            fallback[0].safeDownloadUrl
        )
    }

    @Test
    fun updateRepository_fallsBackToAtomOn403AndUses30sCacheUnlessForceRefreshed() = runBlocking {
        var apiCalls = 0
        var atomCalls = 0
        var nowMs = 1_000_000L

        val failingApi = object : GithubApiService {
            override suspend fun getReleases(owner: String, repo: String): Response<List<GithubRelease>> {
                apiCalls++
                return Response.error(403, """{"message":"API rate limit exceeded"}""".toResponseBody("application/json".toMediaType()))
            }

            override suspend fun getLatestRelease(owner: String, repo: String): Response<GithubRelease> {
                throw UnsupportedOperationException()
            }
        }

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                val body = when {
                    path.endsWith("/releases.atom") -> {
                        atomCalls++
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <feed xmlns="http://www.w3.org/2005/Atom">
                          <entry>
                            <updated>2026-11-25T05:00:00Z</updated>
                            <link rel="alternate" type="text/html" href="https://github.com/szanko02/kcik-tv-app/releases/tag/v2.5.2"/>
                            <title>KCIKTV v2.5.2</title>
                            <content type="html">&lt;p&gt;OTA Ready&lt;/p&gt;</content>
                          </entry>
                        </feed>
                        """.trimIndent()
                    }
                    path.contains("/releases/expanded_assets/") -> {
                        throw IOException("Simulated expanded_assets failure")
                    }
                    else -> ""
                }
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/xml".toMediaType()))
                    .build()
            }
            .build()

        val repo = UpdateRepository(
            githubService = failingApi,
            httpClient = fakeClient,
            clockMs = { nowMs }
        )

        val first = repo.getLatestRelease("stable", UpdateSource.FORK)
        assertTrue(first.isSuccess)
        val release = first.getOrNull()
        assertNotNull(release)
        assertEquals("v2.5.2", release?.safeTagName)
        assertEquals(
            "https://github.com/szanko02/kcik-tv-app/releases/download/v2.5.2/KCIKTV-2.5.2.apk",
            release?.safeAssets?.firstOrNull()?.safeDownloadUrl
        )
        assertEquals(1, apiCalls)
        assertEquals(1, atomCalls)

        // Second call within 30s hits cache (zero extra network calls)
        nowMs += 5_000L
        val cached = repo.getAllReleases(UpdateSource.FORK)
        assertTrue(cached.isSuccess)
        assertEquals(1, apiCalls)
        assertEquals(1, atomCalls)

        // Manual check with forceRefresh=true bypasses cache
        val forced = repo.getLatestRelease("stable", UpdateSource.FORK, forceRefresh = true)
        assertTrue(forced.isSuccess)
        assertEquals(2, apiCalls)
        assertEquals(2, atomCalls)
    }
}
