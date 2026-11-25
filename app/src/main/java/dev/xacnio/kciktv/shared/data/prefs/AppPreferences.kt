/**
 * File: AppPreferences.kt
 *
 * Description: Implementation of App Preferences functionality.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dev.xacnio.kciktv.shared.util.CryptoManager
import dev.xacnio.kciktv.shared.util.SupportedLanguages
import dev.xacnio.kciktv.shared.data.prefs.AppPreferences
import dev.xacnio.kciktv.R
import dev.xacnio.kciktv.mobile.ui.player.VideoCutoutScalingPolicy
import dev.xacnio.kciktv.shared.ui.tv.TvModeRoutingPolicy

class AppPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kick_tv_prefs", Context.MODE_PRIVATE)
    private val cryptoManager = CryptoManager()

    // Shared process-wide cache for decrypted values. Each AES-GCM decrypt involves a KeyStore
    // lookup, Cipher.getInstance/init, and two Base64 decodes — expensive in hot paths like the
    // chat flush loop that reads prefs.username every ~200ms. Sentinel for "key absent" avoids
    // repeated SharedPreferences lookups for missing keys.
    //
    // CRITICAL: This cache MUST be process-wide (companion object), not per-instance. Activities
    // create their own AppPreferences instance; if the cache were per-instance, a write from
    // LoginActivity (e.g. saveAuth) would not invalidate MobilePlayerActivity's cached ABSENT
    // sentinel, leaving the UI stuck showing "Log in" after a successful login.
    companion object {
        const val SIDE_CHAT_WIDTH_MIN = 15
        const val SIDE_CHAT_WIDTH_MAX = 70
        const val SIDE_CHAT_WIDTH_DEFAULT = 35
        const val CHAT_OVERLAY_OPACITY_MIN = 0
        const val CHAT_OVERLAY_OPACITY_MAX = 100
        const val CHAT_OVERLAY_OPACITY_DEFAULT = 50
        const val CHAT_OVERLAY_EDGE_MARGIN_MIN = 0
        const val CHAT_OVERLAY_EDGE_MARGIN_MAX = 30
        const val CHAT_OVERLAY_EDGE_MARGIN_DEFAULT = 0
        const val CHAT_SCALE_MIN = 50
        const val CHAT_SCALE_MAX = 200
        const val CHAT_SCALE_DEFAULT = 100
        const val CHAT_SPACING_MIN = 0
        const val CHAT_SPACING_MAX = 16
        const val CHAT_SPACING_DEFAULT = 4
        const val RESIZE_MODE_FIT = "FIT"
        const val RESIZE_MODE_ZOOM = "ZOOM"
        const val RESIZE_MODE_FILL = "FILL"
        const val UI_MODE_AUTO = "auto"
        const val UI_MODE_TV = "tv"
        const val UI_MODE_MOBILE = "mobile"

        private val decryptCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private const val ABSENT = "__kick_tv_absent__"
    }

    // Helper to encrypt data transparently
    private fun putStringSafe(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            decryptCache[key] = ABSENT
            return
        }
        val encrypted = cryptoManager.encrypt(value)
        if (encrypted != null) {
            prefs.edit().putString(key, encrypted).apply()
            decryptCache[key] = value
        } else {
            android.util.Log.e("AppPreferences", "Skipping plaintext write for key=$key after encryption failure")
        }
    }

    // Helper to decrypt data transparently (with auto-migration)
    private fun getStringSafe(key: String, defValue: String?): String? {
        decryptCache[key]?.let { cached ->
            return if (cached === ABSENT) defValue else cached
        }

        val storedValue = prefs.getString(key, null)
        if (storedValue == null) {
            decryptCache[key] = ABSENT
            return defValue
        }

        // Check if value looks encrypted (contains our separator)
        val resolved = if (storedValue.contains("|||")) {
            cryptoManager.decrypt(storedValue)
        } else {
            // It's plain text (old version), migrate it to encrypted
            putStringSafe(key, storedValue)
            storedValue
        }

        return if (resolved != null) {
            decryptCache[key] = resolved
            resolved
        } else {
            decryptCache[key] = ABSENT
            defValue
        }
    }

    var infoDelay: Int
        get() = prefs.getInt("info_delay", 5)
        set(value) = prefs.edit().putInt("info_delay", value).apply()

    var isFeedContentFullscreen: Boolean
        get() = prefs.getBoolean("feed_content_fullscreen", false)
        set(value) = prefs.edit().putBoolean("feed_content_fullscreen", value).apply()

    var isClipContentFullscreen: Boolean
        get() = prefs.getBoolean("clip_content_fullscreen", false)
        set(value) = prefs.edit().putBoolean("clip_content_fullscreen", value).apply()

    var infoTransparency: Int
        get() = prefs.getInt("info_transparency", 60)
        set(value) = prefs.edit().putInt("info_transparency", value).apply()

    var updateChannel: String
        get() {
            val saved = prefs.getString("update_channel", null)
            if (saved != null) return saved
            
            // Auto-detect based on version name
            return try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (pInfo.versionName.contains("beta", ignoreCase = true)) "beta" else "stable"
            } catch (e: Exception) {
                "stable"
            }
        }
        set(value) = prefs.edit().putString("update_channel", value).apply()

    var language: String
        get() = SupportedLanguages.normalizeLanguageCode(prefs.getString("language", SupportedLanguages.SYSTEM_CODE))
        set(value) = prefs.edit().putString("language", SupportedLanguages.normalizeLanguageCode(value)).apply()

    val languageRaw: String
        get() = language

    var themeColor: Int
        get() = androidx.core.content.ContextCompat.getColor(context, dev.xacnio.kciktv.R.color.theme_primary)
        set(@Suppress("UNUSED_PARAMETER") value) { /* No-op, theme is locked */ }

    var blockedCategories: Set<String>
        get() = prefs.getStringSet("blocked_categories", setOf("Pools, Hot Tubs & Beaches")) ?: setOf("Pools, Hot Tubs & Beaches")
        set(value) = prefs.edit().putStringSet("blocked_categories", value).apply()

    var streamLanguages: Set<String>
        get() {
            if (!prefs.contains("stream_languages")) {
                val systemLang = java.util.Locale.getDefault().language
                return setOf(systemLang)
            }
            return prefs.getStringSet("stream_languages", emptySet()) ?: emptySet()
        }
        set(value) = prefs.edit().putStringSet("stream_languages", value).apply()

    fun isCategoryBlocked(categoryName: String?): Boolean {
        if (categoryName == null) return false
        return blockedCategories.contains(categoryName)
    }

    fun toggleCategoryBlock(categoryName: String) {
        val current = blockedCategories.toMutableSet()
        if (current.contains(categoryName)) {
            current.remove(categoryName)
        } else {
            current.add(categoryName)
        }
        blockedCategories = current
    }

    fun addBlockedCategory(categoryName: String) {
        val current = blockedCategories.toMutableSet()
        current.add(categoryName)
        blockedCategories = current
    }

    fun removeBlockedCategory(categoryName: String) {
        val current = blockedCategories.toMutableSet()
        current.remove(categoryName)
        blockedCategories = current
    }

    var appFont: String
        get() = prefs.getString("app_font", "default") ?: "default"
        set(value) = prefs.edit().putString("app_font", value).apply()

    var animationsEnabled: Boolean
        get() = prefs.getBoolean("animations_enabled", true)
        set(value) = prefs.edit().putBoolean("animations_enabled", value).apply()

    var autoRefreshInterval: Int
        get() = prefs.getInt("auto_refresh_interval", 5)
        set(value) = prefs.edit().putInt("auto_refresh_interval", value).apply()

    var lastListMode: String
        get() = prefs.getString("last_list_mode", "GLOBAL") ?: "GLOBAL"
        set(value) = prefs.edit().putString("last_list_mode", value).apply()

    var zapDelay: Int
        get() = prefs.getInt("zap_delay", 700)
        set(value) = prefs.edit().putInt("zap_delay", value).apply()

    var blerpEnabled: Boolean
        get() = prefs.getBoolean("blerp_enabled", false)
        set(value) = prefs.edit().putBoolean("blerp_enabled", value).apply()

    var globalSortMode: String
        get() = prefs.getString("global_sort_mode", "viewer_count_desc") ?: "viewer_count_desc"
        set(value) = prefs.edit().putString("global_sort_mode", value).apply()
    
    var playerEngine: String
        get() = prefs.getString("player_engine", "amazon_ivs") ?: "amazon_ivs"
        set(value) = prefs.edit().putString("player_engine", value).apply()

    var mobileQualityLimit: String
        get() = prefs.getString("mobile_quality_limit", "none") ?: "none"
        set(value) = prefs.edit().putString("mobile_quality_limit", value).apply()

    var qualityLimit: Int
        get() = prefs.getInt("quality_limit", -1)
        set(value) = prefs.edit().putInt("quality_limit", value).apply()

    var dynamicQualityEnabled: Boolean
        get() = prefs.getBoolean("dynamic_quality_enabled", true)
        set(value) = prefs.edit().putBoolean("dynamic_quality_enabled", value).apply()

    var mobileLayoutMode: String
        get() = prefs.getString("mobile_layout_mode", "list") ?: "list"
        set(value) = prefs.edit().putString("mobile_layout_mode", value).apply()

    var catchUpMode: String
        get() = prefs.getString("catch_up_mode", "low") ?: "low"
        set(value) = prefs.edit().putString("catch_up_mode", value).apply()

    var backgroundAudioEnabled: Boolean
        get() = prefs.getBoolean("background_audio_enabled", true)
        set(value) = prefs.edit().putBoolean("background_audio_enabled", value).apply()

    // Noise Reduction (RNNoise) preset: 0=Off, 1=Light, 2=Balanced, 3=Strong, 4=Voice Focus, 5=Max
    var noiseReductionLevel: Int
        get() = prefs.getInt("noise_reduction_level", 0).coerceIn(0, 5)
        set(value) = prefs.edit().putInt("noise_reduction_level", value.coerceIn(0, 5)).apply()
        
    // Custom EQ Settings (Gain in dB)
    var eqBassGain: Float
        get() = prefs.getFloat("eq_bass_gain", 0f)
        set(value) = prefs.edit().putFloat("eq_bass_gain", value).apply()
        
    var eqLowMidGain: Float
        get() = prefs.getFloat("eq_low_mid_gain", 0f)
        set(value) = prefs.edit().putFloat("eq_low_mid_gain", value).apply()
        
    var eqMidGain: Float
        get() = prefs.getFloat("eq_mid_gain", 0f)
        set(value) = prefs.edit().putFloat("eq_mid_gain", value).apply()
        
    var eqHighMidGain: Float
        get() = prefs.getFloat("eq_high_mid_gain", 0f)
        set(value) = prefs.edit().putFloat("eq_high_mid_gain", value).apply()
        
    var eqTrebleGain: Float
        get() = prefs.getFloat("eq_treble_gain", 0f)
        set(value) = prefs.edit().putFloat("eq_treble_gain", value).apply()
        
    var eqPreAmpGain: Float
        get() = prefs.getFloat("eq_pre_amp_gain", 0f)
        set(value) = prefs.edit().putFloat("eq_pre_amp_gain", value).apply()

    var isVirtualizerEnabled: Boolean
        get() = prefs.getBoolean("is_virtualizer_enabled", false)
        set(value) = prefs.edit().putBoolean("is_virtualizer_enabled", value).apply()

    var virtualizerStrength: Int
        get() = prefs.getInt("virtualizer_strength", 1000)
        set(value) = prefs.edit().putInt("virtualizer_strength", value).apply()

    var reverbPreset: Int
        get() = prefs.getInt("reverb_preset", 0) // 0 = None
        set(value) = prefs.edit().putInt("reverb_preset", value).apply()

    var autoPipEnabled: Boolean
        get() = prefs.getBoolean("auto_pip_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_pip_enabled", value).apply()

    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean("auto_update_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_update_enabled", value).apply()

    var chatShowTimestamps: Boolean
        get() = prefs.getBoolean("chat_show_timestamps", false)
        set(value) = prefs.edit().putBoolean("chat_show_timestamps", value).apply()

    var chatShowSeconds: Boolean
        get() = prefs.getBoolean("chat_show_seconds", false)
        set(value) = prefs.edit().putBoolean("chat_show_seconds", value).apply()

    // Id of the last daily reward challenge whose spin-reveal animation has already played,
    // so reopening the dialog the same day shows the settled result instantly instead of replaying it.
    var lastAnimatedDailyRewardId: String?
        get() = prefs.getString("last_animated_daily_reward_id", null)
        set(value) = prefs.edit().putString("last_animated_daily_reward_id", value).apply()

    var chatTextSize: Float
        get() = prefs.getFloat("chat_text_size", 14f)
        set(value) = prefs.edit().putFloat("chat_text_size", value).apply()

    var chatEmoteSize: Float
        get() = prefs.getFloat("chat_emote_size", 1.8f)
        set(value) = prefs.edit().putFloat("chat_emote_size", value).apply()

    var chatMessageAnimation: String
        get() = prefs.getString("chat_message_animation", "none") ?: "none"
        set(value) = prefs.edit().putString("chat_message_animation", value).apply()
    
    // ==================== Multi-Account Registry ====================

    data class StoredAccount(
        @SerializedName("userId") val userId: Long,
        @SerializedName("username") val username: String,
        @SerializedName("profilePic") val profilePic: String?,
        @SerializedName("slug") val slug: String?,
        @SerializedName("chatColor") val chatColor: String?,
        @SerializedName("authToken") val authToken: String,
        @SerializedName("cookies") val cookies: String?,
        @SerializedName("xsrfToken") val xsrfToken: String?,
        @SerializedName("userJson") val userJson: String?,
        @SerializedName("blerpCookies") val blerpCookies: String?,
        @SerializedName("addedAt") val addedAt: Long = System.currentTimeMillis()
    )

    var activeAccountId: Long
        get() = prefs.getLong("active_account_id", 0L)
        set(value) = prefs.edit().putLong("active_account_id", value).apply()

    private fun getAccountsRaw(): String? = getStringSafe("accounts_json", null)
    private fun putAccountsRaw(json: String?) = putStringSafe("accounts_json", json)

    fun getAccounts(): List<StoredAccount> {
        val raw = getAccountsRaw()
        val parsed = PrefsJsonSanitizer.parseStoredAccounts(raw, gson)
        if (parsed.droppedAny && parsed.items.isNotEmpty()) {
            putAccountsRaw(gson.toJson(parsed.items))
        }

        // Auto-migrate single-account users: if list is empty but active auth exists, seed it.
        if (parsed.items.isEmpty()) {
            val token = getStringSafe("auth_token", null)
            val user = getStringSafe("logged_in_username", null)
            if (token != null && user != null) {
                val acc = StoredAccount(
                    userId = prefs.getLong("logged_in_user_id", 0L),
                    username = user,
                    profilePic = getStringSafe("logged_in_profile_pic", null),
                    slug = getStringSafe("logged_in_slug", null),
                    chatColor = getStringSafe("logged_in_chat_color", null),
                    authToken = token,
                    cookies = getStringSafe("saved_cookies", null),
                    xsrfToken = getStringSafe("xsrf_token", null),
                    userJson = getStringSafe("user_json", null),
                    blerpCookies = getStringSafe("saved_blerp_cookies", null)
                )
                val migrated = listOf(acc)
                putAccountsRaw(gson.toJson(migrated))
                prefs.edit().putLong("active_account_id", acc.userId).apply()
                return migrated
            }
            if (parsed.droppedAny) {
                putAccountsRaw(null)
            }
            return emptyList()
        }
        return parsed.items
    }

    fun commitActiveAccountToList() {
        val token = getStringSafe("auth_token", null) ?: return
        val acc = StoredAccount(
            userId = prefs.getLong("logged_in_user_id", 0L),
            username = getStringSafe("logged_in_username", null) ?: return,
            profilePic = getStringSafe("logged_in_profile_pic", null),
            slug = getStringSafe("logged_in_slug", null),
            chatColor = getStringSafe("logged_in_chat_color", null),
            authToken = token,
            cookies = getStringSafe("saved_cookies", null),
            xsrfToken = getStringSafe("xsrf_token", null),
            userJson = getStringSafe("user_json", null),
            blerpCookies = getStringSafe("saved_blerp_cookies", null)
        )
        val current = PrefsJsonSanitizer.parseStoredAccounts(getAccountsRaw(), gson).items.toMutableList()

        val idx = current.indexOfFirst {
            if (acc.userId > 0L) it.userId == acc.userId else it.username == acc.username
        }
        if (idx >= 0) current[idx] = acc else current.add(0, acc)
        putAccountsRaw(gson.toJson(current))
        prefs.edit().putLong("active_account_id", acc.userId).apply()
    }

    fun applyAccount(acc: StoredAccount) {
        saveAuth(acc.authToken, acc.username, acc.profilePic, acc.userId, acc.slug, acc.chatColor)
        putStringSafe("saved_cookies", acc.cookies)
        putStringSafe("xsrf_token", acc.xsrfToken)
        putStringSafe("user_json", acc.userJson)
        putStringSafe("saved_blerp_cookies", acc.blerpCookies)
        prefs.edit().putLong("active_account_id", acc.userId).apply()
    }

    fun removeAccount(userId: Long) {
        val current = getAccounts().toMutableList()
        current.removeAll { it.userId == userId }
        putAccountsRaw(if (current.isEmpty()) null else gson.toJson(current))
    }

    // ==================== Auth ====================

    var authToken: String?
        get() = getStringSafe("auth_token", null)
        set(value) = putStringSafe("auth_token", value)
    
    var username: String?
        get() = getStringSafe("logged_in_username", null)
        set(value) = putStringSafe("logged_in_username", value)
    
    var profilePic: String?
        get() = getStringSafe("logged_in_profile_pic", null)
        set(value) = putStringSafe("logged_in_profile_pic", value)

    var userId: Long
        get() = prefs.getLong("logged_in_user_id", 0L)
        set(value) = prefs.edit().putLong("logged_in_user_id", value).apply()
    
    val isLoggedIn: Boolean
        get() = authToken != null
    
    var userSlug: String?
        get() = getStringSafe("logged_in_slug", null)
        set(value) = putStringSafe("logged_in_slug", value)

    var chatColor: String?
        get() = getStringSafe("logged_in_chat_color", null)
        set(value) = putStringSafe("logged_in_chat_color", value)

    fun saveAuth(token: String, user: String, pic: String?, id: Long? = null, slug: String? = null, color: String? = null) {
        putStringSafe("auth_token", token)
        putStringSafe("logged_in_username", user)
        putStringSafe("logged_in_profile_pic", pic)
        prefs.edit().putLong("logged_in_user_id", id ?: 0L).apply()
        putStringSafe("logged_in_slug", slug)
        putStringSafe("logged_in_chat_color", color)
    }
    
    fun clearAuth() {
        prefs.edit()
            .remove("auth_token")
            .remove("logged_in_username")
            .remove("logged_in_profile_pic")
            .remove("logged_in_user_id")
            .remove("logged_in_slug")
            .remove("logged_in_chat_color")
            .remove("saved_cookies")
            .remove("saved_blerp_cookies")
            .remove("xsrf_token")
            .remove("user_json")
            .apply()
        // Invalidate decrypted cache entries that bypass putStringSafe.
        listOf(
            "auth_token", "logged_in_username", "logged_in_profile_pic",
            "logged_in_chat_color", "saved_cookies", "saved_blerp_cookies",
            "xsrf_token", "user_json", "logged_in_slug"
        ).forEach { decryptCache[it] = ABSENT }
    }
    
    var xsrfToken: String?
        get() = getStringSafe("xsrf_token", null)
        set(value) = putStringSafe("xsrf_token", value)

    var userJson: String?
        get() = getStringSafe("user_json", null)
        set(value) = putStringSafe("user_json", value)

    // Alias for savedCookies to match helper usage
    var cookies: String?
        get() = savedCookies
        set(value) { savedCookies = value }
    
    // ==================== Cookie Storage for WebView ====================
    
    /**
     * Saves all cookies from WebView as a single string (cookies separated by "|||")
     */
    var savedCookies: String?
        get() = getStringSafe("saved_cookies", null)
        set(value) = putStringSafe("saved_cookies", value)
        
    /**
     * Saves cookies specifically for Blerp domains
     */
    var savedBlerpCookies: String?
        get() = getStringSafe("saved_blerp_cookies", null)
        set(value) = putStringSafe("saved_blerp_cookies", value)
    
    /**
     * Extracts session_token from saved cookies and returns it
     */
    fun getSessionTokenFromCookies(): String? {
        val cookies = savedCookies ?: return null
        // Cookies are stored as "name=value; ..." format, separated by "|:|"
        val cookieList = cookies.split("|:|")
        for (cookie in cookieList) {
            if (cookie.contains("session_token=")) {
                // Extract the value
                val parts = cookie.split(";")
                for (part in parts) {
                    val trimmed = part.trim()
                    if (trimmed.startsWith("session_token=")) {
                        return trimmed.substring("session_token=".length)
                    }
                }
            }
        }
        return null
    }
    
    /**
     * Saves cookies for a specific domain
     * @param cookieString The full cookie string from CookieManager.getCookie()
     */
    fun appendCookies(@Suppress("UNUSED_PARAMETER") domain: String, cookieString: String?) {
        if (cookieString.isNullOrEmpty()) return
        val current = savedCookies ?: ""
        val newCookies = if (current.isEmpty()) cookieString else "$current|:|$cookieString"
        savedCookies = newCookies
    }

    var lastKnownLiveChannelIds: Set<String>
        get() = prefs.getStringSet("last_known_live_channels", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("last_known_live_channels", value).apply()

    var modEmoteChannelSlugs: Set<String>
        get() = prefs.getStringSet("mod_emote_channels", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("mod_emote_channels", value).apply()

    var recentEmoteIds: String?
        get() = prefs.getString("recent_emote_ids", null)
        set(value) = prefs.edit().putString("recent_emote_ids", value).apply()

    var lastWatchedChannelSlug: String?
        get() = prefs.getString("last_watched_channel_slug", null)
        set(value) = prefs.edit().putString("last_watched_channel_slug", value).apply()

    // Comma-separated slugs of recently watched channels (from followed list), most recent first.
    private var recentWatchedSlugsRaw: String?
        get() = prefs.getString("recent_watched_slugs", null)
        set(value) = prefs.edit().putString("recent_watched_slugs", value).apply()

    fun getRecentWatchedSlugs(): List<String> =
        recentWatchedSlugsRaw?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun saveRecentWatchedSlug(slug: String) {
        val current = getRecentWatchedSlugs().toMutableList()
        current.remove(slug)
        current.add(0, slug)
        recentWatchedSlugsRaw = current.take(30).joinToString(",")
    }

    // ==================== Chat Highlight Settings ====================
    
    var highlightOwnMessages: Boolean
        get() = prefs.getBoolean("highlight_own_messages", true)
        set(value) = prefs.edit().putBoolean("highlight_own_messages", value).apply()

    var highlightMentions: Boolean
        get() = prefs.getBoolean("highlight_mentions", true)
        set(value) = prefs.edit().putBoolean("highlight_mentions", value).apply()

    var highlightMods: Boolean
        get() = prefs.getBoolean("highlight_mods", true)
        set(value) = prefs.edit().putBoolean("highlight_mods", value).apply()

    var highlightVips: Boolean
        get() = prefs.getBoolean("highlight_vips", true)
        set(value) = prefs.edit().putBoolean("highlight_vips", value).apply()

    var highlightOg: Boolean
        get() = prefs.getBoolean("highlight_og", true)
        set(value) = prefs.edit().putBoolean("highlight_og", value).apply()

    var chatUseNameColorForHighlight: Boolean
        get() = prefs.getBoolean("chat_use_name_color_for_highlight", false)
        set(value) = prefs.edit().putBoolean("chat_use_name_color_for_highlight", value).apply()

    // When true, the user's name color randomly changes to a different color after each sent chat message.
    var chatRandomizeNameColorOnSend: Boolean
        get() = prefs.getBoolean("chat_randomize_name_color_on_send", false)
        set(value) = prefs.edit().putBoolean("chat_randomize_name_color_on_send", value).apply()

    // When true, highlighted messages are shown with a translucent filled background of the
    // highlight colour instead of just a coloured border.
    var highlightFillBackground: Boolean
        get() = prefs.getBoolean("highlight_fill_background", false)
        set(value) = prefs.edit().putBoolean("highlight_fill_background", value).apply()

    var vibrateOnMentions: Boolean
        get() = prefs.getBoolean("vibrate_on_mentions", true)
        set(value) = prefs.edit().putBoolean("vibrate_on_mentions", value).apply()

    // Plays a short, soft sound when the user is mentioned. Off by default (opt-in).
    var soundOnMentions: Boolean
        get() = prefs.getBoolean("sound_on_mentions", false)
        set(value) = prefs.edit().putBoolean("sound_on_mentions", value).apply()

    // Posts a system notification on mention/reply, but only while NOT actively watching in
    // the foreground (screen locked, PIP, or background audio). Off by default (opt-in).
    var notifyOnMentions: Boolean
        get() = prefs.getBoolean("notify_on_mentions", false)
        set(value) = prefs.edit().putBoolean("notify_on_mentions", value).apply()

    // When enabled, messages containing the user's username (without the @ prefix) will also
    // be treated as mentions. Fuzzy: leading/trailing word-boundary, underscore ↔ space/nothing
    // variants are all matched. Off by default (opt-in).
    var nickDetectionEnabled: Boolean
        get() = prefs.getBoolean("nick_detection_enabled", false)
        set(value) = prefs.edit().putBoolean("nick_detection_enabled", value).apply()

    var showPinnedGifts: Boolean
        get() = prefs.getBoolean("show_pinned_gifts", true)
        set(value) = prefs.edit().putBoolean("show_pinned_gifts", value).apply()

    var emoteComboEnabled: Boolean
        get() = prefs.getBoolean("emote_combo_enabled", false)
        set(value) = prefs.edit().putBoolean("emote_combo_enabled", value).apply()

    var floatingEmotesEnabled: Boolean
        get() = prefs.getBoolean("floating_emotes_enabled", false)
        set(value) = prefs.edit().putBoolean("floating_emotes_enabled", value).apply()

    var lowBatteryModeEnabled: Boolean
        get() = prefs.getBoolean("low_battery_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("low_battery_mode_enabled", value).apply()

    // --- Battery saver: per-surface animation controls ---
    var chatEmotesAnimated: Boolean
        get() = prefs.getBoolean("chat_emotes_animated", true)
        set(value) = prefs.edit().putBoolean("chat_emotes_animated", value).apply()

    // Frame-rate cap for animated chat emotes. Allowed values: 15, 30, 60.
    var chatEmoteFps: Int
        get() = prefs.getInt("chat_emote_fps", 30)
        set(value) = prefs.edit().putInt("chat_emote_fps", value).apply()

    // How the Active Chatters panel splits its list — a ChatterGrouping name.
    var activeChattersGrouping: String
        get() = prefs.getString("active_chatters_grouping", "ROLE") ?: "ROLE"
        set(value) = prefs.edit().putString("active_chatters_grouping", value).apply()

    // Ordering within each Active Chatters section — a ChatterSort name.
    var activeChattersSort: String
        get() = prefs.getString("active_chatters_sort", "MESSAGES") ?: "MESSAGES"
        set(value) = prefs.edit().putString("active_chatters_sort", value).apply()

    var activeChattersSortDescending: Boolean
        get() = prefs.getBoolean("active_chatters_sort_desc", true)
        set(value) = prefs.edit().putBoolean("active_chatters_sort_desc", value).apply()

    var subscriberBadgesAnimated: Boolean
        get() = prefs.getBoolean("subscriber_badges_animated", true)
        set(value) = prefs.edit().putBoolean("subscriber_badges_animated", value).apply()

    var quickPanelEmotesAnimated: Boolean
        get() = prefs.getBoolean("quick_panel_emotes_animated", true)
        set(value) = prefs.edit().putBoolean("quick_panel_emotes_animated", value).apply()

    // --- Battery saver: individually toggleable effects (only apply while the master
    // lowBatteryModeEnabled is ON). Default true = the master keeps its full behaviour. ---
    var batterySaverLimitQuality: Boolean
        get() = prefs.getBoolean("battery_saver_limit_quality", true)
        set(value) = prefs.edit().putBoolean("battery_saver_limit_quality", value).apply()

    var batterySaverSlowTimers: Boolean
        get() = prefs.getBoolean("battery_saver_slow_timers", true)
        set(value) = prefs.edit().putBoolean("battery_saver_slow_timers", value).apply()

    var batterySaverDisableShimmer: Boolean
        get() = prefs.getBoolean("battery_saver_disable_shimmer", true)
        set(value) = prefs.edit().putBoolean("battery_saver_disable_shimmer", value).apply()

    var batterySaverPauseChatInPip: Boolean
        get() = prefs.getBoolean("battery_saver_pause_chat_pip", true)
        set(value) = prefs.edit().putBoolean("battery_saver_pause_chat_pip", value).apply()

    // ==================== Developer / Mock Mode ====================

    var mockModeEnabled: Boolean
        get() = prefs.getBoolean("mock_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("mock_mode_enabled", value).apply()

    var lastSeenWhatsNewVersion: String
        get() = prefs.getString("last_seen_whats_new_version", "") ?: ""
        set(value) = prefs.edit().putString("last_seen_whats_new_version", value).apply()

    // Analytics (Privacy-focused - users can opt-out)
    var analyticsEnabled: Boolean
        get() = prefs.getBoolean("analytics_enabled", true)
        set(value) = prefs.edit().putBoolean("analytics_enabled", value).apply()

    var chatRefreshRate: Long
        // Default 1500 ms. User-customised values are preserved; this only affects fresh installs.
        get() = prefs.getLong("chat_refresh_rate", 1500L)
        set(value) = prefs.edit().putLong("chat_refresh_rate", value).apply()

    var recentCategoriesRaw: String?
        get() = prefs.getString("recent_categories", null)
        set(value) = prefs.edit().putString("recent_categories", value).apply()

    fun getRecentCategories(): List<String> {
        val raw = recentCategoriesRaw ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun addRecentCategory(categoryName: String) {
        val current = getRecentCategories().toMutableList()
        current.remove(categoryName) // Remove if exists to move to top
        current.add(0, categoryName)
        
        // Keep only top 10
        val limited = if (current.size > 10) current.take(10) else current
        recentCategoriesRaw = limited.joinToString(",")
    }

    private var legacySearchHistoryRaw: String?
        get() = prefs.getString("search_history_legacy", null)
        set(value) = prefs.edit().putString("search_history_legacy", value).apply()

    var searchHistoryRaw: String?
        get() = prefs.getString("search_history", null)
        set(value) = prefs.edit().putString("search_history", value).apply()

    fun getSearchHistory(): List<String> {
        val raw = legacySearchHistoryRaw ?: return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        val current = getSearchHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        val limited = if (current.size > 15) current.take(15) else current
        legacySearchHistoryRaw = limited.joinToString("|||")
    }

    fun clearSearchHistory() {
        searchHistoryRaw = null
        legacySearchHistoryRaw = null
    }

    private val gson = Gson()

    data class HistoryEntry(
        @SerializedName("type") val type: String, // "query", "channel", "category"
        @SerializedName("query") val query: String? = null,
        @SerializedName("channelItem") val channelItem: dev.xacnio.kciktv.shared.data.model.SearchResultItem.ChannelResult? = null,
        @SerializedName("categoryItem") val categoryItem: dev.xacnio.kciktv.shared.data.model.SearchResultItem.CategoryResult? = null,
        @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
    )

    fun getSearchHistoryItems(): List<HistoryEntry> {
        val raw = searchHistoryRaw ?: return emptyList()
        val parsed = PrefsJsonSanitizer.parseSearchHistory(raw, gson)
        if (parsed.droppedAny) {
            searchHistoryRaw = if (parsed.items.isEmpty()) null else gson.toJson(parsed.items)
        }
        return parsed.items
    }

    fun addSearchHistoryEntry(entry: HistoryEntry) {
        val current = getSearchHistoryItems().toMutableList()
        
        // Remove duplicates of same content
        current.removeAll { 
            when (entry.type) {
                "query" -> it.type == "query" && it.query == entry.query
                "channel" -> it.type == "channel" && it.channelItem?.slug == entry.channelItem?.slug
                "category" -> it.type == "category" && it.categoryItem?.slug == entry.categoryItem?.slug
                else -> false
            }
        }
        
        current.add(0, entry)
        val limited = if (current.size > 20) current.take(20) else current
        searchHistoryRaw = gson.toJson(limited)
    }
    fun getChannelRecentEmoteIds(channelId: Long): String? {
        return prefs.getString("recent_emote_ids_$channelId", null)
    }

    fun setChannelRecentEmoteIds(channelId: Long, value: String) {
        prefs.edit().putString("recent_emote_ids_$channelId", value).apply()
    }

    fun getAcceptedRuleHash(slug: String): String? {
        return prefs.getString("chat_rules_accepted_$slug", null)
    }

    fun setAcceptedRuleHash(slug: String, hash: String) {
        prefs.edit().putString("chat_rules_accepted_$slug", hash).apply()
    }

    // ==================== VOD Watch History ====================

    data class VodWatchState(
        @SerializedName("videoId") val videoId: String,
        @SerializedName("videoTitle") val videoTitle: String?,
        @SerializedName("thumbnailUrl") val thumbnailUrl: String?,
        @SerializedName("duration") val duration: Long,
        @SerializedName("watchedDuration") val watchedDuration: Long,
        @SerializedName("sourceUrl") val sourceUrl: String?,
        @SerializedName("channelName") val channelName: String?,
        @SerializedName("channelSlug") val channelSlug: String?,
        @SerializedName("profilePic") val profilePic: String?,
        @SerializedName("categoryName") val categoryName: String? = null,
        @SerializedName("categorySlug") val categorySlug: String? = null,
        @SerializedName("channelId") val channelId: String? = null,
        @SerializedName("lastWatchedTimestamp") val lastWatchedTimestamp: Long = System.currentTimeMillis()
    )

    var vodWatchHistoryRaw: String?
        get() = prefs.getString("vod_watch_history", null)
        set(value) = prefs.edit().putString("vod_watch_history", value).apply()

    fun getVodWatchHistory(): List<VodWatchState> {
        val raw = vodWatchHistoryRaw ?: return emptyList()
        val parsed = PrefsJsonSanitizer.parseVodWatchHistory(raw, gson)
        if (parsed.droppedAny) {
            vodWatchHistoryRaw = if (parsed.items.isEmpty()) null else gson.toJson(parsed.items)
        }
        return parsed.items
    }

    fun saveVodProgress(state: VodWatchState) {
        val current = getVodWatchHistory().toMutableList()
        
        // Remove existing entry for same video
        current.removeAll { it.videoId == state.videoId }
        
        // Add new state to top
        current.add(0, state)
        
        // Limit history size (e.g. 50 items)
        val limited = if (current.size > 50) current.take(50) else current
        
        vodWatchHistoryRaw = gson.toJson(limited)
    }

    fun getVodProgress(videoId: String): VodWatchState? {
        return getVodWatchHistory().find { it.videoId == videoId }
    }

    fun clearVodWatchHistory() {
        prefs.edit().remove("vod_watch_history").apply()
    }

    fun removeVodProgress(videoId: String) {
        val current = getVodWatchHistory().toMutableList()
        val removed = current.removeAll { it.videoId == videoId }
        if (removed) {
            vodWatchHistoryRaw = gson.toJson(current)
        }
    }

    // === Trusted Domains for Link Previews ===

    private val defaultTrustedDomains: Set<String>
        get() = dev.xacnio.kciktv.shared.util.Constants.Urls.TRUSTED_DOMAINS.toSet()

    var trustedDomains: Set<String>
        get() {
            val stored = prefs.getStringSet("trusted_domains", null)
            return stored ?: defaultTrustedDomains
        }
        set(value) = prefs.edit().putStringSet("trusted_domains", value).apply()

    fun addTrustedDomain(domain: String) {
        val current = trustedDomains.toMutableSet()
        current.add(domain.lowercase().trim())
        trustedDomains = current
    }

    fun removeTrustedDomain(domain: String) {
        val current = trustedDomains.toMutableSet()
        current.remove(domain.lowercase().trim())
        trustedDomains = current
    }

    fun resetTrustedDomains() {
        prefs.edit().remove("trusted_domains").apply()
    }

    // === Network Proxy (Advanced) ===
    // Only applied to kick.com, web.kick.com and search.kick.com traffic.

    var proxyEnabled: Boolean
        get() = prefs.getBoolean("proxy_enabled", false)
        set(value) = prefs.edit().putBoolean("proxy_enabled", value).apply()

    /** "http" or "socks5" */
    var proxyType: String
        get() = prefs.getString("proxy_type", "socks5") ?: "socks5"
        set(value) = prefs.edit().putString("proxy_type", value).apply()

    /**
     * "critical" (default) routes only the channel/video API endpoints that Kick can
     * geo-block (api/v2/channels, api/v1/channels, api/v1/video) through the proxy.
     * "all" routes every kick.com/web.kick.com/search.kick.com API call through it.
     */
    var proxyScope: String
        get() = prefs.getString("proxy_scope", "critical") ?: "critical"
        set(value) = prefs.edit().putString("proxy_scope", value).apply()

    var proxyHost: String
        get() = prefs.getString("proxy_host", "") ?: ""
        set(value) = prefs.edit().putString("proxy_host", value).apply()

    var proxyPort: Int
        get() = prefs.getInt("proxy_port", 0)
        set(value) = prefs.edit().putInt("proxy_port", value).apply()

    var proxyUsername: String?
        get() = getStringSafe("proxy_username", null)
        set(value) = putStringSafe("proxy_username", value)

    var proxyPassword: String?
        get() = getStringSafe("proxy_password", null)
        set(value) = putStringSafe("proxy_password", value)

    var sideChatWidthPercent: Int
        get() = prefs.getInt("side_chat_width_percent", SIDE_CHAT_WIDTH_DEFAULT).coerceIn(SIDE_CHAT_WIDTH_MIN, SIDE_CHAT_WIDTH_MAX)
        set(value) = prefs.edit().putInt("side_chat_width_percent", value.coerceIn(SIDE_CHAT_WIDTH_MIN, SIDE_CHAT_WIDTH_MAX)).apply()

    var sideChatResizeHandleEnabled: Boolean
        get() = prefs.getBoolean("side_chat_resize_handle_enabled", true)
        set(value) = prefs.edit().putBoolean("side_chat_resize_handle_enabled", value).apply()

    var transparentChatOverlayEnabled: Boolean
        get() = prefs.getBoolean("transparent_chat_overlay_enabled", false)
        set(value) = prefs.edit().putBoolean("transparent_chat_overlay_enabled", value).apply()

    var chatOverlayOpacityPercent: Int
        get() = prefs.getInt("chat_overlay_opacity_percent", CHAT_OVERLAY_OPACITY_DEFAULT)
            .coerceIn(CHAT_OVERLAY_OPACITY_MIN, CHAT_OVERLAY_OPACITY_MAX)
        set(value) = prefs.edit().putInt(
            "chat_overlay_opacity_percent",
            value.coerceIn(CHAT_OVERLAY_OPACITY_MIN, CHAT_OVERLAY_OPACITY_MAX)
        ).apply()

    var chatOverlayEdgeMarginPercent: Int
        get() = prefs.getInt("chat_overlay_edge_margin_percent", CHAT_OVERLAY_EDGE_MARGIN_DEFAULT)
            .coerceIn(CHAT_OVERLAY_EDGE_MARGIN_MIN, CHAT_OVERLAY_EDGE_MARGIN_MAX)
        set(value) = prefs.edit().putInt(
            "chat_overlay_edge_margin_percent",
            value.coerceIn(CHAT_OVERLAY_EDGE_MARGIN_MIN, CHAT_OVERLAY_EDGE_MARGIN_MAX)
        ).apply()

    fun resetChatSettingsToDefaults() {
        val editor = prefs.edit()
        ChatSettingsDefaults.defaults.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }

    var chatMessageScale: Int
        get() = prefs.getInt("chat_message_scale", CHAT_SCALE_DEFAULT).coerceIn(CHAT_SCALE_MIN, CHAT_SCALE_MAX)
        set(value) = prefs.edit().putInt("chat_message_scale", value.coerceIn(CHAT_SCALE_MIN, CHAT_SCALE_MAX)).apply()

    var chatMessageSpacingDp: Int
        get() = prefs.getInt("chat_message_spacing_dp", CHAT_SPACING_DEFAULT).coerceIn(CHAT_SPACING_MIN, CHAT_SPACING_MAX)
        set(value) = prefs.edit().putInt("chat_message_spacing_dp", value.coerceIn(CHAT_SPACING_MIN, CHAT_SPACING_MAX)).apply()

    var chatPinchToResizeEnabled: Boolean
        get() = prefs.getBoolean("chat_pinch_to_resize_enabled", true)
        set(value) = prefs.edit().putBoolean("chat_pinch_to_resize_enabled", value).apply()

    var fullscreenSensorRotation: Boolean
        get() = prefs.getBoolean("fullscreen_sensor_rotation", true)
        set(value) = prefs.edit().putBoolean("fullscreen_sensor_rotation", value).apply()

    var drawUnderCutout: Boolean
        get() = prefs.getBoolean("draw_under_cutout", true)
        set(value) = prefs.edit().putBoolean("draw_under_cutout", value).apply()

    var fullscreenResizeMode: String
        get() = VideoCutoutScalingPolicy.fromPref(
            prefs.getString("fullscreen_resize_mode", RESIZE_MODE_FIT)
        ).prefValue
        set(value) = prefs.edit().putString(
            "fullscreen_resize_mode",
            VideoCutoutScalingPolicy.fromPref(value).prefValue
        ).apply()

    var uiMode: String
        get() = TvModeRoutingPolicy.normalizeUiMode(
            prefs.getString("ui_mode", UI_MODE_AUTO)
        )
        set(value) {
            prefs.edit()
                .putString("ui_mode", TvModeRoutingPolicy.normalizeUiMode(value))
                .commit()
        }
}

/**
 * Pure-Kotlin registry of chat appearance, layout, overlay, feature, animation,
 * highlight, and refresh preference keys and their default values.
 *
 * Kept free of Android runtime dependencies so JVM unit tests can verify default values
 * and guard against accidental resets of non-chat keys (auth, proxy, player, language, ui_mode, etc.).
 */
object ChatSettingsDefaults {
    const val CHAT_TEXT_SIZE = 14f
    const val CHAT_EMOTE_SIZE = 1.8f
    const val CHAT_MESSAGE_SCALE = AppPreferences.CHAT_SCALE_DEFAULT
    const val CHAT_MESSAGE_SPACING_DP = AppPreferences.CHAT_SPACING_DEFAULT
    const val SIDE_CHAT_WIDTH_PERCENT = AppPreferences.SIDE_CHAT_WIDTH_DEFAULT
    const val TRANSPARENT_CHAT_OVERLAY_ENABLED = false
    const val CHAT_OVERLAY_OPACITY_PERCENT = AppPreferences.CHAT_OVERLAY_OPACITY_DEFAULT
    const val CHAT_OVERLAY_EDGE_MARGIN_PERCENT = AppPreferences.CHAT_OVERLAY_EDGE_MARGIN_DEFAULT

    const val CHAT_SHOW_TIMESTAMPS = false
    const val CHAT_SHOW_SECONDS = false
    const val SHOW_PINNED_GIFTS = true
    const val EMOTE_COMBO_ENABLED = false
    const val FLOATING_EMOTES_ENABLED = false
    const val CHAT_PINCH_TO_RESIZE_ENABLED = true
    const val SIDE_CHAT_RESIZE_HANDLE_ENABLED = true

    const val CHAT_MESSAGE_ANIMATION = "none"
    const val HIGHLIGHT_OWN_MESSAGES = true
    const val HIGHLIGHT_MENTIONS = true
    const val HIGHLIGHT_MODS = true
    const val HIGHLIGHT_VIPS = true
    const val HIGHLIGHT_OG = true
    const val CHAT_USE_NAME_COLOR_FOR_HIGHLIGHT = false
    const val HIGHLIGHT_FILL_BACKGROUND = false
    const val VIBRATE_ON_MENTIONS = true
    const val SOUND_ON_MENTIONS = false
    const val NOTIFY_ON_MENTIONS = false
    const val NICK_DETECTION_ENABLED = false
    const val CHAT_RANDOMIZE_NAME_COLOR_ON_SEND = false
    const val CHAT_REFRESH_RATE = 1500L

    val defaults: Map<String, Any> = linkedMapOf(
        // Sizes & Layout (Chat Appearance)
        "chat_text_size" to CHAT_TEXT_SIZE,
        "chat_emote_size" to CHAT_EMOTE_SIZE,
        "chat_message_scale" to CHAT_MESSAGE_SCALE,
        "chat_message_spacing_dp" to CHAT_MESSAGE_SPACING_DP,
        "side_chat_width_percent" to SIDE_CHAT_WIDTH_PERCENT,
        "transparent_chat_overlay_enabled" to TRANSPARENT_CHAT_OVERLAY_ENABLED,
        "chat_overlay_opacity_percent" to CHAT_OVERLAY_OPACITY_PERCENT,
        "chat_overlay_edge_margin_percent" to CHAT_OVERLAY_EDGE_MARGIN_PERCENT,
        // Features & Gestures
        "chat_show_timestamps" to CHAT_SHOW_TIMESTAMPS,
        "chat_show_seconds" to CHAT_SHOW_SECONDS,
        "show_pinned_gifts" to SHOW_PINNED_GIFTS,
        "emote_combo_enabled" to EMOTE_COMBO_ENABLED,
        "floating_emotes_enabled" to FLOATING_EMOTES_ENABLED,
        "chat_pinch_to_resize_enabled" to CHAT_PINCH_TO_RESIZE_ENABLED,
        "side_chat_resize_handle_enabled" to SIDE_CHAT_RESIZE_HANDLE_ENABLED,
        // Animations, Highlights & Refresh
        "chat_message_animation" to CHAT_MESSAGE_ANIMATION,
        "highlight_own_messages" to HIGHLIGHT_OWN_MESSAGES,
        "highlight_mentions" to HIGHLIGHT_MENTIONS,
        "highlight_mods" to HIGHLIGHT_MODS,
        "highlight_vips" to HIGHLIGHT_VIPS,
        "highlight_og" to HIGHLIGHT_OG,
        "chat_use_name_color_for_highlight" to CHAT_USE_NAME_COLOR_FOR_HIGHLIGHT,
        "highlight_fill_background" to HIGHLIGHT_FILL_BACKGROUND,
        "vibrate_on_mentions" to VIBRATE_ON_MENTIONS,
        "sound_on_mentions" to SOUND_ON_MENTIONS,
        "notify_on_mentions" to NOTIFY_ON_MENTIONS,
        "nick_detection_enabled" to NICK_DETECTION_ENABLED,
        "chat_randomize_name_color_on_send" to CHAT_RANDOMIZE_NAME_COLOR_ON_SEND,
        "chat_refresh_rate" to CHAT_REFRESH_RATE
    )

    val keys: Set<String>
        get() = defaults.keys

    fun applyToMap(store: MutableMap<String, Any?>) {
        defaults.forEach { (key, value) ->
            store[key] = value
        }
    }
}
