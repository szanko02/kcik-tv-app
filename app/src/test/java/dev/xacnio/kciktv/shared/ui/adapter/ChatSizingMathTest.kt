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
    fun effectiveTextSizeSp_scalesAndClampsBetween7And44() {
        assertEquals(14f, ChatSizingMath.effectiveTextSizeSp(14f, 100), 0.001f)
        assertEquals(7f, ChatSizingMath.effectiveTextSizeSp(14f, 50), 0.001f)
        assertEquals(7f, ChatSizingMath.effectiveTextSizeSp(10f, 50), 0.001f)
        assertEquals(28f, ChatSizingMath.effectiveTextSizeSp(14f, 200), 0.001f)
        assertEquals(44f, ChatSizingMath.effectiveTextSizeSp(30f, 200), 0.001f)
        // Out-of-range scalePercent should still be clamped via clampScale
        assertEquals(7f, ChatSizingMath.effectiveTextSizeSp(14f, 10), 0.001f)
        assertEquals(40f, ChatSizingMath.effectiveTextSizeSp(20f, 500), 0.001f)
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
