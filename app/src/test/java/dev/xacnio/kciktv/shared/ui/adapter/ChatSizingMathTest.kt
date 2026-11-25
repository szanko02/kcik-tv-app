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
}
