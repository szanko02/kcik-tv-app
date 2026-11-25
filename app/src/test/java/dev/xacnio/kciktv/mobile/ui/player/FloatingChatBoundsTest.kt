package dev.xacnio.kciktv.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatBoundsTest {

    @Test
    fun clampHelpers_clampRangeAndHandleNonFiniteValues() {
        assertEquals(0, FloatingChatBounds.clampEdgeMarginPercent(-10))
        assertEquals(0, FloatingChatBounds.clampEdgeMarginPercent(0))
        assertEquals(15, FloatingChatBounds.clampEdgeMarginPercent(15))
        assertEquals(30, FloatingChatBounds.clampEdgeMarginPercent(30))
        assertEquals(30, FloatingChatBounds.clampEdgeMarginPercent(80))

        assertEquals(35, FloatingChatBounds.clampHeightPercent(10))
        assertEquals(35, FloatingChatBounds.clampHeightPercent(35))
        assertEquals(75, FloatingChatBounds.clampHeightPercent(75))
        assertEquals(100, FloatingChatBounds.clampHeightPercent(100))
        assertEquals(100, FloatingChatBounds.clampHeightPercent(140))

        assertEquals(0f, FloatingChatBounds.clampNormalizedPercent(-0.5f, 0.5f), 0.0001f)
        assertEquals(1f, FloatingChatBounds.clampNormalizedPercent(1.5f, 0.5f), 0.0001f)
        assertEquals(0.42f, FloatingChatBounds.clampNormalizedPercent(0.42f, 0.5f), 0.0001f)
        assertEquals(1.0f, FloatingChatBounds.clampNormalizedPercent(Float.NaN, 1.0f), 0.0001f)
        assertEquals(0.5f, FloatingChatBounds.clampNormalizedPercent(Float.POSITIVE_INFINITY, 0.5f), 0.0001f)
        assertEquals(0.5f, FloatingChatBounds.clampNormalizedPercent(Float.NEGATIVE_INFINITY, 0.5f), 0.0001f)
        assertEquals(0f, FloatingChatBounds.clampNormalizedPercent(Float.NaN, Float.NaN), 0.0001f)
    }

    @Test
    fun computeEdgeMarginPx_clampsToPercentAndRemainingParentWidth() {
        assertEquals(0, FloatingChatBounds.computeEdgeMarginPx(2000, 700, 0))
        assertEquals(200, FloatingChatBounds.computeEdgeMarginPx(2000, 700, 10))
        assertEquals(600, FloatingChatBounds.computeEdgeMarginPx(2000, 700, 30))
        // Clamped to max 30%
        assertEquals(600, FloatingChatBounds.computeEdgeMarginPx(2000, 700, 50))
        // Clamped so chat never clips off left edge (parentW - chatW = 200 < 300)
        assertEquals(200, FloatingChatBounds.computeEdgeMarginPx(1000, 800, 30))
        // Zero / negative parent width guarded
        assertEquals(0, FloatingChatBounds.computeEdgeMarginPx(0, 400, 15))
        assertEquals(0, FloatingChatBounds.computeEdgeMarginPx(-100, 400, 15))
    }

    @Test
    fun computeRemainingRightCutoutPaddingPx_avoidsDoublePaddingAndZeroesWhenFloating() {
        assertEquals(0, FloatingChatBounds.computeRemainingRightCutoutPaddingPx(100, 0, isFloatingActive = true))
        assertEquals(0, FloatingChatBounds.computeRemainingRightCutoutPaddingPx(100, 40, isFloatingActive = true))
        assertEquals(100, FloatingChatBounds.computeRemainingRightCutoutPaddingPx(100, 0, isFloatingActive = false))
        assertEquals(60, FloatingChatBounds.computeRemainingRightCutoutPaddingPx(100, 40, isFloatingActive = false))
        assertEquals(0, FloatingChatBounds.computeRemainingRightCutoutPaddingPx(100, 120, isFloatingActive = false))
        assertEquals(0, FloatingChatBounds.computeRemainingRightCutoutPaddingPx(-20, 10, isFloatingActive = false))
    }

    @Test
    fun computeRightControlsOverlapInsetPx_zeroWhenFloatingOrInactive_cappedWhenDockedOverlay() {
        // Inactive or floating -> 0
        assertEquals(0, FloatingChatBounds.computeRightControlsOverlapInsetPx(false, false, 400, 100, 1000))
        assertEquals(0, FloatingChatBounds.computeRightControlsOverlapInsetPx(true, true, 400, 100, 1000))
        // Docked transparent overlay -> chatWidth + edgeMarginPx, capped at 55% of rootW
        assertEquals(450, FloatingChatBounds.computeRightControlsOverlapInsetPx(true, false, 350, 100, 1000))
        assertEquals(550, FloatingChatBounds.computeRightControlsOverlapInsetPx(true, false, 500, 200, 1000))
        assertEquals(0, FloatingChatBounds.computeRightControlsOverlapInsetPx(true, false, -50, -20, 1000))
    }

    @Test
    fun computeFloatingRect_positionsDefaultRightCenteredAndRespectsEdgeMarginAndCutouts() {
        val rect = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = 2000,
            parentHeightPx = 1000,
            widthPercent = 35,
            heightPercent = 75,
            xPercent = FloatingChatBounds.DEFAULT_X_PERCENT,
            yPercent = FloatingChatBounds.DEFAULT_Y_PERCENT,
            edgeMarginPercent = 10,
            minOuterGapPx = 20,
            cutoutLeftPx = 80,
            cutoutRightPx = 40
        )
        assertEquals(700, rect.width)
        assertEquals(750, rect.height)
        // Right safe inset = max(40 + 20, 200) = 200 -> right = 1800 -> left = 1100
        assertEquals(1100, rect.left)
        assertEquals(1800, rect.right)
        // Vertically centered: minTop = 20, maxTop = 1000 - 750 - 20 = 230 -> mid = 125
        assertEquals(125, rect.top)
        assertEquals(875, rect.bottom)

        // Leftmost placement respects max(cutoutLeft + gap, edgeMargin)
        val leftRect = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = 2000,
            parentHeightPx = 1000,
            widthPercent = 35,
            heightPercent = 75,
            xPercent = 0.0f,
            yPercent = 0.0f,
            edgeMarginPercent = 0,
            minOuterGapPx = 20,
            cutoutLeftPx = 80,
            cutoutTopPx = 30
        )
        assertEquals(100, leftRect.left)
        assertEquals(50, leftRect.top)
    }

    @Test
    fun computeFloatingRect_liftsAndShrinksAboveImeWithoutMutatingSavedIntent() {
        // Case 1: Fits above IME if shifted up
        val shifted = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = 2000,
            parentHeightPx = 1000,
            widthPercent = 35,
            heightPercent = 40, // 400px tall
            xPercent = 1.0f,
            yPercent = 1.0f, // bottom-anchored -> baseTop = 600, bottom = 1000
            edgeMarginPercent = 0,
            minOuterGapPx = 20,
            imeBottomPx = 300 // limit = 1000 - 300 - 20 = 680
        )
        assertEquals(400, shifted.height)
        assertEquals(280, shifted.top)
        assertEquals(680, shifted.bottom)

        // Case 2: Already above IME -> stays untouched
        val untouched = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = 2000,
            parentHeightPx = 1000,
            widthPercent = 35,
            heightPercent = 35, // 350px tall
            xPercent = 1.0f,
            yPercent = 0.0f, // top = 20, bottom = 370 <= 680
            edgeMarginPercent = 0,
            minOuterGapPx = 20,
            imeBottomPx = 300
        )
        assertEquals(350, untouched.height)
        assertEquals(20, untouched.top)

        // Case 3: Taller than space above IME -> shifts to topLimit and shrinks height
        val shrunk = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = 2000,
            parentHeightPx = 1000,
            widthPercent = 35,
            heightPercent = 75, // 750px tall
            xPercent = 1.0f,
            yPercent = 0.5f,
            edgeMarginPercent = 0,
            minOuterGapPx = 20,
            imeBottomPx = 500 // limit = 480, topLimit = 20 -> maxHeight = 460
        )
        assertEquals(20, shrunk.top)
        assertEquals(460, shrunk.height)
        assertEquals(480, shrunk.bottom)
    }

    @Test
    fun computeFloatingRect_guardsZeroParentAndExtremeConstraints() {
        assertEquals(
            FloatingChatBounds.RectPx(0, 0, 0, 0),
            FloatingChatBounds.computeFloatingRect(0, 1000, 35, 75, 1f, 0.5f, 0)
        )
        assertEquals(
            FloatingChatBounds.RectPx(0, 0, 0, 0),
            FloatingChatBounds.computeFloatingRect(1000, -50, 35, 75, 1f, 0.5f, 0)
        )
        // Oversized IME still produces valid positive rect inside parent
        val extremeIme = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = 1000,
            parentHeightPx = 500,
            widthPercent = 35,
            heightPercent = 75,
            xPercent = Float.NaN,
            yPercent = Float.POSITIVE_INFINITY,
            edgeMarginPercent = 30,
            minOuterGapPx = 16,
            imeBottomPx = 900
        )
        assertTrue(extremeIme.width > 0)
        assertTrue(extremeIme.height >= 1)
        assertTrue(extremeIme.top >= 0)
        assertTrue(extremeIme.bottom <= 500)
        assertTrue(extremeIme.left >= 0)
        assertTrue(extremeIme.right <= 1000)
    }

    @Test
    fun clampDraggedOrigin_and_originToNormalizedPercent_roundTripAndHandleZeroTravel() {
        val parentW = 2000
        val parentH = 1000
        val rectW = 700
        val rectH = 750
        val edgeMargin = 10 // 200px
        val gap = 20

        // Overshoot left/top and right/bottom clamp strictly inside safe travel box
        val clampedMin = FloatingChatBounds.clampDraggedOrigin(
            desiredLeftPx = -500,
            desiredTopPx = -300,
            rectWidthPx = rectW,
            rectHeightPx = rectH,
            parentWidthPx = parentW,
            parentHeightPx = parentH,
            edgeMarginPercent = edgeMargin,
            minOuterGapPx = gap
        )
        assertEquals(Pair(200, 20), clampedMin)

        val clampedMax = FloatingChatBounds.clampDraggedOrigin(
            desiredLeftPx = 5000,
            desiredTopPx = 5000,
            rectWidthPx = rectW,
            rectHeightPx = rectH,
            parentWidthPx = parentW,
            parentHeightPx = parentH,
            edgeMarginPercent = edgeMargin,
            minOuterGapPx = gap
        )
        assertEquals(Pair(1100, 230), clampedMax)

        // Round-trip mid position
        val rect = FloatingChatBounds.computeFloatingRect(
            parentWidthPx = parentW,
            parentHeightPx = parentH,
            widthPercent = 35,
            heightPercent = 75,
            xPercent = 0.25f,
            yPercent = 0.75f,
            edgeMarginPercent = edgeMargin,
            minOuterGapPx = gap
        )
        val normalized = FloatingChatBounds.originToNormalizedPercent(
            leftPx = rect.left,
            topPx = rect.top,
            rectWidthPx = rect.width,
            rectHeightPx = rect.height,
            parentWidthPx = parentW,
            parentHeightPx = parentH,
            edgeMarginPercent = edgeMargin,
            minOuterGapPx = gap
        )
        assertEquals(0.25f, normalized.xPercent, 0.01f)
        assertEquals(0.75f, normalized.yPercent, 0.01f)
        assertFalse(normalized.xPercent.isNaN())
        assertFalse(normalized.yPercent.isNaN())

        // Zero-travel (100% height or full width) returns fallback without NaN
        val zeroTravel = FloatingChatBounds.originToNormalizedPercent(
            leftPx = 300,
            topPx = 0,
            rectWidthPx = 700,
            rectHeightPx = 1000,
            parentWidthPx = 1000,
            parentHeightPx = 1000,
            edgeMarginPercent = 30, // 300 + 300 > 300 free -> pinned center, travelX = 0
            fallbackX = 0.8f,
            fallbackY = 0.4f
        )
        assertEquals(0.8f, zeroTravel.xPercent, 0.0001f)
        assertEquals(0.4f, zeroTravel.yPercent, 0.0001f)
    }
}
