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

object ChatSizingMath {
    const val MIN_SCALE = 50
    const val MAX_SCALE = 200
    const val SCALE_STEP = 5
    const val DEFAULT_SCALE = 100

    const val MIN_SPACING_DP = 0
    const val MAX_SPACING_DP = 16
    const val DEFAULT_SPACING_DP = 4

    fun clampScale(percent: Int): Int = percent.coerceIn(MIN_SCALE, MAX_SCALE)

    fun snapScaleToStep(rawPercent: Double): Int {
        if (!rawPercent.isFinite()) return DEFAULT_SCALE
        val bounded = rawPercent.coerceIn(MIN_SCALE.toDouble(), MAX_SCALE.toDouble())
        val snapped = round(bounded / SCALE_STEP).toInt() * SCALE_STEP
        return clampScale(snapped)
    }

    fun effectiveTextSizeSp(baseSizeSp: Float, scalePercent: Int): Float =
        (baseSizeSp * (clampScale(scalePercent) / 100f)).coerceIn(7f, 44f)

    fun clampSpacingDp(dp: Int): Int = dp.coerceIn(MIN_SPACING_DP, MAX_SPACING_DP)

    fun messageTextVerticalPaddingDp(spacingDp: Int): Float =
        (clampSpacingDp(spacingDp) / 2f).coerceIn(0f, 3f)
}
