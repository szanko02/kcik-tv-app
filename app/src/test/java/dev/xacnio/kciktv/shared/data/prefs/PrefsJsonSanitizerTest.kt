package dev.xacnio.kciktv.shared.data.prefs

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsJsonSanitizerTest {

    private val gson = Gson()

    @Test
    fun parseVodWatchHistory_dropsLegacyObfuscatedEntriesAndKeepsValid() {
        val legacyOnly = """[{"a":"123","b":"VOD","c":"https://thumb","d":3600,"e":120}]"""
        val resLegacy = PrefsJsonSanitizer.parseVodWatchHistory(legacyOnly, gson)
        assertTrue(resLegacy.droppedAny)
        assertTrue(resLegacy.items.isEmpty())

        val mixed = """[
            {"videoId":"v1","videoTitle":"Stream 1","duration":1000,"watchedDuration":500,"channelSlug":"chan1"},
            {"a":"old","b":"ghost"},
            {"videoId":"v1","videoTitle":"Duplicate"},
            null,
            {"videoId":"","videoTitle":"Blank"}
        ]"""
        val resMixed = PrefsJsonSanitizer.parseVodWatchHistory(mixed, gson)
        assertTrue(resMixed.droppedAny)
        assertEquals(1, resMixed.items.size)
        assertEquals("v1", resMixed.items.first().videoId)
        assertEquals("chan1", resMixed.items.first().channelSlug)
    }

    @Test
    fun parseVodWatchHistory_handlesMalformedJsonSafely() {
        val malformed = PrefsJsonSanitizer.parseVodWatchHistory("{broken", gson)
        assertTrue(malformed.droppedAny)
        assertTrue(malformed.items.isEmpty())

        val blank = PrefsJsonSanitizer.parseVodWatchHistory("", gson)
        assertFalse(blank.droppedAny)
        assertTrue(blank.items.isEmpty())
    }

    @Test
    fun parseStoredAccounts_stripsGhostAccountFromMixedV256PayloadAndPreservesMultiAccount() {
        val legacyOnly = """[{"a":42,"b":"olduser","f":"tok"}]"""
        val resLegacy = PrefsJsonSanitizer.parseStoredAccounts(legacyOnly, gson)
        assertTrue(resLegacy.droppedAny)
        assertTrue(resLegacy.items.isEmpty())

        // Exact v2.5.6 bug scenario: ghost legacy entry + 1 real migrated account yielded size 2
        val mixedV256 = """[
            {"userId":777,"username":"szanko02","authToken":"secret-tok","profilePic":"https://pic"},
            {"a":777,"b":"szanko02","f":"old-tok"}
        ]"""
        val resMixed = PrefsJsonSanitizer.parseStoredAccounts(mixedV256, gson)
        assertTrue(resMixed.droppedAny)
        assertEquals(1, resMixed.items.size)
        assertEquals("szanko02", resMixed.items.first().username)
        assertEquals(777L, resMixed.items.first().userId)

        // Two genuine accounts stay intact without false drops
        val validTwo = """[
            {"userId":1,"username":"user1","authToken":"tok1"},
            {"userId":2,"username":"user2","authToken":"tok2"}
        ]"""
        val resValid = PrefsJsonSanitizer.parseStoredAccounts(validTwo, gson)
        assertFalse(resValid.droppedAny)
        assertEquals(2, resValid.items.size)
    }

    @Test
    fun parseStoredAccounts_deduplicatesSameUserId() {
        val dup = """[
            {"userId":5,"username":"first","authToken":"tokA"},
            {"userId":5,"username":"dup","authToken":"tokB"}
        ]"""
        val res = PrefsJsonSanitizer.parseStoredAccounts(dup, gson)
        assertTrue(res.droppedAny)
        assertEquals(1, res.items.size)
        assertEquals("first", res.items.first().username)
    }

    @Test
    fun parseSearchHistory_dropsObfuscatedAndIncompleteEntriesAndKeepsValid() {
        val legacyOnly = """[{"a":"channel","c":{"a":"1","b":"slug","c":"User"}}]"""
        val resLegacy = PrefsJsonSanitizer.parseSearchHistory(legacyOnly, gson)
        assertTrue(resLegacy.droppedAny)
        assertTrue(resLegacy.items.isEmpty())

        val mixed = """[
            {"type":"query","query":"valorant"},
            {"type":"query","query":""},
            {"type":"channel","channelItem":{"id":"1","slug":"kick","username":"Kick","isLive":true,"verified":true,"followersCount":10}},
            {"type":"channel","channelItem":{"id":"2","username":"NoSlug"}},
            {"type":"category","categoryItem":{"id":"3","slug":"irl","name":"IRL"}},
            {"type":"category","categoryItem":{"id":"4","slug":"missing-name"}},
            {"type":"unknown","query":"x"}
        ]"""
        val resMixed = PrefsJsonSanitizer.parseSearchHistory(mixed, gson)
        assertTrue(resMixed.droppedAny)
        assertEquals(3, resMixed.items.size)
        assertEquals("valorant", resMixed.items[0].query)
        assertEquals("kick", resMixed.items[1].channelItem?.slug)
        assertEquals("irl", resMixed.items[2].categoryItem?.slug)
    }
}
