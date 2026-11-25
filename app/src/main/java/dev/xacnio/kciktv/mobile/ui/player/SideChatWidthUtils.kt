/**
 * File: SideChatWidthUtils.kt
 *
 * Description: Pure Kotlin math helpers for calculating and clamping resizable side chat width
 * percentages and pixel dimensions in landscape split view.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.player

import kotlin.math.roundToInt

object SideChatWidthUtils {
    const val MIN_PERCENT = 15
    const val MAX_PERCENT = 70
    const val DEFAULT_PERCENT = 35

    fun clampPercent(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun widthPxForPercent(
        rootWidthPx: Int,
        percent: Int,
        minChatPx: Int = 0,
        minVideoPx: Int = 0
    ): Int {
        if (rootWidthPx <= 0) return 0
        val raw = (rootWidthPx * clampPercent(percent) / 100f).roundToInt()
        val maxChatPx = (rootWidthPx - minVideoPx).coerceAtLeast(minChatPx)
        return raw.coerceIn(minChatPx, maxChatPx)
    }

    fun percentForTouchX(
        rawX: Float,
        parentLeftX: Int,
        parentWidthPx: Int,
        rightMarginPx: Int = 0
    ): Int {
        if (parentWidthPx <= 0) return DEFAULT_PERCENT
        val safeRightMargin = rightMarginPx.coerceAtLeast(0)
        val chatPx = (parentLeftX + parentWidthPx - safeRightMargin) - rawX
        val rawPercent = ((chatPx / parentWidthPx) * 100f).roundToInt()
        return clampPercent(rawPercent)
    }
}
