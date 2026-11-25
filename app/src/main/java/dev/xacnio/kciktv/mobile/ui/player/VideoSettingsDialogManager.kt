/**
 * File: VideoSettingsDialogManager.kt
 *
 * Description: Manages the Video Settings panel, providing UI for selecting video quality,
 * opening Chat Settings, changing playback speed, and accessing audio settings.
 * Mounted directly on playerScreenContainer so it remains visible even when chat is hidden.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.ivs.player.Quality
import dev.xacnio.kciktv.R
import dev.xacnio.kciktv.mobile.MobilePlayerActivity
import dev.xacnio.kciktv.shared.data.prefs.AppPreferences

class VideoSettingsDialogManager(
    private val activity: MobilePlayerActivity,
    private val prefs: AppPreferences
) {

    class QualityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badge: TextView = view.findViewById(R.id.qualityBadge)
        val label: TextView = view.findViewById(R.id.qualityLabel)
        val bitrate: TextView = view.findViewById(R.id.qualityBitrate)
        val checkmark: ImageView = view.findViewById(R.id.selectedCheck)
    }

    /** Maps a stream's vertical resolution to a short tier badge (FHD/HD/SD/LD). */
    private fun qualityTierLabel(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1080 -> "FHD"
        height >= 720 -> "HD"
        height >= 480 -> "SD"
        else -> "LD"
    }

    private var currentPanelView: View? = null
    private var scrimView: View? = null
    private var isDismissing: Boolean = false
    private var currentPage: Int = PAGE_ROOT
    private var currentDesiredHeightPx: Int = 0
    private var navigateAction: ((Int) -> Unit)? = null
    private var heightAnimator: ValueAnimator? = null
    private var hostContainer: ViewGroup? = null
    private var containerLayoutListener: View.OnLayoutChangeListener? = null

    private companion object {
        const val PAGE_ROOT = 0
        const val PAGE_QUALITY = 1
        const val PAGE_SPEED = 2
        const val ANIM_OPEN_DURATION_MS = 180L
        const val ANIM_DISMISS_DURATION_MS = 150L
        const val ANIM_HEIGHT_DURATION_MS = 200L
        const val SCRIM_ALPHA = 140
        const val FLOATING_CARD_CORNER_DP = 20
        const val PANEL_BACKGROUND_COLOR = 0xFF1A1A1A.toInt()
    }

    fun isPanelShowing(): Boolean = currentPanelView != null && !isDismissing

    /**
     * Handles back navigation while the Video Settings panel is open:
     * pops Quality/Speed sub-pages back to Root, or dismisses the panel from Root.
     * Returns true if the back press was consumed.
     */
    fun handleBackPress(): Boolean {
        if (!isPanelShowing()) return false
        return if (currentPage == PAGE_QUALITY || currentPage == PAGE_SPEED) {
            navigateAction?.invoke(PAGE_ROOT)
            true
        } else {
            dismissPanel()
            true
        }
    }

    fun dismissPanel() {
        val view = currentPanelView ?: return
        if (isDismissing) return
        isDismissing = true

        detachLayoutListener()
        heightAnimator?.cancel()
        heightAnimator = null

        val scrim = scrimView
        scrim?.apply {
            isClickable = false
            isFocusable = false
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(ANIM_DISMISS_DURATION_MS)
                .start()
        }

        val slideOutDistance = (
            view.height.takeIf { it > 0 }
                ?: view.layoutParams?.height?.takeIf { it > 0 }
                ?: dpToPx(240)
            ).toFloat()

        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(slideOutDistance)
            .setDuration(ANIM_DISMISS_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                (scrim?.parent as? ViewGroup)?.removeView(scrim)
                (view.parent as? ViewGroup)?.removeView(view)
                if (currentPanelView === view) {
                    currentPanelView = null
                    scrimView = null
                    isDismissing = false
                    currentPage = PAGE_ROOT
                    navigateAction = null
                    hostContainer = null
                }
            }
            .start()
    }

    internal fun dismissPanelImmediate() {
        cleanupImmediate()
    }

    private fun cleanupImmediate() {
        detachLayoutListener()
        heightAnimator?.cancel()
        heightAnimator = null
        scrimView?.let {
            it.animate().cancel()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentPanelView?.let {
            it.animate().cancel()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentPanelView = null
        scrimView = null
        isDismissing = false
        currentPage = PAGE_ROOT
        navigateAction = null
        hostContainer = null
    }

    private fun detachLayoutListener() {
        val listener = containerLayoutListener
        if (listener != null) {
            hostContainer?.removeOnLayoutChangeListener(listener)
            containerLayoutListener = null
        }
    }

    private fun dpToPx(dp: Int): Int =
        VideoSettingsPanelSizer.dpToPx(dp, activity.resources.displayMetrics.density)

    private fun computeRootHeight(visibleItemCount: Int): Int =
        VideoSettingsPanelSizer.computeRootDesiredHeightPx(
            visibleItemCount,
            activity.resources.displayMetrics.density
        )

    private fun computeSubPageHeight(itemCount: Int): Int =
        VideoSettingsPanelSizer.computeSubPageDesiredHeightPx(
            itemCount,
            activity.resources.displayMetrics.density
        )

    private fun readSafeInsets(container: View): Pair<Int, Int> {
        val insets = ViewCompat.getRootWindowInsets(container)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        return Pair(insets?.top ?: 0, insets?.bottom ?: 0)
    }

    private fun computeMetrics(
        container: View,
        desiredContentHeightPx: Int
    ): VideoSettingsPanelSizer.PanelLayoutMetrics {
        val dm = activity.resources.displayMetrics
        val containerWidth = container.width.takeIf { it > 0 } ?: dm.widthPixels
        val containerHeight = container.height.takeIf { it > 0 } ?: dm.heightPixels
        val (safeTop, safeBottom) = readSafeInsets(container)
        return VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = containerWidth,
            containerHeightPx = containerHeight,
            density = dm.density,
            desiredContentHeightPx = desiredContentHeightPx,
            safeTopInsetPx = safeTop,
            safeBottomInsetPx = safeBottom
        )
    }

    private fun applyPanelBackground(view: View, isFloatingCard: Boolean) {
        if (isFloatingCard) {
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(PANEL_BACKGROUND_COLOR)
                cornerRadius = dpToPx(FLOATING_CARD_CORNER_DP).toFloat()
            }
        } else {
            view.setBackgroundResource(R.drawable.bg_bottom_sheet)
        }
    }

    private fun applyResponsiveLayout(
        view: View,
        container: View,
        desiredContentHeightPx: Int,
        animateHeight: Boolean
    ): VideoSettingsPanelSizer.PanelLayoutMetrics {
        val metrics = computeMetrics(container, desiredContentHeightPx)
        applyPanelBackground(view, metrics.isFloatingCard)
        view.setPadding(0, 0, 0, metrics.bottomPaddingPx)

        val existingLp = view.layoutParams as? ConstraintLayout.LayoutParams
        val previousHeight = existingLp?.height ?: 0
        val lp = existingLp ?: ConstraintLayout.LayoutParams(metrics.widthPx, metrics.heightPx)

        lp.width = metrics.widthPx
        lp.topToTop = ConstraintLayout.LayoutParams.UNSET
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        lp.marginStart = metrics.horizontalMarginPx
        lp.marginEnd = metrics.horizontalMarginPx
        lp.bottomMargin = metrics.bottomMarginPx

        if (animateHeight && previousHeight > 0 && previousHeight != metrics.heightPx) {
            view.layoutParams = lp
            animatePanelToHeight(metrics.heightPx)
        } else {
            heightAnimator?.cancel()
            heightAnimator = null
            lp.height = metrics.heightPx
            view.layoutParams = lp
        }
        return metrics
    }

    private fun animatePanelToHeight(targetH: Int) {
        val view = currentPanelView ?: return
        val startH = view.layoutParams.height
        if (startH == targetH) return
        heightAnimator?.cancel()
        heightAnimator = ValueAnimator.ofInt(startH, targetH).apply {
            duration = ANIM_HEIGHT_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (currentPanelView !== view || isDismissing) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val lp = view.layoutParams
                lp.height = anim.animatedValue as Int
                view.layoutParams = lp
            }
            start()
        }
    }

    @SuppressLint("InflateParams")
    fun showVideoSettingsDialog() {
        // Toggle-dismiss cleanly if already visible; if mid-dismiss, finalize old view before re-opening.
        if (currentPanelView != null) {
            if (!isDismissing) {
                dismissPanel()
                return
            } else {
                cleanupImmediate()
            }
        }

        // Dismiss Chat Settings if currently open and hide player controls behind the sheet.
        if (activity.chatSettingsSheetManager.isPanelShowing()) {
            activity.chatSettingsSheetManager.dismissPanel()
        }
        activity.overlayControlManager.hideOverlay()

        val container = activity.binding.playerScreenContainer
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_video_settings, null)
        currentPanelView = view
        hostContainer = container
        isDismissing = false

        val backButton = view.findViewById<ImageButton>(R.id.videoPanelBackButton)
        val backSpacer = view.findViewById<View>(R.id.videoPanelBackSpacer)
        val titleText = view.findViewById<TextView>(R.id.videoPanelTitleText)
        val btnDismiss = view.findViewById<View>(R.id.btnDismissVideoSettings)

        val mainScrollView = view.findViewById<View>(R.id.videoSettingsMainScroll)
        val qualityView = view.findViewById<View>(R.id.qualitySettingsView)
        val speedView = view.findViewById<View>(R.id.speedSettingsView)

        val playMode = activity.vodManager.currentPlaybackMode
        val isVodOrClip = playMode != VodManager.PlaybackMode.LIVE
        val hasAudio = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P

        // Build quality items upfront so sub-page height matches actual row count.
        val qualities = activity.ivsPlayer?.qualities?.sortedByDescending { it.height } ?: emptyList()
        val realQualities = qualities.filter { it.height > 0 }
        // Not every channel's manifest has a genuine no-video "audio_only" rendition — when it
        // doesn't, synthesize the option (see PlayerManager.resolveAudioOnlyQuality). isAudioOnly
        // is a separate tag rather than inferred from height, since a synthesized entry shares
        // its Quality with a real row above.
        val audioOnlyTarget = activity.playerManager.resolveAudioOnlyQuality()
        val qualityItems = mutableListOf<Triple<Quality?, String, Boolean>>()
        qualityItems.add(Triple(null, activity.getString(R.string.auto_quality), false))
        realQualities.forEach { q -> qualityItems.add(Triple(q, "${q.height}p${q.framerate.toInt()}", false)) }
        audioOnlyTarget?.let { qualityItems.add(Triple(it, activity.getString(R.string.quality_audio_only), true)) }

        var rootItemCount = 4 // Quality, Chat Settings, Refresh, Stats always visible
        if (isVodOrClip) rootItemCount++
        if (hasAudio) rootItemCount++

        fun updateHeader(titleRes: Int, showBack: Boolean) {
            titleText.text = activity.getString(titleRes)
            backButton.visibility = if (showBack) View.VISIBLE else View.GONE
            backSpacer.visibility = if (showBack) View.GONE else View.VISIBLE
        }

        fun navigate(page: Int, animateHeight: Boolean = true) {
            currentPage = page
            when (page) {
                PAGE_ROOT -> {
                    currentDesiredHeightPx = computeRootHeight(rootItemCount)
                    applyResponsiveLayout(view, container, currentDesiredHeightPx, animateHeight)
                    mainScrollView.visibility = View.VISIBLE
                    qualityView.visibility = View.GONE
                    speedView.visibility = View.GONE
                    updateHeader(R.string.settings, false)
                }
                PAGE_QUALITY -> {
                    currentDesiredHeightPx = computeSubPageHeight(qualityItems.size)
                    applyResponsiveLayout(view, container, currentDesiredHeightPx, animateHeight)
                    mainScrollView.visibility = View.GONE
                    qualityView.visibility = View.VISIBLE
                    speedView.visibility = View.GONE
                    updateHeader(R.string.quality_selection, true)
                }
                PAGE_SPEED -> {
                    currentDesiredHeightPx = computeSubPageHeight(6)
                    applyResponsiveLayout(view, container, currentDesiredHeightPx, animateHeight)
                    mainScrollView.visibility = View.GONE
                    qualityView.visibility = View.GONE
                    speedView.visibility = View.VISIBLE
                    updateHeader(R.string.playback_speed_title, true)
                }
            }
        }
        navigateAction = { page -> navigate(page, animateHeight = true) }

        backButton.setOnClickListener {
            if (currentPage == PAGE_QUALITY || currentPage == PAGE_SPEED) navigate(PAGE_ROOT)
        }
        btnDismiss.setOnClickListener { dismissPanel() }

        // Quality value text
        val qualityValueText = view.findViewById<TextView>(R.id.videoSettingQualityValue)
        fun updateQualityValue() {
            val currentQuality = activity.ivsPlayer?.quality
            val isAuto = activity.ivsPlayer?.isAutoQualityMode == true
            val userLimit = activity.userSelectedQualityLimit
            qualityValueText.text = when {
                // Audio-only (genuine or synthesized from the lowest real quality) is always
                // pinned directly rather than used as an ABR cap — reflect that regardless of
                // the dynamic quality setting or which underlying quality it resolved to.
                activity.playerManager.isAudioOnlyActive -> activity.getString(R.string.quality_audio_only)
                prefs.dynamicQualityEnabled -> {
                    if (userLimit != null)
                        activity.getString(R.string.auto_quality_with_limit, userLimit.height)
                    else
                        activity.getString(R.string.auto_quality)
                }
                isAuto -> activity.getString(R.string.auto_quality)
                (currentQuality?.height ?: 0) <= 0 -> activity.getString(R.string.quality_audio_only)
                else -> "${currentQuality?.height}p"
            }
        }
        updateQualityValue()

        view.findViewById<View>(R.id.videoSettingQuality).setOnClickListener { navigate(PAGE_QUALITY) }

        view.findViewById<View>(R.id.videoSettingChatSettings).setOnClickListener {
            dismissPanelImmediate()
            activity.chatSettingsSheetManager.showChatSettingsSheet()
        }

        view.findViewById<View>(R.id.videoSettingRefresh).setOnClickListener {
            dismissPanel()
            activity.refreshCurrentPlayback()
        }

        view.findViewById<View>(R.id.videoSettingStats).setOnClickListener {
            dismissPanel()
            activity.showPlayerStatsSheet()
        }

        // Speed option
        val speedOption = view.findViewById<View>(R.id.videoSettingSpeed)
        val speedValueText = view.findViewById<TextView>(R.id.videoSettingSpeedValue)
        if (isVodOrClip) {
            speedOption.visibility = View.VISIBLE
            val currentSpeed = activity.ivsPlayer?.playbackRate ?: 1.0f
            speedValueText.text = if (currentSpeed == 1.0f) activity.getString(R.string.speed_normal_1x)
                else activity.getString(R.string.speed_x_format, currentSpeed)
            speedOption.setOnClickListener { navigate(PAGE_SPEED) }
        }

        // Audio EQ option
        val audioOption = view.findViewById<View>(R.id.videoSettingAudio)
        if (hasAudio) {
            audioOption.visibility = View.VISIBLE
            audioOption.setOnClickListener {
                dismissPanel()
                activity.customEqDialogManager.showCustomEqDialog()
            }
        }

        // Quality RecyclerView
        val qualityRecycler = view.findViewById<RecyclerView>(R.id.qualityRecyclerView)
        qualityRecycler.layoutManager = LinearLayoutManager(activity)
        qualityRecycler.adapter = object : RecyclerView.Adapter<QualityViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                QualityViewHolder(activity.layoutInflater.inflate(R.layout.item_quality_option, parent, false))

            override fun onBindViewHolder(holder: QualityViewHolder, position: Int) {
                val (q, label, isAudioOnlyItem) = qualityItems[position]
                holder.label.text = label
                val currentQuality = activity.ivsPlayer?.quality
                val isAutoMode = activity.ivsPlayer?.isAutoQualityMode == true
                val isAudioOnlyActive = activity.playerManager.isAudioOnlyActive
                holder.badge.visibility = View.VISIBLE
                if (isAudioOnlyItem) {
                    // A genuine audio_only track (q.height <= 0) has no video, so its bitrate is
                    // meaningful on its own. A synthetic one is really a real video quality with
                    // the picture hidden — make that explicit rather than showing its full bitrate,
                    // which would misleadingly read like a lean audio-only number.
                    holder.bitrate.text = if (q != null && q.height <= 0) {
                        String.format("%.1f Mbps", q.bitrate / 1_000_000.0)
                    } else {
                        activity.getString(R.string.quality_audio_only_synthetic, q?.height ?: 0)
                    }
                    holder.bitrate.visibility = View.VISIBLE
                    holder.badge.text = "AUD"
                } else if (q != null) {
                    holder.bitrate.text = String.format("%.1f Mbps", q.bitrate / 1_000_000.0)
                    holder.bitrate.visibility = View.VISIBLE
                    holder.badge.text = qualityTierLabel(q.height)
                } else {
                    val liveHeight = currentQuality?.height ?: 0
                    holder.bitrate.text = when {
                        currentQuality == null -> ""
                        isAudioOnlyActive -> activity.getString(R.string.quality_audio_only)
                        else -> "${liveHeight}p"
                    }
                    holder.bitrate.visibility = if (isAutoMode) View.VISIBLE else View.GONE
                    holder.badge.text = "AUTO"
                }
                val userLimit = activity.userSelectedQualityLimit
                val isSelected = when {
                    isAudioOnlyItem -> isAudioOnlyActive
                    q == null -> !isAudioOnlyActive && (if (prefs.dynamicQualityEnabled) userLimit == null else isAutoMode)
                    else -> !isAudioOnlyActive && (
                        if (prefs.dynamicQualityEnabled) q.bitrate == userLimit?.bitrate && q.height == userLimit.height
                        else q.bitrate == currentQuality?.bitrate && q.height == currentQuality.height && !isAutoMode
                    )
                }
                holder.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
                holder.checkmark.imageTintList = android.content.res.ColorStateList.valueOf(prefs.themeColor)
                holder.itemView.setOnClickListener {
                    when {
                        isAudioOnlyItem -> {
                            activity.playerManager.updateAudioOnlyVisual(true)
                            val resolved = activity.playerManager.resolveAudioOnlyQuality()
                            if (resolved != null) {
                                activity.userSelectedQualityLimit = resolved
                                activity.ivsPlayer?.isAutoQualityMode = false
                                activity.ivsPlayer?.quality = resolved
                            }
                            activity.analytics.logFeatureUsed("quality_audio_only")
                        }
                        q == null -> {
                            activity.userSelectedQualityLimit = null
                            activity.ivsPlayer?.isAutoQualityMode = true
                            activity.checkAndApplyQualityLimit()
                            activity.analytics.logFeatureUsed("quality_auto")
                            activity.playerManager.updateAudioOnlyVisual(false)
                        }
                        else -> {
                            activity.userSelectedQualityLimit = q
                            activity.analytics.logFeatureUsed("quality_${q.height}p")
                            if (prefs.dynamicQualityEnabled) {
                                activity.ivsPlayer?.isAutoQualityMode = true
                                activity.ivsPlayer?.setAutoMaxQuality(q)
                                activity.checkAndApplyQualityLimit()
                            } else {
                                activity.ivsPlayer?.isAutoQualityMode = false
                                activity.ivsPlayer?.quality = q
                                activity.checkAndApplyQualityLimit()
                            }
                            activity.playerManager.updateAudioOnlyVisual(false)
                        }
                    }
                    updateQualityValue()
                    when {
                        isAudioOnlyItem -> activity.binding.videoQualityBadge.text = activity.getString(R.string.quality_audio_only)
                        q != null -> activity.binding.videoQualityBadge.text = "${q.height}p${q.framerate.toInt()}"
                    }
                    dismissPanel()
                }
            }

            override fun getItemCount() = qualityItems.size
        }

        // Speed RecyclerView
        val speedRecycler = view.findViewById<RecyclerView>(R.id.speedRecyclerView)
        speedRecycler.layoutManager = LinearLayoutManager(activity)
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        speedRecycler.adapter = object : RecyclerView.Adapter<QualityViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                QualityViewHolder(activity.layoutInflater.inflate(R.layout.item_quality_option, parent, false))

            override fun onBindViewHolder(holder: QualityViewHolder, position: Int) {
                val s = speeds[position]
                holder.label.text = if (s == 1.0f) activity.getString(R.string.speed_normal_1x)
                    else activity.getString(R.string.speed_x_format, s)
                holder.bitrate.visibility = View.GONE
                holder.badge.visibility = View.INVISIBLE
                val currentSpeed = activity.ivsPlayer?.playbackRate ?: 1.0f
                holder.checkmark.visibility = if (s == currentSpeed) View.VISIBLE else View.GONE
                holder.checkmark.imageTintList = android.content.res.ColorStateList.valueOf(prefs.themeColor)
                holder.itemView.setOnClickListener {
                    activity.ivsPlayer?.playbackRate = s
                    activity.analytics.logFeatureUsed("playback_speed_${s}x")
                    dismissPanel()
                }
            }

            override fun getItemCount() = speeds.size
        }

        // High Z-order strictly above sideChatContainer (180dp), sideChatWidthBadge (195dp),
        // and Theatre Mode chatContainer (1000f).
        val scrimZ = maxOf(1150f, dpToPx(230).toFloat())
        val panelZ = maxOf(1200f, dpToPx(240).toFloat())

        // Full-container dimmed scrim behind the panel: tapping outside dismisses the sheet.
        val scrim = View(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(0, 0).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            setBackgroundColor(Color.argb(SCRIM_ALPHA, 0, 0, 0))
            isClickable = true
            isFocusable = true
            alpha = 0f
            ViewCompat.setElevation(this, scrimZ)
            translationZ = scrimZ
            setOnClickListener { dismissPanel() }
        }
        container.addView(scrim)
        scrimView = scrim

        // Consume stray taps on the panel itself so they don't fall through to the scrim.
        view.isClickable = true
        view.isFocusable = true
        ViewCompat.setElevation(view, panelZ)
        view.translationZ = panelZ

        currentDesiredHeightPx = computeRootHeight(rootItemCount)
        val initialMetrics = applyResponsiveLayout(
            view = view,
            container = container,
            desiredContentHeightPx = currentDesiredHeightPx,
            animateHeight = false
        )
        container.addView(view)

        // Re-size and re-clamp dynamically if orientation, window size, or insets change while open.
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
            if (sizeChanged && currentPanelView === view && !isDismissing) {
                applyResponsiveLayout(
                    view = view,
                    container = container,
                    desiredContentHeightPx = currentDesiredHeightPx,
                    animateHeight = false
                )
            }
        }
        containerLayoutListener = layoutListener
        container.addOnLayoutChangeListener(layoutListener)

        navigate(PAGE_ROOT, animateHeight = false)

        // Animate scrim fade-in and panel slide-up + fade-in.
        scrim.animate()
            .alpha(1f)
            .setDuration(ANIM_OPEN_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        view.alpha = 0f
        view.translationY = initialMetrics.heightPx.coerceAtLeast(dpToPx(120)).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIM_OPEN_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
