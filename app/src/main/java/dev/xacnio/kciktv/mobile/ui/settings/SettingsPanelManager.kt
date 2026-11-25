/**
 * File: SettingsPanelManager.kt
 *
 * Description: Manages business logic, state, and UI interactions for Settings Panel.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.xacnio.kciktv.BuildConfig
import dev.xacnio.kciktv.mobile.MobilePlayerActivity
import dev.xacnio.kciktv.mobile.ui.player.VideoCutoutScalingPolicy
import dev.xacnio.kciktv.mobile.ui.player.VideoScaleMode
import dev.xacnio.kciktv.R
import dev.xacnio.kciktv.shared.data.prefs.AppPreferences
import dev.xacnio.kciktv.shared.data.repository.UpdateReleaseSelector
import dev.xacnio.kciktv.shared.data.repository.UpdateRepository
import dev.xacnio.kciktv.shared.data.repository.UpdateSource
import dev.xacnio.kciktv.databinding.ActivityMobilePlayerBinding
import dev.xacnio.kciktv.shared.util.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.xacnio.kciktv.mobile.InternalBrowserSheet
import dev.xacnio.kciktv.shared.data.api.RetrofitClient
import dev.xacnio.kciktv.shared.ui.font.FontManager
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages the settings panel UI as a bottom sheet with category navigation.
 */
class SettingsPanelManager(
    private val activity: MobilePlayerActivity,
    private val binding: ActivityMobilePlayerBinding,
    private val prefs: AppPreferences,
    private val updateRepository: UpdateRepository
) {
    var isSettingsVisible = false
        private set

    private var pendingInstallApkFile: File? = null
    
    private var currentDialog: BottomSheetDialog? = null
    private var detailDialog: BottomSheetDialog? = null

    /**
     * Shows the settings bottom sheet with categories.
     */
    fun showSettingsPanel() {
        val dialog = BottomSheetDialog(activity, R.style.BottomSheetDialogTheme)
        currentDialog = dialog
        
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_settings_categories, null)
        dialog.setContentView(view)
        
        // Configure bottom sheet behavior
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            it.setBackgroundResource(R.drawable.bg_bottom_sheet)
        }
        
        // Setup category click listeners
        view.findViewById<View>(R.id.categoryPlayer)?.setOnClickListener {
            showPlayerSettings()
        }
        
        view.findViewById<View>(R.id.categoryAppearance)?.setOnClickListener {
            showAppearanceSettings()
        }
        
        view.findViewById<View>(R.id.categoryThirdParty)?.setOnClickListener {
            showThirdPartySettings()
        }
        
        view.findViewById<View>(R.id.categoryApp)?.setOnClickListener {
            showAppSettings()
        }

        view.findViewById<View>(R.id.categoryAdvanced)?.setOnClickListener {
            showAdvancedSettings()
        }

        if (dev.xacnio.kciktv.BuildConfig.DEBUG) {
            view.findViewById<View>(R.id.categoryDeveloper)?.visibility = View.VISIBLE
            view.findViewById<View>(R.id.categoryDeveloper)?.setOnClickListener {
                showDeveloperSettings()
            }
        }

        dialog.setOnDismissListener {
            isSettingsVisible = false
            currentDialog = null
        }
        
        dialog.show()
        isSettingsVisible = true
    }
    
    /**
     * Hides the settings panel.
     */
    fun hideSettingsPanel() {
        currentDialog?.dismiss()
        detailDialog?.dismiss()
        isSettingsVisible = false
    }
    
    // === Category Detail Screens ===
    
    private fun showPlayerSettings() {
        showDetailSheet(activity.getString(R.string.settings_category_player)) { container ->
            // Quality Setting
            addSettingItem(container, R.drawable.ic_hd, 
                activity.getString(R.string.setting_mobile_quality),
                getQualityLimitValue()) {
                showQualityLimitDialog()
            }
            
            // Dynamic Quality Toggle
            addToggleSetting(container, R.drawable.ic_settings,
                activity.getString(R.string.setting_dynamic_quality),
                activity.getString(R.string.dynamic_quality_desc),
                prefs.dynamicQualityEnabled) { isEnabled ->
                prefs.dynamicQualityEnabled = isEnabled
            }
            
            // Background Audio Toggle
            addToggleSetting(container, R.drawable.ic_headphones,
                activity.getString(R.string.setting_background_audio),
                if (prefs.backgroundAudioEnabled) activity.getString(R.string.on) else activity.getString(R.string.off),
                prefs.backgroundAudioEnabled) { isEnabled ->
                prefs.backgroundAudioEnabled = isEnabled
            }

            // Fullscreen Sensor Rotation Toggle
            addToggleSetting(container, R.drawable.ic_fullscreen,
                activity.getString(R.string.setting_fullscreen_sensor_rotation),
                activity.getString(R.string.setting_fullscreen_sensor_rotation_desc),
                prefs.fullscreenSensorRotation) { isEnabled ->
                prefs.fullscreenSensorRotation = isEnabled
                activity.fullscreenToggleManager.applyFullscreenOrientationSetting()
            }

            // Draw Under Camera Cutout Toggle
            addToggleSetting(container, R.drawable.ic_fullscreen,
                activity.getString(R.string.setting_draw_under_cutout),
                activity.getString(R.string.setting_draw_under_cutout_desc),
                prefs.drawUnderCutout) { isEnabled ->
                prefs.drawUnderCutout = isEnabled
                activity.fullscreenToggleManager.applyDisplayCutoutMode()
            }

            // Fullscreen Video Scaling Mode
            addSettingItem(container, R.drawable.ic_video_scale,
                activity.getString(R.string.setting_fullscreen_scale_mode),
                getFullscreenScaleModeValue()) {
                showFullscreenScaleModeDialog()
            }
        }
    }
    
    private fun showAppearanceSettings() {
        showDetailSheet(activity.getString(R.string.settings_category_appearance)) { container ->
            // App Language
            addSettingItem(container, R.drawable.ic_language,
                activity.getString(R.string.setting_app_language),
                getAppLanguageValue()) {
                showAppLanguageDialog()
            }

            // App Font
            addSettingItem(container, R.drawable.ic_text_fields,
                activity.getString(R.string.setting_app_font),
                getAppFontValue()) {
                showFontPickerSheet()
            }
        }
    }
    
    private fun showThirdPartySettings() {
        showDetailSheet(activity.getString(R.string.settings_category_third_party)) { container ->
            // Blerp Toggle
            addToggleSetting(container, R.drawable.ic_blerp,
                activity.getString(R.string.blerp_title),
                activity.getString(R.string.blerp_desc),
                prefs.blerpEnabled) { isEnabled ->
                prefs.blerpEnabled = isEnabled
                // Update button visibility immediately
                if (isEnabled && !activity.currentBlerpUrl.isNullOrEmpty()) {
                    binding.blerpButtonContainer.visibility = View.VISIBLE
                    activity.ensureBlerpEarnTicker()
                } else {
                    binding.blerpButtonContainer.visibility = View.GONE
                    activity.stopBlerpEarnTicker()
                }
            }
        }
    }
    
    private fun showAppSettings() {
        showDetailSheet(activity.getString(R.string.settings_category_app)) { container ->
            // Auto-update Toggle
            addToggleSetting(container, R.drawable.ic_update,
                activity.getString(R.string.setting_auto_update),
                activity.getString(R.string.setting_auto_update_desc),
                prefs.autoUpdateEnabled) { isEnabled ->
                prefs.autoUpdateEnabled = isEnabled
            }

            // Update Source
            val updateSource = UpdateSource.fromKey(prefs.updateSource)
            addSettingItem(container, R.drawable.ic_settings,
                activity.getString(R.string.setting_update_source),
                getUpdateSourceDisplayName(updateSource)) {
                showUpdateSourceDialog()
            }

            // Update Channel
            val channelSubtitle = if (prefs.updateChannel == "beta") 
                activity.getString(R.string.beta) 
            else 
                activity.getString(R.string.stable)
                
            addSettingItem(container, R.drawable.ic_settings,
                activity.getString(R.string.setting_update_channel),
                channelSubtitle) {
                showUpdateChannelDialog()
            }

            // Check for Updates
            addSettingItem(container, R.drawable.ic_update,
                activity.getString(R.string.check_update), null) {
                checkForUpdates()
            }
            
            // What's New
            addSettingItem(container, null,
                activity.getString(R.string.setting_whats_new), null,
                emojiIcon = "🎉") {
                WhatsNewSheetManager(activity, updateRepository).show()
            }
            
            // Privacy Policy
            addSettingItem(container, R.drawable.ic_security,
                activity.getString(R.string.privacy_policy), null) {
                val policyUrl = "https://github.com/szanko02/kcik-tv-app#readme"
                dev.xacnio.kciktv.mobile.InternalBrowserSheet.newInstance(policyUrl).show(activity.supportFragmentManager, "PrivacyPolicy")
                hideSettingsPanel()
            }
            
            // About
            addSettingItem(container, R.drawable.ic_info,
                activity.getString(R.string.setting_about),
                "v${getVersionName()}") {
                activity.showAboutDialog()
            }
        }
    }

    private fun showAdvancedSettings() {
        showDetailSheet(activity.getString(R.string.settings_category_advanced)) { container ->
            // Kick WebView
            addSettingItem(container, null,
                activity.getString(R.string.settings_kick_webview),
                activity.getString(R.string.settings_kick_webview_desc),
                emojiIcon = "🌐") {
                
                val sheet = dev.xacnio.kciktv.mobile.InternalBrowserSheet.newInstance()
                sheet.show(activity.supportFragmentManager, "InternalBrowserSheet")
                hideSettingsPanel()
            }

            // Trusted Domains
            addSettingItem(container, null,
                activity.getString(R.string.settings_trusted_domains),
                activity.getString(R.string.trusted_domains_count, prefs.trustedDomains.size),
                emojiIcon = "🔗") {
                showTrustedDomainsSheet()
            }
            
            // Clear Cache
            addSettingItem(container, null,
                activity.getString(R.string.settings_clear_cache),
                activity.getString(R.string.settings_clear_cache_desc),
                emojiIcon = "🗑️") {
                clearAppCache()
            }
            
            // Clear Blerp Auth
            addSettingItem(container, null,
                activity.getString(R.string.settings_clear_blerp_auth),
                activity.getString(R.string.settings_clear_blerp_auth_desc),
                emojiIcon = "🔐") {
                clearBlerpAuth()
            }

            // Proxy
            addSettingItem(container, null,
                activity.getString(R.string.settings_proxy),
                getProxyStatusValue(),
                emojiIcon = "🛡️") {
                showProxySettingsSheet()
            }
        }
    }

    private fun getProxyStatusValue(): String {
        return if (prefs.proxyEnabled && prefs.proxyHost.isNotBlank() && prefs.proxyPort in 1..65535) {
            val typeLabel = if (prefs.proxyType == dev.xacnio.kciktv.shared.data.api.ProxyConfig.TYPE_SOCKS5) "SOCKS5" else "HTTP"
            activity.getString(R.string.proxy_status_enabled, typeLabel, prefs.proxyHost, prefs.proxyPort)
        } else {
            activity.getString(R.string.proxy_status_disabled)
        }
    }

    private fun refreshProxySheet() {
        detailDialog?.dismiss()
        showProxySettingsSheet()
    }

    private fun showProxySettingsSheet() {
        showDetailSheet(activity.getString(R.string.settings_proxy)) { container ->
            // Enable Toggle
            addToggleSetting(container, R.drawable.ic_security,
                activity.getString(R.string.proxy_enable),
                activity.getString(R.string.proxy_enable_desc),
                prefs.proxyEnabled) { isEnabled ->
                prefs.proxyEnabled = isEnabled
                dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
            }

            // Protocol
            val typeLabel = if (prefs.proxyType == dev.xacnio.kciktv.shared.data.api.ProxyConfig.TYPE_SOCKS5)
                activity.getString(R.string.proxy_protocol_socks5)
            else
                activity.getString(R.string.proxy_protocol_http)

            addSettingItem(container, null,
                activity.getString(R.string.proxy_protocol),
                typeLabel,
                emojiIcon = "🔀") {
                showProxyProtocolDialog()
            }

            // Scope
            val scopeLabel = if (prefs.proxyScope == dev.xacnio.kciktv.shared.data.api.ProxyConfig.SCOPE_ALL)
                activity.getString(R.string.proxy_scope_all)
            else
                activity.getString(R.string.proxy_scope_critical)

            addSettingItem(container, null,
                activity.getString(R.string.proxy_scope),
                scopeLabel,
                emojiIcon = "🎯") {
                showProxyScopeDialog()
            }

            TextView(activity).apply {
                text = activity.getString(R.string.proxy_scope_desc)
                textSize = 12f
                setTextColor(0xFF888888.toInt())
                val density = activity.resources.displayMetrics.density
                setPadding((20 * density).toInt(), (2 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
                container.addView(this)
            }

            // Host
            addSettingItem(container, null,
                activity.getString(R.string.proxy_host),
                prefs.proxyHost.ifBlank { activity.getString(R.string.proxy_not_set) },
                emojiIcon = "🌐") {
                showProxyTextInputDialog(
                    title = activity.getString(R.string.proxy_host),
                    hint = activity.getString(R.string.proxy_host_hint),
                    initialValue = prefs.proxyHost,
                    isPassword = false
                ) { value ->
                    val host = value.trim()
                        .removePrefix("http://")
                        .removePrefix("https://")
                        .removePrefix("socks5://")
                        .trimEnd('/')
                    if (host.isBlank()) {
                        Toast.makeText(activity, R.string.proxy_invalid_host, Toast.LENGTH_SHORT).show()
                        return@showProxyTextInputDialog
                    }
                    prefs.proxyHost = host
                    dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
                    refreshProxySheet()
                }
            }

            // Port
            addSettingItem(container, null,
                activity.getString(R.string.proxy_port),
                if (prefs.proxyPort in 1..65535) prefs.proxyPort.toString() else activity.getString(R.string.proxy_not_set),
                emojiIcon = "🔢") {
                showProxyTextInputDialog(
                    title = activity.getString(R.string.proxy_port),
                    hint = activity.getString(R.string.proxy_port_hint),
                    initialValue = if (prefs.proxyPort > 0) prefs.proxyPort.toString() else "",
                    isPassword = false,
                    isNumeric = true
                ) { value ->
                    val port = value.trim().toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        Toast.makeText(activity, R.string.proxy_invalid_port, Toast.LENGTH_SHORT).show()
                        return@showProxyTextInputDialog
                    }
                    prefs.proxyPort = port
                    dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
                    refreshProxySheet()
                }
            }

            // Username (optional)
            addSettingItem(container, null,
                activity.getString(R.string.proxy_username),
                prefs.proxyUsername?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.proxy_username_desc),
                emojiIcon = "👤") {
                showProxyTextInputDialog(
                    title = activity.getString(R.string.proxy_username),
                    hint = activity.getString(R.string.proxy_username),
                    initialValue = prefs.proxyUsername ?: "",
                    isPassword = false
                ) { value ->
                    prefs.proxyUsername = value.trim().ifBlank { null }
                    dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
                    refreshProxySheet()
                }
            }

            // Password (optional)
            addSettingItem(container, null,
                activity.getString(R.string.proxy_password),
                if (!prefs.proxyPassword.isNullOrBlank()) activity.getString(R.string.proxy_password_set) else activity.getString(R.string.proxy_password_desc),
                emojiIcon = "🔑") {
                showProxyTextInputDialog(
                    title = activity.getString(R.string.proxy_password),
                    hint = activity.getString(R.string.proxy_password),
                    initialValue = prefs.proxyPassword ?: "",
                    isPassword = true
                ) { value ->
                    prefs.proxyPassword = value.ifBlank { null }
                    dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
                    refreshProxySheet()
                }
            }

            // Test connection
            addSettingItem(container, null,
                activity.getString(R.string.proxy_test),
                null,
                emojiIcon = "🧪") {
                testProxyConnection()
            }
        }
    }

    private fun testProxyConnection() {
        val host = prefs.proxyHost.trim()
        val port = prefs.proxyPort
        if (host.isBlank() || port !in 1..65535) {
            Toast.makeText(activity, R.string.proxy_invalid_host, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(activity, R.string.proxy_testing, Toast.LENGTH_SHORT).show()

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val proxyType = if (prefs.proxyType == dev.xacnio.kciktv.shared.data.api.ProxyConfig.TYPE_SOCKS5) {
                java.net.Proxy.Type.SOCKS
            } else {
                java.net.Proxy.Type.HTTP
            }
            val proxy = java.net.Proxy(proxyType, java.net.InetSocketAddress.createUnresolved(host, port))
            val testClient = dev.xacnio.kciktv.shared.data.api.RetrofitClient.buildProxyTestClient(proxy)

            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("https://kick.com/")
                    .head()
                    .build()
                testClient.newCall(request).execute().use { response ->
                    val elapsedMs = System.currentTimeMillis() - startTime
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful || response.code in 200..499) {
                            Toast.makeText(activity, activity.getString(R.string.proxy_test_success, elapsedMs), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(activity, activity.getString(R.string.proxy_test_failed, "HTTP ${response.code}"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, activity.getString(R.string.proxy_test_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                }
            } finally {
                testClient.connectionPool.evictAll()
                testClient.dispatcher.executorService.shutdown()
            }
        }
    }

    private fun showProxyProtocolDialog() {
        val options = arrayOf(
            activity.getString(R.string.proxy_protocol_socks5),
            activity.getString(R.string.proxy_protocol_http)
        )
        val values = arrayOf(
            dev.xacnio.kciktv.shared.data.api.ProxyConfig.TYPE_SOCKS5,
            dev.xacnio.kciktv.shared.data.api.ProxyConfig.TYPE_HTTP
        )
        val currentIndex = values.indexOf(prefs.proxyType).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.proxy_protocol)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.proxyType = values[which]
                dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
                dialog.dismiss()
                refreshProxySheet()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showProxyScopeDialog() {
        val options = arrayOf(
            activity.getString(R.string.proxy_scope_critical),
            activity.getString(R.string.proxy_scope_all)
        )
        val values = arrayOf(
            dev.xacnio.kciktv.shared.data.api.ProxyConfig.SCOPE_CRITICAL,
            dev.xacnio.kciktv.shared.data.api.ProxyConfig.SCOPE_ALL
        )
        val currentIndex = values.indexOf(prefs.proxyScope).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.proxy_scope)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.proxyScope = values[which]
                dev.xacnio.kciktv.shared.data.api.RetrofitClient.refreshProxy(prefs)
                dialog.dismiss()
                refreshProxySheet()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showProxyTextInputDialog(
        title: String,
        hint: String,
        initialValue: String,
        isPassword: Boolean,
        isNumeric: Boolean = false,
        onSave: (String) -> Unit
    ) {
        val inputLayout = FrameLayout(activity).apply {
            setPadding(
                (24 * activity.resources.displayMetrics.density).toInt(),
                (16 * activity.resources.displayMetrics.density).toInt(),
                (24 * activity.resources.displayMetrics.density).toInt(),
                0
            )
        }
        val editText = android.widget.EditText(activity).apply {
            this.hint = hint
            setText(initialValue)
            setSingleLine(true)
            inputType = when {
                isPassword -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                isNumeric -> android.text.InputType.TYPE_CLASS_NUMBER
                else -> android.text.InputType.TYPE_TEXT_VARIATION_URI
            }
            setSelection(text.length)
        }
        inputLayout.addView(editText)

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(inputLayout)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                onSave(editText.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTrustedDomainsSheet() {
        showDetailSheet(activity.getString(R.string.settings_trusted_domains)) { container ->
            val domains = prefs.trustedDomains.toSortedSet()

            // Add Domain button
            addSettingItem(container, null,
                activity.getString(R.string.trusted_domain_add_title),
                activity.getString(R.string.settings_trusted_domains_desc),
                emojiIcon = "➕") {
                showAddDomainDialog()
            }

            // Reset to Defaults button
            addSettingItem(container, null,
                activity.getString(R.string.trusted_domains_reset),
                null,
                emojiIcon = "🔄") {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.trusted_domains_reset)
                    .setMessage(R.string.trusted_domains_reset_confirm)
                    .setPositiveButton(R.string.confirm) { dialog, _ ->
                        prefs.resetTrustedDomains()
                        Toast.makeText(activity, R.string.trusted_domains_reset_done, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Refresh the sheet
                        detailDialog?.dismiss()
                        showTrustedDomainsSheet()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }

            // Separator-like spacing via a divider view
            val divider = android.view.View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * activity.resources.displayMetrics.density).toInt()
                ).apply {
                    topMargin = (8 * activity.resources.displayMetrics.density).toInt()
                    bottomMargin = (8 * activity.resources.displayMetrics.density).toInt()
                    marginStart = (16 * activity.resources.displayMetrics.density).toInt()
                    marginEnd = (16 * activity.resources.displayMetrics.density).toInt()
                }
                setBackgroundColor(0x33FFFFFF)
            }
            container.addView(divider)

            // List each domain
            for (domain in domains) {
                addSettingItem(container, null,
                    domain,
                    null,
                    emojiIcon = "🌐") {
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.remove)
                        .setMessage(activity.getString(R.string.trusted_domain_remove_confirm, domain))
                        .setPositiveButton(R.string.remove) { dialog, _ ->
                            prefs.removeTrustedDomain(domain)
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.trusted_domain_removed, domain),
                                Toast.LENGTH_SHORT
                            ).show()
                            dialog.dismiss()
                            // Refresh the sheet
                            detailDialog?.dismiss()
                            showTrustedDomainsSheet()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun showDeveloperSettings() {
        showDetailSheet("Developer") { container ->
            val isMockEnabled = prefs.mockModeEnabled
            addToggleSetting(
                container,
                R.drawable.ic_settings,
                "Mock Data Mode",
                if (isMockEnabled) "Active — restart app to apply" else "Inactive — restart app to apply",
                isMockEnabled
            ) { enabled ->
                prefs.mockModeEnabled = enabled
                dev.xacnio.kciktv.shared.data.mock.MockConfig.enabled = enabled
                Toast.makeText(
                    activity,
                    if (enabled) "Mock mode ON — restart the app" else "Mock mode OFF — restart the app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showAddDomainDialog() {
        val inputLayout = android.widget.FrameLayout(activity).apply {
            setPadding(
                (24 * activity.resources.displayMetrics.density).toInt(),
                (16 * activity.resources.displayMetrics.density).toInt(),
                (24 * activity.resources.displayMetrics.density).toInt(),
                0
            )
        }
        val editText = android.widget.EditText(activity).apply {
            hint = activity.getString(R.string.trusted_domain_add_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        inputLayout.addView(editText)

        AlertDialog.Builder(activity)
            .setTitle(R.string.trusted_domain_add_title)
            .setView(inputLayout)
            .setPositiveButton(R.string.add) { dialog, _ ->
                val domain = editText.text.toString().lowercase().trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .removePrefix("www.")
                    .trimEnd('/')

                if (domain.isBlank() || !domain.contains('.')) {
                    Toast.makeText(activity, R.string.trusted_domain_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (prefs.trustedDomains.contains(domain)) {
                    Toast.makeText(activity, R.string.trusted_domain_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                prefs.addTrustedDomain(domain)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.trusted_domain_added, domain),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                // Refresh the sheet
                detailDialog?.dismiss()
                showTrustedDomainsSheet()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun clearAppCache() {
        try {
            // Clear app cache directory
            activity.cacheDir.deleteRecursively()

            // Clear emote and mention caches stored in filesDir
            activity.filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("emotes_") || file.name.startsWith("mentions_")) {
                    file.delete()
                }
            }

            // Clear WebView cache
            android.webkit.WebView(activity).apply {
                clearCache(true)
                destroy()
            }
            
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.cache_cleared),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("SettingsPanelManager", "Error clearing cache", e)
        }
    }
    
    private fun clearBlerpAuth() {
        try {
            // Clear Blerp cookies
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies { success ->
                android.util.Log.d("SettingsPanelManager", "Cookies cleared: $success")
            }
            cookieManager.flush()
            
            // Dismiss cached Blerp fragment
            activity.cachedBlerpFragment?.let {
                if (it.isAdded) it.dismissAllowingStateLoss()
            }
            activity.cachedBlerpFragment = null
            
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.blerp_auth_cleared),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("SettingsPanelManager", "Error clearing Blerp auth", e)
        }
    }
    
    private fun showDetailSheet(title: String, setupContent: (LinearLayout) -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.BottomSheetDialogTheme)
        detailDialog = dialog
        
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_settings_detail, null)
        dialog.setContentView(view)
        
        // Configure bottom sheet behavior
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            it.setBackgroundResource(R.drawable.bg_bottom_sheet)
        }
        
        // Set title
        view.findViewById<TextView>(R.id.settingsDetailTitle)?.text = title
        
        // Back button
        view.findViewById<View>(R.id.settingsDetailBackButton)?.setOnClickListener {
            dialog.dismiss()
        }
        
        // Setup content
        val container = view.findViewById<LinearLayout>(R.id.settingsDetailContainer)
        setupContent(container)
        
        dialog.setOnDismissListener {
            detailDialog = null
        }
        
        dialog.show()
    }
    
    // === Helper Methods for Building Settings Items ===
    
    private fun addSettingItem(
        container: LinearLayout,
        iconRes: Int?,
        title: String,
        subtitle: String?,
        emojiIcon: String? = null,
        onClick: () -> Unit
    ) {
        val itemView = LayoutInflater.from(activity).inflate(R.layout.item_settings_row, container, false)
        
        val iconView = itemView.findViewById<android.widget.ImageView>(R.id.settingIcon)
        val emojiView = itemView.findViewById<TextView>(R.id.settingEmojiIcon)
        val titleView = itemView.findViewById<TextView>(R.id.settingTitle)
        val subtitleView = itemView.findViewById<TextView>(R.id.settingSubtitle)
        val chevron = itemView.findViewById<android.widget.ImageView>(R.id.settingChevron)
        
        if (emojiIcon != null) {
            iconView?.visibility = View.GONE
            emojiView?.visibility = View.VISIBLE
            emojiView?.text = emojiIcon
        } else if (iconRes != null) {
            iconView?.setImageResource(iconRes)
            iconView?.visibility = View.VISIBLE
            emojiView?.visibility = View.GONE
        } else {
            iconView?.visibility = View.GONE
            emojiView?.visibility = View.GONE
        }
        
        titleView?.text = title
        
        if (subtitle != null) {
            subtitleView?.text = subtitle
            subtitleView?.visibility = View.VISIBLE
        } else {
            subtitleView?.visibility = View.GONE
        }
        
        chevron?.visibility = View.VISIBLE
        
        itemView.setOnClickListener { onClick() }
        container.addView(itemView)
    }
    
    private fun addToggleSetting(
        container: LinearLayout,
        iconRes: Int,
        title: String,
        subtitle: String,
        initialState: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val itemView = LayoutInflater.from(activity).inflate(R.layout.item_settings_toggle, container, false)
        
        val iconView = itemView.findViewById<android.widget.ImageView>(R.id.settingIcon)
        val titleView = itemView.findViewById<TextView>(R.id.settingTitle)
        val subtitleView = itemView.findViewById<TextView>(R.id.settingSubtitle)
        val toggleBg = itemView.findViewById<FrameLayout>(R.id.toggleBackground)
        val toggleThumb = itemView.findViewById<View>(R.id.toggleThumb)
        
        iconView?.setImageResource(iconRes)
        titleView?.text = title
        subtitleView?.text = subtitle
        
        var isEnabled = initialState
        
        fun updateToggleState(animate: Boolean = true) {
            val endX = if (isEnabled) 24f * activity.resources.displayMetrics.density else 0f
            
            if (animate) {
                toggleThumb?.animate()?.translationX(endX)?.setDuration(150)?.start()
            } else {
                toggleThumb?.translationX = endX
            }
            
            if (isEnabled) {
                val onDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 14 * activity.resources.displayMetrics.density
                    setColor(prefs.themeColor)
                }
                toggleBg?.background = onDrawable
            } else {
                toggleBg?.setBackgroundResource(R.drawable.bg_toggle_off)
            }
        }
        
        updateToggleState(false)
        
        itemView.setOnClickListener {
            isEnabled = !isEnabled
            updateToggleState()
            onToggle(isEnabled)
        }
        
        container.addView(itemView)
    }
    
    // === Value Getters ===
    
    private fun getQualityLimitValue(): String {
        return when (prefs.mobileQualityLimit) {
            "none" -> activity.getString(R.string.quality_unlimited)
            "1080p" -> "1080p"
            "720p" -> "720p"
            "480p" -> "480p"
            "360p" -> "360p"
            else -> activity.getString(R.string.quality_unlimited)
        }
    }
    
    private fun getAppLanguageValue(): String {
        val savedLangRaw = prefs.languageRaw
        return when (savedLangRaw) {
            "system" -> activity.getString(R.string.system_default)
            else -> SupportedLanguages.getDisplayName(savedLangRaw)
        }
    }

    private fun getAppFontValue(): String {
        val fontId = prefs.appFont
        return FontManager.getOption(fontId)?.displayName
            ?: activity.getString(R.string.font_default)
    }
    
    private fun getVersionName(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
    }
    
    private fun getChatAnimationValue(): String {
        return when (prefs.chatMessageAnimation) {
            "none" -> activity.getString(R.string.animation_none)
            "fade_in" -> activity.getString(R.string.animation_fade_in)
            "slide_left" -> activity.getString(R.string.animation_slide_left)
            "slide_right" -> activity.getString(R.string.animation_slide_right)
            "slide_bottom" -> activity.getString(R.string.animation_slide_bottom)
            "scale" -> activity.getString(R.string.animation_scale)
            "typewriter" -> activity.getString(R.string.animation_typewriter)
            "curtain" -> activity.getString(R.string.animation_curtain)
            "flip" -> activity.getString(R.string.animation_flip)
            else -> activity.getString(R.string.animation_none)
        }
    }
    
    // === Dialogs ===
    
    private fun showQualityLimitDialog() {
        val qualities = arrayOf(activity.getString(R.string.quality_unlimited), "1080p", "720p", "480p", "360p")
        val qualityValues = arrayOf("none", "1080p", "720p", "480p", "360p")
        val currentIndex = qualityValues.indexOf(prefs.mobileQualityLimit).takeIf { it >= 0 } ?: 0
        
        AlertDialog.Builder(activity)
            .setTitle(R.string.setting_mobile_quality)
            .setSingleChoiceItems(qualities, currentIndex) { dialog, which ->
                prefs.mobileQualityLimit = qualityValues[which]
                activity.checkAndApplyQualityLimit()
                dialog.dismiss()
                // Refresh detail sheet
                detailDialog?.dismiss()
                showPlayerSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getFullscreenScaleModeValue(): String {
        return when (VideoCutoutScalingPolicy.fromPref(prefs.fullscreenResizeMode)) {
            VideoScaleMode.FIT -> activity.getString(R.string.video_scale_fit)
            VideoScaleMode.ZOOM -> activity.getString(R.string.video_scale_zoom)
            VideoScaleMode.FILL -> activity.getString(R.string.video_scale_fill)
        }
    }

    private fun showFullscreenScaleModeDialog() {
        val options = arrayOf(
            activity.getString(R.string.video_scale_fit),
            activity.getString(R.string.video_scale_zoom),
            activity.getString(R.string.video_scale_fill)
        )
        val modes = arrayOf(VideoScaleMode.FIT, VideoScaleMode.ZOOM, VideoScaleMode.FILL)
        val currentMode = VideoCutoutScalingPolicy.fromPref(prefs.fullscreenResizeMode)
        val currentIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.setting_fullscreen_scale_mode)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.fullscreenResizeMode = modes[which].prefValue
                activity.fullscreenToggleManager.applyEffectiveVideoResizeMode()
                dialog.dismiss()
                detailDialog?.dismiss()
                showPlayerSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showFontPickerSheet() {
        showDetailSheet(activity.getString(R.string.setting_app_font)) { container ->
            val density = activity.resources.displayMetrics.density

            fun addSectionHeader(title: String) {
                val header = TextView(activity).apply {
                    textSize = 11f
                    setTextColor(0xFF888888.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (12 * density).toInt()
                        bottomMargin = (4 * density).toInt()
                        marginStart = (20 * density).toInt()
                    }
                    text = title.uppercase()
                }
                container.addView(header)
            }

            fun addFontItem(opt: dev.xacnio.kciktv.shared.ui.font.FontOption) {
                val isGoogleFont = !opt.isSystem
                val itemView = LayoutInflater.from(activity)
                    .inflate(R.layout.item_font_option, container, false)
                val previewText  = itemView.findViewById<TextView>(R.id.fontPreview)
                val nameText     = itemView.findViewById<TextView>(R.id.fontName)
                val sourceText   = itemView.findViewById<TextView>(R.id.fontSource)
                val spinner      = itemView.findViewById<ProgressBar>(R.id.fontLoadingSpinner)
                val statusIcon   = itemView.findViewById<ImageView>(R.id.fontStatusIcon)
                val deleteButton = itemView.findViewById<ImageView>(R.id.fontDeleteButton)

                nameText.text = opt.displayName
                sourceText.text = if (isGoogleFont)
                    activity.getString(R.string.font_source_google)
                else
                    activity.getString(R.string.font_source_system)

                val fullTypeface: android.graphics.Typeface? = if (!isGoogleFont) {
                    FontManager.getSystemTypeface(opt.id)
                } else {
                    FontManager.getCachedGoogleFont(activity, opt.id)
                }
                var previewTypeface: android.graphics.Typeface? = fullTypeface
                    ?: if (isGoogleFont) FontManager.getCachedPreviewTypeface(activity, opt.id) else null

                fun applyPreview(tf: android.graphics.Typeface) {
                    previewText.typeface = tf
                    nameText.typeface = tf
                }
                previewTypeface?.let { applyPreview(it) }

                fun refreshStatus() {
                    val isSelected = opt.id == prefs.appFont
                    val isFullCached = isGoogleFont && FontManager.isGoogleFontCached(activity, opt.id)
                    when {
                        isSelected -> {
                            statusIcon.setImageResource(R.drawable.ic_check)
                            statusIcon.imageTintList = ColorStateList.valueOf(0xFF53FC18.toInt())
                            statusIcon.visibility = View.VISIBLE
                            deleteButton.visibility = View.GONE
                        }
                        isGoogleFont && isFullCached -> {
                            statusIcon.visibility = View.GONE
                            deleteButton.visibility = View.VISIBLE
                        }
                        isGoogleFont -> {
                            statusIcon.setImageResource(R.drawable.ic_download)
                            statusIcon.imageTintList = ColorStateList.valueOf(0xFF888888.toInt())
                            statusIcon.visibility = View.VISIBLE
                            deleteButton.visibility = View.GONE
                        }
                        else -> {
                            statusIcon.visibility = View.GONE
                            deleteButton.visibility = View.GONE
                        }
                    }
                }
                refreshStatus()

                // Auto-download subset preview if not yet cached
                if (isGoogleFont && previewTypeface == null) {
                    FontManager.downloadGoogleFontPreview(activity, opt.id) { tf ->
                        if (tf != null && previewTypeface == null) {
                            previewTypeface = tf
                            applyPreview(tf)
                        }
                    }
                }

                deleteButton.setOnClickListener {
                    if (opt.id == prefs.appFont) {
                        // Switch back to default before deleting
                        prefs.appFont = "default"
                        activity.currentAppTypeface = null
                        FontManager.applyToView(activity.binding.root, android.graphics.Typeface.DEFAULT)
                        activity.chatAdapter.setAppTypeface(null)
                    }
                    FontManager.deleteGoogleFont(activity, opt.id)
                    previewTypeface = null
                    previewText.typeface = android.graphics.Typeface.DEFAULT
                    nameText.typeface = android.graphics.Typeface.DEFAULT
                    refreshStatus()
                    // Re-trigger background preview download
                    FontManager.downloadGoogleFontPreview(activity, opt.id) { tf ->
                        if (tf != null) {
                            previewTypeface = tf
                            applyPreview(tf)
                        }
                    }
                }

                itemView.setOnClickListener {
                    when {
                        opt.id == prefs.appFont -> {
                            detailDialog?.dismiss()
                            showAppearanceSettings()
                        }
                        fullTypeface != null -> {
                            prefs.appFont = opt.id
                            applyFontToActivity(fullTypeface)
                            detailDialog?.dismiss()
                            showAppearanceSettings()
                        }
                        else -> {
                            statusIcon.visibility = View.GONE
                            deleteButton.visibility = View.GONE
                            spinner.visibility = View.VISIBLE
                            itemView.isClickable = false
                            FontManager.downloadGoogleFont(activity, opt.id) { typeface ->
                                spinner.visibility = View.GONE
                                itemView.isClickable = true
                                if (typeface != null) {
                                    applyPreview(typeface)
                                    prefs.appFont = opt.id
                                    applyFontToActivity(typeface)
                                    detailDialog?.dismiss()
                                    showAppearanceSettings()
                                } else {
                                    refreshStatus()
                                    Toast.makeText(activity, R.string.font_download_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                container.addView(itemView)
            }

            addSectionHeader(activity.getString(R.string.font_section_system))
            FontManager.systemFonts.forEach { addFontItem(it) }

            val googleByCategory = FontManager.googleFonts.groupBy { it.category }
            val categoryLabels = mapOf(
                "sans"    to activity.getString(R.string.font_category_sans),
                "serif"   to activity.getString(R.string.font_category_serif),
                "display" to activity.getString(R.string.font_category_display),
                "mono"    to activity.getString(R.string.font_category_mono)
            )
            listOf("sans", "serif", "display", "mono").forEach { cat ->
                googleByCategory[cat]?.let { fonts ->
                    addSectionHeader(categoryLabels[cat] ?: cat)
                    fonts.forEach { addFontItem(it) }
                }
            }
        }
    }

    private fun applyFontToActivity(typeface: android.graphics.Typeface) {
        // Update factory reference so all future inflations use the new font
        activity.currentAppTypeface = typeface
        // Apply to current view hierarchy immediately
        FontManager.applyToView(activity.binding.root, typeface)
        // Update chat adapter for recycled ViewHolders
        activity.chatAdapter.setAppTypeface(typeface)
    }

    private fun showAppLanguageDialog() {
        val languages = SupportedLanguages.getDisplayNamesForDialog(
            activity, 
            activity.getString(R.string.system_default)
        )
        val locales = SupportedLanguages.getCodesForDialog()
        val currentIndex = SupportedLanguages.getDialogIndex(prefs.languageRaw)
        
        AlertDialog.Builder(activity)
            .setTitle(R.string.setting_app_language)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedLocale = locales[which]
                
                // Save to SharedPreferences
                prefs.language = selectedLocale
                
                val locale = if (selectedLocale == "system") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        android.content.res.Resources.getSystem().configuration.locales.get(0)
                    } else {
                        @Suppress("DEPRECATION")
                        android.content.res.Resources.getSystem().configuration.locale
                    }
                } else {
                    java.util.Locale(selectedLocale)
                }
                
                java.util.Locale.setDefault(locale)
                val config = activity.resources.configuration
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
                
                dialog.dismiss()
                
                // Restart activity to apply language change
                activity.recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun getUpdateSourceDisplayName(source: UpdateSource): String {
        return when (source) {
            UpdateSource.FORK -> activity.getString(R.string.update_source_fork)
            UpdateSource.UPSTREAM -> activity.getString(R.string.update_source_upstream)
        }
    }

    private fun showUpdateSourceDialog() {
        val options = arrayOf(
            "${activity.getString(R.string.update_source_fork)} (szanko02/kcik-tv-app)",
            "${activity.getString(R.string.update_source_upstream)} (xacnio/kcik-tv-app)"
        )
        val values = arrayOf(UpdateSource.FORK.key, UpdateSource.UPSTREAM.key)
        val currentIndex = if (UpdateSource.fromKey(prefs.updateSource) == UpdateSource.UPSTREAM) 1 else 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.setting_update_source)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.updateSource = values[which]
                dialog.dismiss()
                // Refresh detail sheet
                detailDialog?.dismiss()
                showAppSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showUpdateChannelDialog() {
        val options = arrayOf(
            activity.getString(R.string.stable),
            activity.getString(R.string.beta)
        )
        val values = arrayOf("stable", "beta")
        val currentIndex = values.indexOf(prefs.updateChannel).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.setting_update_channel)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.updateChannel = values[which]
                dialog.dismiss()
                // Refresh detail sheet
                detailDialog?.dismiss()
                showAppSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showChatAnimationDialog() {
        val options = arrayOf(
            activity.getString(R.string.animation_none),
            activity.getString(R.string.animation_fade_in),
            activity.getString(R.string.animation_slide_left),
            activity.getString(R.string.animation_slide_right),
            activity.getString(R.string.animation_slide_bottom),
            activity.getString(R.string.animation_scale),
            activity.getString(R.string.animation_typewriter),
            activity.getString(R.string.animation_curtain),
            activity.getString(R.string.animation_flip)
        )
        val values = arrayOf("none", "fade_in", "slide_left", "slide_right", "slide_bottom", "scale", "typewriter", "curtain", "flip")
        val currentIndex = values.indexOf(prefs.chatMessageAnimation).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.setting_chat_animation)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.chatMessageAnimation = values[which]
                // Live-update the chat animator
                try {
                    activity.chatUiManager.updateChatMessageAnimation()
                } catch (_: Exception) { }
                dialog.dismiss()
                // Refresh detail sheet
                detailDialog?.dismiss()
                showAppearanceSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    internal fun checkForUpdates(isSilent: Boolean = false) {
        if (!isSilent) {
            Toast.makeText(activity, R.string.checking_updates, Toast.LENGTH_SHORT).show()
        }

        val source = UpdateSource.fromKey(prefs.updateSource)
        val channel = prefs.updateChannel

        activity.lifecycleScope.launch {
            val result = try {
                updateRepository.getLatestRelease(channel, source, forceRefresh = !isSilent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (activity.isFinishing || activity.isDestroyed) return@launch

            result.onSuccess { release ->
                if (release != null) {
                    val currentVersion = BuildConfig.VERSION_NAME
                    val releaseTag = release.safeTagName
                    val newVersion = UpdateReleaseSelector.normalizeVersion(releaseTag)

                    if (UpdateReleaseSelector.isNewerRelease(currentVersion, releaseTag)) {
                        try {
                            showModernUpdateDialog(release, currentVersion, newVersion, source)
                        } catch (uiError: Exception) {
                            android.util.Log.e("SettingsPanelManager", "Failed to render update dialog for $releaseTag", uiError)
                            if (!isSilent) {
                                val errSummary = summarizeUpdateError(uiError)
                                Toast.makeText(
                                    activity,
                                    "${activity.getString(R.string.update_check_failed)} ($errSummary)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else if (!isSilent) {
                        Toast.makeText(activity, R.string.no_updates, Toast.LENGTH_SHORT).show()
                    }
                } else if (!isSilent) {
                    Toast.makeText(activity, R.string.no_updates, Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                android.util.Log.e(
                    "SettingsPanelManager",
                    "Update check failed (source=${source.key}, channel=$channel, silent=$isSilent)",
                    error
                )
                if (!isSilent) {
                    val errSummary = summarizeUpdateError(error)
                    Toast.makeText(
                        activity,
                        "${activity.getString(R.string.update_check_failed)} ($errSummary)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun summarizeUpdateError(error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        return if (raw.isNotEmpty()) {
            if (raw.length > 90) raw.take(87) + "..." else raw
        } else {
            error.javaClass.simpleName
        }
    }

    private fun showModernUpdateDialog(
        release: dev.xacnio.kciktv.shared.data.model.GithubRelease,
        currentVersion: String,
        newVersion: String,
        source: UpdateSource
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)

        val dialog = AlertDialog.Builder(activity, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Setup views
        val titleView = dialogView.findViewById<TextView>(R.id.updateTitle)
        val versionView = dialogView.findViewById<TextView>(R.id.updateVersion)
        val changelogView = dialogView.findViewById<TextView>(R.id.updateChangelog)
        val btnLater = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdateLater)
        val btnUpdateNow = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdateNow)
        val progressSection = dialogView.findViewById<LinearLayout>(R.id.progressSection)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.updateProgressBar)
        val progressPercent = dialogView.findViewById<TextView>(R.id.progressPercent)
        val downloadSize = dialogView.findViewById<TextView>(R.id.downloadSize)
        val downloadSpeed = dialogView.findViewById<TextView>(R.id.downloadSpeed)

        // Set content
        titleView.text = activity.getString(R.string.update_available_title)
        versionView.text = "${activity.getString(R.string.update_version_format, currentVersion, newVersion)} · ${getUpdateSourceDisplayName(source)}"

        // Parse and format changelog (markdown body from GitHub)
        val changelog = release.safeBody.trim()
        if (changelog.isNotEmpty()) {
            // Simple markdown parsing for bullet points
            val formattedChangelog = changelog
                .replace(Regex("^#+\\s*(.+)$", RegexOption.MULTILINE), "$1")  // Remove markdown headers
                .replace(Regex("^[-*]\\s*", RegexOption.MULTILINE), "• ")     // Convert bullets
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")                     // Remove bold
                .replace(Regex("\\*(.+?)\\*"), "$1")                           // Remove italic
                .trim()
            changelogView.text = formattedChangelog
        } else {
            changelogView.text = activity.getString(R.string.update_available_msg, newVersion)
        }
        dev.xacnio.kciktv.mobile.util.DialogUtils.setupClickableLinks(changelogView)

        // Button actions
        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        var isDownloading = false

        fun setButtonsEnabled(enabled: Boolean) {
            isDownloading = !enabled
            btnUpdateNow.isEnabled = enabled
            btnLater.isEnabled = enabled
            val alpha = if (enabled) 1.0f else 0.5f
            btnUpdateNow.alpha = alpha
            btnLater.alpha = alpha
        }

        btnUpdateNow.setOnClickListener {
            if (isDownloading) return@setOnClickListener

            val asset = release.safeAssets.firstOrNull {
                it.safeName.endsWith(".apk", ignoreCase = true) && it.safeDownloadUrl.isNotBlank()
            }
            if (asset == null) {
                Toast.makeText(activity, activity.getString(R.string.apk_not_found), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setButtonsEnabled(false)
            progressSection.visibility = View.VISIBLE
            progressBar.progress = 0
            progressPercent.text = "0%"
            downloadSize.text = ""
            downloadSpeed.text = ""

            downloadAndInstallApk(asset.safeDownloadUrl, asset.size) { progress, downloadedBytes, totalBytes, speed ->
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    when {
                        progress >= 0 -> {
                            progressBar.progress = progress
                            progressPercent.text = "$progress%"
                            downloadSize.text = if (totalBytes > 0) {
                                "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}"
                            } else {
                                formatFileSize(downloadedBytes)
                            }
                            if (speed > 0) {
                                downloadSpeed.text = "${formatFileSize(speed.toLong())}/s"
                            }
                            if (progress >= 100) {
                                setButtonsEnabled(true)
                            }
                        }
                        progress == -1 -> {
                            // Keep dialog open and re-enable buttons so user can retry immediately
                            setButtonsEnabled(true)
                            progressSection.visibility = View.GONE
                        }
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun downloadAndInstallApk(
        url: String,
        totalSize: Long,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: Float) -> Unit
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val partFile = File(activity.cacheDir, "update.apk.part")
            val apkFile = File(activity.cacheDir, "update.apk")
            try {
                if (partFile.exists()) {
                    partFile.delete()
                }

                val request = Request.Builder().url(url).build()
                RetrofitClient.githubHttpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        throw IOException("HTTP ${response.code}")
                    }

                    val responseLength = body.contentLength()
                    val expectedLength = if (responseLength > 0L) responseLength else totalSize
                    var totalBytesRead = 0L
                    var lastProgressTime = System.currentTimeMillis()
                    var lastBytesRead = 0L
                    var currentSpeed = 0f

                    body.byteStream().use { inputStream ->
                        FileOutputStream(partFile).use { outputStream ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val currentTime = System.currentTimeMillis()
                                val elapsedTime = currentTime - lastProgressTime
                                if (elapsedTime >= 500) {
                                    val bytesInInterval = totalBytesRead - lastBytesRead
                                    currentSpeed = (bytesInInterval * 1000f) / elapsedTime
                                    lastProgressTime = currentTime
                                    lastBytesRead = totalBytesRead
                                }

                                if (expectedLength > 0L) {
                                    val progress = ((totalBytesRead * 100L) / expectedLength).toInt().coerceIn(0, 99)
                                    onProgress(progress, totalBytesRead, expectedLength, currentSpeed)
                                } else {
                                    onProgress(0, totalBytesRead, 0L, currentSpeed)
                                }
                            }
                            outputStream.flush()
                        }
                    }

                    if (totalBytesRead <= 4L) {
                        throw IOException("Downloaded APK is empty")
                    }
                    if (expectedLength > 0L && totalBytesRead != expectedLength) {
                        throw IOException("Incomplete APK ($totalBytesRead / $expectedLength bytes)")
                    }
                    if (!hasZipMagicHeader(partFile)) {
                        throw IOException("Downloaded file is not a valid APK archive")
                    }

                    if (apkFile.exists() && !apkFile.delete()) {
                        android.util.Log.w("SettingsPanelManager", "Failed to delete previous update.apk before replace")
                    }
                    if (!partFile.renameTo(apkFile)) {
                        partFile.copyTo(apkFile, overwrite = true)
                        partFile.delete()
                    }

                    val finalTotal = if (expectedLength > 0L) expectedLength else totalBytesRead
                    withContext(Dispatchers.Main) {
                        onProgress(100, totalBytesRead, finalTotal, currentSpeed)
                        installApk(apkFile)
                    }
                }
            } catch (e: CancellationException) {
                partFile.delete()
                throw e
            } catch (e: Exception) {
                partFile.delete()
                android.util.Log.e("SettingsPanelManager", "APK download failed from $url", e)
                withContext(Dispatchers.Main) {
                    val summary = summarizeUpdateError(e)
                    Toast.makeText(
                        activity,
                        "${activity.getString(R.string.download_failed)}: $summary",
                        Toast.LENGTH_LONG
                    ).show()
                    onProgress(-1, 0, 0, 0f)
                }
            }
        }
    }

    private fun hasZipMagicHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            val header = ByteArray(4)
            val read = FileInputStream(file).use { it.read(header) }
            read == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        } catch (_: Exception) {
            false
        }
    }

    internal fun resumePendingApkInstallIfAllowed() {
        val pendingFile = pendingInstallApkFile ?: return
        if (!pendingFile.exists() || pendingFile.length() <= 4L) {
            pendingInstallApkFile = null
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApkFile = null
            installApk(pendingFile)
        }
    }

    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApkFile = file
            try {
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(settingsIntent)
                return
            } catch (e: Exception) {
                android.util.Log.w("SettingsPanelManager", "Package-scoped unknown sources settings failed, trying generic", e)
                try {
                    activity.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                    return
                } catch (fallbackError: Exception) {
                    android.util.Log.e("SettingsPanelManager", "Unable to open unknown app sources settings", fallbackError)
                }
            }
        }

        pendingInstallApkFile = null
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SettingsPanelManager", "Failed to launch package installer for ${file.absolutePath}", e)
            Toast.makeText(
                activity,
                "${activity.getString(R.string.download_failed)}: ${summarizeUpdateError(e)}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            else -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
        }
    }
}
