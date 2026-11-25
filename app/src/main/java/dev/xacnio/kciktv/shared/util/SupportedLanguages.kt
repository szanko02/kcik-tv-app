/**
 * File: SupportedLanguages.kt
 *
 * Description: Implementation of Supported Languages functionality.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.util

import android.content.Context
import java.util.Locale

/**
 * Manages supported languages for the app.
 * Languages are dynamically detected based on available string resources.
 */
object SupportedLanguages {
    
    const val SYSTEM_CODE = "system"
    const val FALLBACK_CODE = "en"

    /**
     * Data class representing a supported language.
     */
    data class Language(
        val code: String,          // e.g., "en", "ru"
        val displayName: String,   // e.g., "English", "Russian"
        val nativeName: String     // Native name in that language
    )
    
    /**
     * List of all supported languages in the app.
     * Add new languages here when creating new values-XX folders.
     * 
     * Format: Language(code, englishName, nativeName)
     */
    private val SUPPORTED_LANGUAGES = listOf(
        Language("en", "English", "English"),
        Language("ru", "Russian", "Русский")
    )
    
    /**
     * Get all supported languages.
     */
    fun getAll(): List<Language> = SUPPORTED_LANGUAGES
    
    /**
     * Get a language by its code.
     */
    fun getByCode(code: String): Language? {
        val normalized = code.trim().lowercase(Locale.ROOT)
        return SUPPORTED_LANGUAGES.find { it.code == normalized }
    }

    /**
     * Normalize a persisted or user-supplied language code.
     * Any unsupported/legacy code (e.g., "tr", "de", "es", "fr", "ar") falls back to "system".
     */
    fun normalizeLanguageCode(code: String?): String {
        val trimmed = code?.trim()?.lowercase(Locale.ROOT)
        if (trimmed.isNullOrEmpty() || trimmed == SYSTEM_CODE) return SYSTEM_CODE
        return if (isSupported(trimmed)) trimmed else SYSTEM_CODE
    }

    /**
     * Resolve the effective [Locale] for a saved language preference and system locale.
     * Unsupported saved codes fall back to system default; unsupported system locales fall back to English.
     */
    fun resolveLocale(savedCode: String?, systemLocale: Locale): Locale {
        val normalized = normalizeLanguageCode(savedCode)
        return if (normalized == SYSTEM_CODE) {
            if (isSupported(systemLocale.language)) systemLocale else Locale.ENGLISH
        } else {
            Locale(normalized)
        }
    }
    
    /**
     * Get display name for a language code.
     * Returns the native name (e.g., "Русский" for "ru"), or [fallback] for unsupported/legacy codes.
     */
    fun getDisplayName(code: String, fallback: String = getByCode(FALLBACK_CODE)!!.nativeName): String {
        return getByCode(code)?.nativeName ?: fallback
    }
    
    /**
     * Get the current device locale's language code.
     */
    fun getDeviceLanguageCode(): String {
        return Locale.getDefault().language
    }
    
    /**
     * Check if a language is supported.
     */
    fun isSupported(code: String): Boolean {
        val normalized = code.trim().lowercase(Locale.ROOT)
        return SUPPORTED_LANGUAGES.any { it.code == normalized }
    }
    
    /**
     * Get display names array for dialog (includes "System Default" as first option).
     */
    fun getDisplayNamesForDialog(@Suppress("UNUSED_PARAMETER") context: Context, systemDefaultLabel: String): Array<String> {
        val names = mutableListOf(systemDefaultLabel)
        names.addAll(SUPPORTED_LANGUAGES.map { it.nativeName })
        return names.toTypedArray()
    }
    
    /**
     * Get locale codes array for dialog (includes "system" as first option).
     */
    fun getCodesForDialog(): Array<String> {
        val codes = mutableListOf(SYSTEM_CODE)
        codes.addAll(SUPPORTED_LANGUAGES.map { it.code })
        return codes.toTypedArray()
    }
    
    /**
     * Get the index of a language code in the dialog list.
     * Returns 0 (system default) if not found or unsupported.
     */
    fun getDialogIndex(code: String?): Int {
        val normalized = normalizeLanguageCode(code)
        if (normalized == SYSTEM_CODE) return 0
        val index = SUPPORTED_LANGUAGES.indexOfFirst { it.code == normalized }
        return if (index >= 0) index + 1 else 0 // +1 because "system" is at index 0
    }
}
