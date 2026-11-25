/**
 * File: FloatingChatBounds.kt
 *
 * Description: Pure Kotlin geometry and layout helpers for landscape floating transparent chat
 * and horizontal edge-offset positioning, including display-cutout safe bounds and IME lift/shrink.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.player

import kotlin.math.roundToInt

object FloatingChatBounds {
    const val MIN_EDGE_MARGIN_PERCENT = 0
    const val MAX_EDGE_MARGIN_PERCENT = 30
    const val DEFAULT_EDGE_MARGIN_PERCENT = 0

    const val MIN_HEIGHT_PERCENT = 35
    const val MAX_HEIGHT_PERCENT = 100
    const val DEFAULT_HEIGHT_PERCENT = 75

    const val DEFAULT_X_PERCENT = 1.0f
    const val DEFAULT_Y_PERCENT = 0.5f

    const val MAX_CONTROLS_OVERLAP_RATIO = 0.55f

    data class RectPx(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    data class NormalizedPosition(
        val xPercent: Float,
        val yPercent: Float
    )

    private data class RangePx(val min: Int, val max: Int)

    fun clampEdgeMarginPercent(percent: Int): Int =
        percent.coerceIn(MIN_EDGE_MARGIN_PERCENT, MAX_EDGE_MARGIN_PERCENT)

    fun clampHeightPercent(percent: Int): Int =
        percent.coerceIn(MIN_HEIGHT_PERCENT, MAX_HEIGHT_PERCENT)

    fun clampNormalizedPercent(value: Float, fallback: Float): Float {
        val safeFallback = if (fallback.isFinite()) fallback.coerceIn(0f, 1f) else 0f
        if (!value.isFinite()) return safeFallback
        return value.coerceIn(0f, 1f)
    }

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
        edgeMarginPx: Int,
        isFloatingActive: Boolean
    ): Int {
        if (isFloatingActive) return 0
        return (cutoutRightPx.coerceAtLeast(0) - edgeMarginPx.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun computeRightControlsOverlapInsetPx(
        isTransparentSideChatActive: Boolean,
        isFloatingChatActive: Boolean,
        overlayWidthPx: Int,
        edgeMarginPx: Int,
        rootWidthPx: Int
    ): Int {
        if (!isTransparentSideChatActive || isFloatingChatActive) return 0
        val raw = overlayWidthPx.coerceAtLeast(0) + edgeMarginPx.coerceAtLeast(0)
        if (rootWidthPx <= 0) return raw
        val maxCap = (rootWidthPx * MAX_CONTROLS_OVERLAP_RATIO).roundToInt().coerceAtLeast(0)
        return raw.coerceAtMost(maxCap)
    }

    private fun computeHorizontalTravelRange(
        parentWidthPx: Int,
        rectWidthPx: Int,
        edgeMarginPercent: Int,
        minOuterGapPx: Int,
        cutoutLeftPx: Int,
        cutoutRightPx: Int
    ): RangePx {
        val safeParentW = parentWidthPx.coerceAtLeast(0)
        val safeW = rectWidthPx.coerceIn(0, safeParentW)
        val freeSpace = (safeParentW - safeW).coerceAtLeast(0)
        if (freeSpace == 0) return RangePx(0, 0)

        val gap = minOuterGapPx.coerceAtLeast(0)
        val cLeft = cutoutLeftPx.coerceAtLeast(0)
        val cRight = cutoutRightPx.coerceAtLeast(0)
        val edgeMarginPx = (safeParentW * clampEdgeMarginPercent(edgeMarginPercent) / 100f)
            .roundToInt()
            .coerceAtLeast(0)

        val leftMin = cLeft + gap
        val rightMin = cRight + gap
        val desiredLeft = maxOf(leftMin, edgeMarginPx)
        val desiredRight = maxOf(rightMin, edgeMarginPx)

        if (desiredLeft + desiredRight <= freeSpace) {
            return RangePx(desiredLeft, freeSpace - desiredRight)
        }
        if (leftMin + rightMin <= freeSpace) {
            val pinned = (freeSpace / 2).coerceIn(leftMin, freeSpace - rightMin)
            return RangePx(pinned, pinned)
        }
        if (cLeft + cRight <= freeSpace) {
            val pinned = (freeSpace / 2).coerceIn(cLeft, freeSpace - cRight)
            return RangePx(pinned, pinned)
        }
        val centered = (freeSpace / 2).coerceIn(0, freeSpace)
        return RangePx(centered, centered)
    }

    private fun computeVerticalTravelRange(
        parentHeightPx: Int,
        rectHeightPx: Int,
        minOuterGapPx: Int,
        cutoutTopPx: Int,
        cutoutBottomPx: Int,
        imeBottomPx: Int
    ): RangePx {
        val safeParentH = parentHeightPx.coerceAtLeast(0)
        val safeH = rectHeightPx.coerceIn(0, safeParentH)
        val freeSpace = (safeParentH - safeH).coerceAtLeast(0)
        if (freeSpace == 0) return RangePx(0, 0)

        val gap = minOuterGapPx.coerceAtLeast(0)
        val cTop = cutoutTopPx.coerceAtLeast(0)
        val cBottom = cutoutBottomPx.coerceAtLeast(0)
        val safeIme = imeBottomPx.coerceAtLeast(0)

        val topInset = cTop + gap
        val bottomInset = if (safeIme > 0) maxOf(cBottom, safeIme) + gap else cBottom + gap

        if (topInset + bottomInset <= freeSpace) {
            return RangePx(topInset, freeSpace - bottomInset)
        }
        if (safeIme > 0) {
            val pinnedTop = topInset.coerceAtMost(freeSpace)
            return RangePx(pinnedTop, pinnedTop)
        }
        if (cTop + cBottom <= freeSpace) {
            val pinned = (freeSpace / 2).coerceIn(cTop, freeSpace - cBottom)
            return RangePx(pinned, pinned)
        }
        val centered = (freeSpace / 2).coerceIn(0, freeSpace)
        return RangePx(centered, centered)
    }

    fun computeFloatingRect(
        parentWidthPx: Int,
        parentHeightPx: Int,
        widthPercent: Int,
        heightPercent: Int,
        xPercent: Float,
        yPercent: Float,
        edgeMarginPercent: Int,
        minOuterGapPx: Int = 0,
        cutoutLeftPx: Int = 0,
        cutoutTopPx: Int = 0,
        cutoutRightPx: Int = 0,
        cutoutBottomPx: Int = 0,
        imeBottomPx: Int = 0,
        minWidthPx: Int = 1,
        minHeightPx: Int = 1
    ): RectPx {
        if (parentWidthPx <= 0 || parentHeightPx <= 0) {
            return RectPx(0, 0, 0, 0)
        }

        val gap = minOuterGapPx.coerceAtLeast(0)
        val cLeft = cutoutLeftPx.coerceAtLeast(0)
        val cTop = cutoutTopPx.coerceAtLeast(0)
        val cRight = cutoutRightPx.coerceAtLeast(0)
        val cBottom = cutoutBottomPx.coerceAtLeast(0)
        val safeIme = imeBottomPx.coerceAtLeast(0)

        val maxSafeWidth = (parentWidthPx - cLeft - cRight - 2 * gap)
            .let { if (it > 0) it else parentWidthPx }
            .coerceAtMost(parentWidthPx)
        val effectiveMinWidth = minWidthPx.coerceAtLeast(1).coerceAtMost(maxSafeWidth)
        val rawWidth = SideChatWidthUtils.widthPxForPercent(parentWidthPx, widthPercent)
        val width = rawWidth.coerceIn(effectiveMinWidth, maxSafeWidth)

        val maxSafeHeightNoIme = (parentHeightPx - cTop - cBottom - 2 * gap)
            .let { if (it > 0) it else parentHeightPx }
            .coerceAtMost(parentHeightPx)
        val effectiveMinHeight = minHeightPx.coerceAtLeast(1).coerceAtMost(maxSafeHeightNoIme)
        val rawHeight = (parentHeightPx * clampHeightPercent(heightPercent) / 100f).roundToInt()
        val baseHeight = rawHeight.coerceIn(effectiveMinHeight, maxSafeHeightNoIme)

        val xRange = computeHorizontalTravelRange(
            parentWidthPx = parentWidthPx,
            rectWidthPx = width,
            edgeMarginPercent = edgeMarginPercent,
            minOuterGapPx = gap,
            cutoutLeftPx = cLeft,
            cutoutRightPx = cRight
        )
        val clampedX = clampNormalizedPercent(xPercent, DEFAULT_X_PERCENT)
        val left = xRange.min + ((xRange.max - xRange.min) * clampedX).roundToInt()

        val yRangeNoIme = computeVerticalTravelRange(
            parentHeightPx = parentHeightPx,
            rectHeightPx = baseHeight,
            minOuterGapPx = gap,
            cutoutTopPx = cTop,
            cutoutBottomPx = cBottom,
            imeBottomPx = 0
        )
        val clampedY = clampNormalizedPercent(yPercent, DEFAULT_Y_PERCENT)
        val baseTop = yRangeNoIme.min + ((yRangeNoIme.max - yRangeNoIme.min) * clampedY).roundToInt()

        if (safeIme <= 0) {
            return RectPx(left = left, top = baseTop, width = width, height = baseHeight)
        }

        val topLimit = (cTop + gap).coerceAtMost((parentHeightPx - 1).coerceAtLeast(0))
        val imeBottomLimit = (parentHeightPx - maxOf(cBottom, safeIme) - gap)
            .coerceIn((topLimit + 1).coerceAtMost(parentHeightPx), parentHeightPx)

        if (baseTop + baseHeight <= imeBottomLimit) {
            return RectPx(left = left, top = baseTop, width = width, height = baseHeight)
        }

        val maxHeightAboveIme = (imeBottomLimit - topLimit).coerceAtLeast(1)
        val finalHeight = baseHeight.coerceAtMost(maxHeightAboveIme)
        val finalTop = (imeBottomLimit - finalHeight).coerceIn(topLimit, (parentHeightPx - finalHeight).coerceAtLeast(0))

        return RectPx(left = left, top = finalTop, width = width, height = finalHeight)
    }

    fun clampDraggedOrigin(
        desiredLeftPx: Int,
        desiredTopPx: Int,
        rectWidthPx: Int,
        rectHeightPx: Int,
        parentWidthPx: Int,
        parentHeightPx: Int,
        edgeMarginPercent: Int,
        minOuterGapPx: Int = 0,
        cutoutLeftPx: Int = 0,
        cutoutTopPx: Int = 0,
        cutoutRightPx: Int = 0,
        cutoutBottomPx: Int = 0,
        imeBottomPx: Int = 0
    ): Pair<Int, Int> {
        if (parentWidthPx <= 0 || parentHeightPx <= 0) return Pair(0, 0)
        val xRange = computeHorizontalTravelRange(
            parentWidthPx = parentWidthPx,
            rectWidthPx = rectWidthPx,
            edgeMarginPercent = edgeMarginPercent,
            minOuterGapPx = minOuterGapPx,
            cutoutLeftPx = cutoutLeftPx,
            cutoutRightPx = cutoutRightPx
        )
        val yRange = computeVerticalTravelRange(
            parentHeightPx = parentHeightPx,
            rectHeightPx = rectHeightPx,
            minOuterGapPx = minOuterGapPx,
            cutoutTopPx = cutoutTopPx,
            cutoutBottomPx = cutoutBottomPx,
            imeBottomPx = imeBottomPx
        )
        return Pair(
            desiredLeftPx.coerceIn(xRange.min, xRange.max),
            desiredTopPx.coerceIn(yRange.min, yRange.max)
        )
    }

    fun originToNormalizedPercent(
        leftPx: Int,
        topPx: Int,
        rectWidthPx: Int,
        rectHeightPx: Int,
        parentWidthPx: Int,
        parentHeightPx: Int,
        edgeMarginPercent: Int,
        minOuterGapPx: Int = 0,
        cutoutLeftPx: Int = 0,
        cutoutTopPx: Int = 0,
        cutoutRightPx: Int = 0,
        cutoutBottomPx: Int = 0,
        imeBottomPx: Int = 0,
        fallbackX: Float = DEFAULT_X_PERCENT,
        fallbackY: Float = DEFAULT_Y_PERCENT
    ): NormalizedPosition {
        if (parentWidthPx <= 0 || parentHeightPx <= 0) {
            return NormalizedPosition(
                clampNormalizedPercent(fallbackX, DEFAULT_X_PERCENT),
                clampNormalizedPercent(fallbackY, DEFAULT_Y_PERCENT)
            )
        }
        val xRange = computeHorizontalTravelRange(
            parentWidthPx = parentWidthPx,
            rectWidthPx = rectWidthPx,
            edgeMarginPercent = edgeMarginPercent,
            minOuterGapPx = minOuterGapPx,
            cutoutLeftPx = cutoutLeftPx,
            cutoutRightPx = cutoutRightPx
        )
        val yRange = computeVerticalTravelRange(
            parentHeightPx = parentHeightPx,
            rectHeightPx = rectHeightPx,
            minOuterGapPx = minOuterGapPx,
            cutoutTopPx = cutoutTopPx,
            cutoutBottomPx = cutoutBottomPx,
            imeBottomPx = imeBottomPx
        )
        val travelX = xRange.max - xRange.min
        val travelY = yRange.max - yRange.min

        val xPercent = if (travelX <= 0) {
            clampNormalizedPercent(fallbackX, DEFAULT_X_PERCENT)
        } else {
            clampNormalizedPercent((leftPx - xRange.min).toFloat() / travelX.toFloat(), fallbackX)
        }
        val yPercent = if (travelY <= 0) {
            clampNormalizedPercent(fallbackY, DEFAULT_Y_PERCENT)
        } else {
            clampNormalizedPercent((topPx - yRange.min).toFloat() / travelY.toFloat(), fallbackY)
        }
        return NormalizedPosition(xPercent, yPercent)
    }
}
