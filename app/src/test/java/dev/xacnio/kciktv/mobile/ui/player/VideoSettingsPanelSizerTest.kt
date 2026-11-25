package dev.xacnio.kciktv.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSettingsPanelSizerTest {

    @Test
    fun portraitPhone_usesFullWidthFlushBottomWithSafeBottomPadding() {
        val density = 3f
        val desired = VideoSettingsPanelSizer.computeRootDesiredHeightPx(5, density)
        val metrics = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = 1080, // 360dp
            containerHeightPx = 2400, // 800dp
            density = density,
            desiredContentHeightPx = desired,
            safeTopInsetPx = 96,
            safeBottomInsetPx = 144
        )

        assertFalse(metrics.isFloatingCard)
        assertEquals(VideoSettingsPanelSizer.MATCH_PARENT, metrics.widthPx)
        assertEquals(0, metrics.horizontalMarginPx)
        assertEquals(0, metrics.bottomMarginPx)
        assertEquals(144, metrics.bottomPaddingPx)
        assertEquals(desired + 144, metrics.heightPx)
    }

    @Test
    fun narrowLandscapeUnder480dp_staysFullWidthSheet() {
        val metrics = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = 460, // 460dp < 480dp breakpoint
            containerHeightPx = 320,
            density = 1f,
            desiredContentHeightPx = 220
        )

        assertFalse(metrics.isFloatingCard)
        assertEquals(VideoSettingsPanelSizer.MATCH_PARENT, metrics.widthPx)
        assertEquals(0, metrics.horizontalMarginPx)
        assertEquals(0, metrics.bottomMarginPx)
        assertEquals(220, metrics.heightPx)
    }

    @Test
    fun wideLandscape_usesCenteredMax420dpFloatingCard() {
        val density = 2f
        val metrics = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = 1920, // 960dp
            containerHeightPx = 1080, // 540dp
            density = density,
            desiredContentHeightPx = VideoSettingsPanelSizer.computeRootDesiredHeightPx(5, density),
            safeTopInsetPx = 0,
            safeBottomInsetPx = 24
        )

        assertTrue(metrics.isFloatingCard)
        assertEquals(420 * 2, metrics.widthPx)
        assertEquals(16 * 2, metrics.horizontalMarginPx)
        assertEquals(12 * 2 + 24, metrics.bottomMarginPx)
        assertEquals(0, metrics.bottomPaddingPx)
        assertEquals(VideoSettingsPanelSizer.computeRootDesiredHeightPx(5, density), metrics.heightPx)
    }

    @Test
    fun shortLandscape360dp_clampsHeightTo80Percent() {
        val density = 3f
        val containerHeightPx = 360 * 3 // 1080px (360dp)
        val containerWidthPx = 800 * 3 // 2400px (800dp)
        val desiredRoot = VideoSettingsPanelSizer.computeRootDesiredHeightPx(6, density) // 425dp = 1275px

        val metrics = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            density = density,
            desiredContentHeightPx = desiredRoot
        )

        val expectedMaxHeight = (containerHeightPx * VideoSettingsPanelSizer.MAX_HEIGHT_FRACTION).toInt() // 864px (288dp)
        assertTrue(metrics.isFloatingCard)
        assertEquals(420 * 3, metrics.widthPx)
        assertEquals(expectedMaxHeight, metrics.heightPx)
        assertTrue(metrics.heightPx + metrics.bottomMarginPx <= containerHeightPx)
    }

    @Test
    fun subPageVsRootHeight_capsAndClampsCorrectly() {
        val density = 1f
        val root4 = VideoSettingsPanelSizer.computeRootDesiredHeightPx(4, density)
        val root6 = VideoSettingsPanelSizer.computeRootDesiredHeightPx(6, density)
        assertEquals(89 + 4 * 56, root4)
        assertEquals(89 + 6 * 56, root6)

        // Sub-page with 3 items stays below the 420dp cap
        val sub3 = VideoSettingsPanelSizer.computeSubPageDesiredHeightPx(3, density)
        assertEquals(89 + 3 * 52, sub3)

        // Sub-page with 15 items caps desired height at 420dp
        val sub15 = VideoSettingsPanelSizer.computeSubPageDesiredHeightPx(15, density)
        assertEquals(VideoSettingsPanelSizer.SUB_PAGE_MAX_DESIRED_HEIGHT_DP, sub15)

        // On a tall 800dp portrait screen, 420dp sub-page is not clamped further
        val tallMetrics = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = 400,
            containerHeightPx = 800,
            density = density,
            desiredContentHeightPx = sub15
        )
        assertEquals(420, tallMetrics.heightPx)

        // On a short 360dp landscape screen, 420dp sub-page is clamped to 288dp (80%)
        val shortMetrics = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = 800,
            containerHeightPx = 360,
            density = density,
            desiredContentHeightPx = sub15
        )
        assertEquals(288, shortMetrics.heightPx)
    }

    @Test
    fun zeroAndNegativeDimensions_areGuardedSafely() {
        val zeroWidth = VideoSettingsPanelSizer.computeLayout(0, 800, 2f, 400)
        assertFalse(zeroWidth.isFloatingCard)
        assertEquals(VideoSettingsPanelSizer.MATCH_PARENT, zeroWidth.widthPx)
        assertEquals(0, zeroWidth.heightPx)

        val negativeHeight = VideoSettingsPanelSizer.computeLayout(800, -100, 2f, 400)
        assertFalse(negativeHeight.isFloatingCard)
        assertEquals(0, negativeHeight.heightPx)

        val negativeDesiredAndInsets = VideoSettingsPanelSizer.computeLayout(
            containerWidthPx = 800,
            containerHeightPx = 360,
            density = -1f,
            desiredContentHeightPx = -50,
            safeTopInsetPx = -20,
            safeBottomInsetPx = -10
        )
        assertEquals(0, negativeDesiredAndInsets.heightPx)
        assertEquals(0, negativeDesiredAndInsets.bottomPaddingPx)
        assertTrue(negativeDesiredAndInsets.bottomMarginPx >= 0)

        assertEquals(
            VideoSettingsPanelSizer.PANEL_OVERHEAD_DP,
            VideoSettingsPanelSizer.computeRootDesiredHeightPx(-3, Float.NaN)
        )
        assertEquals(
            VideoSettingsPanelSizer.PANEL_OVERHEAD_DP,
            VideoSettingsPanelSizer.computeSubPageDesiredHeightPx(-5, 0f)
        )
    }
}
