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
    fun floatingBorderArgb_staysSubtleTranslucentWhiteAcrossOpacityRange() {
        val atZero = ChatOverlayStyleUtils.floatingBorderArgb(0)
        val atMid = ChatOverlayStyleUtils.floatingBorderArgb(50)
        val atMax = ChatOverlayStyleUtils.floatingBorderArgb(100)

        assertEquals(0x38FFFFFF, atZero)
        assertEquals(0x4DFFFFFF, atMax)
        assertEquals(0x00FFFFFF, atMid and 0x00FFFFFF)
        val midAlpha = (atMid ushr 24) and 0xFF
        assertTrue(midAlpha in 0x38..0x4D)
        assertEquals(atZero, ChatOverlayStyleUtils.floatingBorderArgb(-20))
        assertEquals(atMax, ChatOverlayStyleUtils.floatingBorderArgb(150))
    }

    @Test
    fun floatingHandleBgArgb_enforcesMinimumFloorAndScalesWithOpacity() {
        val expectedFloor = ChatOverlayStyleUtils.argbWithOpacity(0x181818, 35)
        assertEquals(expectedFloor, ChatOverlayStyleUtils.floatingHandleBgArgb(0))
        assertEquals(expectedFloor, ChatOverlayStyleUtils.floatingHandleBgArgb(20))
        assertEquals(expectedFloor, ChatOverlayStyleUtils.floatingHandleBgArgb(35))
        assertEquals(ChatOverlayStyleUtils.argbWithOpacity(0x181818, 70), ChatOverlayStyleUtils.floatingHandleBgArgb(70))
        assertEquals(0xFF181818.toInt(), ChatOverlayStyleUtils.floatingHandleBgArgb(100))
        assertEquals(0xFF181818.toInt(), ChatOverlayStyleUtils.floatingHandleBgArgb(140))
    }
}
