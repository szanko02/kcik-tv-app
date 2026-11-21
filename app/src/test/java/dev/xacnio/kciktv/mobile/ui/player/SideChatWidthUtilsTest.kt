package dev.xacnio.kciktv.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SideChatWidthUtilsTest {

    @Test
    fun clampPercent_clampsOutOfBoundsValues() {
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.clampPercent(-10))
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.clampPercent(0))
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.clampPercent(14))
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.clampPercent(15))
        assertEquals(35, SideChatWidthUtils.clampPercent(35))
        assertEquals(SideChatWidthUtils.MAX_PERCENT, SideChatWidthUtils.clampPercent(70))
        assertEquals(SideChatWidthUtils.MAX_PERCENT, SideChatWidthUtils.clampPercent(71))
        assertEquals(SideChatWidthUtils.MAX_PERCENT, SideChatWidthUtils.clampPercent(100))
    }

    @Test
    fun widthPxForPercent_calculatesNormalValues() {
        assertEquals(350, SideChatWidthUtils.widthPxForPercent(1000, 35))
        assertEquals(672, SideChatWidthUtils.widthPxForPercent(1920, 35))
        assertEquals(150, SideChatWidthUtils.widthPxForPercent(1000, 15))
        assertEquals(700, SideChatWidthUtils.widthPxForPercent(1000, 70))
        assertEquals(351, SideChatWidthUtils.widthPxForPercent(1002, 35))
    }

    @Test
    fun widthPxForPercent_clampsOutOfRangePercents() {
        assertEquals(150, SideChatWidthUtils.widthPxForPercent(1000, 5))
        assertEquals(700, SideChatWidthUtils.widthPxForPercent(1000, 95))
    }

    @Test
    fun widthPxForPercent_respectsMinChatAndMinVideoConstraints() {
        assertEquals(200, SideChatWidthUtils.widthPxForPercent(1000, 15, minChatPx = 200))
        assertEquals(600, SideChatWidthUtils.widthPxForPercent(1000, 70, minVideoPx = 400))
        assertEquals(300, SideChatWidthUtils.widthPxForPercent(500, 50, minChatPx = 300, minVideoPx = 300))
    }

    @Test
    fun widthPxForPercent_guardsZeroAndNegativeRootWidth() {
        assertEquals(0, SideChatWidthUtils.widthPxForPercent(0, 35))
        assertEquals(0, SideChatWidthUtils.widthPxForPercent(-100, 35))
    }

    @Test
    fun percentForTouchX_computesExpectedPercentagesAndClamps() {
        // Right edge (rawX == parentWidthPx) -> 0% clamped to MIN_PERCENT (15%)
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.percentForTouchX(1000f, 0, 1000))
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.percentForTouchX(1150f, 0, 1000))

        // Left edge (rawX == 0) -> 100% clamped to MAX_PERCENT (70%)
        assertEquals(SideChatWidthUtils.MAX_PERCENT, SideChatWidthUtils.percentForTouchX(0f, 0, 1000))
        assertEquals(SideChatWidthUtils.MAX_PERCENT, SideChatWidthUtils.percentForTouchX(-50f, 0, 1000))

        // 35% spot from right edge
        assertEquals(35, SideChatWidthUtils.percentForTouchX(650f, 0, 1000))
        assertEquals(50, SideChatWidthUtils.percentForTouchX(500f, 0, 1000))
    }

    @Test
    fun percentForTouchX_handlesOffsetParentAndZeroWidth() {
        // Parent starts at x=200, width=1000 -> right edge at 1200, 35% spot at 850, left edge at 200
        assertEquals(SideChatWidthUtils.MIN_PERCENT, SideChatWidthUtils.percentForTouchX(1200f, 200, 1000))
        assertEquals(35, SideChatWidthUtils.percentForTouchX(850f, 200, 1000))
        assertEquals(SideChatWidthUtils.MAX_PERCENT, SideChatWidthUtils.percentForTouchX(200f, 200, 1000))

        // Zero / negative width falls back to DEFAULT_PERCENT
        assertEquals(SideChatWidthUtils.DEFAULT_PERCENT, SideChatWidthUtils.percentForTouchX(500f, 0, 0))
        assertEquals(SideChatWidthUtils.DEFAULT_PERCENT, SideChatWidthUtils.percentForTouchX(500f, 0, -200))
    }
}
