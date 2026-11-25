package dev.xacnio.kciktv.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOverlayStyleUtilsTest {

    @Test
    fun clampOpacityPercent_clampsOutOfRangeAndPreservesValidValues() {
        assertEquals(50, ChatOverlayStyleUtils.DEFAULT_OPACITY_PERCENT)
        assertEquals(0, ChatOverlayStyleUtils.clampOpacityPercent(-25))
        assertEquals(0, ChatOverlayStyleUtils.clampOpacityPercent(0))
        assertEquals(50, ChatOverlayStyleUtils.clampOpacityPercent(50))
        assertEquals(85, ChatOverlayStyleUtils.clampOpacityPercent(85))
        assertEquals(100, ChatOverlayStyleUtils.clampOpacityPercent(100))
        assertEquals(100, ChatOverlayStyleUtils.clampOpacityPercent(140))
    }

    @Test
    fun overlayAlphaInt_mapsPercentToRoundedByteRange() {
        assertEquals(0x00, ChatOverlayStyleUtils.overlayAlphaInt(0))
        assertEquals(0x00, ChatOverlayStyleUtils.overlayAlphaInt(-10))
        assertEquals(128, ChatOverlayStyleUtils.overlayAlphaInt(50))
        assertEquals(0xFF, ChatOverlayStyleUtils.overlayAlphaInt(100))
        assertEquals(0xFF, ChatOverlayStyleUtils.overlayAlphaInt(150))
    }

    @Test
    fun argbWithOpacity_combinesComputedAlphaWithRgbBitsCleanly() {
        assertEquals(0x00000000, ChatOverlayStyleUtils.argbWithOpacity(0x000000, 0))
        assertEquals(0x80000000.toInt(), ChatOverlayStyleUtils.argbWithOpacity(0x000000, 50))
        assertEquals(0xFF000000.toInt(), ChatOverlayStyleUtils.argbWithOpacity(0x000000, 100))
        assertEquals(0x801A2B3C.toInt(), ChatOverlayStyleUtils.argbWithOpacity(0xFF1A2B3C.toInt(), 50))
    }

    @Test
    fun shouldUseTextShadow_onlyTrueWhenOverlayActiveAndBelowThreshold() {
        assertFalse(ChatOverlayStyleUtils.shouldUseTextShadow(false, 0))
        assertFalse(ChatOverlayStyleUtils.shouldUseTextShadow(false, 50))
        assertTrue(ChatOverlayStyleUtils.shouldUseTextShadow(true, -5))
        assertTrue(ChatOverlayStyleUtils.shouldUseTextShadow(true, 0))
        assertTrue(ChatOverlayStyleUtils.shouldUseTextShadow(true, 50))
        assertTrue(ChatOverlayStyleUtils.shouldUseTextShadow(true, 84))
        assertFalse(ChatOverlayStyleUtils.shouldUseTextShadow(true, 85))
        assertFalse(ChatOverlayStyleUtils.shouldUseTextShadow(true, 100))
    }

    @Test
    fun computeRightControlsOverlapInsetPx_returnsWidthOnlyInTransparentSideChatMode() {
        assertEquals(0, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(false, 420))
        assertEquals(420, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(true, 420))
        assertEquals(0, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(true, -30))
    }

    @Test
    fun effectiveTheatreKeyboardOpacity_appliesMinimumFloorOnlyWhenImeIsOpen() {
        assertEquals(0, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(0, isImeOpen = false))
        assertEquals(35, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(35, isImeOpen = false))
        assertEquals(60, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(0, isImeOpen = true))
        assertEquals(60, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(35, isImeOpen = true))
        assertEquals(60, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(60, isImeOpen = true))
        assertEquals(85, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(85, isImeOpen = true))
        assertEquals(100, ChatOverlayStyleUtils.effectiveTheatreKeyboardOpacity(120, isImeOpen = true))
    }

    @Test
    fun isDockedSideChat_trueOnlyWhenSideChatVisibleAndOverlayDisabled() {
        assertFalse(ChatOverlayStyleUtils.isDockedSideChat(isSideChatVisible = false, transparentChatOverlayEnabled = false))
        assertFalse(ChatOverlayStyleUtils.isDockedSideChat(isSideChatVisible = false, transparentChatOverlayEnabled = true))
        assertTrue(ChatOverlayStyleUtils.isDockedSideChat(isSideChatVisible = true, transparentChatOverlayEnabled = false))
        assertFalse(ChatOverlayStyleUtils.isDockedSideChat(isSideChatVisible = true, transparentChatOverlayEnabled = true))
    }

    @Test
    fun theatreGradientAlpha_zeroAtExtremesAndBoundedInMiddle() {
        assertEquals(0f, ChatOverlayStyleUtils.theatreGradientAlpha(0), 0.0001f)
        assertEquals(0f, ChatOverlayStyleUtils.theatreGradientAlpha(100), 0.0001f)
        val midAlpha = ChatOverlayStyleUtils.theatreGradientAlpha(50)
        assertTrue(midAlpha > 0f && midAlpha <= 1f)
    }

    @Test
    fun clampEdgeMarginPercent_clampsOutOfRangeAndExposesConstants() {
        assertEquals(0, ChatOverlayStyleUtils.MIN_EDGE_MARGIN_PERCENT)
        assertEquals(30, ChatOverlayStyleUtils.MAX_EDGE_MARGIN_PERCENT)
        assertEquals(0, ChatOverlayStyleUtils.DEFAULT_EDGE_MARGIN_PERCENT)
        assertEquals(0.55f, ChatOverlayStyleUtils.MAX_CONTROLS_OVERLAP_RATIO, 0.0001f)
        assertEquals(0, ChatOverlayStyleUtils.clampEdgeMarginPercent(-10))
        assertEquals(0, ChatOverlayStyleUtils.clampEdgeMarginPercent(0))
        assertEquals(15, ChatOverlayStyleUtils.clampEdgeMarginPercent(15))
        assertEquals(30, ChatOverlayStyleUtils.clampEdgeMarginPercent(30))
        assertEquals(30, ChatOverlayStyleUtils.clampEdgeMarginPercent(80))
    }

    @Test
    fun computeEdgeMarginPx_clampsToPercentAndRemainingParentWidth() {
        assertEquals(0, ChatOverlayStyleUtils.computeEdgeMarginPx(2000, 700, 0))
        assertEquals(200, ChatOverlayStyleUtils.computeEdgeMarginPx(2000, 700, 10))
        assertEquals(600, ChatOverlayStyleUtils.computeEdgeMarginPx(2000, 700, 30))
        // Clamped to max 30%
        assertEquals(600, ChatOverlayStyleUtils.computeEdgeMarginPx(2000, 700, 50))
        // Clamped so chat never clips off left edge (parentW - chatW = 200 < 300)
        assertEquals(200, ChatOverlayStyleUtils.computeEdgeMarginPx(1000, 800, 30))
        // Zero / negative parent width guarded
        assertEquals(0, ChatOverlayStyleUtils.computeEdgeMarginPx(0, 400, 15))
        assertEquals(0, ChatOverlayStyleUtils.computeEdgeMarginPx(-100, 400, 15))
    }

    @Test
    fun computeRemainingRightCutoutPaddingPx_avoidsDoublePaddingAndGuardsNegatives() {
        assertEquals(100, ChatOverlayStyleUtils.computeRemainingRightCutoutPaddingPx(100, 0))
        assertEquals(60, ChatOverlayStyleUtils.computeRemainingRightCutoutPaddingPx(100, 40))
        assertEquals(0, ChatOverlayStyleUtils.computeRemainingRightCutoutPaddingPx(100, 120))
        assertEquals(0, ChatOverlayStyleUtils.computeRemainingRightCutoutPaddingPx(-20, 10))
        assertEquals(100, ChatOverlayStyleUtils.computeRemainingRightCutoutPaddingPx(100, -10))
    }

    @Test
    fun computeRightControlsOverlapInsetPx_addsEdgeMarginAndCapsAtFiftyFivePercentOfRootWidth() {
        // Inactive -> 0
        assertEquals(0, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(false, 400, 100, 1000))
        // Active under 55% cap -> chatWidth + edgeMarginPx
        assertEquals(450, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(true, 350, 100, 1000))
        // Active over 55% cap -> capped at 55% of rootWidthPx
        assertEquals(550, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(true, 500, 200, 1000))
        // Negative widths/margins coerced to 0
        assertEquals(0, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(true, -50, -20, 1000))
        // Non-positive rootWidthPx leaves inset uncapped
        assertEquals(700, ChatOverlayStyleUtils.computeRightControlsOverlapInsetPx(true, 500, 200, 0))
    }
}
