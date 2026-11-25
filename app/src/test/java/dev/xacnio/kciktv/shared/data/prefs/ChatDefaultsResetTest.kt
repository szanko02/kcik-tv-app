package dev.xacnio.kciktv.shared.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDefaultsResetTest {

    @Test
    fun defaults_containsAllChatAppearanceOverlayFloatingFeatureHighlightKeysWithExpectedValues() {
        val d = ChatSettingsDefaults.defaults

        // Sizes & layout
        assertEquals(14f, d["chat_text_size"])
        assertEquals(1.8f, d["chat_emote_size"])
        assertEquals(AppPreferences.CHAT_SCALE_DEFAULT, d["chat_message_scale"])
        assertEquals(100, d["chat_message_scale"])
        assertEquals(AppPreferences.CHAT_SPACING_DEFAULT, d["chat_message_spacing_dp"])
        assertEquals(4, d["chat_message_spacing_dp"])
        assertEquals(AppPreferences.SIDE_CHAT_WIDTH_DEFAULT, d["side_chat_width_percent"])
        assertEquals(35, d["side_chat_width_percent"])

        // Transparent overlay & floating window
        assertEquals(false, d["transparent_chat_overlay_enabled"])
        assertEquals(AppPreferences.CHAT_OVERLAY_OPACITY_DEFAULT, d["chat_overlay_opacity_percent"])
        assertEquals(50, d["chat_overlay_opacity_percent"])
        assertEquals(false, d["floating_chat_overlay_enabled"])
        assertEquals(AppPreferences.CHAT_OVERLAY_EDGE_MARGIN_DEFAULT, d["chat_overlay_edge_margin_percent"])
        assertEquals(0, d["chat_overlay_edge_margin_percent"])
        assertEquals(AppPreferences.FLOATING_CHAT_HEIGHT_DEFAULT, d["floating_chat_height_percent"])
        assertEquals(75, d["floating_chat_height_percent"])
        assertEquals(AppPreferences.FLOATING_CHAT_X_DEFAULT, d["floating_chat_x_percent"])
        assertEquals(1.0f, d["floating_chat_x_percent"])
        assertEquals(AppPreferences.FLOATING_CHAT_Y_DEFAULT, d["floating_chat_y_percent"])
        assertEquals(0.5f, d["floating_chat_y_percent"])

        // Features & gestures
        assertEquals(false, d["chat_show_timestamps"])
        assertEquals(false, d["chat_show_seconds"])
        assertEquals(true, d["show_pinned_gifts"])
        assertEquals(false, d["emote_combo_enabled"])
        assertEquals(false, d["floating_emotes_enabled"])
        assertEquals(true, d["chat_pinch_to_resize_enabled"])
        assertEquals(true, d["side_chat_resize_handle_enabled"])

        // Animations, highlights & refresh
        assertEquals("none", d["chat_message_animation"])
        assertEquals(true, d["highlight_own_messages"])
        assertEquals(true, d["highlight_mentions"])
        assertEquals(true, d["highlight_mods"])
        assertEquals(true, d["highlight_vips"])
        assertEquals(true, d["highlight_og"])
        assertEquals(false, d["chat_use_name_color_for_highlight"])
        assertEquals(false, d["highlight_fill_background"])
        assertEquals(true, d["vibrate_on_mentions"])
        assertEquals(false, d["sound_on_mentions"])
        assertEquals(false, d["notify_on_mentions"])
        assertEquals(false, d["nick_detection_enabled"])
        assertEquals(false, d["chat_randomize_name_color_on_send"])
        assertEquals(1500L, d["chat_refresh_rate"])
    }

    @Test
    fun keys_neverOverlapWithProtectedNonChatPreferences() {
        val protectedKeys = setOf(
            // Auth & multi-account
            "auth_token",
            "logged_in_username",
            "logged_in_profile_pic",
            "logged_in_user_id",
            "logged_in_slug",
            "logged_in_chat_color",
            "accounts_json",
            "active_account_id",
            "saved_cookies",
            "xsrf_token",
            "user_json",
            "saved_blerp_cookies",
            // Proxy
            "proxy_enabled",
            "proxy_type",
            "proxy_scope",
            "proxy_host",
            "proxy_port",
            "proxy_username",
            "proxy_password",
            // Video / player
            "player_engine",
            "mobile_quality_limit",
            "quality_limit",
            "dynamic_quality_enabled",
            "fullscreen_sensor_rotation",
            "draw_under_cutout",
            "fullscreen_resize_mode",
            "auto_pip_enabled",
            "background_audio_enabled",
            // App / UI / history / battery
            "language",
            "app_font",
            "animations_enabled",
            "blocked_categories",
            "stream_languages",
            "trusted_domains",
            "low_battery_mode_enabled",
            "battery_saver_limit_quality",
            "battery_saver_slow_timers",
            "battery_saver_disable_shimmer",
            "battery_saver_pause_chat_pip",
            "recent_emote_ids",
            "vod_watch_history",
            "search_history"
        )

        val overlap = ChatSettingsDefaults.keys.intersect(protectedKeys)
        assertTrue("Protected non-chat keys must never be reset: $overlap", overlap.isEmpty())
    }

    @Test
    fun applyToMap_resetsCustomizedChatSettingsWhilePreservingProtectedEntries() {
        val store = mutableMapOf<String, Any?>(
            // Customized chat settings
            "chat_text_size" to 24f,
            "chat_emote_size" to 2.6f,
            "chat_message_scale" to 165,
            "chat_message_spacing_dp" to 12,
            "side_chat_width_percent" to 55,
            "transparent_chat_overlay_enabled" to true,
            "chat_overlay_opacity_percent" to 15,
            "floating_chat_overlay_enabled" to true,
            "chat_overlay_edge_margin_percent" to 25,
            "floating_chat_height_percent" to 45,
            "floating_chat_x_percent" to 0.15f,
            "floating_chat_y_percent" to 0.85f,
            "chat_message_animation" to "flip",
            "chat_refresh_rate" to 200L,
            "highlight_own_messages" to false,
            "chat_randomize_name_color_on_send" to true,
            // Protected entries
            "auth_token" to "secret-token",
            "proxy_host" to "127.0.0.1",
            "language" to "ru",
            "draw_under_cutout" to false,
            "fullscreen_resize_mode" to "ZOOM",
            "blocked_categories" to setOf("Pools, Hot Tubs & Beaches"),
            "vod_watch_history" to "[{\"videoId\":\"123\"}]"
        )

        ChatSettingsDefaults.applyToMap(store)

        // All chat settings restored to defaults
        ChatSettingsDefaults.defaults.forEach { (key, expected) ->
            assertEquals("Key $key should be restored to default", expected, store[key])
        }

        // Protected entries untouched
        assertEquals("secret-token", store["auth_token"])
        assertEquals("127.0.0.1", store["proxy_host"])
        assertEquals("ru", store["language"])
        assertEquals(false, store["draw_under_cutout"])
        assertEquals("ZOOM", store["fullscreen_resize_mode"])
        assertEquals(setOf("Pools, Hot Tubs & Beaches"), store["blocked_categories"])
        assertEquals("[{\"videoId\":\"123\"}]", store["vod_watch_history"])
    }

    @Test
    fun clampNormalizedFloat_handlesFiniteOutOfRangeAndNonFiniteValues() {
        assertEquals(0f, ChatSettingsDefaults.clampNormalizedFloat(-0.5f, 1.0f), 0.0001f)
        assertEquals(1f, ChatSettingsDefaults.clampNormalizedFloat(1.75f, 0.5f), 0.0001f)
        assertEquals(0.42f, ChatSettingsDefaults.clampNormalizedFloat(0.42f, 0.5f), 0.0001f)
        assertEquals(1.0f, ChatSettingsDefaults.clampNormalizedFloat(Float.NaN, 1.0f), 0.0001f)
        assertEquals(0.5f, ChatSettingsDefaults.clampNormalizedFloat(Float.POSITIVE_INFINITY, 0.5f), 0.0001f)
        assertEquals(0.5f, ChatSettingsDefaults.clampNormalizedFloat(Float.NEGATIVE_INFINITY, 0.5f), 0.0001f)
    }

    @Test
    fun floatingChatCompanionConstants_haveExpectedBoundsAndDefaults() {
        assertEquals(0, AppPreferences.CHAT_OVERLAY_EDGE_MARGIN_MIN)
        assertEquals(30, AppPreferences.CHAT_OVERLAY_EDGE_MARGIN_MAX)
        assertEquals(0, AppPreferences.CHAT_OVERLAY_EDGE_MARGIN_DEFAULT)
        assertEquals(35, AppPreferences.FLOATING_CHAT_HEIGHT_MIN)
        assertEquals(100, AppPreferences.FLOATING_CHAT_HEIGHT_MAX)
        assertEquals(75, AppPreferences.FLOATING_CHAT_HEIGHT_DEFAULT)
        assertEquals(1.0f, AppPreferences.FLOATING_CHAT_X_DEFAULT, 0.0001f)
        assertEquals(0.5f, AppPreferences.FLOATING_CHAT_Y_DEFAULT, 0.0001f)
        assertFalse(ChatSettingsDefaults.FLOATING_CHAT_OVERLAY_ENABLED)
    }
}
