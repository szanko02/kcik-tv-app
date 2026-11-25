/**
 * File: FullscreenToggleManager.kt
 *
 * Description: Orchestrates toggling between Standard and Theatre fullscreen modes.
 * It manages the complex UI reparenting, layout constraints, and visibility states required to
 * switch between the default view and the immersive "Theatre Mode" with transparent chat overlay.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.player

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.xacnio.kciktv.mobile.MobilePlayerActivity
import dev.xacnio.kciktv.R
import dev.xacnio.kciktv.mobile.ui.feed.StreamFeedAdapter
import dev.xacnio.kciktv.shared.data.model.PinnedMessage
import dev.xacnio.kciktv.shared.data.prefs.AppPreferences
import kotlin.math.roundToInt

class FullscreenToggleManager(private val activity: MobilePlayerActivity) {

    private val binding get() = activity.binding
    private val vodManager get() = activity.vodManager
    private val fullscreenManager get() = activity.fullscreenManager
    private val prefs get() = activity.prefs
    private val chatUiManager get() = activity.chatUiManager

    // Track if we're in theatre mode (portrait fullscreen with transparent chat)
    var isTheatreMode = false
        private set

    var hasAutoEnteredTheatreMode = false

    // Saved state for PiP transition
    private var savedVideoParams: ConstraintLayout.LayoutParams? = null
    private var savedResizeMode: com.amazonaws.ivs.player.ResizeMode? = null

    private data class HorizontalPadding(val left: Int, val right: Int)
    private var baseVideoTopBarPadding: HorizontalPadding? = null
    private var baseVideoOverlayPadding: HorizontalPadding? = null
    private var basePlayPauseOverlayPadding: HorizontalPadding? = null
    private var baseSideChatPadding: HorizontalPadding? = null
    private var isFullscreenScaleButtonWired = false

    private val isInPipModeCompat: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode

    private val isMiniPlayerCompat: Boolean
        get() = try {
            activity.miniPlayerManager.isMiniPlayerMode
        } catch (_: UninitializedPropertyAccessException) {
            false
        }

    // Saved state for overlay reparenting
    private var originalOverlayParent: android.view.ViewGroup? = null
    private var originalOverlayIndex: Int = -1
    private var originalOverlayParams: android.view.ViewGroup.LayoutParams? = null
    
    private var originalInfoPanelParent: android.view.ViewGroup? = null
    private var originalInfoPanelIndex: Int = -1
    private var originalInfoPanelParams: android.view.ViewGroup.LayoutParams? = null

    private var originalActionBarParent: android.view.ViewGroup? = null
    private var originalActionBarIndex: Int = -1
    private var originalActionBarParams: android.view.ViewGroup.LayoutParams? = null

    private var originalPinnedParent: android.view.ViewGroup? = null
    private var originalPinnedIndex: Int = -1
    private var originalPinnedParams: android.view.ViewGroup.LayoutParams? = null

    private var originalPollParent: android.view.ViewGroup? = null
    private var originalPollIndex: Int = -1
    private var originalPollParams: android.view.ViewGroup.LayoutParams? = null

    private var originalPredictionParent: android.view.ViewGroup? = null
    private var originalPredictionIndex: Int = -1
    private var originalPredictionParams: android.view.ViewGroup.LayoutParams? = null

    private var theatreTopBar: android.widget.LinearLayout? = null
    
    private var originalViewerParent: android.view.ViewGroup? = null
    private var originalViewerIndex: Int = -1
    private var originalViewerParams: android.view.ViewGroup.LayoutParams? = null

    private var originalClipParent: android.view.ViewGroup? = null
    private var originalClipIndex: Int = -1
    private var originalClipParams: android.view.ViewGroup.LayoutParams? = null

    private var originalMuteParent: android.view.ViewGroup? = null
    private var originalMuteIndex: Int = -1
    private var originalMuteParams: android.view.ViewGroup.LayoutParams? = null

    private var originalDividerParent: android.view.ViewGroup? = null
    private var originalDividerIndex: Int = -1
    private var originalDividerParams: android.view.ViewGroup.LayoutParams? = null

    private var originalTimeParent: android.view.ViewGroup? = null
    private var originalTimeIndex: Int = -1
    private var originalTimeParams: android.view.ViewGroup.LayoutParams? = null

    private var originalFullscreenParent: android.view.ViewGroup? = null
    private var originalFullscreenIndex: Int = -1
    private var originalFullscreenParams: android.view.ViewGroup.LayoutParams? = null

    // Theatre Mode Top Bar Reparenting
    private var originalQualityParent: android.view.ViewGroup? = null
    private var originalQualityIndex: Int = -1
    private var originalQualityParams: android.view.ViewGroup.LayoutParams? = null

    private var originalSettingsParent: android.view.ViewGroup? = null
    private var originalSettingsIndex: Int = -1
    private var originalSettingsParams: android.view.ViewGroup.LayoutParams? = null

    private var originalReturnLiveParent: android.view.ViewGroup? = null
    private var originalReturnLiveIndex: Int = -1
    private var originalReturnLiveParams: android.view.ViewGroup.LayoutParams? = null

    // Emote Combo Reparenting
    private var originalEmoteComboParent: android.view.ViewGroup? = null
    private var originalEmoteComboIndex: Int = -1
    private var originalEmoteComboParams: android.view.ViewGroup.LayoutParams? = null

    // Floating Emote Reparenting
    private var originalFloatingEmoteParent: android.view.ViewGroup? = null
    private var originalFloatingEmoteIndex: Int = -1
    private var originalFloatingEmoteParams: android.view.ViewGroup.LayoutParams? = null

    private var originalPinnedGiftsParent: android.view.ViewGroup? = null
    private var originalPinnedGiftsIndex: Int = -1
    private var originalPinnedGiftsParams: android.view.ViewGroup.LayoutParams? = null

    // Side Chat State
    var isSideChatVisible = false
        private set
    private var originalChatParent: android.view.ViewGroup? = null
    private var originalChatIndex: Int = -1
    private var originalChatParams: android.view.ViewGroup.LayoutParams? = null
    private var lastWindowInsets: WindowInsetsCompat? = null
    private var theatreImeOpen: Boolean = false
    private var activeSideChatAnimator: android.animation.ValueAnimator? = null

    // Side Chat Resize Handle & Floating Chat State
    private var isDividerSetup = false
    private var isDraggingDivider = false
    private var isPreviewingSideChatWidth = false
    private var lastHandleTapMs = 0L
    private var lastDraggedPercent = -1
    private var pillDrawable: android.graphics.drawable.GradientDrawable? = null
    private var badgeDrawable: android.graphics.drawable.GradientDrawable? = null
    private var sideChatWidthPreviewLine: View? = null

    var isDraggingFloatingChat: Boolean = false
        private set
    private var isFloatingDragSetup = false
    private var lastFloatingHandleTapMs = 0L
    private var floatingDragPillDrawable: android.graphics.drawable.GradientDrawable? = null
    private var floatingChatPreviewBox: View? = null
    private var floatingChatPreviewDrawable: android.graphics.drawable.GradientDrawable? = null
    private var currentImeBottomPx: Int = 0
    private var lastFloatingRect: FloatingChatBounds.RectPx? = null
    private var isParentLayoutListenerAttached = false
    private var lastObservedParentWidth = 0
    private var lastObservedParentHeight = 0

    private val parentLayoutChangeListener = View.OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val newW = right - left
        val newH = bottom - top
        val oldW = oldRight - oldLeft
        val oldH = oldBottom - oldTop
        if (newW <= 0 || newH <= 0) return@OnLayoutChangeListener
        val changed = newW != oldW || newH != oldH ||
            newW != lastObservedParentWidth || newH != lastObservedParentHeight
        if (!changed) return@OnLayoutChangeListener
        lastObservedParentWidth = newW
        lastObservedParentHeight = newH
        if (isSideChatVisible && !isDraggingFloatingChat && !isDraggingDivider) {
            v.post {
                if (isSideChatVisible && !isDraggingFloatingChat && !isDraggingDivider) {
                    applySideChatLayout()
                    refreshPlayerWindowInsets()
                }
            }
        }
    }

    private fun isTransparentSideChatActive(): Boolean =
        isSideChatVisible && prefs.transparentChatOverlayEnabled

    fun isFloatingSideChatActive(): Boolean =
        isTransparentSideChatActive() && prefs.floatingChatOverlayEnabled

    fun isDockedSideChatActive(): Boolean =
        ChatOverlayStyleUtils.isDockedSideChat(isSideChatVisible, prefs.transparentChatOverlayEnabled)

    private fun effectiveRootWidth(rootWidth: Int = binding.playerScreenContainer.width): Int {
        if (rootWidth > 0) return rootWidth
        if (binding.root.width > 0) return binding.root.width
        val dm = activity.resources.displayMetrics
        return kotlin.math.max(dm.widthPixels, dm.heightPixels)
    }

    private fun effectiveRootHeight(rootHeight: Int = binding.playerScreenContainer.height): Int {
        if (rootHeight > 0) return rootHeight
        if (binding.root.height > 0) return binding.root.height
        val dm = activity.resources.displayMetrics
        return kotlin.math.min(dm.widthPixels, dm.heightPixels)
    }

    private fun currentDisplayCutoutInsets(): Insets {
        if (!activity.isFullscreen || isTheatreMode || !prefs.drawUnderCutout) {
            return Insets.NONE
        }
        val insets = lastWindowInsets ?: ViewCompat.getRootWindowInsets(binding.playerScreenContainer)
        return insets?.getInsets(WindowInsetsCompat.Type.displayCutout()) ?: Insets.NONE
    }

    private fun floatingOuterGapPx(): Int =
        (8f * activity.resources.displayMetrics.density).roundToInt()

    private fun computeSideChatWidthPx(rootWidth: Int, percent: Int = prefs.sideChatWidthPercent): Int {
        return SideChatWidthUtils.widthPxForPercent(
            effectiveRootWidth(rootWidth),
            percent
        )
    }

    private fun currentSideChatWidthPx(rootWidth: Int = effectiveRootWidth()): Int {
        val activePercent = if (isDraggingDivider && lastDraggedPercent > 0) {
            lastDraggedPercent
        } else {
            prefs.sideChatWidthPercent
        }
        val lpWidth = binding.sideChatContainer.layoutParams?.width ?: 0
        return if (isSideChatVisible && lpWidth > 0) {
            lpWidth
        } else {
            computeSideChatWidthPx(rootWidth, activePercent)
        }
    }

    private fun effectiveDockedEdgeMarginPx(
        rootWidth: Int = effectiveRootWidth(),
        chatWidth: Int = currentSideChatWidthPx(rootWidth)
    ): Int {
        if (!isTransparentSideChatActive() || isFloatingSideChatActive()) return 0
        return FloatingChatBounds.computeEdgeMarginPx(
            parentWidthPx = rootWidth,
            chatWidthPx = chatWidth,
            edgeMarginPercent = prefs.chatOverlayEdgeMarginPercent
        )
    }

    private fun computeCurrentFloatingRect(
        widthPercent: Int = prefs.sideChatWidthPercent,
        edgeMarginPercent: Int = prefs.chatOverlayEdgeMarginPercent,
        heightPercent: Int = prefs.floatingChatHeightPercent,
        xPercent: Float = prefs.floatingChatXPercent,
        yPercent: Float = prefs.floatingChatYPercent,
        parentWidth: Int = effectiveRootWidth(),
        parentHeight: Int = effectiveRootHeight(),
        imeBottomPx: Int = currentImeBottomPx
    ): FloatingChatBounds.RectPx {
        val density = activity.resources.displayMetrics.density
        val cutout = currentDisplayCutoutInsets()
        return FloatingChatBounds.computeFloatingRect(
            parentWidthPx = parentWidth,
            parentHeightPx = parentHeight,
            widthPercent = widthPercent,
            heightPercent = heightPercent,
            xPercent = xPercent,
            yPercent = yPercent,
            edgeMarginPercent = edgeMarginPercent,
            minOuterGapPx = floatingOuterGapPx(),
            cutoutLeftPx = cutout.left,
            cutoutTopPx = cutout.top,
            cutoutRightPx = cutout.right,
            cutoutBottomPx = cutout.bottom,
            imeBottomPx = imeBottomPx,
            minWidthPx = (96f * density).roundToInt(),
            minHeightPx = (120f * density).roundToInt()
        )
    }

    private fun ensureParentLayoutListener() {
        if (isParentLayoutListenerAttached) return
        isParentLayoutListenerAttached = true
        lastObservedParentWidth = binding.playerScreenContainer.width
        lastObservedParentHeight = binding.playerScreenContainer.height
        binding.playerScreenContainer.addOnLayoutChangeListener(parentLayoutChangeListener)
    }

    private fun ensureChatContainerWeightedInSideChat() {
        val chat = binding.chatContainer
        if (chat.parent != binding.sideChatContainer) return
        val lp = (chat.layoutParams as? android.widget.LinearLayout.LayoutParams)
            ?: android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        if (lp.width != android.widget.LinearLayout.LayoutParams.MATCH_PARENT ||
            lp.height != 0 ||
            lp.weight != 1.0f
        ) {
            lp.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            lp.height = 0
            lp.weight = 1.0f
            lp.setMargins(0, 0, 0, 0)
            chat.layoutParams = lp
        }
    }

    private fun ensureFloatingDragSetup() {
        if (isFloatingDragSetup) return
        isFloatingDragSetup = true

        val density = activity.resources.displayMetrics.density
        floatingDragPillDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 2f * density
            setColor(Color.parseColor("#B3FFFFFF"))
        }
        binding.floatingChatDragPill.background = floatingDragPillDrawable

        binding.floatingChatResetPositionBtn.setOnClickListener {
            resetFloatingChatPosition(showToast = true)
        }

        val touchSlop = android.view.ViewConfiguration.get(activity).scaledTouchSlop
        val baseDividerTx = 11f * density
        var downRawX = 0f
        var downRawY = 0f
        var startLeft = 0
        var startTop = 0
        var rectW = 0
        var rectH = 0
        var moved = false
        var isDoubleTap = false

        binding.floatingChatDragHandle.setOnTouchListener { v, event ->
            if (!isFloatingSideChatActive()) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    activeSideChatAnimator?.cancel()
                    activeSideChatAnimator = null
                    clearSideChatWidthPreview()
                    val lp = binding.sideChatContainer.layoutParams as ConstraintLayout.LayoutParams
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startLeft = lp.marginStart
                    startTop = lp.topMargin
                    rectW = binding.sideChatContainer.width.takeIf { it > 0 } ?: lp.width.coerceAtLeast(1)
                    rectH = binding.sideChatContainer.height.takeIf { it > 0 } ?: lp.height.coerceAtLeast(1)
                    moved = false
                    val now = android.os.SystemClock.uptimeMillis()
                    isDoubleTap = (now - lastFloatingHandleTapMs) < 300L
                    lastFloatingHandleTapMs = now
                    isDraggingFloatingChat = true
                    floatingDragPillDrawable?.setColor(Color.parseColor("#53FC18"))
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && (kotlin.math.abs(dx) >= touchSlop || kotlin.math.abs(dy) >= touchSlop)) {
                        moved = true
                    }
                    if (moved) {
                        val cutout = currentDisplayCutoutInsets()
                        val (clampedLeft, clampedTop) = FloatingChatBounds.clampDraggedOrigin(
                            desiredLeftPx = (startLeft + dx).roundToInt(),
                            desiredTopPx = (startTop + dy).roundToInt(),
                            rectWidthPx = rectW,
                            rectHeightPx = rectH,
                            parentWidthPx = effectiveRootWidth(),
                            parentHeightPx = effectiveRootHeight(),
                            edgeMarginPercent = prefs.chatOverlayEdgeMarginPercent,
                            minOuterGapPx = floatingOuterGapPx(),
                            cutoutLeftPx = cutout.left,
                            cutoutTopPx = cutout.top,
                            cutoutRightPx = cutout.right,
                            cutoutBottomPx = cutout.bottom,
                            imeBottomPx = currentImeBottomPx
                        )
                        val tx = (clampedLeft - startLeft).toFloat()
                        val ty = (clampedTop - startTop).toFloat()
                        binding.sideChatContainer.translationX = tx
                        binding.sideChatContainer.translationY = ty
                        binding.sideChatDividerHandle.translationX = baseDividerTx + tx
                        binding.sideChatDividerHandle.translationY = ty
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingFloatingChat = false
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    floatingDragPillDrawable?.setColor(Color.parseColor("#B3FFFFFF"))
                    if (moved) {
                        val finalLeft = (startLeft + binding.sideChatContainer.translationX).roundToInt()
                        val finalTop = (startTop + binding.sideChatContainer.translationY).roundToInt()
                        val cutout = currentDisplayCutoutInsets()
                        val norm = FloatingChatBounds.originToNormalizedPercent(
                            leftPx = finalLeft,
                            topPx = finalTop,
                            rectWidthPx = rectW,
                            rectHeightPx = rectH,
                            parentWidthPx = effectiveRootWidth(),
                            parentHeightPx = effectiveRootHeight(),
                            edgeMarginPercent = prefs.chatOverlayEdgeMarginPercent,
                            minOuterGapPx = floatingOuterGapPx(),
                            cutoutLeftPx = cutout.left,
                            cutoutTopPx = cutout.top,
                            cutoutRightPx = cutout.right,
                            cutoutBottomPx = cutout.bottom,
                            imeBottomPx = currentImeBottomPx,
                            fallbackX = prefs.floatingChatXPercent,
                            fallbackY = prefs.floatingChatYPercent
                        )
                        prefs.floatingChatXPercent = norm.xPercent
                        prefs.floatingChatYPercent = norm.yPercent
                        binding.sideChatContainer.translationX = 0f
                        binding.sideChatContainer.translationY = 0f
                        binding.sideChatDividerHandle.translationX = baseDividerTx
                        binding.sideChatDividerHandle.translationY = 0f
                        applySideChatLayout()
                        refreshPlayerWindowInsets()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && isDoubleTap) {
                        lastFloatingHandleTapMs = 0L
                        resetFloatingChatPosition(showToast = true)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFloatingContainerBoundsOnly(rect: FloatingChatBounds.RectPx) {
        val density = activity.resources.displayMetrics.density
        val sideParams = binding.sideChatContainer.layoutParams as ConstraintLayout.LayoutParams
        val changed = sideParams.startToStart != ConstraintLayout.LayoutParams.PARENT_ID ||
            sideParams.topToTop != ConstraintLayout.LayoutParams.PARENT_ID ||
            sideParams.endToEnd != ConstraintLayout.LayoutParams.UNSET ||
            sideParams.bottomToBottom != ConstraintLayout.LayoutParams.UNSET ||
            sideParams.width != rect.width ||
            sideParams.height != rect.height ||
            sideParams.marginStart != rect.left ||
            sideParams.topMargin != rect.top ||
            sideParams.marginEnd != 0 ||
            sideParams.bottomMargin != 0
        if (changed) {
            sideParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            sideParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            sideParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            sideParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            sideParams.width = rect.width
            sideParams.height = rect.height
            sideParams.marginStart = rect.left
            sideParams.topMargin = rect.top
            sideParams.marginEnd = 0
            sideParams.bottomMargin = 0
            binding.sideChatContainer.layoutParams = sideParams
        }
        lastFloatingRect = rect
        val minResetBtnWidthPx = (140f * density).roundToInt()
        binding.floatingChatResetPositionBtn.visibility =
            if (rect.width >= minResetBtnWidthPx) View.VISIBLE else View.GONE
    }

    private fun applySideChatLayout(
        widthPercentOverride: Int? = null,
        xPercentOverride: Float? = null
    ) {
        if (!isSideChatVisible) return

        val density = activity.resources.displayMetrics.density
        val rootWidth = effectiveRootWidth()
        val activeWidthPercent = SideChatWidthUtils.clampPercent(
            widthPercentOverride ?: if (isDraggingDivider && lastDraggedPercent > 0) {
                lastDraggedPercent
            } else {
                prefs.sideChatWidthPercent
            }
        )
        val dividerHalfPx = 11f * density
        val isTablet = activity.isTabletOrLargeWindow()
        val videoParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams

        ensureChatContainerWeightedInSideChat()

        if (isFloatingSideChatActive()) {
            ensureFloatingDragSetup()
            ensureParentLayoutListener()
            val rect = computeCurrentFloatingRect(
                widthPercent = activeWidthPercent,
                xPercent = xPercentOverride ?: prefs.floatingChatXPercent
            )
            updateFloatingContainerBoundsOnly(rect)
            if (!isDraggingFloatingChat) {
                binding.sideChatContainer.translationX = 0f
                binding.sideChatContainer.translationY = 0f
                binding.sideChatDividerHandle.translationX = dividerHalfPx
                binding.sideChatDividerHandle.translationY = 0f
            }
            binding.sideChatContainer.elevation = 180f * density
            binding.sideChatContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
            binding.sideChatContainer.clipToOutline = true
            binding.floatingChatDragHandle.visibility = View.VISIBLE
            if (isTablet) {
                binding.infoPanel.visibility = View.GONE
            }

            videoParams.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.dimensionRatio = null
            videoParams.topMargin = 0
            videoParams.bottomMargin = 0
            videoParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.endToStart = ConstraintLayout.LayoutParams.UNSET
            videoParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.verticalBias = 0.5f
            binding.videoContainer.layoutParams = videoParams
            return
        }

        lastFloatingRect = null
        binding.floatingChatDragHandle.visibility = View.GONE
        binding.sideChatContainer.clipToOutline = false
        binding.sideChatContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.sideChatContainer.translationY = 0f
        binding.sideChatDividerHandle.translationY = 0f

        val chatWidth = SideChatWidthUtils.widthPxForPercent(rootWidth, activeWidthPercent)
        val sideParams = binding.sideChatContainer.layoutParams as ConstraintLayout.LayoutParams

        if (prefs.transparentChatOverlayEnabled) {
            ensureParentLayoutListener()
            val edgeMarginPx = FloatingChatBounds.computeEdgeMarginPx(
                parentWidthPx = rootWidth,
                chatWidthPx = chatWidth,
                edgeMarginPercent = prefs.chatOverlayEdgeMarginPercent
            )
            sideParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            sideParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            sideParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            sideParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            sideParams.width = chatWidth
            sideParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            sideParams.marginStart = 0
            sideParams.topMargin = 0
            sideParams.marginEnd = edgeMarginPx
            sideParams.bottomMargin = 0
            binding.sideChatContainer.layoutParams = sideParams
            binding.sideChatContainer.elevation = 180f * density
            if (isTablet) {
                binding.infoPanel.visibility = View.GONE
            }

            videoParams.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.dimensionRatio = null
            videoParams.topMargin = 0
            videoParams.bottomMargin = 0
            videoParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.endToStart = ConstraintLayout.LayoutParams.UNSET
            videoParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.verticalBias = 0.5f
            binding.videoContainer.layoutParams = videoParams
            return
        }

        // Docked split-screen mode
        sideParams.startToStart = ConstraintLayout.LayoutParams.UNSET
        sideParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        sideParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        sideParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        sideParams.width = chatWidth
        sideParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        sideParams.marginStart = 0
        sideParams.topMargin = 0
        sideParams.marginEnd = 0
        sideParams.bottomMargin = 0
        binding.sideChatContainer.layoutParams = sideParams

        if (isTablet) {
            binding.sideChatContainer.elevation = 0f
            binding.infoPanel.visibility = View.VISIBLE
            videoParams.width = (rootWidth - chatWidth).coerceAtLeast(1)
            videoParams.dimensionRatio = "H,16:9"
            videoParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.verticalBias = 0.0f
        } else {
            binding.sideChatContainer.elevation = 180f * density
            videoParams.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.dimensionRatio = null
            videoParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            videoParams.verticalBias = 0.5f
        }
        videoParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.endToStart = R.id.sideChatContainer
        videoParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
        videoParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        binding.videoContainer.layoutParams = videoParams
    }

    private fun resetSideChatContainerToDefaultState(keepVisibility: Boolean = false) {
        isDraggingFloatingChat = false
        clearSideChatWidthPreview()
        val density = activity.resources.displayMetrics.density
        val side = binding.sideChatContainer
        side.translationX = 0f
        side.translationY = 0f
        side.alpha = 1f
        side.scaleX = 1f
        side.scaleY = 1f
        side.clipToOutline = false
        side.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.floatingChatDragHandle.visibility = View.GONE
        floatingDragPillDrawable?.setColor(Color.parseColor("#B3FFFFFF"))
        lastFloatingRect = null

        val sideParams = side.layoutParams as ConstraintLayout.LayoutParams
        sideParams.startToStart = ConstraintLayout.LayoutParams.UNSET
        sideParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        sideParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        sideParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        sideParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        sideParams.marginStart = 0
        sideParams.marginEnd = 0
        sideParams.topMargin = 0
        sideParams.bottomMargin = 0
        side.layoutParams = sideParams

        binding.sideChatDividerHandle.translationX = 11f * density
        binding.sideChatDividerHandle.translationY = 0f
        if (!keepVisibility) {
            side.visibility = View.GONE
        }
    }

    private fun ensureSideChatDividerSetup() {
        if (isDividerSetup) return
        isDividerSetup = true

        val density = activity.resources.displayMetrics.density
        pillDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 2f * density
            setColor(Color.parseColor("#CCFFFFFF"))
        }
        binding.sideChatDividerPill.background = pillDrawable

        badgeDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(Color.parseColor("#CC121212"))
            setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#53FC18"))
        }
        binding.sideChatWidthBadge.background = badgeDrawable

        val touchSlop = android.view.ViewConfiguration.get(activity).scaledTouchSlop
        val parentLoc = IntArray(2)
        var downX = 0f
        var moved = false
        var isDoubleTap = false
        var fixedFloatingRightPx = -1
        var lastFloatingDraggedXPercent = Float.NaN

        binding.sideChatDividerHandle.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    downX = event.rawX
                    moved = false
                    lastDraggedPercent = -1
                    lastFloatingDraggedXPercent = Float.NaN
                    fixedFloatingRightPx = if (isFloatingSideChatActive()) {
                        val lp = binding.sideChatContainer.layoutParams as ConstraintLayout.LayoutParams
                        val currentW = binding.sideChatContainer.width.takeIf { it > 0 } ?: lp.width.coerceAtLeast(1)
                        lp.marginStart + currentW
                    } else {
                        -1
                    }
                    val now = android.os.SystemClock.uptimeMillis()
                    isDoubleTap = (now - lastHandleTapMs) < 300L
                    lastHandleTapMs = now
                    isDraggingDivider = true
                    clearSideChatWidthPreview()
                    pillDrawable?.setColor(Color.parseColor("#53FC18"))
                    v.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && kotlin.math.abs(event.rawX - downX) >= touchSlop) {
                        moved = true
                    }
                    if (moved) {
                        val parentView = binding.playerScreenContainer
                        parentView.getLocationOnScreen(parentLoc)
                        val parentW = effectiveRootWidth(parentView.width)
                        val parentH = effectiveRootHeight(parentView.height)
                        val rightMarginPx = when {
                            isFloatingSideChatActive() && fixedFloatingRightPx > 0 ->
                                (parentW - fixedFloatingRightPx).coerceAtLeast(0)
                            isTransparentSideChatActive() ->
                                FloatingChatBounds.computeEdgeMarginPx(parentW, 0, prefs.chatOverlayEdgeMarginPercent)
                            else -> 0
                        }
                        val percent = SideChatWidthUtils.percentForTouchX(
                            event.rawX,
                            parentLoc[0],
                            parentW,
                            rightMarginPx
                        )
                        lastDraggedPercent = percent
                        if (isFloatingSideChatActive() && fixedFloatingRightPx > 0) {
                            val probeRect = computeCurrentFloatingRect(
                                widthPercent = percent,
                                parentWidth = parentW,
                                parentHeight = parentH
                            )
                            val desiredLeft = fixedFloatingRightPx - probeRect.width
                            val cutout = currentDisplayCutoutInsets()
                            val (clampedLeft, _) = FloatingChatBounds.clampDraggedOrigin(
                                desiredLeftPx = desiredLeft,
                                desiredTopPx = probeRect.top,
                                rectWidthPx = probeRect.width,
                                rectHeightPx = probeRect.height,
                                parentWidthPx = parentW,
                                parentHeightPx = parentH,
                                edgeMarginPercent = prefs.chatOverlayEdgeMarginPercent,
                                minOuterGapPx = floatingOuterGapPx(),
                                cutoutLeftPx = cutout.left,
                                cutoutTopPx = cutout.top,
                                cutoutRightPx = cutout.right,
                                cutoutBottomPx = cutout.bottom,
                                imeBottomPx = currentImeBottomPx
                            )
                            val norm = FloatingChatBounds.originToNormalizedPercent(
                                leftPx = clampedLeft,
                                topPx = probeRect.top,
                                rectWidthPx = probeRect.width,
                                rectHeightPx = probeRect.height,
                                parentWidthPx = parentW,
                                parentHeightPx = parentH,
                                edgeMarginPercent = prefs.chatOverlayEdgeMarginPercent,
                                minOuterGapPx = floatingOuterGapPx(),
                                cutoutLeftPx = cutout.left,
                                cutoutTopPx = cutout.top,
                                cutoutRightPx = cutout.right,
                                cutoutBottomPx = cutout.bottom,
                                imeBottomPx = currentImeBottomPx,
                                fallbackX = prefs.floatingChatXPercent,
                                fallbackY = prefs.floatingChatYPercent
                            )
                            lastFloatingDraggedXPercent = norm.xPercent
                            applySideChatLayout(widthPercentOverride = percent, xPercentOverride = norm.xPercent)
                            refreshPlayerWindowInsets()
                        } else {
                            applySideChatWidthPercent(percent, persist = false)
                        }
                        binding.sideChatWidthBadge.text = "$percent%"
                        binding.sideChatWidthBadge.visibility = View.VISIBLE
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingDivider = false
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    pillDrawable?.setColor(Color.parseColor("#CCFFFFFF"))
                    binding.sideChatWidthBadge.visibility = View.GONE
                    if (moved && lastDraggedPercent > 0) {
                        prefs.sideChatWidthPercent = lastDraggedPercent
                        if (lastFloatingDraggedXPercent.isFinite()) {
                            prefs.floatingChatXPercent = lastFloatingDraggedXPercent
                        }
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && isDoubleTap) {
                        lastHandleTapMs = 0L
                        applySideChatWidthPercent(
                            AppPreferences.SIDE_CHAT_WIDTH_DEFAULT,
                            persist = true
                        )
                        Toast.makeText(activity, R.string.side_chat_width_reset_toast, Toast.LENGTH_SHORT).show()
                    }
                    fixedFloatingRightPx = -1
                    lastFloatingDraggedXPercent = Float.NaN
                    lastDraggedPercent = -1
                    updateSideChatHandleVisibility()
                    true
                }
                else -> false
            }
        }
    }

    private fun hideSideChatDivider() {
        isDraggingDivider = false
        isDraggingFloatingChat = false
        lastDraggedPercent = -1
        binding.sideChatDividerHandle.visibility = View.GONE
        clearSideChatWidthPreview()
        binding.sideChatWidthBadge.visibility = View.GONE
    }

    fun previewSideChatWidthPercent(percent: Int) {
        if (!isSideChatVisible || binding.sideChatContainer.visibility != View.VISIBLE) {
            clearSideChatWidthPreview()
            return
        }
        if (isFloatingSideChatActive() || (isTransparentSideChatActive() && prefs.chatOverlayEdgeMarginPercent > 0)) {
            previewSideChatLayout(widthPercent = percent)
            return
        }
        floatingChatPreviewBox?.visibility = View.GONE
        ensureSideChatDividerSetup()
        val clamped = SideChatWidthUtils.clampPercent(percent)
        val parent = binding.playerScreenContainer
        val parentWidth = effectiveRootWidth(parent.width)
        val targetChatWidthPx = computeSideChatWidthPx(parentWidth, clamped)

        val line = sideChatWidthPreviewLine ?: View(activity).apply {
            val density = activity.resources.displayMetrics.density
            val lineWidthPx = (2f * density).toInt().coerceAtLeast(1)
            layoutParams = ConstraintLayout.LayoutParams(
                lineWidthPx,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            ).apply {
                leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
            setBackgroundColor(Color.parseColor("#53FC18"))
            isClickable = false
            isFocusable = false
            // Keep above sideChatContainer (180dp) and sideChatDividerHandle (190dp)
            // so the preview line remains visible when dragging left over the chat panel.
            elevation = kotlin.math.max(120f, binding.sideChatContainer.elevation + 14f * density)
            visibility = View.GONE
            parent.addView(this)
        }.also { sideChatWidthPreviewLine = it }

        val lineWidthPx = line.layoutParams.width.coerceAtLeast(1)
        val maxX = (parentWidth - lineWidthPx).coerceAtLeast(0).toFloat()
        val splitX = (parentWidth - targetChatWidthPx).toFloat().coerceIn(0f, maxX)
        line.translationX = splitX
        line.visibility = View.VISIBLE

        isPreviewingSideChatWidth = true
        binding.sideChatWidthBadge.text = "$clamped%"
        binding.sideChatWidthBadge.visibility = View.VISIBLE
    }

    fun previewSideChatLayout(
        widthPercent: Int? = null,
        edgeMarginPercent: Int? = null,
        heightPercent: Int? = null
    ) {
        if (!isSideChatVisible || binding.sideChatContainer.visibility != View.VISIBLE) {
            clearSideChatWidthPreview()
            return
        }
        ensureSideChatDividerSetup()
        val density = activity.resources.displayMetrics.density
        val parent = binding.playerScreenContainer
        val parentW = effectiveRootWidth(parent.width)
        val parentH = effectiveRootHeight(parent.height)

        val targetWidthPct = SideChatWidthUtils.clampPercent(widthPercent ?: prefs.sideChatWidthPercent)
        val targetEdgePct = FloatingChatBounds.clampEdgeMarginPercent(edgeMarginPercent ?: prefs.chatOverlayEdgeMarginPercent)
        val targetHeightPct = FloatingChatBounds.clampHeightPercent(heightPercent ?: prefs.floatingChatHeightPercent)

        val isFloatingPreview = isFloatingSideChatActive() || (isTransparentSideChatActive() && heightPercent != null)
        if (!isFloatingPreview && (!isTransparentSideChatActive() || (edgeMarginPercent == null && targetEdgePct == 0))) {
            previewSideChatWidthPercent(targetWidthPct)
            return
        }

        sideChatWidthPreviewLine?.visibility = View.GONE

        val box = floatingChatPreviewBox ?: View(activity).apply {
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(Color.parseColor("#1A53FC18"))
                setStroke((2f * density).roundToInt().coerceAtLeast(1), Color.parseColor("#53FC18"))
            }
            floatingChatPreviewDrawable = drawable
            background = drawable
            isClickable = false
            isFocusable = false
            elevation = kotlin.math.max(120f, binding.sideChatContainer.elevation + 16f * density)
            visibility = View.GONE
            layoutParams = ConstraintLayout.LayoutParams(1, 1).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            }
            parent.addView(this)
        }.also { floatingChatPreviewBox = it }

        box.elevation = kotlin.math.max(120f, binding.sideChatContainer.elevation + 16f * density)
        val boxParams = box.layoutParams as ConstraintLayout.LayoutParams

        if (isFloatingPreview) {
            floatingChatPreviewDrawable?.cornerRadius = 12f * density
            val rect = computeCurrentFloatingRect(
                widthPercent = targetWidthPct,
                edgeMarginPercent = targetEdgePct,
                heightPercent = targetHeightPct,
                parentWidth = parentW,
                parentHeight = parentH
            )
            boxParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            boxParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            boxParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            boxParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            boxParams.width = rect.width
            boxParams.height = rect.height
            boxParams.marginStart = rect.left
            boxParams.topMargin = rect.top
            boxParams.marginEnd = 0
            boxParams.bottomMargin = 0
        } else {
            floatingChatPreviewDrawable?.cornerRadius = 0f
            val targetChatW = SideChatWidthUtils.widthPxForPercent(parentW, targetWidthPct)
            val targetEdgePx = FloatingChatBounds.computeEdgeMarginPx(parentW, targetChatW, targetEdgePct)
            boxParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            boxParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            boxParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            boxParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            boxParams.width = targetChatW
            boxParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            boxParams.marginStart = 0
            boxParams.topMargin = 0
            boxParams.marginEnd = targetEdgePx
            boxParams.bottomMargin = 0
        }

        box.layoutParams = boxParams
        box.visibility = View.VISIBLE

        isPreviewingSideChatWidth = true
        val badgeText = when {
            heightPercent != null -> "${targetHeightPct}%"
            edgeMarginPercent != null -> "${targetEdgePct}%"
            else -> "${targetWidthPct}%"
        }
        binding.sideChatWidthBadge.text = badgeText
        binding.sideChatWidthBadge.visibility = View.VISIBLE
    }

    fun clearSideChatWidthPreview() {
        isPreviewingSideChatWidth = false
        sideChatWidthPreviewLine?.visibility = View.GONE
        floatingChatPreviewBox?.visibility = View.GONE
        if (!isDraggingDivider) {
            binding.sideChatWidthBadge.visibility = View.GONE
        }
    }

    fun applySideChatWidthPercent(percent: Int, persist: Boolean = false) {
        val clamped = SideChatWidthUtils.clampPercent(percent)
        if (persist) {
            prefs.sideChatWidthPercent = clamped
        }
        if (!isSideChatVisible) return
        applySideChatLayout(widthPercentOverride = clamped)
        refreshPlayerWindowInsets()
    }

    fun resetFloatingChatPosition(showToast: Boolean = false) {
        prefs.floatingChatXPercent = AppPreferences.FLOATING_CHAT_X_DEFAULT
        prefs.floatingChatYPercent = AppPreferences.FLOATING_CHAT_Y_DEFAULT
        val density = activity.resources.displayMetrics.density
        binding.sideChatContainer.translationX = 0f
        binding.sideChatContainer.translationY = 0f
        binding.sideChatDividerHandle.translationX = 11f * density
        binding.sideChatDividerHandle.translationY = 0f
        if (isFloatingSideChatActive()) {
            applySideChatLayout()
            refreshPlayerWindowInsets()
            updateSideChatHandleVisibility()
        }
        if (showToast) {
            Toast.makeText(activity, R.string.floating_chat_position_reset_toast, Toast.LENGTH_SHORT).show()
        }
    }

    fun applyChatDefaultsReset() {
        activeSideChatAnimator?.cancel()
        activeSideChatAnimator = null
        isDraggingFloatingChat = false
        isDraggingDivider = false
        lastDraggedPercent = -1
        clearSideChatWidthPreview()
        resetSideChatContainerToDefaultState(keepVisibility = isSideChatVisible)
        applySideChatWidthPercent(AppPreferences.SIDE_CHAT_WIDTH_DEFAULT, persist = true)
        onTransparentChatOverlaySettingChanged()
        updateSideChatHandleVisibility()
        refreshPlayerWindowInsets()
    }

    fun updateSideChatHandleVisibility(overlayVisible: Boolean? = null) {
        ensureSideChatDividerSetup()
        val shouldShow = isSideChatVisible && prefs.sideChatResizeHandleEnabled
        binding.sideChatDividerHandle.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow) {
            if (!isPreviewingSideChatWidth) {
                binding.sideChatWidthBadge.visibility = View.GONE
            }
            return
        }
        if (isDraggingDivider) {
            binding.sideChatDividerHandle.alpha = 1.0f
            pillDrawable?.setColor(Color.parseColor("#53FC18"))
            val displayPercent = if (lastDraggedPercent > 0) lastDraggedPercent else prefs.sideChatWidthPercent
            binding.sideChatWidthBadge.text = "$displayPercent%"
            binding.sideChatWidthBadge.visibility = View.VISIBLE
        } else {
            val emphasized = overlayVisible == true || (overlayVisible == null && binding.videoOverlay.visibility == View.VISIBLE)
            binding.sideChatDividerHandle.alpha = if (emphasized) 1.0f else 0.28f
            pillDrawable?.setColor(Color.parseColor("#CCFFFFFF"))
            if (!isPreviewingSideChatWidth) {
                binding.sideChatWidthBadge.visibility = View.GONE
            }
        }
    }

    internal fun resolveFullscreenOrientation(): Int {
        return if (prefs.fullscreenSensorRotation || activity.isRotationAllowed()) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    fun applyFullscreenOrientationSetting() {
        if (!activity.isFullscreen || isTheatreMode) return
        val isTablet = activity.isTabletOrLargeWindow()
        if (isTablet && activity.isRotationAllowed()) {
            activity.setAllowedOrientation()
        } else {
            activity.requestedOrientation = resolveFullscreenOrientation()
        }
    }

    // For Tablet Split View - tracking if ActionBar was moved to side
    private var isActionBarReparentedToSide = false

    // Theatre Mode Video Logic
    private var originalVideoDimensionRatio: String? = null
    private var isTheatreFitMode = false
    private var originalResizeMode: com.amazonaws.ivs.player.ResizeMode? = null
    
    private var currentVideoWidth: Int = 0
    private var currentVideoHeight: Int = 0

    fun updateVideoSize(width: Int, height: Int) {
        currentVideoWidth = width
        currentVideoHeight = height
        
        if (isTheatreMode) {
             val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
             
             if (isTheatreFitMode) {
                 // FIT Mode: Always enforce 16:9 regardless of actual video size
                 params.dimensionRatio = "16:9"
             } else {
                 // ZOOM Mode: Use native video aspect ratio
                 if (width > 0 && height > 0) {
                     params.dimensionRatio = "$width:$height"
                 }
             }
             binding.videoContainer.layoutParams = params
        }
    }

    /**
     * Toggles between Theatre Fit modes (Fit vs Zoom)
     */
    fun toggleTheatreFitMode() {
        if (!isTheatreMode) return

        isTheatreFitMode = !isTheatreFitMode
        
        val metrics = activity.resources.displayMetrics
        val sHeight = metrics.heightPixels
        
        val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        if (isTheatreFitMode) {
            // FIT: Constrain strictly within the "stage" area (Top 15% to Bottom ~35%)
            // This ensures ANY aspect ratio fits without overflowing or covering chat
            params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            
            val targetRatio = if (currentVideoWidth > 0 && currentVideoHeight > 0 && !isTheatreFitMode) {
                "$currentVideoWidth:$currentVideoHeight"
            } else {
                originalVideoDimensionRatio ?: "16:9"
            }
            params.dimensionRatio = targetRatio
            
            // Remove previous constraints
            params.matchConstraintPercentHeight = 1.0f 
            params.matchConstraintPercentWidth = 1.0f
            
            // Define the "Box"
            // Top: 15% down
            // Bottom: Leave 35% space for Chat (approx)
            params.topMargin = (sHeight * 0.15f).toInt()
            params.bottomMargin = (sHeight * 0.35f).toInt()
            
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            
            params.verticalBias = 0.5f // Center vertically within the defined top/bottom margins
            
            // Increase Chat Height in Fit Mode
            theatreModeChatCollapsedHeight = (sHeight * 0.45f).toInt()
            theatreModeChatExpandedHeight = (sHeight * 0.70f).toInt()
            
            // Reset scaleX/Y just in case
            binding.videoContainer.scaleX = 1f
            binding.videoContainer.scaleY = 1f
        } else {
            // ZOOM: Height 100%, Width overflows (Center Crop effect)
            val ratioVal = if (currentVideoWidth > 0 && currentVideoHeight > 0) {
                 currentVideoWidth.toFloat() / currentVideoHeight.toFloat()
            } else {
                 16f / 9f
            }
            // Logic for "Fill Screen" / Zoom
            val zWidth = (sHeight * ratioVal).toInt()
            
            params.dimensionRatio = null
            params.width = zWidth
            params.height = sHeight
            params.matchConstraintPercentHeight = 1.0f // Reset
            params.matchConstraintPercentWidth = 1.0f // Reset
            
            params.topMargin = 0
            params.bottomMargin = 0
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            
            // Center and remove margin
            params.verticalBias = 0.5f 
            
            // Restore Standard Chat Height
            theatreModeChatCollapsedHeight = (sHeight * 0.30f).toInt()
            theatreModeChatExpandedHeight = (sHeight * 0.55f).toInt()
        }
        binding.videoContainer.layoutParams = params
        
        // Apply updated height immediately
        val chatParams = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
        chatParams.height = theatreModeChatCollapsedHeight
        binding.chatContainer.layoutParams = chatParams
    }

    /**
     * Cleans up side chat state and resets video container constraints.
     * @param forceReset If true, always perform full cleanup even if state appears clean.
     *                    Used during playChannel to ensure absolutely clean state.
     */
    internal fun cleanupSideChat(forceReset: Boolean = false) {
        activeSideChatAnimator?.cancel()
        activeSideChatAnimator = null
        hideSideChatDivider()
        // Use early return guard for normal calls, but skip it if forcing reset
        if (!forceReset && !isSideChatVisible && binding.sideChatContainer.visibility != View.VISIBLE) return
        
        val chat = binding.chatContainer
        resetSideChatContainerToDefaultState(keepVisibility = false)
        
        // Remove chat from side container if it's there
        if (chat.parent == binding.sideChatContainer) {
            binding.sideChatContainer.removeView(chat)
            chat.setOnTouchListener(null)
            
            if (originalChatParent != null) {
                val safeIndex = if (originalChatIndex in 0..originalChatParent!!.childCount) originalChatIndex else -1
                originalChatParent?.addView(chat, safeIndex, originalChatParams)
            }
        }
        
        // --- Restore ActionBar (Tablets / Phone Side Chat) ---
        if (isActionBarReparentedToSide) {
            val actionBar = binding.actionBar
            if (actionBar.parent == binding.sideChatContainer) {
                binding.sideChatContainer.removeView(actionBar)
                
                if (originalActionBarParent != null) {
                    val safeIndex = if (originalActionBarIndex in 0..originalActionBarParent!!.childCount) originalActionBarIndex else -1
                    originalActionBarParent?.addView(actionBar, safeIndex, originalActionBarParams)
                }
                isActionBarReparentedToSide = false
                originalActionBarParent = null
            }
        }

        isSideChatVisible = false
        originalChatParent = null
        chat.setPadding(chat.paddingLeft, chat.paddingTop, chat.paddingRight, 0)
        
        // CRITICAL: Reset video layout params to prevent "Stuck Split Screen"
        val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.endToStart = ConstraintLayout.LayoutParams.UNSET
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        binding.videoContainer.layoutParams = params

        // Restore all overlay backgrounds, text shadows, control insets, and resize mode
        applyChatOverlayAppearance()
        refreshPlayerWindowInsets()
        applyEffectiveVideoResizeMode()
        ViewCompat.requestApplyInsets(binding.chatContainer)

        // Restore Chat Rules Container to default
        activity.chatRulesManager.updateContainer(activity.binding.bottomSheetCoordinator)
    }

    /**
     * Toggles between fullscreen and portrait mode.
     */
    fun toggleFullscreen() {
        if (activity.isFullscreen) {
            exitFullscreen()
        } else {
            enterFullscreen()
        }
    }

    fun onChatJumpToBottomClicked() {
        if (isTheatreMode) {
             collapseTheatreModeChat()
        }
    }

    /**
     * Enters Theatre Mode - Portrait fullscreen with transparent chat overlay.
     * Video covers entire screen, chat covers bottom half with gradient fade.
     */
    fun enterTheatreMode() {
        if (isTheatreMode) {
            exitTheatreMode()
            return
        }

        // Clean up tablet side chat if active to avoid ClassCastException
        if (isSideChatVisible) {
            cleanupSideChat()
        }

        // Enable theatre mode preference
        isTheatreMode = true
        theatreImeOpen = false
        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()

        // Update PiP state immediately to disable auto-PiP
        activity.updatePiPUi()

        // Keep portrait for theatre mode (it's a portrait-specific feature)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // FULLSCREEN is handled by WindowInsetsControllerCompat below
        // activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Hide status bar but keep navigation bar visible
        val windowInsetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Update nav bar color (handled by Activity logic: Theatre -> Transparent, Standard -> #0f0f0f)
        activity.updateNavigationBarColor(true)

        // Calculate fullscreen dimensions (same approach as StreamFeedAdapter.setScaleMode)
        val displayMetrics = activity.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        (screenHeight * (16f / 9f)).toInt()

        // Position chat container at bottom 30% of screen (smaller, will expand on scroll)
        // Position chat container at bottom 30% of screen (smaller, will expand on scroll)
        // Default to Fit Mode heights (larger chat area)
        theatreModeChatCollapsedHeight = (screenHeight * 0.45f).toInt()
        theatreModeChatExpandedHeight = (screenHeight * 0.70f).toInt()
        
        val chatParams = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
        chatParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        chatParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
        chatParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        chatParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        chatParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        chatParams.width = 0
        chatParams.height = theatreModeChatCollapsedHeight
        chatParams.matchConstraintPercentHeight = 1f
        // No bottom margin - content extends to edge, nav bar overlays
        chatParams.bottomMargin = 0
        binding.chatContainer.layoutParams = chatParams
        
        // Hide seekbar in theatre mode
        binding.playerSeekBar.visibility = View.GONE
        
        // Override chat container insets listener for theatre mode
        // In theatre mode, the chat container has a fixed height. Using padding shrinks the content area.
        // Instead, we use bottomMargin to push the entire container up above the keyboard.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.chatContainer) { view, insets ->
            val imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            val navHeight = systemBars.bottom
            val keyboardHeight = imeInsets.bottom
            
            val params = view.layoutParams as ConstraintLayout.LayoutParams
            // Push up by keyboard height MINUS nav height to remove gap
            // The container might already have nav bar padding from parent or insets logic
            params.bottomMargin = if (keyboardHeight > 0) {
                 kotlin.math.max(0, keyboardHeight - navHeight)
            } else {
                 0 
            }
            
            view.layoutParams = params
            
            // Ensure padding is reset to 0 (bottom) so we don't double-dip if some other logic added padding
            // Also force top padding to 0 for Theatre Mode
            view.setPadding(view.paddingLeft, 0, view.paddingRight, 0)
            
            // Track keyboard open state and re-apply user-configured overlay opacity (with IME floor)
            theatreImeOpen = keyboardHeight > 0
            applyChatOverlayAppearance()
            
            insets
        }

        // Style chat container for overlay
        binding.chatContainer.visibility = View.VISIBLE
        binding.chatContainer.setBackgroundColor(Color.TRANSPARENT)
        ViewCompat.setElevation(binding.chatContainer, 1000f)
        ViewCompat.setTranslationZ(binding.chatContainer, 1000f)
        // Ensure no shadow clipping/issues
        binding.chatContainer.outlineProvider = null
        
        // Disable clipping so translated views are visible above keyboard
        binding.chatContainer.clipChildren = false
        binding.chatContainer.clipToPadding = false
        binding.root.clipChildren = false
        binding.playerScreenContainer.clipChildren = false

        // Remove gradient from chat list container
        binding.chatListContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.chatInputRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.chatInput.setBackgroundResource(dev.xacnio.kciktv.R.drawable.bg_chat_input_border)
        
        // Elevate chat input to ensure it's always on top
        ViewCompat.setElevation(binding.chatInputRoot, 2000f)
        ViewCompat.setTranslationZ(binding.chatInputRoot, 2000f)
        
        // Make quick emote bar fully transparent and elevate
        binding.quickEmoteRecyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        ViewCompat.setElevation(binding.quickEmoteRecyclerView, 2000f)
        ViewCompat.setTranslationZ(binding.quickEmoteRecyclerView, 2000f)
        
        // Force bring to front
        binding.chatContainer.bringToFront()
        binding.playerScreenContainer.invalidate()

        // Add bottom gradient to screen (30% from bottom up)
        binding.theatreBottomGradient.let { gradient ->
            gradient.visibility = View.VISIBLE
            val gradientParams = gradient.layoutParams as? ConstraintLayout.LayoutParams
            gradientParams?.let {
                it.height = (screenHeight * 0.50f).toInt()
                gradient.layoutParams = it
            }
            ViewCompat.setElevation(gradient, 1f)
            ViewCompat.setZ(gradient, 1f)
        }

        // Do NOT reparent video overlay to root, so chat remains on top
        // We keep it in its original hierarchy but lower elevation than chat
        
        /*
        // Code removed to prevent overlay from covering chat
        val overlay = binding.videoOverlay
        val navBarHeight = getNavigationBarHeight()
        val overlayBottomMargin = navBarHeight + (5 * activity.resources.displayMetrics.density).toInt()
        if (overlay.parent != binding.root) {
            originalOverlayParent = overlay.parent as android.view.ViewGroup
            originalOverlayIndex = originalOverlayParent?.indexOfChild(overlay) ?: -1
            originalOverlayParams = overlay.layoutParams
            
            originalOverlayParent?.removeView(overlay)
            
            // Add to root (ConstraintLayout)
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.width = screenWidth
            newParams.bottomMargin = overlayBottomMargin
            
            root.addView(overlay, newParams)
        } else {
             // Just update params if already root
             val overlayParams = overlay.layoutParams as ConstraintLayout.LayoutParams
             overlayParams.width = screenWidth
             overlayParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
             overlayParams.bottomMargin = overlayBottomMargin
             overlay.layoutParams = overlayParams
        }
        */
        
        // Add background to video overlay for better visibility
        binding.videoOverlay.setBackgroundResource(R.drawable.gradient_bottom_dark)
        
        // Ensure elevation is lower than chat (which is set to 1000f/2000f above)
        ViewCompat.setElevation(binding.videoOverlay, 100f)
        ViewCompat.setZ(binding.videoOverlay, 100f)
        binding.videoOverlay.isClickable = true

        // Hide global header and other UI panels
        binding.mobileHeader.visibility = View.GONE
        binding.infoPanel.visibility = View.GONE
        binding.actionBar.visibility = View.GONE

        // --- Create Theatre Top Bar (Always Visible) ---
        if (theatreTopBar == null) {
            theatreTopBar = android.widget.LinearLayout(activity).apply {
                id = View.generateViewId()
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
                setPadding(
                    (16 * activity.resources.displayMetrics.density).toInt(),
                    (8 * activity.resources.displayMetrics.density).toInt(),
                    (16 * activity.resources.displayMetrics.density).toInt(),
                    (8 * activity.resources.displayMetrics.density).toInt()
                )
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(android.graphics.Color.parseColor("#D9000000"), android.graphics.Color.TRANSPARENT)
                ) 
                gravity = android.view.Gravity.CENTER_VERTICAL
                isClickable = true 
            }
            (binding.root as android.view.ViewGroup).addView(theatreTopBar)
            ViewCompat.setElevation(theatreTopBar!!, 160f)
            ViewCompat.setZ(theatreTopBar!!, 160f)

            // --- Reparent Buttons to Top Bar ---
            
            // Return to Live Button (Top Left)
            val returnLiveBtn = binding.returnToLiveButton
            originalReturnLiveParent = returnLiveBtn.parent as android.view.ViewGroup
            originalReturnLiveIndex = originalReturnLiveParent?.indexOfChild(returnLiveBtn) ?: -1
            originalReturnLiveParams = returnLiveBtn.layoutParams
            
            originalReturnLiveParent?.removeView(returnLiveBtn)
            
            // Style for top bar
             val returnLiveParams = android.widget.LinearLayout.LayoutParams(
                 android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                 (28 * activity.resources.displayMetrics.density).toInt()
             ).apply {
                 marginEnd = (8 * activity.resources.displayMetrics.density).toInt()
                 gravity = android.view.Gravity.CENTER_VERTICAL
             }
             returnLiveBtn.layoutParams = returnLiveParams
             theatreTopBar?.addView(returnLiveBtn)
            
            // Viewer
            val viewerLayout = binding.viewerLayout
            originalViewerParent = viewerLayout.parent as android.view.ViewGroup
            originalViewerIndex = originalViewerParent?.indexOfChild(viewerLayout) ?: -1
            originalViewerParams = viewerLayout.layoutParams
            originalViewerParent?.removeView(viewerLayout)
            theatreTopBar?.addView(viewerLayout)

            // Uptime Divider
            val uptimeDivider = binding.uptimeDivider
            if (uptimeDivider.visibility == View.VISIBLE || activity.prefs.isLoggedIn) {
                 originalDividerParent = uptimeDivider.parent as android.view.ViewGroup
                 originalDividerIndex = originalDividerParent?.indexOfChild(uptimeDivider) ?: -1
                 originalDividerParams = uptimeDivider.layoutParams
                 
                 originalDividerParent?.removeView(uptimeDivider)
                 
                 uptimeDivider.visibility = View.VISIBLE
                 theatreTopBar?.addView(uptimeDivider)
            }

            // Stream Time
            val timeBadge = binding.streamTimeBadge
            if (timeBadge.visibility == View.VISIBLE || activity.prefs.isLoggedIn) {
                 // Only move if it's potentially visible or enabled
                 originalTimeParent = timeBadge.parent as android.view.ViewGroup
                 originalTimeIndex = originalTimeParent?.indexOfChild(timeBadge) ?: -1
                 originalTimeParams = timeBadge.layoutParams
                 
                 originalTimeParent?.removeView(timeBadge)
                 
                 // Style for top bar
                 val timeParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                     marginStart = (8 * activity.resources.displayMetrics.density).toInt()
                     gravity = android.view.Gravity.CENTER_VERTICAL
                 }
                 timeBadge.layoutParams = timeParams
                 timeBadge.visibility = View.VISIBLE // Ensure it's visible in top bar
                 theatreTopBar?.addView(timeBadge)
            }

            // Spacer
            theatreTopBar?.addView(android.view.View(activity).apply {
                 layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
            })

            // Video Quality Badge
            val qualityBadge = binding.videoQualityBadge
            originalQualityParent = qualityBadge.parent as android.view.ViewGroup
            originalQualityIndex = originalQualityParent?.indexOfChild(qualityBadge) ?: -1
            originalQualityParams = qualityBadge.layoutParams
            originalQualityParent?.removeView(qualityBadge)
            
            // Adjust params for LinearLayout
            val qualityParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                 marginStart = (12 * activity.resources.displayMetrics.density).toInt()
                 gravity = android.view.Gravity.CENTER_VERTICAL
            }
            qualityBadge.layoutParams = qualityParams
            theatreTopBar?.addView(qualityBadge)

            // Video Settings Button
            val settingsBtn = binding.videoSettingsButton
            originalSettingsParent = settingsBtn.parent as android.view.ViewGroup
            originalSettingsIndex = originalSettingsParent?.indexOfChild(settingsBtn) ?: -1
            originalSettingsParams = settingsBtn.layoutParams
            originalSettingsParent?.removeView(settingsBtn)
            
            val settingsParams = android.widget.LinearLayout.LayoutParams((36 * activity.resources.displayMetrics.density).toInt(), (36 * activity.resources.displayMetrics.density).toInt()).apply {
                 marginStart = (8 * activity.resources.displayMetrics.density).toInt()
                 gravity = android.view.Gravity.CENTER_VERTICAL
            }
            settingsBtn.layoutParams = settingsParams
            // Update padding for smaller touch target in top bar
            settingsBtn.setPadding((6 * activity.resources.displayMetrics.density).toInt(), (6 * activity.resources.displayMetrics.density).toInt(), (6 * activity.resources.displayMetrics.density).toInt(), (6 * activity.resources.displayMetrics.density).toInt())
            theatreTopBar?.addView(settingsBtn)

            // Clip
            val clipContainer = binding.clipButtonContainer 
            originalClipParent = clipContainer.parent as android.view.ViewGroup
            originalClipIndex = originalClipParent?.indexOfChild(clipContainer) ?: -1
            originalClipParams = clipContainer.layoutParams
            originalClipParent?.removeView(clipContainer)
            theatreTopBar?.addView(clipContainer)

            // Mute
            val muteButton = binding.muteButton
            originalMuteParent = muteButton.parent as android.view.ViewGroup
            originalMuteIndex = originalMuteParent?.indexOfChild(muteButton) ?: -1
            originalMuteParams = muteButton.layoutParams
            originalMuteParent?.removeView(muteButton)
            theatreTopBar?.addView(muteButton)
            
            // Hide original fullscreen button and theatre mode button
            binding.fullscreenButton.visibility = android.view.View.GONE
            binding.theatreModeButton.visibility = android.view.View.GONE
            // binding.uptimeDivider.visibility = android.view.View.GONE // Now managed by reparenting
            
            // Adjust margins
            val displayDensity = activity.resources.displayMetrics.density
            val marginParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (12 * displayDensity).toInt()
            }
            clipContainer.layoutParams = marginParams
            muteButton.layoutParams = marginParams
        }

        // --- Reparent Emote Combo (Top Right) ---
        val emoteCombo = binding.emoteComboContainer
        if (emoteCombo.parent != binding.root) {
             originalEmoteComboParent = emoteCombo.parent as android.view.ViewGroup
             originalEmoteComboIndex = originalEmoteComboParent?.indexOfChild(emoteCombo) ?: -1
             originalEmoteComboParams = emoteCombo.layoutParams
             
             originalEmoteComboParent?.removeView(emoteCombo)
             
             val root = binding.root as android.view.ViewGroup
             val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
             newParams.topToBottom = theatreTopBar!!.id // Below Top Bar
             newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
             newParams.topMargin = (16 * activity.resources.displayMetrics.density).toInt()
             newParams.marginEnd = (16 * activity.resources.displayMetrics.density).toInt()
             
             root.addView(emoteCombo, newParams)
             ViewCompat.setElevation(emoteCombo, 155f)
             ViewCompat.setZ(emoteCombo, 155f)
        }

        // --- Reparent Floating Emote (Full Screen Height) ---
        val floatingEmote = binding.floatingEmoteContainer
        if (floatingEmote.parent != binding.root) {
             originalFloatingEmoteParent = floatingEmote.parent as android.view.ViewGroup
             originalFloatingEmoteIndex = originalFloatingEmoteParent?.indexOfChild(floatingEmote) ?: -1
             originalFloatingEmoteParams = floatingEmote.layoutParams
             
             originalFloatingEmoteParent?.removeView(floatingEmote)
             
             val root = binding.root as android.view.ViewGroup
             val newParams = ConstraintLayout.LayoutParams((120 * activity.resources.displayMetrics.density).toInt(), ConstraintLayout.LayoutParams.MATCH_PARENT)
             newParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
             newParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
             newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
             
             root.addView(floatingEmote, newParams)
             ViewCompat.setElevation(floatingEmote, 140f) 
             ViewCompat.setZ(floatingEmote, 140f)
        }

        // --- Reparent InfoPanel to Root (Below TopBar) ---
        val infoPanel = binding.infoPanel
        if (infoPanel.parent != binding.root) {
            originalInfoPanelParent = infoPanel.parent as android.view.ViewGroup
            originalInfoPanelIndex = originalInfoPanelParent?.indexOfChild(infoPanel) ?: -1
            originalInfoPanelParams = infoPanel.layoutParams
            
            originalInfoPanelParent?.removeView(infoPanel)
            
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.topToBottom = theatreTopBar!!.id // Below Top Bar
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            
            root.addView(infoPanel, newParams)
        } else {
             val params = infoPanel.layoutParams as ConstraintLayout.LayoutParams
             params.topToBottom = theatreTopBar!!.id
             params.topToTop = ConstraintLayout.LayoutParams.UNSET
             infoPanel.layoutParams = params
        }
        
        // Ensure visibility and styling
        binding.infoPanel.visibility = View.GONE
        binding.infoPanel.bringToFront()
        binding.infoPanel.setBackgroundColor(android.graphics.Color.parseColor("#CC0f0f0f")) // Semi-transparent black
        ViewCompat.setElevation(binding.infoPanel, 150f)
        ViewCompat.setZ(binding.infoPanel, 150f)

        // --- Reparent ActionBar to Root ---
        val actionBar = binding.actionBar
        if (actionBar.parent != binding.root) {
            originalActionBarParent = actionBar.parent as android.view.ViewGroup
            originalActionBarIndex = originalActionBarParent?.indexOfChild(actionBar) ?: -1
            originalActionBarParams = actionBar.layoutParams
            
            originalActionBarParent?.removeView(actionBar)
            
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.topToBottom = binding.infoPanel.id // Below InfoPanel
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            
            root.addView(actionBar, newParams)
        } else {
             val params = actionBar.layoutParams as ConstraintLayout.LayoutParams
             params.topToBottom = binding.infoPanel.id
             actionBar.layoutParams = params
        }

        // Ensure visibility and styling
        binding.actionBar.visibility = View.GONE
        binding.actionBar.bringToFront()
        ViewCompat.setElevation(binding.actionBar, 150f)
        ViewCompat.setZ(binding.actionBar, 150f)

        // --- Reparent Pinned Gifts ---
        val pinnedGifts = binding.pinnedGiftsBlur 
        if (pinnedGifts.parent != binding.root) {
            originalPinnedGiftsParent = pinnedGifts.parent as android.view.ViewGroup
            originalPinnedGiftsIndex = originalPinnedGiftsParent?.indexOfChild(pinnedGifts) ?: -1
            originalPinnedGiftsParams = pinnedGifts.layoutParams
            
            originalPinnedGiftsParent?.removeView(pinnedGifts)
            
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.topToBottom = theatreTopBar!!.id // Anchor to visible Top Bar
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            
            root.addView(pinnedGifts, newParams)
            ViewCompat.setElevation(pinnedGifts, 150f)
            ViewCompat.setZ(pinnedGifts, 150f)
            // pinnedGifts.background = null // Preserving background as it affects layout/visuals
            pinnedGifts.requestLayout()
        }

        // --- Reparent Pinned Message ---
        val pinnedContainer = binding.pinnedMessageContainer
        if (pinnedContainer.parent != binding.root) {
            originalPinnedParent = pinnedContainer.parent as android.view.ViewGroup
            originalPinnedIndex = originalPinnedParent?.indexOfChild(pinnedContainer) ?: -1
            originalPinnedParams = pinnedContainer.layoutParams
            
            originalPinnedParent?.removeView(pinnedContainer)
            
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.topToBottom = binding.pinnedGiftsBlur.id // Below Pinned Gifts
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.marginStart = (12 * activity.resources.displayMetrics.density).toInt()
            newParams.marginEnd = (12 * activity.resources.displayMetrics.density).toInt()
            
            root.addView(pinnedContainer, newParams)
        }
        
        // --- Reparent Poll ---
        val pollContainer = binding.pollContainer
        if (pollContainer.parent != binding.root) {
            originalPollParent = pollContainer.parent as android.view.ViewGroup
            originalPollIndex = originalPollParent?.indexOfChild(pollContainer) ?: -1
            originalPollParams = pollContainer.layoutParams
            
            originalPollParent?.removeView(pollContainer)
            
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.topToBottom = binding.pinnedMessageContainer.id 
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.marginStart = (12 * activity.resources.displayMetrics.density).toInt()
            newParams.marginEnd = (12 * activity.resources.displayMetrics.density).toInt()
            
            root.addView(pollContainer, newParams)
        }

        // --- Reparent Prediction ---
        val predictionContainer = binding.predictionContainer
        if (predictionContainer.parent != binding.root) {
            originalPredictionParent = predictionContainer.parent as android.view.ViewGroup
            originalPredictionIndex = originalPredictionParent?.indexOfChild(predictionContainer) ?: -1
            originalPredictionParams = predictionContainer.layoutParams
            
            originalPredictionParent?.removeView(predictionContainer)
            
            val root = binding.root as android.view.ViewGroup
            val newParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            newParams.topToBottom = binding.pollContainer.id
            newParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            newParams.marginStart = (12 * activity.resources.displayMetrics.density).toInt()
            newParams.marginEnd = (12 * activity.resources.displayMetrics.density).toInt()
            
            root.addView(predictionContainer, newParams)
        }

        ViewCompat.setElevation(binding.pinnedMessageContainer, 150f)
        ViewCompat.setZ(binding.pinnedMessageContainer, 150f)
        
        ViewCompat.setElevation(binding.pollContainer, 150f)
        ViewCompat.setZ(binding.pollContainer, 150f)
        
        ViewCompat.setElevation(binding.predictionContainer, 150f)
        ViewCompat.setZ(binding.predictionContainer, 150f)

        // Setup chat scroll listener for expand/collapse behavior
        setupTheatreModeChatScrollListener()

        // Update fullscreen state
        fullscreenManager.isFullscreen = true
        updateFullscreenButtonState()

        // --- Setup Video for Theatre Mode ---
        16f / 9f
        // val targetZoomWidth = (screenHeight * videoAspectRatio).toInt() // Initial calculation only needed for Zoom toggle

        val videoParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        originalVideoDimensionRatio = videoParams.dimensionRatio

        // Default: FIT (Width 100%, Height adapted by ratio)
        val targetRatio = if (currentVideoWidth > 0 && currentVideoHeight > 0) {
             "$currentVideoWidth:$currentVideoHeight"
        } else {
             originalVideoDimensionRatio ?: "16:9"
        }

        videoParams.dimensionRatio = targetRatio
        videoParams.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        videoParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        
        // Remove percentage constraints
        videoParams.matchConstraintPercentHeight = 1.0f
        videoParams.matchConstraintPercentWidth = 1.0f
        
        // Manual positioning with margin to define the "Box"
        // Top: 15%, Bottom: 35% (Space for chat)
        videoParams.topMargin = (screenHeight * 0.15f).toInt()
        videoParams.bottomMargin = (screenHeight * 0.35f).toInt()
        
        // Ensure constraints are set
        videoParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        
        videoParams.verticalBias = 0.5f 
        
        binding.videoContainer.layoutParams = videoParams
        
        isTheatreFitMode = true
        
        // Auto-switch to Zoom (Fill) mode for Portrait content
        if (currentVideoHeight > 0 && currentVideoHeight > currentVideoWidth) {
             toggleTheatreFitMode()
        }

        val longClickListener = android.view.View.OnLongClickListener {
            toggleTheatreFitMode()
            true
        }
        binding.videoContainer.setOnLongClickListener(longClickListener)
        binding.videoOverlay.setOnLongClickListener(longClickListener)
        binding.theatreBackground.setOnLongClickListener(longClickListener)
        binding.playPauseOverlay.setOnLongClickListener(longClickListener)
        
        // --- Setup Theatre Background ---
        binding.theatreBackground.visibility = View.VISIBLE
        binding.theatreBackground.setImageDrawable(null)
        binding.theatreBackground.setBackgroundColor(android.graphics.Color.BLACK)

        binding.theatreBackground.setOnClickListener {
            if (isTheatreMode) {
                activity.handleScreenTap()
            }
        }

        // Apply configured transparent overlay opacity & text shadows across Theatre Mode views
        applyChatOverlayAppearance()

        // Show toast
        Toast.makeText(activity, R.string.theatre_mode_enabled, Toast.LENGTH_SHORT).show()
    }
    
    // Theatre mode chat heights
    private var theatreModeChatCollapsedHeight = 0
    private var theatreModeChatExpandedHeight = 0
    private var chatCollapseHandler: android.os.Handler? = null
    private var chatCollapseRunnable: Runnable? = null
    
    /**
     * Sets up scroll listener for chat to expand/collapse in Theatre Mode
     */
    private fun setupTheatreModeChatScrollListener() {
        binding.chatRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!isTheatreMode) return
                if (isTheatreFitMode) return // Don't expand/collapse in Fit mode
                
                when (newState) {
                    androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING -> {
                        // User started scrolling - expand chat
                        cancelChatCollapse()
                        expandTheatreModeChat()
                    }
                    androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE -> {
                        // User stopped scrolling - schedule collapse if auto-scroll is enabled
                        if (chatUiManager.isChatAutoScrollEnabled) {
                            scheduleChatCollapse()
                        }
                    }
                }
            }
        })
    }
    
    private fun expandTheatreModeChat() {
        val chatParams = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
        if (chatParams.height == theatreModeChatExpandedHeight) return
        
        // Animate expansion
        val animator = android.animation.ValueAnimator.ofInt(chatParams.height, theatreModeChatExpandedHeight)
        animator.duration = 200
        animator.addUpdateListener { animation ->
            val params = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
            params.height = animation.animatedValue as Int
            binding.chatContainer.layoutParams = params
        }
        animator.start()
    }
    
    private fun collapseTheatreModeChat() {
        val chatParams = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
        if (chatParams.height == theatreModeChatCollapsedHeight) return
        
        // Animate collapse
        val animator = android.animation.ValueAnimator.ofInt(chatParams.height, theatreModeChatCollapsedHeight)
        animator.duration = 200
        animator.addUpdateListener { animation ->
            val params = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
            params.height = animation.animatedValue as Int
            binding.chatContainer.layoutParams = params
        }
        animator.start()
    }
    
    private fun scheduleChatCollapse() {
        cancelChatCollapse()
        chatCollapseHandler = android.os.Handler(android.os.Looper.getMainLooper())
        chatCollapseRunnable = Runnable {
            if (isTheatreMode) {
                collapseTheatreModeChat()
            }
        }
        chatCollapseHandler?.postDelayed(chatCollapseRunnable!!, 3000) // 3 seconds
        
        // Ensure chat padding is reset for Theatre Mode (should be 0)
        activity.channelUiManager.updateChatPaddingForPanels(false)
    }
    
    private fun cancelChatCollapse() {
        chatCollapseRunnable?.let { chatCollapseHandler?.removeCallbacks(it) }
        chatCollapseRunnable = null
        chatCollapseHandler = null
    }

    /**
     * Exits Theatre Mode back to normal portrait view.
     */
    fun exitTheatreMode() {
        if (!isTheatreMode) return

        // Cancel any pending chat collapse
        cancelChatCollapse()
        
        // Restore original insets listener for chat container
        // This ensures normal behavior when exiting theatre mode
        activity.setupChatContainerInsetsListener()
        
        // Remove old keyboard listener if any
        removeTheatreModeKeyboardListener()
        binding.chatInputRoot.translationY = 0f
        binding.quickEmoteRecyclerView.translationY = 0f
        
        // Reset clipping
        binding.chatContainer.clipChildren = true
        binding.chatContainer.clipToPadding = true
        binding.root.clipChildren = true
        binding.playerScreenContainer.clipChildren = true
        
        // Show seekbar again
        binding.playerSeekBar.visibility = View.VISIBLE

        isTheatreMode = false
        theatreImeOpen = false

        // Respect system rotation setting when exiting theatre mode
        // Always force portrait to guarantee the rotation fires even if the device is held
        // in landscape (SENSOR would leave the window in landscape, breaking the layout).
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // After layout settles, restore USER so the user can freely rotate again.
        binding.root.postDelayed({
            if (activity.isRotationAllowed()) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }, 1000)
        // activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // --- Restore Video ---
        if (originalVideoDimensionRatio != null) {
            val videoParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            videoParams.dimensionRatio = originalVideoDimensionRatio
            videoParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT
            videoParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            binding.videoContainer.layoutParams = videoParams
        }
        
        binding.videoContainer.setOnLongClickListener(null)
        binding.videoOverlay.setOnLongClickListener(null)
        binding.theatreBackground.setOnLongClickListener(null)
        binding.playPauseOverlay.setOnLongClickListener(null)
        binding.theatreBackground.setOnClickListener(null)
        binding.theatreBackground.visibility = View.GONE

        // Show system bars
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        
        // Restore standard player nav bar color (#0f0f0f)
        activity.updateNavigationBarColor(true)

        // Show global header (Disabled by user request)
        // binding.mobileHeader.visibility = View.VISIBLE
        binding.fullscreenButton.visibility = View.VISIBLE
        binding.theatreModeButton.visibility = View.VISIBLE
        // binding.uptimeDivider.visibility = View.VISIBLE // Restored via reparenting

        // --- Restore InfoPanel ---
        val infoPanel = binding.infoPanel
        val rootGroup = binding.root as android.view.ViewGroup
        
        if (originalInfoPanelParent != null && infoPanel.parent == rootGroup) {
            rootGroup.removeView(infoPanel)
            val parent = originalInfoPanelParent!!
            try {
                val safeIndex = if (originalInfoPanelIndex >= 0 && originalInfoPanelIndex <= parent.childCount) originalInfoPanelIndex else -1
                parent.addView(infoPanel, safeIndex, originalInfoPanelParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore InfoPanel at index $originalInfoPanelIndex", e)
                parent.addView(infoPanel, originalInfoPanelParams)
            }
        }
        originalInfoPanelParent = null
        binding.infoPanel.setBackgroundColor(android.graphics.Color.parseColor("#CC0f0f0f")) 

        // --- Restore ActionBar ---
        val actionBar = binding.actionBar
        if (originalActionBarParent != null && actionBar.parent == rootGroup) {
            rootGroup.removeView(actionBar)
            val parent = originalActionBarParent!!
            try {
                val safeIndex = if (originalActionBarIndex >= 0 && originalActionBarIndex <= parent.childCount) originalActionBarIndex else -1
                parent.addView(actionBar, safeIndex, originalActionBarParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore ActionBar at index $originalActionBarIndex", e)
                parent.addView(actionBar, originalActionBarParams)
            }
        }
        originalActionBarParent = null
        binding.actionBar.setBackgroundColor(android.graphics.Color.parseColor("#CC121212"))

        // --- Restore Pinned Gifts ---
        val pinnedGifts = binding.pinnedGiftsBlur
        if (originalPinnedGiftsParent != null && pinnedGifts.parent == rootGroup) {
            rootGroup.removeView(pinnedGifts)
            val parent = originalPinnedGiftsParent!!
            val safeIndex = if (originalPinnedGiftsIndex in 0..parent.childCount) originalPinnedGiftsIndex else -1
            parent.addView(pinnedGifts, safeIndex, originalPinnedGiftsParams)
        }
        originalPinnedGiftsParent = null
        val displayDensity = activity.resources.displayMetrics.density
        ViewCompat.setElevation(pinnedGifts, 10f * displayDensity) 
        ViewCompat.setZ(pinnedGifts, 0f)
        pinnedGifts.setBackgroundColor(android.graphics.Color.parseColor("#CC0f0f0f"))

        // --- Restore Pinned Message ---
        val pinnedContainer = binding.pinnedMessageContainer
        if (originalPinnedParent != null && pinnedContainer.parent == rootGroup) {
            rootGroup.removeView(pinnedContainer)
            val parent = originalPinnedParent!!
            try {
                val safeIndex = if (originalPinnedIndex >= 0 && originalPinnedIndex <= parent.childCount) originalPinnedIndex else -1
                parent.addView(pinnedContainer, safeIndex, originalPinnedParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore PinnedMessage at index $originalPinnedIndex", e)
                parent.addView(pinnedContainer, originalPinnedParams)
            }
        }
        originalPinnedParent = null

        // --- Restore Poll ---
        val pollContainer = binding.pollContainer
        if (originalPollParent != null && pollContainer.parent == rootGroup) {
            rootGroup.removeView(pollContainer)
            val parent = originalPollParent!!
            try {
                val safeIndex = if (originalPollIndex >= 0 && originalPollIndex <= parent.childCount) originalPollIndex else -1
                parent.addView(pollContainer, safeIndex, originalPollParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore Poll at index $originalPollIndex", e)
                parent.addView(pollContainer, originalPollParams)
            }
        }
        originalPollParent = null

        // --- Restore Prediction ---
        val predictionContainer = binding.predictionContainer
        if (originalPredictionParent != null && predictionContainer.parent == rootGroup) {
            rootGroup.removeView(predictionContainer)
            val parent = originalPredictionParent!!
            try {
                val safeIndex = if (originalPredictionIndex >= 0 && originalPredictionIndex <= parent.childCount) originalPredictionIndex else -1
                parent.addView(predictionContainer, safeIndex, originalPredictionParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore Prediction at index $originalPredictionIndex", e)
                parent.addView(predictionContainer, originalPredictionParams)
            }
        }
        originalPredictionParent = null

        // --- Restore Buttons from Top Bar (In Reverse Order of Removal) ---
        
        // 1. Mute Button
        val muteButton = binding.muteButton
        if (originalMuteParent != null && muteButton.parent == theatreTopBar) {
            theatreTopBar?.removeView(muteButton)
            val parent = originalMuteParent!!
            try {
                val safeIndex = if (originalMuteIndex >= 0 && originalMuteIndex <= parent.childCount) originalMuteIndex else -1
                parent.addView(muteButton, safeIndex, originalMuteParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore MuteButton", e)
                parent.addView(muteButton, originalMuteParams)
            }
        }
        originalMuteParent = null

        // 2. Clip Button
        val clipContainer = binding.clipButtonContainer 
        if (originalClipParent != null && clipContainer.parent == theatreTopBar) {
            theatreTopBar?.removeView(clipContainer)
            val parent = originalClipParent!!
            try {
                val safeIndex = if (originalClipIndex >= 0 && originalClipIndex <= parent.childCount) originalClipIndex else -1
                parent.addView(clipContainer, safeIndex, originalClipParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore ClipButton", e)
                parent.addView(clipContainer, originalClipParams)
            }
        }
        originalClipParent = null

        // 3. Settings Button
        val settingsBtn = binding.videoSettingsButton
        if (originalSettingsParent != null && settingsBtn.parent == theatreTopBar) {
            theatreTopBar?.removeView(settingsBtn)
            val parent = originalSettingsParent!!
            try {
                settingsBtn.setPadding((8 * displayDensity).toInt(), (8 * displayDensity).toInt(), (8 * displayDensity).toInt(), (8 * displayDensity).toInt())

                val safeIndex = if (originalSettingsIndex >= 0 && originalSettingsIndex <= parent.childCount) originalSettingsIndex else -1
                parent.addView(settingsBtn, safeIndex, originalSettingsParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore SettingsButton", e)
                parent.addView(settingsBtn, originalSettingsParams)
            }
        }
        originalSettingsParent = null
        
        // 4. Quality Badge
        val qualityBadge = binding.videoQualityBadge
        if (originalQualityParent != null && qualityBadge.parent == theatreTopBar) {
            theatreTopBar?.removeView(qualityBadge)
            val parent = originalQualityParent!!
            try {
                val safeIndex = if (originalQualityIndex >= 0 && originalQualityIndex <= parent.childCount) originalQualityIndex else -1
                parent.addView(qualityBadge, safeIndex, originalQualityParams)
            } catch (e: Exception) {
                 Log.e("FullscreenToggleManager", "Failed to restore QualityBadge", e)
                 parent.addView(qualityBadge, originalQualityParams)
            }
        }
        originalQualityParent = null

        // 5. Stream Time
        val timeBadge = binding.streamTimeBadge
        if (originalTimeParent != null && timeBadge.parent == theatreTopBar) {
            theatreTopBar?.removeView(timeBadge)
             val parent = originalTimeParent!!
            try {
                val safeIndex = if (originalTimeIndex >= 0 && originalTimeIndex <= parent.childCount) originalTimeIndex else -1
                parent.addView(timeBadge, safeIndex, originalTimeParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore TimeBadge", e)
                parent.addView(timeBadge, originalTimeParams)
            }
        }
        originalTimeParent = null
        
        // 6. Uptime Divider
        val uptimeDivider = binding.uptimeDivider
        if (originalDividerParent != null && uptimeDivider.parent == theatreTopBar) {
            theatreTopBar?.removeView(uptimeDivider)
            val parent = originalDividerParent!!
            try {
                val safeIndex = if (originalDividerIndex >= 0 && originalDividerIndex <= parent.childCount) originalDividerIndex else -1
                parent.addView(uptimeDivider, safeIndex, originalDividerParams)
                uptimeDivider.visibility = View.VISIBLE 
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore UptimeDivider", e)
                parent.addView(uptimeDivider, originalDividerParams)
            }
        }
        originalDividerParent = null
        
        // 7. Viewer Layout
        val viewerLayout = binding.viewerLayout
        if (originalViewerParent != null && viewerLayout.parent == theatreTopBar) {
            theatreTopBar?.removeView(viewerLayout)
            val parent = originalViewerParent!!
            try {
                val safeIndex = if (originalViewerIndex >= 0 && originalViewerIndex <= parent.childCount) originalViewerIndex else -1
                parent.addView(viewerLayout, safeIndex, originalViewerParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore Viewer", e)
                parent.addView(viewerLayout, originalViewerParams)
            }
        }
        originalViewerParent = null

        // NOTE: Settings Button restoration moved above Clip restoration to maintain correct index order

        // Emote Combo
        val emoteCombo = binding.emoteComboContainer
        if (originalEmoteComboParent != null && emoteCombo.parent == rootGroup) {
            rootGroup.removeView(emoteCombo)
            val parent = originalEmoteComboParent!!
            try {
                val safeIndex = if (originalEmoteComboIndex >= 0 && originalEmoteComboIndex <= parent.childCount) originalEmoteComboIndex else -1
                parent.addView(emoteCombo, safeIndex, originalEmoteComboParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore EmoteCombo at index $originalEmoteComboIndex", e)
                parent.addView(emoteCombo, originalEmoteComboParams)
            }
        }
        originalEmoteComboParent = null

        // Floating Emote
        val floatingEmote = binding.floatingEmoteContainer
        if (originalFloatingEmoteParent != null && floatingEmote.parent == rootGroup) {
            rootGroup.removeView(floatingEmote)
            val parent = originalFloatingEmoteParent!!
            try {
                val safeIndex = if (originalFloatingEmoteIndex >= 0 && originalFloatingEmoteIndex <= parent.childCount) originalFloatingEmoteIndex else -1
                parent.addView(floatingEmote, safeIndex, originalFloatingEmoteParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore FloatingEmote at index $originalFloatingEmoteIndex", e)
                parent.addView(floatingEmote, originalFloatingEmoteParams)
            }
        }
        originalFloatingEmoteParent = null

        // Remove Theatre Top Bar
        if (theatreTopBar != null) {
            rootGroup.removeView(theatreTopBar)
            theatreTopBar = null
        }

        // Reset elevations for Pinned/Poll/Prediction
        ViewCompat.setElevation(binding.pinnedMessageContainer, 0f)
        ViewCompat.setZ(binding.pinnedMessageContainer, 0f)
        
        ViewCompat.setElevation(binding.pollContainer, 0f)
        ViewCompat.setZ(binding.pollContainer, 0f)
        
        ViewCompat.setElevation(binding.predictionContainer, 0f)
        ViewCompat.setZ(binding.predictionContainer, 0f)

        // Reset info panel and action bar settings
        ViewCompat.setElevation(binding.infoPanel, 0f)
        ViewCompat.setZ(binding.infoPanel, 0f)
        
        ViewCompat.setElevation(binding.actionBar, 10f * displayDensity)
        ViewCompat.setZ(binding.actionBar, 10f * displayDensity)

        // Show action bar and info panel based on state
        binding.actionBar.visibility = if (vodManager.currentPlaybackMode != dev.xacnio.kciktv.mobile.ui.player.VodManager.PlaybackMode.LIVE) View.GONE else View.VISIBLE
        binding.infoPanel.visibility = View.VISIBLE

        // Reset video container to 16:9 at top
        val videoParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        videoParams.width = 0
        videoParams.height = 0
        videoParams.dimensionRatio = "H,16:9"
        videoParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.verticalBias = 0.0f
        videoParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.topMargin = 0
        binding.videoContainer.layoutParams = videoParams

        // Reset chat container to normal position below video
        val chatParams = binding.chatContainer.layoutParams as ConstraintLayout.LayoutParams
        chatParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        chatParams.topToBottom = R.id.videoContainer
        chatParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        chatParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        chatParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        chatParams.width = 0
        chatParams.height = 0
        chatParams.matchConstraintPercentHeight = 1f
        chatParams.bottomMargin = 0 // Reset theatre mode margin
        binding.chatContainer.layoutParams = chatParams

        // Reset chat container styling
        binding.chatContainer.setBackgroundColor(Color.parseColor("#0f0f0f"))
        ViewCompat.setElevation(binding.chatContainer, 0f)
        ViewCompat.setZ(binding.chatContainer, 0f)
        
        // Reset chat container padding
        binding.chatContainer.setPadding(
            binding.chatContainer.paddingLeft,
            binding.chatContainer.paddingTop,
            binding.chatContainer.paddingRight,
            0 // Reset bottom padding
        )

        // Reset chat list container background
        binding.chatListContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.chatInputRoot.setBackgroundColor(android.graphics.Color.parseColor("#1a1a1a"))
        binding.chatInput.setBackgroundResource(dev.xacnio.kciktv.R.drawable.bg_chat_input)
        
        // Reset chatInputRoot bottom margin and elevation
        val chatInputParams = binding.chatInputRoot.layoutParams as ConstraintLayout.LayoutParams
        chatInputParams.bottomMargin = 0
        binding.chatInputRoot.layoutParams = chatInputParams
        ViewCompat.setElevation(binding.chatInputRoot, 0f)
        ViewCompat.setZ(binding.chatInputRoot, 0f)
        ViewCompat.setElevation(binding.quickEmoteRecyclerView, 0f)
        ViewCompat.setZ(binding.quickEmoteRecyclerView, 0f)
        
        // Restore Return to Live Button
        val returnLiveBtn = binding.returnToLiveButton
        if (originalReturnLiveParent != null) {
            (returnLiveBtn.parent as? android.view.ViewGroup)?.removeView(returnLiveBtn)
            val parent = originalReturnLiveParent!!
            try {
                val safeIndex = if (originalReturnLiveIndex >= 0 && originalReturnLiveIndex <= parent.childCount) originalReturnLiveIndex else -1
                parent.addView(returnLiveBtn, safeIndex, originalReturnLiveParams)
            } catch (e: Exception) {
                 parent.addView(returnLiveBtn, originalReturnLiveParams)
            }
        }
        originalReturnLiveParent = null
        
        // Reset quick emote bar background
        
        // Reset quick emote bar background
        binding.quickEmoteRecyclerView.setBackgroundColor(android.graphics.Color.parseColor("#151515"))
        
        // Hide bottom gradient and restore solid styling/shadows across all chat & overlay cards
        binding.theatreBottomGradient.visibility = View.GONE
        applyChatOverlayAppearance()

        // Restore video overlay to original parent
        val overlay = binding.videoOverlay
        val root = binding.root as android.view.ViewGroup
        
        if (originalOverlayParent != null && overlay.parent == root) {
            root.removeView(overlay)
            val parent = originalOverlayParent!!
            try {
                val safeIndex = if (originalOverlayIndex >= 0 && originalOverlayIndex <= parent.childCount) originalOverlayIndex else -1
                parent.addView(overlay, safeIndex, originalOverlayParams)
            } catch (e: Exception) {
                Log.e("FullscreenToggleManager", "Failed to restore Overlay at index $originalOverlayIndex", e)
                parent.addView(overlay, originalOverlayParams)
            }
        } else if (overlay.parent == root) {
            // Fallback if original parent lost but it is at root
             val overlayParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            overlayParams.gravity = android.view.Gravity.BOTTOM
            overlay.layoutParams = overlayParams
        }
        originalOverlayParent = null
        
        // Reset video overlay to normal (if restored to FrameLayout)
        if (binding.videoOverlay.layoutParams is android.widget.FrameLayout.LayoutParams) {
            val overlayParams = binding.videoOverlay.layoutParams as android.widget.FrameLayout.LayoutParams
            overlayParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            overlayParams.gravity = android.view.Gravity.BOTTOM
            binding.videoOverlay.layoutParams = overlayParams
        }
        
        // Clear overlay background and reset properties
        binding.videoOverlay.background = null
        ViewCompat.setElevation(binding.videoOverlay, 0f)
        ViewCompat.setZ(binding.videoOverlay, 0f)
        binding.videoOverlay.isClickable = false
        
        // Restore Standard UI - info panel visible, header stays hidden while player is active
        // mobileHeader should remain GONE during player - it gets restored when navigating away from player
        binding.infoPanel.visibility = View.VISIBLE
        
        // Action Bar logic depends on VOD/Live status
        binding.actionBar.visibility = if (vodManager.currentPlaybackMode != dev.xacnio.kciktv.mobile.ui.player.VodManager.PlaybackMode.LIVE) View.GONE else View.VISIBLE
        
        // Ensure InfoPanel is above chat container (which is 0f)
        ViewCompat.setElevation(binding.infoPanel, 100f)
        ViewCompat.setZ(binding.infoPanel, 100f)
        binding.infoPanel.translationY = 0f

        // Force layout update to fix positioning issues using post to run after current frame
        binding.root.post {
             binding.playerScreenContainer.requestLayout()
             binding.root.requestLayout()
             binding.infoPanel.requestLayout()
             
             // Force show controls and UI to ensure consistent state when exiting theatre mode
             // This solves the issue where UI elements might remain hidden if controls were hidden in theatre mode
             binding.videoOverlay.visibility = android.view.View.VISIBLE
             binding.videoOverlay.alpha = 1f
             
             binding.infoPanel.visibility = android.view.View.VISIBLE
             
             // Update chat padding to account for restored panels
             activity.channelUiManager.updateChatPaddingForPanels(false)
        }
        
        isTheatreMode = false
        fullscreenManager.isFullscreen = false
        updateFullscreenButtonState()
        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()
        
        // Update PiP state to re-enable auto-PiP
        Log.d("FullscreenToggleManager", "exitTheatreMode: Updating PiP UI to re-enable auto-PiP")
        activity.updatePiPUi()

        // Re-apply the audio-only placeholder/shrink — the video container restore above resets it.
        activity.playerManager.updateAudioOnlyVisual(activity.playerManager.isAudioOnlyActive)
    }

    /**
     * Schedule a TransitionManager pass on the root layout so that the next layout-affecting
     * changes (visibility toggles, ConstraintLayout bounds) animate smoothly instead of
     * snapping. Skips while the activity is mid-rotation — Android already runs the window
     * rotation animation then and stacking another auto-transition causes jitter.
     */
    private fun runFullscreenLayoutTransition(entering: Boolean) {
        try {
            val root = binding.root as? ViewGroup ?: return
            if (!root.isAttachedToWindow) return
            val transition = AutoTransition().apply {
                duration = if (entering) 260L else 220L
                interpolator = AccelerateDecelerateInterpolator()
                // Avoid animating the surface-view container's children — IVS player surface
                // doesn't react well to bounds animation while decoding.
                excludeChildren(binding.playerView, true)
            }
            TransitionManager.beginDelayedTransition(root, transition)
        } catch (e: Exception) {
            Log.w("FullscreenToggleManager", "transition setup failed: ${e.message}")
        }
    }


    /**
     * Enters fullscreen (landscape) mode.
     */
    fun enterFullscreen() {
        // Exit theatre mode first if active
        if (isTheatreMode) {
            exitTheatreMode()
        }


        val isTablet = activity.isTabletOrLargeWindow()


        val isCurrentlyLandscape = activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isTablet || isCurrentlyLandscape) {
            runFullscreenLayoutTransition(entering = true)
        }

        // For phones: allow 180° landscape flip if fullscreenSensorRotation or system auto-rotate is ON
        // For tablets: allow full sensor rotation when system auto-rotate is ON, or 180° landscape flip when fullscreenSensorRotation is ON
        if (isTablet) {
            if (activity.isRotationAllowed()) {
                activity.setAllowedOrientation() // Tablets use full sensor
            } else if (prefs.fullscreenSensorRotation) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.setAllowedOrientation()
            }
        } else {
            activity.requestedOrientation = resolveFullscreenOrientation()
        }
        // FULLSCREEN is handled by WindowInsetsControllerCompat below

        // Hide system bars
        val windowInsetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Reset playerScreenContainer padding (system bars are hidden in fullscreen)
        binding.playerScreenContainer.setPadding(0, 0, 0, 0)

        // Hide chat and other UI
        binding.chatContainer.visibility = View.GONE
        binding.actionBar.visibility = View.GONE
        binding.infoPanel.visibility = View.GONE
        binding.theatreModeButton.visibility = View.GONE

        // Expand player to match parent
        val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = null
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
        // Clear any margins left from theatre mode
        params.topMargin = 0
        params.bottomMargin = 0
        params.marginStart = 0
        params.marginEnd = 0
        params.verticalBias = 0.5f
        binding.videoContainer.layoutParams = params

        fullscreenManager.isFullscreen = true
        updateFullscreenButtonState()

        // In fullscreen mode, hide side chat if visible (tablet landscape)
        if (isSideChatVisible) {
            cleanupSideChat()
        } else {
            hideSideChatDivider()
        }

        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()
    }

    /**
     * Exits fullscreen mode back to portrait.
     * @param forceCleanReset If true, always reset to portrait mode without auto-applying split screen.
     *                         Used during playChannel to ensure clean state before re-applying layout.
     */
    fun exitFullscreen(forceCleanReset: Boolean = false) {
        if (isTheatreMode) {
            exitTheatreMode()
            return
        }

        clearSideChatWidthPreview()
        hideSideChatDivider()

        val isTablet = activity.resources.configuration.smallestScreenWidthDp >= 720
        
        val isVisuallyLandscape = binding.root.width > binding.root.height
        val needsPhysicalRotation = !isTablet && isVisuallyLandscape && !forceCleanReset

        if (needsPhysicalRotation) {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            if (activity.isRotationAllowed()) {
                binding.root.postDelayed({
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                }, 2000)
            }
            return // EARLY RETURN: Let onConfigurationChanged trigger the second pass for layout!
        } else if (isTablet) {
            activity.setAllowedOrientation()
            if (isVisuallyLandscape && !isSideChatVisible && !isTheatreMode) {
                showSideChat(skipAnimation = true)
            }
            fullscreenManager.isFullscreen = false
            updateFullscreenButtonState()
            applyDisplayCutoutMode()
            applyEffectiveVideoResizeMode()
            activity.playerManager.updateAudioOnlyVisual(activity.playerManager.isAudioOnlyActive)
            return // EARLY RETURN FOR TABLETS!
        }

        val transitionTargets = listOf(
            binding.root,
            binding.playerScreenContainer,
            binding.actionBar,
            binding.chatContainer,
            binding.infoPanel
        )
        val savedTransitions = transitionTargets.map { it.layoutTransition }
        transitionTargets.forEach { it.layoutTransition = null }

        applyPortraitLayout()
        cleanupSideChat()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(binding.playerScreenContainer)
        binding.chatContainer.visibility = View.VISIBLE
        binding.actionBar.visibility = if (vodManager.currentPlaybackMode != dev.xacnio.kciktv.mobile.ui.player.VodManager.PlaybackMode.LIVE) View.GONE else View.VISIBLE
        binding.infoPanel.visibility = View.VISIBLE
        binding.theatreModeButton.visibility = View.VISIBLE
        activity.channelUiManager.updateChatPaddingForPanels(false)
        fullscreenManager.isFullscreen = false
        updateFullscreenButtonState()
        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()
        activity.playerManager.updateAudioOnlyVisual(activity.playerManager.isAudioOnlyActive)

        binding.root.post {
            transitionTargets.forEachIndexed { i, v -> v.layoutTransition = savedTransitions[i] }
        }
    }

    fun applyPortraitLayout() {
        val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = "H,16:9"
        params.width = 0
        params.height = 0
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.verticalBias = 0.0f
        params.endToStart = ConstraintLayout.LayoutParams.UNSET
        binding.videoContainer.layoutParams = params
        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()
    }

    private fun ensureBaseCutoutPaddingsCaptured() {
        if (baseVideoTopBarPadding == null) {
            baseVideoTopBarPadding = HorizontalPadding(
                binding.videoTopBar.paddingLeft,
                binding.videoTopBar.paddingRight
            )
        }
        if (baseVideoOverlayPadding == null) {
            baseVideoOverlayPadding = HorizontalPadding(
                binding.videoOverlay.paddingLeft,
                binding.videoOverlay.paddingRight
            )
        }
        if (basePlayPauseOverlayPadding == null) {
            basePlayPauseOverlayPadding = HorizontalPadding(
                binding.playPauseOverlay.paddingLeft,
                binding.playPauseOverlay.paddingRight
            )
        }
        if (baseSideChatPadding == null) {
            baseSideChatPadding = HorizontalPadding(
                binding.sideChatContainer.paddingLeft,
                binding.sideChatContainer.paddingRight
            )
        }
    }

    private fun applyHorizontalPadding(view: View, left: Int, right: Int) {
        if (view.paddingLeft != left || view.paddingRight != right) {
            view.setPadding(left, view.paddingTop, right, view.paddingBottom)
        }
    }

    fun applyDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val isFullscreen = activity.isFullscreen
            val useCutout = VideoCutoutScalingPolicy.shouldDrawUnderCutout(
                isFullscreen = isFullscreen,
                isTheatreMode = isTheatreMode,
                isInPip = isInPipModeCompat,
                drawUnderCutoutPref = prefs.drawUnderCutout
            )
            val desired = when {
                useCutout && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                useCutout ->
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                isFullscreen && !isTheatreMode ->
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                else ->
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            val lp = activity.window.attributes
            if (lp.layoutInDisplayCutoutMode != desired) {
                lp.layoutInDisplayCutoutMode = desired
                activity.window.attributes = lp
            }
            ViewCompat.getRootWindowInsets(binding.playerScreenContainer)?.let {
                onPlayerWindowInsetsChanged(binding.playerScreenContainer, it)
            }
            ViewCompat.requestApplyInsets(binding.playerScreenContainer)
        }
    }

    fun onPlayerWindowInsetsChanged(playerScreenView: View, windowInsets: WindowInsetsCompat) {
        lastWindowInsets = windowInsets
        currentImeBottomPx = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        ensureBaseCutoutPaddingsCaptured()

        val isFloating = isFloatingSideChatActive()
        if (isFloating && !isDraggingFloatingChat) {
            updateFloatingContainerBoundsOnly(
                computeCurrentFloatingRect(
                    parentWidth = effectiveRootWidth(playerScreenView.width),
                    parentHeight = effectiveRootHeight(playerScreenView.height),
                    imeBottomPx = currentImeBottomPx
                )
            )
        }

        val topBarBase = baseVideoTopBarPadding!!
        val overlayBase = baseVideoOverlayPadding!!
        val playPauseBase = basePlayPauseOverlayPadding!!
        val sideChatBase = baseSideChatPadding!!
        val rootW = effectiveRootWidth(playerScreenView.width)
        val chatW = currentSideChatWidthPx(rootW)
        val edgeMarginPx = effectiveDockedEdgeMarginPx(rootW, chatW)
        val sideChatOverlayRightInset = FloatingChatBounds.computeRightControlsOverlapInsetPx(
            isTransparentSideChatActive = isTransparentSideChatActive(),
            isFloatingChatActive = isFloating,
            overlayWidthPx = chatW,
            edgeMarginPx = edgeMarginPx,
            rootWidthPx = rootW
        )

        if (activity.isFullscreen && !isTheatreMode) {
            if (playerScreenView.paddingLeft != 0 ||
                playerScreenView.paddingTop != 0 ||
                playerScreenView.paddingRight != 0 ||
                playerScreenView.paddingBottom != 0
            ) {
                playerScreenView.setPadding(0, 0, 0, 0)
            }
            if (prefs.drawUnderCutout) {
                val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                val overlayLeft = cutout.left
                val baseCutoutRight = if (isSideChatVisible && !isFloating) 0 else cutout.right
                val overlayRight = maxOf(baseCutoutRight, sideChatOverlayRightInset)
                val sideChatRight = if (isSideChatVisible) {
                    FloatingChatBounds.computeRemainingRightCutoutPaddingPx(
                        cutoutRightPx = cutout.right,
                        edgeMarginPx = edgeMarginPx,
                        isFloatingActive = isFloating
                    )
                } else {
                    0
                }
                applyHorizontalPadding(binding.videoTopBar, topBarBase.left + overlayLeft, topBarBase.right + overlayRight)
                applyHorizontalPadding(binding.videoOverlay, overlayBase.left + overlayLeft, overlayBase.right + overlayRight)
                applyHorizontalPadding(binding.playPauseOverlay, playPauseBase.left + overlayLeft, playPauseBase.right + overlayRight)
                applyHorizontalPadding(binding.sideChatContainer, sideChatBase.left, sideChatBase.right + sideChatRight)
            } else {
                applyHorizontalPadding(binding.videoTopBar, topBarBase.left, topBarBase.right + sideChatOverlayRightInset)
                applyHorizontalPadding(binding.videoOverlay, overlayBase.left, overlayBase.right + sideChatOverlayRightInset)
                applyHorizontalPadding(binding.playPauseOverlay, playPauseBase.left, playPauseBase.right + sideChatOverlayRightInset)
                applyHorizontalPadding(binding.sideChatContainer, sideChatBase.left, sideChatBase.right)
            }
        } else {
            val sys = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (playerScreenView.paddingTop != sys.top || playerScreenView.paddingBottom != sys.bottom) {
                playerScreenView.setPadding(
                    playerScreenView.paddingLeft,
                    sys.top,
                    playerScreenView.paddingRight,
                    sys.bottom
                )
            }
            applyHorizontalPadding(binding.videoTopBar, topBarBase.left, topBarBase.right + sideChatOverlayRightInset)
            applyHorizontalPadding(binding.videoOverlay, overlayBase.left, overlayBase.right + sideChatOverlayRightInset)
            applyHorizontalPadding(binding.playPauseOverlay, playPauseBase.left, playPauseBase.right + sideChatOverlayRightInset)
            applyHorizontalPadding(binding.sideChatContainer, sideChatBase.left, sideChatBase.right)
        }
    }

    private fun refreshPlayerWindowInsets() {
        val insets = lastWindowInsets ?: ViewCompat.getRootWindowInsets(binding.playerScreenContainer)
        if (insets != null) {
            onPlayerWindowInsetsChanged(binding.playerScreenContainer, insets)
        } else {
            ensureBaseCutoutPaddingsCaptured()
            val topBarBase = baseVideoTopBarPadding!!
            val overlayBase = baseVideoOverlayPadding!!
            val playPauseBase = basePlayPauseOverlayPadding!!
            val sideChatBase = baseSideChatPadding!!
            val rootW = effectiveRootWidth()
            val chatW = currentSideChatWidthPx(rootW)
            val edgeMarginPx = effectiveDockedEdgeMarginPx(rootW, chatW)
            val sideChatOverlayRightInset = FloatingChatBounds.computeRightControlsOverlapInsetPx(
                isTransparentSideChatActive = isTransparentSideChatActive(),
                isFloatingChatActive = isFloatingSideChatActive(),
                overlayWidthPx = chatW,
                edgeMarginPx = edgeMarginPx,
                rootWidthPx = rootW
            )
            applyHorizontalPadding(binding.videoTopBar, topBarBase.left, topBarBase.right + sideChatOverlayRightInset)
            applyHorizontalPadding(binding.videoOverlay, overlayBase.left, overlayBase.right + sideChatOverlayRightInset)
            applyHorizontalPadding(binding.playPauseOverlay, playPauseBase.left, playPauseBase.right + sideChatOverlayRightInset)
            applyHorizontalPadding(binding.sideChatContainer, sideChatBase.left, sideChatBase.right)
            ViewCompat.requestApplyInsets(binding.playerScreenContainer)
        }
    }

    private fun ensureFullscreenScaleButtonWired() {
        if (!isFullscreenScaleButtonWired) {
            isFullscreenScaleButtonWired = true
            binding.fullscreenScaleButton.setOnClickListener {
                cycleFullscreenResizeMode()
            }
        }
    }

    fun applyEffectiveVideoResizeMode() {
        ensureFullscreenScaleButtonWired()
        val isFullscreen = activity.isFullscreen
        val isInPip = isInPipModeCompat
        val isMiniPlayer = isMiniPlayerCompat
        val preferredMode = VideoCutoutScalingPolicy.fromPref(prefs.fullscreenResizeMode)
        val effectiveMode = VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
            isFullscreen = isFullscreen,
            isTheatreMode = isTheatreMode,
            isInPip = isInPip,
            isMiniPlayer = isMiniPlayer,
            isSideChatVisible = isSideChatVisible,
            preferredMode = preferredMode,
            transparentChatOverlayEnabled = prefs.transparentChatOverlayEnabled
        )
        val targetResizeMode = when (effectiveMode) {
            VideoScaleMode.FIT -> com.amazonaws.ivs.player.ResizeMode.FIT
            VideoScaleMode.ZOOM -> com.amazonaws.ivs.player.ResizeMode.ZOOM
            VideoScaleMode.FILL -> com.amazonaws.ivs.player.ResizeMode.FILL
        }
        if (binding.playerView.resizeMode != targetResizeMode) {
            binding.playerView.resizeMode = targetResizeMode
        }

        val showScaleButton = isFullscreen && !isTheatreMode && !isInPip && !isMiniPlayer && !isDockedSideChatActive()
        binding.fullscreenScaleButton.visibility = if (showScaleButton) View.VISIBLE else View.GONE
        val tintColor = if (effectiveMode == VideoScaleMode.FIT) {
            Color.WHITE
        } else {
            Color.parseColor("#53FC18")
        }
        binding.fullscreenScaleButton.imageTintList = ColorStateList.valueOf(tintColor)
        binding.fullscreenScaleButton.contentDescription = activity.getString(
            when (effectiveMode) {
                VideoScaleMode.FIT -> R.string.video_scale_fit
                VideoScaleMode.ZOOM -> R.string.video_scale_zoom
                VideoScaleMode.FILL -> R.string.video_scale_fill
            }
        )
    }

    fun setFullscreenResizeMode(mode: VideoScaleMode, showFeedback: Boolean = false) {
        prefs.fullscreenResizeMode = mode.prefValue
        applyEffectiveVideoResizeMode()
        if (showFeedback) {
            val labelRes = when (mode) {
                VideoScaleMode.FIT -> R.string.video_scale_fit
                VideoScaleMode.ZOOM -> R.string.video_scale_zoom
                VideoScaleMode.FILL -> R.string.video_scale_fill
            }
            Toast.makeText(
                activity,
                activity.getString(R.string.video_scale_mode_toast, activity.getString(labelRes)),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun cycleFullscreenResizeMode() {
        val current = VideoCutoutScalingPolicy.fromPref(prefs.fullscreenResizeMode)
        val next = VideoCutoutScalingPolicy.nextCycleMode(current)
        setFullscreenResizeMode(next, showFeedback = true)
    }

    fun restoreRotationAnimation() {
        try {
            val attrs = activity.window.attributes
            attrs.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
            activity.window.attributes = attrs
        } catch (e: Exception) {
            Log.w("FullscreenToggleManager", "restoreRotationAnimation: ${e.message}")
        }
    }

    /**
     * Updates the fullscreen button icon based on current state.
     */
    fun updateFullscreenButtonState() {
        val iconRes = if (activity.isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        binding.fullscreenButton.setImageResource(iconRes)
    }

    /**
     * Toggles side chat in landscape mode.
     */
    fun toggleSideChat() {
        if (isSideChatVisible) {
            hideSideChat()
        } else {
            showSideChat()
        }
    }

    /**
     * Shows the chat as a side overlay in landscape mode.
     * Resizes the video to fit in the remaining space on the left.
     */
    fun showSideChat(skipAnimation: Boolean = false) {
        // Use actual root dimensions for landscape detection (more reliable than configuration)
        val checkWidth = binding.root.width
        val checkHeight = binding.root.height
        val isLandscape = if (checkWidth > 0 && checkHeight > 0) {
            checkWidth > checkHeight
        } else {
            activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        
        android.util.Log.d("FullscreenToggle", "showSideChat: isLandscape=$isLandscape, isSideChatVisible=$isSideChatVisible, isTheatreMode=$isTheatreMode, root=${checkWidth}x${checkHeight}")
        
        if (isSideChatVisible || !isLandscape || isTheatreMode) return

        activeSideChatAnimator?.cancel()
        activeSideChatAnimator = null
        
        val displayMetrics = activity.resources.displayMetrics
        val density = displayMetrics.density
        val rootWidth = effectiveRootWidth(checkWidth)
        
        val chat = binding.chatContainer
        val isTablet = activity.isTabletOrLargeWindow()
        val useTransparentOverlay = prefs.transparentChatOverlayEnabled
        
        // Save original parent and params
        originalChatParent = chat.parent as? android.view.ViewGroup
        originalChatIndex = originalChatParent?.indexOfChild(chat) ?: -1
        originalChatParams = chat.layoutParams
        
        // Remove from current parent
        originalChatParent?.removeView(chat)
        
        // Add to side container
        binding.sideChatContainer.alpha = 1f
        binding.sideChatContainer.scaleX = 1f
        binding.sideChatContainer.scaleY = 1f
        binding.sideChatContainer.translationX = 0f
        binding.sideChatContainer.translationY = 0f
        binding.sideChatContainer.visibility = View.VISIBLE
        if (chat.parent != binding.sideChatContainer) {
            binding.sideChatContainer.addView(chat)
        }
        
        // Always use 0dp + weight 1f so floatingChatDragHandle / actionBar never clip chatInputRoot
        val chatParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        )
        chat.layoutParams = chatParams
        chat.visibility = View.VISIBLE

        // --- Reparent ActionBar (Tablets) ---
        if (isTablet) {
            val actionBar = binding.actionBar
            if (actionBar.parent != binding.sideChatContainer) {
                originalActionBarParent = actionBar.parent as? android.view.ViewGroup
                originalActionBarIndex = originalActionBarParent?.indexOfChild(actionBar) ?: -1
                originalActionBarParams = actionBar.layoutParams
                
                originalActionBarParent?.removeView(actionBar)
                val actionBarIndex = if (binding.floatingChatDragHandle.parent == binding.sideChatContainer) 1 else 0
                binding.sideChatContainer.addView(
                    actionBar,
                    actionBarIndex.coerceAtMost(binding.sideChatContainer.childCount)
                )
                
                // Adjust actionBar params for the new LinearLayout parent
                val aParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (44 * density).toInt()
                )
                actionBar.layoutParams = aParams
                actionBar.visibility = View.VISIBLE
                isActionBarReparentedToSide = true
            }
            activity.chatRulesManager.updateContainer(binding.sideChatContainer)
        }
        
        if (useTransparentOverlay) {
            binding.sideChatContainer.elevation = 180f * density
            if (isTablet) {
                binding.infoPanel.visibility = View.GONE
            }
        } else if (isTablet) {
            chat.setBackgroundColor(android.graphics.Color.parseColor("#0f0f0f"))
            binding.sideChatContainer.setBackgroundColor(android.graphics.Color.parseColor("#0f0f0f"))
            binding.sideChatContainer.elevation = 0f
            binding.infoPanel.visibility = View.VISIBLE
        } else {
            chat.setBackgroundColor(android.graphics.Color.parseColor("#FF0F0F0F"))
            binding.sideChatContainer.elevation = 180f * density
        }
        
        // Attach touch listener to chat so we can swipe it away too
        chat.setOnTouchListener { _, event ->
             activity.playerGestureManager.playerGestureDetector.onTouchEvent(event)
             false // Return false to allow clicks/scrolls to pass through
        }
        
        isSideChatVisible = true
        val chatWidth = computeSideChatWidthPx(rootWidth)
        val dividerHalfPx = 11f * density

        ensureParentLayoutListener()
        ensureSideChatDividerSetup()
        applySideChatLayout()
        applyChatOverlayAppearance()
        applyDisplayCutoutMode()
        refreshPlayerWindowInsets()
        applyEffectiveVideoResizeMode()
        ViewCompat.requestApplyInsets(binding.chatContainer)

        val videoParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        if (useTransparentOverlay) {
            binding.chatContainer.setPadding(
                binding.chatContainer.paddingLeft,
                0,
                binding.chatContainer.paddingRight,
                binding.chatContainer.paddingBottom
            )

            if (isFloatingSideChatActive()) {
                binding.sideChatContainer.translationX = 0f
                binding.sideChatContainer.translationY = 0f
                binding.sideChatDividerHandle.translationX = dividerHalfPx
                binding.sideChatDividerHandle.translationY = 0f
                if (skipAnimation) {
                    binding.sideChatContainer.alpha = 1f
                    binding.sideChatContainer.scaleX = 1f
                    binding.sideChatContainer.scaleY = 1f
                    updateSideChatHandleVisibility()
                    return
                }

                binding.sideChatDividerHandle.visibility = View.GONE
                binding.sideChatContainer.alpha = 0f
                binding.sideChatContainer.scaleX = 0.94f
                binding.sideChatContainer.scaleY = 0.94f

                val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
                animator.duration = 200
                animator.interpolator = android.view.animation.DecelerateInterpolator()
                animator.addUpdateListener { animation ->
                    val fraction = animation.animatedFraction
                    binding.sideChatContainer.alpha = fraction
                    val scale = 0.94f + (0.06f * fraction)
                    binding.sideChatContainer.scaleX = scale
                    binding.sideChatContainer.scaleY = scale
                }
                animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        cancelled = true
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (activeSideChatAnimator === animator) activeSideChatAnimator = null
                        if (cancelled) return
                        binding.sideChatContainer.alpha = 1f
                        binding.sideChatContainer.scaleX = 1f
                        binding.sideChatContainer.scaleY = 1f
                        updateSideChatHandleVisibility()
                    }
                })
                activeSideChatAnimator = animator
                animator.start()
                return
            }

            val slideDistance = (chatWidth + effectiveDockedEdgeMarginPx(rootWidth, chatWidth)).toFloat()
            if (skipAnimation) {
                binding.sideChatContainer.translationX = 0f
                binding.sideChatDividerHandle.translationX = dividerHalfPx
                updateSideChatHandleVisibility()
                return
            }

            binding.sideChatContainer.translationX = slideDistance
            binding.sideChatDividerHandle.translationX = slideDistance + dividerHalfPx

            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 300
            animator.interpolator = android.view.animation.DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val translation = slideDistance * (1f - fraction)
                binding.sideChatContainer.translationX = translation
                binding.sideChatDividerHandle.translationX = translation + dividerHalfPx
            }
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (activeSideChatAnimator === animator) activeSideChatAnimator = null
                    if (cancelled) return
                    binding.sideChatContainer.translationX = 0f
                    binding.sideChatDividerHandle.translationX = dividerHalfPx
                    updateSideChatHandleVisibility()
                }
            })
            activeSideChatAnimator = animator
            animator.start()
            updateSideChatHandleVisibility()
            return
        }
        
        // Docked split-screen mode: resize videoContainer alongside chat
        if (isTablet) {
            // For tablets, we maintain 16:9 ratio so info bar is visible below on the left
            videoParams.dimensionRatio = "H,16:9"
            videoParams.height = 0 // MATCH_CONSTRAINT
            videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.verticalBias = 0.0f
        } else {
            videoParams.dimensionRatio = null
            videoParams.height = 0 // MATCH_CONSTRAINT (Fill height)
            videoParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            videoParams.verticalBias = 0.5f
        }
        
        videoParams.width = rootWidth // Start full width
        videoParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        videoParams.endToStart = ConstraintLayout.LayoutParams.UNSET 
        videoParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
        videoParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        
        binding.videoContainer.layoutParams = videoParams

        if (skipAnimation) {
            binding.sideChatContainer.translationX = 0f
            binding.sideChatDividerHandle.translationX = dividerHalfPx
            // Use constraints for skipped animation
            if (isTablet) {
                videoParams.width = (rootWidth - chatWidth).coerceAtLeast(1)
            } else {
                videoParams.width = 0 // MATCH_CONSTRAINT
            }
            videoParams.endToStart = R.id.sideChatContainer
            videoParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            binding.videoContainer.layoutParams = videoParams
            updateSideChatHandleVisibility()
            if (!isTablet) {
                binding.chatContainer.setPadding(binding.chatContainer.paddingLeft, 0, binding.chatContainer.paddingRight, binding.chatContainer.paddingBottom)
            }
            return
        }

        binding.sideChatContainer.translationX = chatWidth.toFloat()
        binding.sideChatDividerHandle.translationX = chatWidth.toFloat() + dividerHalfPx
        
        // Create synchronized animator
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 300
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedFraction
            
            // 1. Update Video Width
            val newWidth = (rootWidth - (chatWidth * fraction)).toInt()
            val vParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
            if (vParams.width != newWidth) {
                vParams.width = newWidth
                if (isTablet) {
                    vParams.dimensionRatio = "H,16:9"
                    vParams.height = 0
                } else {
                    vParams.dimensionRatio = null
                    vParams.height = 0 // MATCH_CONSTRAINT
                    vParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                binding.videoContainer.layoutParams = vParams
            }
            
            // 2. Update Chat Translation
            val translation = chatWidth * (1 - fraction)
            binding.sideChatContainer.translationX = translation
            binding.sideChatDividerHandle.translationX = translation + dividerHalfPx
        }
        
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (activeSideChatAnimator === animator) activeSideChatAnimator = null
                if (cancelled) return
                binding.sideChatDividerHandle.translationX = dividerHalfPx
                // Finalize constraints to ensure child views (InfoPanel) resize correctly
                val vParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
                if (isTablet) {
                    vParams.width = (rootWidth - chatWidth).coerceAtLeast(1)
                } else {
                    vParams.width = 0 // MATCH_CONSTRAINT
                }
                vParams.endToStart = R.id.sideChatContainer
                vParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
                
                if (!isTablet) {
                    vParams.height = 0 // MATCH_CONSTRAINT
                    vParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                
                binding.videoContainer.layoutParams = vParams
                updateSideChatHandleVisibility()
            }
        })
        
        activeSideChatAnimator = animator
        animator.start()
        updateSideChatHandleVisibility()
        
        // Force chat padding to 0 for phones to avoid gaps from overlay
        if (!isTablet) {
            binding.chatContainer.setPadding(binding.chatContainer.paddingLeft, 0, binding.chatContainer.paddingRight, binding.chatContainer.paddingBottom)
        }
    }

    /**
     * Hides the side overlay chat.
     * Restores the video to full screen size.
     */
    fun hideSideChat() {
        clearSideChatWidthPreview()
        if (!isSideChatVisible) return
        activeSideChatAnimator?.cancel()
        activeSideChatAnimator = null
        hideSideChatDivider()

        val chat = binding.chatContainer
        val isTablet = activity.isTabletOrLargeWindow()
        val wasTransparentOverlay = isTransparentSideChatActive()
        val wasFloatingOverlay = isFloatingSideChatActive()
        
        // Calculate dimensions
        val rootWidth = effectiveRootWidth()

        val chatWidth = binding.sideChatContainer.width.takeIf { it > 0 }
            ?: computeSideChatWidthPx(rootWidth)
        val slideDistance = (chatWidth + effectiveDockedEdgeMarginPx(rootWidth, chatWidth)).toFloat()
            
        val currentVideoWidth = binding.videoContainer.width
        
        // Create synchronized animator
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animator.duration = if (wasFloatingOverlay) 180 else 250
        animator.interpolator = android.view.animation.AccelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedFraction
            if (wasFloatingOverlay) {
                binding.sideChatContainer.alpha = 1f - fraction
                val scale = 1f - (0.06f * fraction)
                binding.sideChatContainer.scaleX = scale
                binding.sideChatContainer.scaleY = scale
                return@addUpdateListener
            }
            
            // 1. Update Video Width only in docked split-screen mode
            if (!wasTransparentOverlay) {
                val newWidth = (currentVideoWidth + ((rootWidth - currentVideoWidth) * fraction)).toInt()
                val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
                if (params.width != newWidth) {
                    params.width = newWidth
                    if (isTablet) {
                        params.dimensionRatio = "H,16:9"
                        params.height = 0
                    } else {
                        params.dimensionRatio = null
                        params.height = 0 // MATCH_CONSTRAINT
                        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                    binding.videoContainer.layoutParams = params
                }
            }
            
            // 2. Update Chat Translation (Slide out past edge margin)
            val translation = slideDistance * fraction
            binding.sideChatContainer.translationX = translation
        }
        
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (activeSideChatAnimator === animator) activeSideChatAnimator = null
                if (cancelled) return
                resetSideChatContainerToDefaultState(keepVisibility = false)
                binding.sideChatContainer.removeView(chat)
                chat.setOnTouchListener(null)
                
                // Restore video container to standard full screen constraints
                val isLandscapeNow = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
                params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                
                if (isLandscapeNow || activity.isFullscreen) {
                    // Landscape or Fullscreen -> Full screen video
                    params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                    params.dimensionRatio = null
                } else {
                    // Portrait -> 16:9 Video at top
                    params.height = 0
                    params.dimensionRatio = "H,16:9"
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    params.verticalBias = 0.0f
                }
                
                params.endToStart = ConstraintLayout.LayoutParams.UNSET
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                binding.videoContainer.layoutParams = params
                
                 // Restore to original parent
                if (originalChatParent != null) {
                    val safeIndex = if (originalChatIndex in 0..originalChatParent!!.childCount) originalChatIndex else -1
                    originalChatParent?.addView(chat, safeIndex, originalChatParams)
                }
                
                // If still in landscape fullscreen, keep hidden
                if (activity.isFullscreen && !isTheatreMode) {
                    chat.visibility = View.GONE
                } else {
                    chat.visibility = View.VISIBLE
                }
                chat.setPadding(chat.paddingLeft, chat.paddingTop, chat.paddingRight, 0)
                
                // Restore ActionBar if reparented
                if (isActionBarReparentedToSide) {
                    val actionBar = binding.actionBar
                    binding.sideChatContainer.removeView(actionBar)
                    if (originalActionBarParent != null) {
                        val safeIndex = if (originalActionBarIndex in 0..originalActionBarParent!!.childCount) originalActionBarIndex else -1
                        originalActionBarParent?.addView(actionBar, safeIndex, originalActionBarParams)
                    }
                    isActionBarReparentedToSide = false
                }
                
                val isPortrait = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                if (isTablet && isPortrait && !isTheatreMode) {
                    binding.infoPanel.visibility = View.VISIBLE
                }

                isSideChatVisible = false
                originalChatParent = null
                originalActionBarParent = null
                applyChatOverlayAppearance()
                applyDisplayCutoutMode()
                refreshPlayerWindowInsets()
                applyEffectiveVideoResizeMode()
                ViewCompat.requestApplyInsets(binding.chatContainer)
            }
        })
        
        activeSideChatAnimator = animator
        animator.start()
    }
    fun onEnterPipMode() {
        // 1. Save Current Component State
        val currentParams = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        savedVideoParams = ConstraintLayout.LayoutParams(currentParams)
        savedResizeMode = binding.playerView.resizeMode

        // 2. Hide Common Overlays
        hideSideChatDivider()
        binding.videoOverlay.visibility = View.GONE
        binding.playPauseOverlay.visibility = View.GONE
        binding.actionBar.visibility = View.GONE
        binding.infoPanel.visibility = View.GONE

        // 3. Hide Theatre Specifics
        if (isTheatreMode) {
             theatreTopBar?.visibility = View.GONE
             binding.theatreBackground.visibility = View.GONE
             binding.chatContainer.visibility = View.GONE
             binding.theatreBottomGradient.visibility = View.GONE
             
             binding.emoteComboContainer.visibility = View.GONE
             binding.floatingEmoteContainer.visibility = View.GONE
             binding.pinnedGiftsBlur.visibility = View.GONE
             binding.pinnedMessageContainer.visibility = View.GONE
             binding.pollContainer.visibility = View.GONE
             binding.predictionContainer.visibility = View.GONE
        }

        // 4. Force FIT Mode and FILL PARENT for PiP Window
        binding.playerView.resizeMode = com.amazonaws.ivs.player.ResizeMode.FIT
        
        val params = binding.videoContainer.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
        
        // Clear all constraints that might limit size or aspect ratio
        params.dimensionRatio = null 
        params.matchConstraintPercentWidth = 1f
        params.matchConstraintPercentHeight = 1f
        params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
        params.matchConstraintDefaultHeight = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
        
        params.topMargin = 0
        params.bottomMargin = 0
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        
        // Center it
        params.verticalBias = 0.5f 
        params.horizontalBias = 0.5f
        
        binding.videoContainer.layoutParams = params
        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()
    }

    fun onExitPipMode() {
        val isTablet = activity.resources.getBoolean(R.bool.is_tablet)
        val isPortrait = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        if (isTablet && isSideChatVisible) {
            binding.actionBar.visibility = View.VISIBLE
        }
        if (isTablet && isPortrait && !isTheatreMode) {
            binding.infoPanel.visibility = View.VISIBLE
        }
        
        // 1. Restore Theatre Specifics
        if (isTheatreMode) {
             theatreTopBar?.visibility = View.VISIBLE
             binding.theatreBackground.visibility = View.VISIBLE
             binding.chatContainer.visibility = View.VISIBLE
             binding.theatreBottomGradient.visibility = View.VISIBLE
             
             binding.emoteComboContainer.visibility = View.VISIBLE
             binding.floatingEmoteContainer.visibility = View.VISIBLE
             
             activity.overlayManager.updatePinnedGiftsUI()
             
             if (activity.chatStateManager.isPinnedMessageActive) {
                 binding.pinnedMessageContainer.visibility = View.VISIBLE
             }
             if (activity.chatStateManager.currentPoll != null) {
                 binding.pollContainer.visibility = View.VISIBLE
             }
             if (activity.chatStateManager.currentPrediction != null) {
                 binding.predictionContainer.visibility = View.VISIBLE
             }
        }
        
        // 2. Restore Video Layout & ResizeMode
        savedResizeMode?.let {
            binding.playerView.resizeMode = it
        }
        savedVideoParams?.let {
            binding.videoContainer.layoutParams = it
        }
        
        // Clear saved state
        savedVideoParams = null
        savedResizeMode = null
        applyDisplayCutoutMode()
        applyEffectiveVideoResizeMode()
    }
    
    /**
     * Gets the navigation bar height in pixels
     */
    private fun getNavigationBarHeight(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.navigationBars()
            )
            insets.bottom
        } else {
            val resourceId = activity.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                activity.resources.getDimensionPixelSize(resourceId)
            } else {
                0
            }
        }
    }
    
    // Theatre mode keyboard listener
    private var theatreKeyboardListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
    
    /**
     * Setup keyboard listener for theatre mode.
     * Currently disabled - relying on Activity's WindowInsetsListener for keyboard handling.
     * Chat input uses high elevation (2000f) to stay above video content.
     */
    private fun setupTheatreModeKeyboardListener() {
        // Disabled - relying on WindowInsetsListener in Activity
        theatreKeyboardListener = null
    }
    
    /**
     * Remove keyboard listener when exiting theatre mode
     */
    private fun removeTheatreModeKeyboardListener() {
        theatreKeyboardListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        theatreKeyboardListener = null
    }

    /**
     * Updates the side chat layout based on overlay visibility.
     * When video overlay is visible on phones (fullscreen landscape),
     * shrink chat height and show action bar above it.
     */
    fun updateSideChatOverlayState(isOverlayVisible: Boolean) {
        if (!isSideChatVisible) return
        updateSideChatHandleVisibility(overlayVisible = isOverlayVisible)
        val isTablet = activity.isTabletOrLargeWindow()
        if (isTablet) {
            if (isTransparentSideChatActive()) {
                binding.infoPanel.visibility = View.GONE
            }
            applyChatOverlayAppearance()
            return
        }
        
        // Only apply this logic in Landscape mode (Fullscreen on phone)
        // In Portrait, ActionBar is in normal position, not side container
        val isPortrait = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) return
        
        val chat = binding.chatContainer
        // If chat isn't attached to side container, nothing to do
        if (chat.parent != binding.sideChatContainer) return
        
        // Force reset padding to avoid gaps from other animations
        chat.setPadding(chat.paddingLeft, 0, chat.paddingRight, chat.paddingBottom)
        
        val actionBar = binding.actionBar
        
        if (isOverlayVisible) {
            // Reparent ActionBar to side container if needed
            if (actionBar.parent != binding.sideChatContainer) {
                if (originalActionBarParent == null) {
                    originalActionBarParent = actionBar.parent as? android.view.ViewGroup
                    originalActionBarIndex = originalActionBarParent?.indexOfChild(actionBar) ?: -1
                    originalActionBarParams = actionBar.layoutParams
                }
                (actionBar.parent as? android.view.ViewGroup)?.removeView(actionBar)
                // Keep floatingChatDragHandle at index 0 when present
                val actionBarIndex = if (binding.floatingChatDragHandle.parent == binding.sideChatContainer) 1 else 0
                binding.sideChatContainer.addView(
                    actionBar,
                    actionBarIndex.coerceAtMost(binding.sideChatContainer.childCount)
                )
                isActionBarReparentedToSide = true
            }
            
            // Show ActionBar
            actionBar.visibility = View.VISIBLE
            
            // Set params for ActionBar (wrap content, no weight)
            val actionParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            actionParams.weight = 0f
            actionParams.setMargins(0, 0, 0, 0) // Reset margins
            actionBar.layoutParams = actionParams
            
            // Remove any excessive padding that might cause gaps
            actionBar.setPadding(0, 0, 0, 0)
            
            // Shrink chat (weight 1, match parent width)
            val chatParams = chat.layoutParams as android.widget.LinearLayout.LayoutParams
            chatParams.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            chatParams.height = 0
            chatParams.weight = 1f
            chatParams.setMargins(0, 0, 0, 0)
            
            // Force padding 0 here too just in case
            chat.setPadding(chat.paddingLeft, 0, chat.paddingRight, chat.paddingBottom)
            
            chat.layoutParams = chatParams
            
        } else {
            // Hide ActionBar
            actionBar.visibility = View.GONE
            
            // Keep weight 1f + height 0 so floatingChatDragHandle never clips chatInputRoot off the bottom
            val chatParams = chat.layoutParams as android.widget.LinearLayout.LayoutParams
            chatParams.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            chatParams.height = 0
            chatParams.weight = 1f
            chatParams.setMargins(0, 0, 0, 0)
            chat.layoutParams = chatParams
        }
        applyChatOverlayAppearance()
    }

    /**
     * Dynamically switches between Transparent Chat Overlay mode and Docked Split-Screen mode
     * when `prefs.transparentChatOverlayEnabled` is toggled at runtime.
     */
    fun onTransparentChatOverlaySettingChanged() {
        onFloatingChatSettingChanged()
    }

    /**
     * Re-applies side-chat layout, floating window bounds, edge offsets, appearance, and IME insets
     * when transparent/floating/edge-margin/height settings change at runtime.
     */
    fun onFloatingChatSettingChanged() {
        clearSideChatWidthPreview()
        if (isSideChatVisible) {
            activeSideChatAnimator?.cancel()
            activeSideChatAnimator = null

            val density = activity.resources.displayMetrics.density
            binding.sideChatContainer.alpha = 1f
            binding.sideChatContainer.scaleX = 1f
            binding.sideChatContainer.scaleY = 1f
            binding.sideChatContainer.translationX = 0f
            binding.sideChatContainer.translationY = 0f
            binding.sideChatDividerHandle.translationX = 11f * density
            binding.sideChatDividerHandle.translationY = 0f

            applySideChatLayout()

            sideChatWidthPreviewLine?.let { line ->
                line.elevation = kotlin.math.max(120f, binding.sideChatContainer.elevation + 14f * density)
            }
            floatingChatPreviewBox?.let { box ->
                box.elevation = kotlin.math.max(120f, binding.sideChatContainer.elevation + 16f * density)
            }

            refreshPlayerWindowInsets()
            applyEffectiveVideoResizeMode()
            applyChatOverlayAppearance()
            updateSideChatHandleVisibility()
        } else {
            applyEffectiveVideoResizeMode()
            applyChatOverlayAppearance()
        }
        ViewCompat.requestApplyInsets(binding.chatContainer)
    }

    /**
     * Applies background opacity, border styling, and text shadows across Landscape Transparent
     * Side Chat, Portrait Theatre Mode, or restores Solid/Docked defaults.
     *
     * @param overrideOpacityPercent Optional live preview opacity (0..100) while dragging SeekBar.
     */
    fun applyChatOverlayAppearance(overrideOpacityPercent: Int? = null) {
        val rawOpacity = overrideOpacityPercent ?: prefs.chatOverlayOpacityPercent
        val opacity = ChatOverlayStyleUtils.clampOpacityPercent(rawOpacity)

        if (isTransparentSideChatActive()) {
            // 1. Landscape Transparent Side Chat Overlay (Docked column or Floating window)
            if (isFloatingSideChatActive()) {
                val density = activity.resources.displayMetrics.density
                binding.sideChatContainer.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(ChatOverlayStyleUtils.argbWithOpacity(0x000000, opacity))
                    setStroke(
                        (1f * density).roundToInt().coerceAtLeast(1),
                        ChatOverlayStyleUtils.floatingBorderArgb(opacity)
                    )
                }
                binding.sideChatContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
                binding.sideChatContainer.clipToOutline = true
                binding.floatingChatDragHandle.setBackgroundColor(
                    ChatOverlayStyleUtils.floatingHandleBgArgb(opacity)
                )
            } else {
                binding.sideChatContainer.clipToOutline = false
                binding.sideChatContainer.setBackgroundColor(
                    ChatOverlayStyleUtils.argbWithOpacity(0x000000, opacity)
                )
            }
            binding.chatContainer.setBackgroundColor(Color.TRANSPARENT)
            binding.chatListContainer.setBackgroundColor(Color.TRANSPARENT)
            binding.chatRecyclerView.setBackgroundColor(Color.TRANSPARENT)
            binding.actionBar.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x121212, opacity)
            )
            binding.chatInputRoot.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, opacity)
            )
            binding.quickEmoteRecyclerView.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x151515, opacity)
            )
            binding.commandSuggestionsRecycler.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, maxOf(opacity, 50))
            )
            binding.replyContainer.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x222222, opacity)
            )
            applyLoginAndBannedOverlayBackgrounds(opacityPercent = opacity, translucent = true)
            applyChatInputBackground(opacityPercent = opacity, forceSolid = false)

            binding.pinnedGiftsBlur.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x0F0F0F, opacity)
            )
            val cardOpacity = maxOf(opacity, 35)
            binding.pinnedBlurView.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, cardOpacity)
            )
            binding.pollContainer.setCardBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, cardOpacity)
            )
            binding.predictionContainer.setCardBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, cardOpacity)
            )

            applyOverlayTextShadows(
                ChatOverlayStyleUtils.shouldUseTextShadow(isTransparentOverlayActive = true, opacityPercent = opacity)
            )
        } else if (isTheatreMode) {
            // 2. Portrait Theatre Mode
            val effectiveOpacity = ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(opacity, theatreImeOpen)
            binding.chatContainer.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x000000, effectiveOpacity)
            )
            binding.chatListContainer.setBackgroundColor(Color.TRANSPARENT)
            binding.chatRecyclerView.setBackgroundColor(Color.TRANSPARENT)
            binding.theatreBottomGradient.alpha = ChatOverlayStyleUtils.theatreGradientAlpha(effectiveOpacity)
            binding.actionBar.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x121212, maxOf(opacity, 65))
            )
            binding.chatInputRoot.setBackgroundColor(Color.TRANSPARENT)
            binding.quickEmoteRecyclerView.setBackgroundColor(Color.TRANSPARENT)
            binding.commandSuggestionsRecycler.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, maxOf(effectiveOpacity, 60))
            )
            binding.replyContainer.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x222222, effectiveOpacity)
            )
            applyLoginAndBannedOverlayBackgrounds(opacityPercent = effectiveOpacity, translucent = true)
            applyChatInputBackground(opacityPercent = effectiveOpacity, forceSolid = false)

            binding.pinnedGiftsBlur.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x0F0F0F, maxOf(opacity, 35))
            )
            val cardOpacity = maxOf(opacity, 35)
            binding.pinnedBlurView.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, cardOpacity)
            )
            binding.pollContainer.setCardBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, cardOpacity)
            )
            binding.predictionContainer.setCardBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, cardOpacity)
            )

            applyOverlayTextShadows(
                ChatOverlayStyleUtils.shouldUseTextShadow(
                    isTransparentOverlayActive = true,
                    opacityPercent = effectiveOpacity
                )
            )
        } else {
            // 3. Solid / Docked / Normal Mode — restore exact XML & dark UI defaults
            binding.sideChatContainer.clipToOutline = false
            binding.sideChatContainer.setBackgroundColor(Color.parseColor("#0f0f0f"))
            binding.chatContainer.setBackgroundColor(Color.parseColor("#0f0f0f"))
            binding.chatListContainer.setBackgroundColor(Color.TRANSPARENT)
            binding.chatRecyclerView.setBackgroundColor(Color.TRANSPARENT)
            binding.theatreBottomGradient.alpha = 1f
            binding.actionBar.setBackgroundColor(Color.parseColor("#CC1a1a1a"))
            binding.chatInputRoot.setBackgroundColor(Color.parseColor("#1a1a1a"))
            binding.quickEmoteRecyclerView.setBackgroundColor(Color.parseColor("#151515"))
            binding.commandSuggestionsRecycler.setBackgroundColor(Color.parseColor("#1A1A1A"))
            binding.replyContainer.setBackgroundColor(Color.parseColor("#222222"))
            applyLoginAndBannedOverlayBackgrounds(opacityPercent = 100, translucent = false)
            applyChatInputBackground(opacityPercent = 100, forceSolid = true)

            binding.pinnedGiftsBlur.setBackgroundColor(Color.parseColor("#CC0f0f0f"))
            binding.pinnedBlurView.setBackgroundColor(Color.parseColor("#EB1A1A1A"))
            binding.pollContainer.setCardBackgroundColor(Color.parseColor("#F21A1A1A"))
            binding.predictionContainer.setCardBackgroundColor(Color.parseColor("#F21A1A1A"))

            applyOverlayTextShadows(false)
        }
    }

    private fun applyChatInputBackground(opacityPercent: Int, forceSolid: Boolean) {
        if (!forceSolid &&
            ChatOverlayStyleUtils.clampOpacityPercent(opacityPercent) < ChatOverlayStyleUtils.TEXT_SHADOW_OPACITY_THRESHOLD
        ) {
            val density = activity.resources.displayMetrics.density
            binding.chatInput.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(ChatOverlayStyleUtils.argbWithOpacity(0x2A2A2A, opacityPercent))
                setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#33FFFFFF"))
            }
        } else {
            binding.chatInput.setBackgroundResource(R.drawable.bg_chat_input)
        }
    }

    private fun applyLoginAndBannedOverlayBackgrounds(opacityPercent: Int, translucent: Boolean) {
        if (translucent) {
            val density = activity.resources.displayMetrics.density
            // Keep login pill opaque enough to cover underlying chatInput hint while matching rounded pill style
            binding.chatLoginOverlay.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, maxOf(opacityPercent, 85)))
                setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#33FFFFFF"))
            }
            binding.chatBannedOverlay.setBackgroundColor(
                ChatOverlayStyleUtils.argbWithOpacity(0x1A1A1A, opacityPercent)
            )
        } else {
            binding.chatLoginOverlay.setBackgroundColor(Color.parseColor("#1a1a1a"))
            binding.chatBannedOverlay.setBackgroundColor(Color.parseColor("#1a1a1a"))
        }
    }

    private fun applyOverlayTextShadows(enabled: Boolean) {
        try {
            activity.chatAdapter.setTransparentOverlayMode(enabled)
        } catch (e: Exception) {
            Log.w("FullscreenToggleManager", "applyOverlayTextShadows adapter not ready: ${e.message}")
        }
        val density = activity.resources.displayMetrics.density
        val shadowViews = listOf(
            binding.emptyChatText,
            binding.chatLoginPromptPrefix,
            binding.chatBannedText
        )
        if (enabled) {
            val radius = 3f * density
            val dy = 1f * density
            val shadowColor = 0xE6000000.toInt()
            shadowViews.forEach { it.setShadowLayer(radius, 0f, dy, shadowColor) }
            binding.emptyChatText.setTextColor(Color.parseColor("#B0B0B0"))
            binding.chatLoginPromptPrefix.setTextColor(Color.parseColor("#CCCCCC"))
        } else {
            shadowViews.forEach { it.setShadowLayer(0f, 0f, 0f, 0) }
            binding.emptyChatText.setTextColor(Color.parseColor("#666666"))
            binding.chatLoginPromptPrefix.setTextColor(Color.parseColor("#808080"))
        }
    }
}
