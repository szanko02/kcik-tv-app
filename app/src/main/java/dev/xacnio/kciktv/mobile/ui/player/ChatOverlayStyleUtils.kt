/**
 * File: ChatOverlayStyleUtils.kt
 *
 * Description: Pure Kotlin math, color/alpha, text-shadow, and control-inset helpers for
 * Transparent Chat Overlay over Stream (landscape side-chat overlay and portrait Theatre Mode).
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.player

import kotlin.math.roundToInt

object ChatOverlayStyleUtils {
    const val DEFAULT_OPACITY_PERCENT = 50
    const val MIN_OPACITY_PERCENT = 0
    const val MAX_OPACITY_PERCENT = 100
    const val TEXT_SHADOW_OPACITY_THRESHOLD = 85
    const val THEATRE_IME_MIN_OPACITY_PERCENT = 60

    const val MIN_EDGE_MARGIN_PERCENT = 0
    const val MAX_EDGE_MARGIN_PERCENT = 30
    const val DEFAULT_EDGE_MARGIN_PERCENT = 0
    const val MAX_CONTROLS_OVERLAP_RATIO = 0.55f

    fun clampOpacityPercent(percent: Int): Int =
        percent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    fun clampEdgeMarginPercent(percent: Int): Int =
        percent.coerceIn(MIN_EDGE_MARGIN_PERCENT, MAX_EDGE_MARGIN_PERCENT)

    fun overlayAlphaInt(opacityPercent: Int): Int =
        (clampOpacityPercent(opacityPercent) * 255f / 100f).roundToInt().coerceIn(0, 255)

    fun argbWithOpacity(baseRgb: Int, opacityPercent: Int): Int =
        (overlayAlphaInt(opacityPercent) shl 24) or (baseRgb and 0x00FFFFFF)

    fun shouldUseTextShadow(isTransparentOverlayActive: Boolean, opacityPercent: Int): Boolean =
        isTransparentOverlayActive && clampOpacityPercent(opacityPercent) < TEXT_SHADOW_OPACITY_THRESHOLD

    fun computeEdgeMarginPx(
        parentWidthPx: Int,
        chatWidthPx: Int,
        edgeMarginPercent: Int
    ): Int {
        if (parentWidthPx <= 0) return 0
        val clampedPercent = clampEdgeMarginPercent(edgeMarginPercent)
        val raw = (parentWidthPx * clampedPercent / 100f).roundToInt().coerceAtLeast(0)
        val maxMargin = (parentWidthPx - chatWidthPx.coerceAtLeast(0)).coerceAtLeast(0)
        return raw.coerceIn(0, maxMargin)
    }

    fun computeRemainingRightCutoutPaddingPx(
        cutoutRightPx: Int,
        edgeMarginPx: Int
    ): Int = (cutoutRightPx.coerceAtLeast(0) - edgeMarginPx.coerceAtLeast(0)).coerceAtLeast(0)

    fun computeRightControlsOverlapInsetPx(
        isTransparentSideChatActive: Boolean,
        overlayWidthPx: Int,
        edgeMarginPx: Int = 0,
        rootWidthPx: Int = 0
    ): Int {
        if (!isTransparentSideChatActive) return 0
        val raw = overlayWidthPx.coerceAtLeast(0) + edgeMarginPx.coerceAtLeast(0)
        if (rootWidthPx <= 0) return raw
        val maxCap = (rootWidthPx * MAX_CONTROLS_OVERLAP_RATIO).roundToInt().coerceAtLeast(0)
        return raw.coerceAtMost(maxCap)
    }

    fun effectiveTheatreKeyboardOpacity(opacityPercent: Int, isImeOpen: Boolean): Int {
        val clamped = clampOpacityPercent(opacityPercent)
        return if (isImeOpen) maxOf(clamped, THEATRE_IME_MIN_OPACITY_PERCENT) else clamped
    }

    fun isDockedSideChat(
        isSideChatVisible: Boolean,
        transparentChatOverlayEnabled: Boolean
    ): Boolean = isSideChatVisible && !transparentChatOverlayEnabled

    fun theatreGradientAlpha(opacityPercent: Int): Float {
        val clamped = clampOpacityPercent(opacityPercent)
        if (clamped <= MIN_OPACITY_PERCENT || clamped >= MAX_OPACITY_PERCENT) return 0f
        val fadeIn = (clamped / 50f).coerceAtMost(1f)
        val fadeOut = 1f - (clamped / 100f)
        return (fadeIn * fadeOut * 0.7f).coerceIn(0f, 1f)
    }
}
