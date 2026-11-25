/**
 * File: ChatSizingMath.kt
 *
 * Description: Pure Kotlin math helpers for chat message scaling and spacing.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.ui.adapter

import kotlin.math.round
import kotlin.math.roundToInt

object ChatSizingMath {
    const val MIN_SCALE = 50
    const val MAX_SCALE = 200
    const val SCALE_STEP = 5
    const val DEFAULT_SCALE = 100

    const val MIN_EFFECTIVE_TEXT_SP = 5f
    const val MAX_EFFECTIVE_TEXT_SP = 48f

    const val MIN_SPACING_DP = 0
    const val MAX_SPACING_DP = 16
    const val DEFAULT_SPACING_DP = 4

    fun clampScale(percent: Int): Int = percent.coerceIn(MIN_SCALE, MAX_SCALE)

    fun scaleFactor(scalePercent: Int): Float = clampScale(scalePercent) / 100f

    fun snapScaleToStep(rawPercent: Double): Int {
        if (!rawPercent.isFinite()) return DEFAULT_SCALE
        val bounded = rawPercent.coerceIn(MIN_SCALE.toDouble(), MAX_SCALE.toDouble())
        val snapped = round(bounded / SCALE_STEP).toInt() * SCALE_STEP
        return clampScale(snapped)
    }

    fun effectiveTextSizeSp(baseSizeSp: Float, scalePercent: Int): Float =
        (baseSizeSp * scaleFactor(scalePercent)).coerceIn(MIN_EFFECTIVE_TEXT_SP, MAX_EFFECTIVE_TEXT_SP)

    fun scaledPx(basePx: Int, scalePercent: Int, minPx: Int = 1): Int {
        if (basePx <= 0) return 0
        val safeMin = minPx.coerceAtLeast(0)
        return (basePx * scaleFactor(scalePercent)).roundToInt().coerceAtLeast(safeMin)
    }

    fun scaledDpToPx(baseDp: Float, density: Float, scalePercent: Int, minPx: Int = 0): Int {
        if (!baseDp.isFinite() || !density.isFinite() || baseDp <= 0f || density <= 0f) return 0
        val safeMin = minPx.coerceAtLeast(0)
        return (baseDp * density * scaleFactor(scalePercent)).roundToInt().coerceAtLeast(safeMin)
    }

    fun clampSpacingDp(dp: Int): Int = dp.coerceIn(MIN_SPACING_DP, MAX_SPACING_DP)

    fun messageTextVerticalPaddingDp(spacingDp: Int): Float =
        (clampSpacingDp(spacingDp) / 2f).coerceIn(0f, 3f)
}
