package dev.xacnio.kciktv.shared.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSizingMathTest {

    @Test
    fun clampScale_clampsWithinBounds() {
        assertEquals(ChatSizingMath.MIN_SCALE, ChatSizingMath.clampScale(0))
        assertEquals(ChatSizingMath.MIN_SCALE, ChatSizingMath.clampScale(-25))
        assertEquals(ChatSizingMath.MIN_SCALE, ChatSizingMath.clampScale(50))
        assertEquals(125, ChatSizingMath.clampScale(125))
        assertEquals(ChatSizingMath.MAX_SCALE, ChatSizingMath.clampScale(200))
        assertEquals(ChatSizingMath.MAX_SCALE, ChatSizingMath.clampScale(350))
    }

    @Test
    fun snapScaleToStep_handlesNonFiniteValuesSafely() {
        assertEquals(ChatSizingMath.DEFAULT_SCALE, ChatSizingMath.snapScaleToStep(Double.NaN))
        assertEquals(ChatSizingMath.DEFAULT_SCALE, ChatSizingMath.snapScaleToStep(Double.POSITIVE_INFINITY))
        assertEquals(ChatSizingMath.DEFAULT_SCALE, ChatSizingMath.snapScaleToStep(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun snapScaleToStep_roundsToNearestFiveAndClamps() {
        assertEquals(50, ChatSizingMath.snapScaleToStep(10.0))
        assertEquals(50, ChatSizingMath.snapScaleToStep(52.4))
        assertEquals(55, ChatSizingMath.snapScaleToStep(52.6))
        assertEquals(100, ChatSizingMath.snapScaleToStep(98.0))
        assertEquals(100, ChatSizingMath.snapScaleToStep(102.0))
        assertEquals(105, ChatSizingMath.snapScaleToStep(103.0))
        assertEquals(150, ChatSizingMath.snapScaleToStep(149.9))
        assertEquals(200, ChatSizingMath.snapScaleToStep(198.0))
        assertEquals(200, ChatSizingMath.snapScaleToStep(500.0))
    }

    @Test
    fun scaleFactor_convertsClampedScaleToMultiplier() {
        assertEquals(0.5f, ChatSizingMath.scaleFactor(10), 0.001f)
        assertEquals(0.5f, ChatSizingMath.scaleFactor(50), 0.001f)
        assertEquals(1.0f, ChatSizingMath.scaleFactor(100), 0.001f)
        assertEquals(1.35f, ChatSizingMath.scaleFactor(135), 0.001f)
        assertEquals(2.0f, ChatSizingMath.scaleFactor(200), 0.001f)
        assertEquals(2.0f, ChatSizingMath.scaleFactor(400), 0.001f)
    }

    @Test
    fun effectiveTextSizeSp_scalesAndClampsBetween5And48() {
        assertEquals(14f, ChatSizingMath.effectiveTextSizeSp(14f, 100), 0.001f)
        assertEquals(7f, ChatSizingMath.effectiveTextSizeSp(14f, 50), 0.001f)
        assertEquals(6.5f, ChatSizingMath.effectiveTextSizeSp(13f, 50), 0.001f)
        assertEquals(5f, ChatSizingMath.effectiveTextSizeSp(10f, 50), 0.001f)
        assertEquals(5f, ChatSizingMath.effectiveTextSizeSp(8f, 50), 0.001f)
        assertEquals(28f, ChatSizingMath.effectiveTextSizeSp(14f, 200), 0.001f)
        assertEquals(48f, ChatSizingMath.effectiveTextSizeSp(30f, 200), 0.001f)
        // Out-of-range scalePercent should still be clamped via clampScale
        assertEquals(5f, ChatSizingMath.effectiveTextSizeSp(10f, 10), 0.001f)
        assertEquals(40f, ChatSizingMath.effectiveTextSizeSp(20f, 500), 0.001f)
    }

    @Test
    fun scaledPx_scalesProportionallyAndRespectsMinPx() {
        assertEquals(0, ChatSizingMath.scaledPx(0, 100))
        assertEquals(0, ChatSizingMath.scaledPx(-4, 150))
        assertEquals(5, ChatSizingMath.scaledPx(10, 50))
        assertEquals(10, ChatSizingMath.scaledPx(10, 100))
        assertEquals(20, ChatSizingMath.scaledPx(10, 200))
        assertEquals(1, ChatSizingMath.scaledPx(1, 50))
        assertEquals(2, ChatSizingMath.scaledPx(2, 50, minPx = 2))
    }

    @Test
    fun scaledDpToPx_convertsDpWithScaleAndMinFloor() {
        assertEquals(0, ChatSizingMath.scaledDpToPx(0f, 3f, 100, minPx = 1))
        assertEquals(0, ChatSizingMath.scaledDpToPx(8f, 0f, 100, minPx = 1))
        assertEquals(0, ChatSizingMath.scaledDpToPx(Float.NaN, 3f, 100))
        assertEquals(12, ChatSizingMath.scaledDpToPx(8f, 3f, 50))
        assertEquals(24, ChatSizingMath.scaledDpToPx(8f, 3f, 100))
        assertEquals(48, ChatSizingMath.scaledDpToPx(8f, 3f, 200))
        assertEquals(1, ChatSizingMath.scaledDpToPx(1f, 0.75f, 50, minPx = 1))
        assertEquals(0, ChatSizingMath.scaledDpToPx(1f, 0.75f, 50, minPx = 0))
    }

    @Test
    fun clampSpacingDp_clampsWithinBounds() {
        assertEquals(ChatSizingMath.MIN_SPACING_DP, ChatSizingMath.clampSpacingDp(-5))
        assertEquals(0, ChatSizingMath.clampSpacingDp(0))
        assertEquals(4, ChatSizingMath.clampSpacingDp(4))
        assertEquals(16, ChatSizingMath.clampSpacingDp(16))
        assertEquals(ChatSizingMath.MAX_SPACING_DP, ChatSizingMath.clampSpacingDp(32))
    }

    @Test
    fun messageTextVerticalPaddingDp_halvesAndCapsAt3Dp() {
        assertEquals(0f, ChatSizingMath.messageTextVerticalPaddingDp(-4), 0.001f)
        assertEquals(0f, ChatSizingMath.messageTextVerticalPaddingDp(0), 0.001f)
        assertEquals(0.5f, ChatSizingMath.messageTextVerticalPaddingDp(1), 0.001f)
        assertEquals(2f, ChatSizingMath.messageTextVerticalPaddingDp(4), 0.001f)
        assertEquals(3f, ChatSizingMath.messageTextVerticalPaddingDp(6), 0.001f)
        assertEquals(3f, ChatSizingMath.messageTextVerticalPaddingDp(12), 0.001f)
        assertEquals(3f, ChatSizingMath.messageTextVerticalPaddingDp(16), 0.001f)
        assertEquals(3f, ChatSizingMath.messageTextVerticalPaddingDp(50), 0.001f)
    }

    @Test
    fun clampHorizontalPaddingDp_clampsTo0And24() {
        assertEquals(0, ChatSizingMath.clampHorizontalPaddingDp(-5))
        assertEquals(0, ChatSizingMath.clampHorizontalPaddingDp(0))
        assertEquals(12, ChatSizingMath.clampHorizontalPaddingDp(12))
        assertEquals(24, ChatSizingMath.clampHorizontalPaddingDp(24))
        assertEquals(24, ChatSizingMath.clampHorizontalPaddingDp(40))
    }

    @Test
    fun scaledHorizontalPaddingDp_scalesProportionallyAcrossLayers() {
        assertEquals(0f, ChatSizingMath.listHorizontalPaddingDp(0), 0.001f)
        assertEquals(0f, ChatSizingMath.messageItemHorizontalPaddingDp(0), 0.001f)
        assertEquals(0f, ChatSizingMath.messageTextHorizontalPaddingDp(0), 0.001f)

        assertEquals(6f, ChatSizingMath.listHorizontalPaddingDp(12), 0.001f)
        assertEquals(4f, ChatSizingMath.messageItemHorizontalPaddingDp(12), 0.001f)
        assertEquals(2f, ChatSizingMath.messageTextHorizontalPaddingDp(12), 0.001f)

        assertEquals(12f, ChatSizingMath.listHorizontalPaddingDp(24), 0.001f)
        assertEquals(8f, ChatSizingMath.messageItemHorizontalPaddingDp(24), 0.001f)
        assertEquals(4f, ChatSizingMath.messageTextHorizontalPaddingDp(24), 0.001f)
    }

    @Test
    fun clampQuickEmoteBarScale_clampsWithin50And150() {
        assertEquals(50, ChatSizingMath.MIN_QUICK_EMOTE_BAR_SCALE)
        assertEquals(150, ChatSizingMath.MAX_QUICK_EMOTE_BAR_SCALE)
        assertEquals(5, ChatSizingMath.QUICK_EMOTE_BAR_SCALE_STEP)
        assertEquals(100, ChatSizingMath.DEFAULT_QUICK_EMOTE_BAR_SCALE)

        assertEquals(50, ChatSizingMath.clampQuickEmoteBarScale(0))
        assertEquals(50, ChatSizingMath.clampQuickEmoteBarScale(50))
        assertEquals(115, ChatSizingMath.clampQuickEmoteBarScale(115))
        assertEquals(150, ChatSizingMath.clampQuickEmoteBarScale(150))
        assertEquals(150, ChatSizingMath.clampQuickEmoteBarScale(200))
    }

    @Test
    fun quickEmoteBarMetrics_scaleAndRespectMinFloors() {
        // Default 100%
        assertEquals(1.0f, ChatSizingMath.quickEmoteBarScaleFactor(100), 0.001f)
        assertEquals(32f, ChatSizingMath.quickEmoteBarEmoteHeightDp(100), 0.001f)
        assertEquals(40f, ChatSizingMath.quickEmoteBarItemSizeDp(100), 0.001f)
        assertEquals(2f, ChatSizingMath.quickEmoteBarItemMarginDp(100), 0.001f)
        assertEquals(44f, ChatSizingMath.quickEmoteBarContainerHeightDp(100), 0.001f)
        assertEquals(28f, ChatSizingMath.quickEmoteBarPlaceholderSizeDp(100), 0.001f)
        assertEquals(14f, ChatSizingMath.quickEmoteBarProgressSizeDp(100), 0.001f)
        assertEquals(6f, ChatSizingMath.quickEmoteBarShimmerMarginDp(100), 0.001f)

        // Min 50% (and out-of-range low 10%)
        assertEquals(0.5f, ChatSizingMath.quickEmoteBarScaleFactor(10), 0.001f)
        assertEquals(16f, ChatSizingMath.quickEmoteBarEmoteHeightDp(50), 0.001f)
        assertEquals(20f, ChatSizingMath.quickEmoteBarItemSizeDp(50), 0.001f)
        assertEquals(1f, ChatSizingMath.quickEmoteBarItemMarginDp(50), 0.001f)
        assertEquals(22f, ChatSizingMath.quickEmoteBarContainerHeightDp(50), 0.001f)
        assertEquals(14f, ChatSizingMath.quickEmoteBarPlaceholderSizeDp(50), 0.001f)
        assertEquals(8f, ChatSizingMath.quickEmoteBarProgressSizeDp(50), 0.001f) // 7f floored to 8f
        assertEquals(3f, ChatSizingMath.quickEmoteBarShimmerMarginDp(50), 0.001f)

        // Max 150% (and out-of-range high 250%)
        assertEquals(1.5f, ChatSizingMath.quickEmoteBarScaleFactor(250), 0.001f)
        assertEquals(48f, ChatSizingMath.quickEmoteBarEmoteHeightDp(150), 0.001f)
        assertEquals(60f, ChatSizingMath.quickEmoteBarItemSizeDp(150), 0.001f)
        assertEquals(3f, ChatSizingMath.quickEmoteBarItemMarginDp(150), 0.001f)
        assertEquals(66f, ChatSizingMath.quickEmoteBarContainerHeightDp(150), 0.001f)
        assertEquals(42f, ChatSizingMath.quickEmoteBarPlaceholderSizeDp(150), 0.001f)
        assertEquals(21f, ChatSizingMath.quickEmoteBarProgressSizeDp(150), 0.001f)
        assertEquals(9f, ChatSizingMath.quickEmoteBarShimmerMarginDp(150), 0.001f)
    }
}
