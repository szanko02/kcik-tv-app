package dev.xacnio.kciktv.mobile.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavVisibilityPolicyTest {

    @Test
    fun shouldShowBottomNav_topLevelTabs_returnsTrue() {
        assertTrue(BottomNavVisibilityPolicy.shouldShowBottomNav("HOME"))
        assertTrue(BottomNavVisibilityPolicy.shouldShowBottomNav("BROWSE"))
        assertTrue(BottomNavVisibilityPolicy.shouldShowBottomNav("FOLLOWING"))
    }

    @Test
    fun shouldShowBottomNav_fullscreenOverlays_returnsFalse() {
        val hiddenScreens = listOf(
            "CHANNEL_PROFILE",
            "CATEGORY_DETAILS",
            "COLLECTIBLES",
            "DROPS",
            "STREAM_FEED",
            "CLIP_FEED",
            "UNKNOWN"
        )
        hiddenScreens.forEach { screen ->
            assertFalse("Expected $screen to hide bottom nav", BottomNavVisibilityPolicy.shouldShowBottomNav(screen))
        }
    }

    @Test
    fun initialScrollY_syncsFollowingAndHomeAndClampsNegative() {
        assertEquals(400, BottomNavVisibilityPolicy.initialScrollY("FOLLOWING", followingScrollY = 400, homeScrollY = 120))
        assertEquals(120, BottomNavVisibilityPolicy.initialScrollY("HOME", followingScrollY = 400, homeScrollY = 120))
        assertEquals(0, BottomNavVisibilityPolicy.initialScrollY("BROWSE", followingScrollY = 400, homeScrollY = 120))
        assertEquals(0, BottomNavVisibilityPolicy.initialScrollY("FOLLOWING", followingScrollY = -10, homeScrollY = 50))
    }

    @Test
    fun resolveScrollDeltaVisibility_returningToScrolledFollowingTabWithJitter_keepsNavVisible() {
        val syncedLastScrollY = BottomNavVisibilityPolicy.initialScrollY(
            screenName = "FOLLOWING",
            followingScrollY = 400,
            homeScrollY = 0
        )

        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = true,
                currentScrollY = 402,
                lastScrollY = syncedLastScrollY
            )
        )
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = true,
                currentScrollY = 398,
                lastScrollY = syncedLastScrollY
            )
        )
    }

    @Test
    fun resolveScrollDeltaVisibility_scrollDownAndUpPastThreshold_togglesVisibility() {
        assertFalse(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = true,
                currentScrollY = 420,
                lastScrollY = 400
            )
        )
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = false,
                currentScrollY = 380,
                lastScrollY = 400
            )
        )
    }

    @Test
    fun resolveScrollDeltaVisibility_exactThresholdBoundaries_requireStrictExceed() {
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = true,
                currentScrollY = 408,
                lastScrollY = 400
            )
        )
        assertFalse(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = true,
                currentScrollY = 409,
                lastScrollY = 400
            )
        )
        assertFalse(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = false,
                currentScrollY = 392,
                lastScrollY = 400
            )
        )
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = false,
                currentScrollY = 391,
                lastScrollY = 400
            )
        )
    }

    @Test
    fun resolveScrollDeltaVisibility_nearTop_alwaysShows() {
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = false,
                currentScrollY = 12,
                lastScrollY = 0
            )
        )
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = false,
                currentScrollY = 0,
                lastScrollY = 100
            )
        )
        assertTrue(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = false,
                currentScrollY = -4,
                lastScrollY = 10
            )
        )
        assertFalse(
            BottomNavVisibilityPolicy.resolveScrollDeltaVisibility(
                currentlyVisible = true,
                currentScrollY = 13,
                lastScrollY = 0
            )
        )
    }
}
