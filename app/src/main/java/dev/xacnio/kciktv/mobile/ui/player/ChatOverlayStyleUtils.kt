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

    fun clampOpacityPercent(percent: Int): Int =
        percent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    fun overlayAlphaInt(opacityPercent: Int): Int =
        (clampOpacityPercent(opacityPercent) * 255f / 100f).roundToInt().coerceIn(0, 255)

    fun argbWithOpacity(baseRgb: Int, opacityPercent: Int): Int =
        (overlayAlphaInt(opacityPercent) shl 24) or (baseRgb and 0x00FFFFFF)

    fun shouldUseTextShadow(isTransparentOverlayActive: Boolean, opacityPercent: Int): Boolean =
        isTransparentOverlayActive && clampOpacityPercent(opacityPercent) < TEXT_SHADOW_OPACITY_THRESHOLD

    fun computeRightControlsOverlapInsetPx(
        isTransparentSideChatActive: Boolean,
        overlayWidthPx: Int
    ): Int = if (isTransparentSideChatActive) overlayWidthPx.coerceAtLeast(0) else 0

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
