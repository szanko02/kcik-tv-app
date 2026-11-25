package dev.xacnio.kciktv.shared.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SupportedLanguagesTest {

    @Test
    fun getAll_containsOnlyEnglishAndRussian() {
        val all = SupportedLanguages.getAll()
        assertEquals(listOf("en", "ru"), all.map { it.code })
        assertEquals("English", all[0].displayName)
        assertEquals("English", all[0].nativeName)
        assertEquals("Russian", all[1].displayName)
        assertEquals("Русский", all[1].nativeName)
    }

    @Test
    fun isSupported_allowsOnlyEnglishAndRussian() {
        assertTrue(SupportedLanguages.isSupported("en"))
        assertTrue(SupportedLanguages.isSupported("ru"))
        assertTrue(SupportedLanguages.isSupported("EN"))
        assertTrue(SupportedLanguages.isSupported(" ru "))

        listOf("tr", "de", "es", "fr", "ar", "ja", "system", "").forEach { code ->
            assertFalse("Expected '$code' to be unsupported", SupportedLanguages.isSupported(code))
        }
    }

    @Test
    fun getByCode_andGetDisplayName_handleLegacyUnsupportedCodesCleanly() {
        assertEquals("English", SupportedLanguages.getByCode("en")?.nativeName)
        assertEquals("Русский", SupportedLanguages.getByCode("ru")?.nativeName)
        assertNull(SupportedLanguages.getByCode("tr"))
        assertNull(SupportedLanguages.getByCode("de"))

        assertEquals("English", SupportedLanguages.getDisplayName("en"))
        assertEquals("Русский", SupportedLanguages.getDisplayName("ru"))
        // Unsupported/legacy codes must never leak raw uppercase codes like "TR"
        listOf("tr", "de", "es", "fr", "ar").forEach { legacy ->
            assertEquals("English", SupportedLanguages.getDisplayName(legacy))
            assertEquals("System Default", SupportedLanguages.getDisplayName(legacy, "System Default"))
        }
    }

    @Test
    fun getCodesForDialog_andGetDialogIndex_handleSupportedAndLegacyCodes() {
        assertArrayEquals(arrayOf("system", "en", "ru"), SupportedLanguages.getCodesForDialog())

        assertEquals(0, SupportedLanguages.getDialogIndex(null))
        assertEquals(0, SupportedLanguages.getDialogIndex(""))
        assertEquals(0, SupportedLanguages.getDialogIndex("system"))
        assertEquals(1, SupportedLanguages.getDialogIndex("en"))
        assertEquals(2, SupportedLanguages.getDialogIndex("ru"))

        // Legacy saved language codes safely map back to index 0 (system)
        listOf("tr", "de", "es", "fr", "ar").forEach { legacy ->
            assertEquals("Expected legacy code '$legacy' to map to system index 0", 0, SupportedLanguages.getDialogIndex(legacy))
        }
    }

    @Test
    fun normalizeLanguageCode_mapsUnsupportedCodesToSystem() {
        assertEquals("system", SupportedLanguages.normalizeLanguageCode(null))
        assertEquals("system", SupportedLanguages.normalizeLanguageCode(""))
        assertEquals("system", SupportedLanguages.normalizeLanguageCode("   "))
        assertEquals("system", SupportedLanguages.normalizeLanguageCode("system"))
        assertEquals("en", SupportedLanguages.normalizeLanguageCode("en"))
        assertEquals("ru", SupportedLanguages.normalizeLanguageCode("ru"))
        assertEquals("ru", SupportedLanguages.normalizeLanguageCode(" RU "))

        listOf("tr", "de", "es", "fr", "ar", "pt").forEach { legacy ->
            assertEquals("system", SupportedLanguages.normalizeLanguageCode(legacy))
        }
    }

    @Test
    fun resolveLocale_appliesExplicitSupportedAndFallsBackCleanly() {
        val ruSystem = Locale("ru", "RU")
        val enSystem = Locale("en", "US")
        val trSystem = Locale("tr", "TR")

        // Explicit supported overrides
        assertEquals("en", SupportedLanguages.resolveLocale("en", ruSystem).language)
        assertEquals("ru", SupportedLanguages.resolveLocale("ru", enSystem).language)

        // System default keeps supported system locale, otherwise falls back to English
        assertEquals(ruSystem, SupportedLanguages.resolveLocale("system", ruSystem))
        assertEquals(enSystem, SupportedLanguages.resolveLocale("system", enSystem))
        assertEquals(Locale.ENGLISH, SupportedLanguages.resolveLocale("system", trSystem))

        // Legacy saved codes fall back via system default (and to English if system is unsupported)
        assertEquals(ruSystem, SupportedLanguages.resolveLocale("tr", ruSystem))
        assertEquals(Locale.ENGLISH, SupportedLanguages.resolveLocale("tr", trSystem))
        assertEquals(Locale.ENGLISH, SupportedLanguages.resolveLocale("ar", Locale("de", "DE")))
    }
}
