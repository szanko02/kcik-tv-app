package dev.xacnio.kciktv.shared.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvModeRoutingPolicyTest {

    @Test
    fun normalizeUiMode_handlesCanonicalCaseWhitespaceAndFallback() {
        assertEquals(TvModeRoutingPolicy.UI_MODE_TV, TvModeRoutingPolicy.normalizeUiMode("tv"))
        assertEquals(TvModeRoutingPolicy.UI_MODE_TV, TvModeRoutingPolicy.normalizeUiMode("  TV \n"))
        assertEquals(TvModeRoutingPolicy.UI_MODE_MOBILE, TvModeRoutingPolicy.normalizeUiMode("mobile"))
        assertEquals(TvModeRoutingPolicy.UI_MODE_MOBILE, TvModeRoutingPolicy.normalizeUiMode(" Mobile "))
        assertEquals(TvModeRoutingPolicy.UI_MODE_AUTO, TvModeRoutingPolicy.normalizeUiMode("auto"))
        assertEquals(TvModeRoutingPolicy.UI_MODE_AUTO, TvModeRoutingPolicy.normalizeUiMode(" AUTO "))
        assertEquals(TvModeRoutingPolicy.UI_MODE_AUTO, TvModeRoutingPolicy.normalizeUiMode(null))
        assertEquals(TvModeRoutingPolicy.UI_MODE_AUTO, TvModeRoutingPolicy.normalizeUiMode(""))
        assertEquals(TvModeRoutingPolicy.UI_MODE_AUTO, TvModeRoutingPolicy.normalizeUiMode("   "))
        assertEquals(TvModeRoutingPolicy.UI_MODE_AUTO, TvModeRoutingPolicy.normalizeUiMode("corrupt_mode"))
    }

    @Test
    fun shouldRouteToTv_explicitTvAlwaysRoutesToTv() {
        assertTrue(TvModeRoutingPolicy.shouldRouteToTv("tv", isTelevisionDevice = false))
        assertTrue(TvModeRoutingPolicy.shouldRouteToTv("TV", isTelevisionDevice = true))
    }

    @Test
    fun shouldRouteToTv_explicitMobileNeverTrapsPhoneOrTvDeviceInTvMode() {
        assertFalse(TvModeRoutingPolicy.shouldRouteToTv("mobile", isTelevisionDevice = false))
        assertFalse(TvModeRoutingPolicy.shouldRouteToTv("mobile", isTelevisionDevice = true))
        assertFalse(TvModeRoutingPolicy.shouldRouteToTv(" MOBILE ", isTelevisionDevice = true))
    }

    @Test
    fun shouldRouteToTv_autoNullOrUnknownUsesHardwareDetection() {
        assertFalse(TvModeRoutingPolicy.shouldRouteToTv("auto", isTelevisionDevice = false))
        assertTrue(TvModeRoutingPolicy.shouldRouteToTv("auto", isTelevisionDevice = true))
        assertFalse(TvModeRoutingPolicy.shouldRouteToTv(null, isTelevisionDevice = false))
        assertTrue(TvModeRoutingPolicy.shouldRouteToTv(null, isTelevisionDevice = true))
        assertFalse(TvModeRoutingPolicy.shouldRouteToTv("unknown", isTelevisionDevice = false))
        assertTrue(TvModeRoutingPolicy.shouldRouteToTv("unknown", isTelevisionDevice = true))
    }

    @Test
    fun nextAndPreviousChannelIndex_returnNullOnEmptyOrSearchMode() {
        // Regression proof against ArithmeticException: divide by zero on empty channel list
        assertNull(TvModeRoutingPolicy.nextChannelIndex(currentIndex = 0, channelCount = 0, isSearchMode = false))
        assertNull(TvModeRoutingPolicy.previousChannelIndex(currentIndex = 0, channelCount = 0, isSearchMode = false))
        assertNull(TvModeRoutingPolicy.nextChannelIndex(currentIndex = 0, channelCount = -3, isSearchMode = false))
        assertNull(TvModeRoutingPolicy.previousChannelIndex(currentIndex = 0, channelCount = -3, isSearchMode = false))

        // Search mode blocks channel cycling even when channels exist
        assertNull(TvModeRoutingPolicy.nextChannelIndex(currentIndex = 0, channelCount = 5, isSearchMode = true))
        assertNull(TvModeRoutingPolicy.previousChannelIndex(currentIndex = 0, channelCount = 5, isSearchMode = true))
    }

    @Test
    fun nextAndPreviousChannelIndex_wrapCleanlyAndHandleBounds() {
        // Single item list stays at 0
        assertEquals(0, TvModeRoutingPolicy.nextChannelIndex(0, channelCount = 1, isSearchMode = false))
        assertEquals(0, TvModeRoutingPolicy.previousChannelIndex(0, channelCount = 1, isSearchMode = false))

        // Forward progression and wrap at end
        assertEquals(1, TvModeRoutingPolicy.nextChannelIndex(0, channelCount = 3, isSearchMode = false))
        assertEquals(2, TvModeRoutingPolicy.nextChannelIndex(1, channelCount = 3, isSearchMode = false))
        assertEquals(0, TvModeRoutingPolicy.nextChannelIndex(2, channelCount = 3, isSearchMode = false))

        // Backward progression and wrap at start
        assertEquals(2, TvModeRoutingPolicy.previousChannelIndex(0, channelCount = 3, isSearchMode = false))
        assertEquals(0, TvModeRoutingPolicy.previousChannelIndex(1, channelCount = 3, isSearchMode = false))
        assertEquals(1, TvModeRoutingPolicy.previousChannelIndex(2, channelCount = 3, isSearchMode = false))

        // Out-of-bounds current index recovers safely into valid range
        assertEquals(0, TvModeRoutingPolicy.nextChannelIndex(-1, channelCount = 3, isSearchMode = false))
        assertEquals(0, TvModeRoutingPolicy.nextChannelIndex(9, channelCount = 3, isSearchMode = false))
        assertEquals(2, TvModeRoutingPolicy.previousChannelIndex(-1, channelCount = 3, isSearchMode = false))
        assertEquals(2, TvModeRoutingPolicy.previousChannelIndex(9, channelCount = 3, isSearchMode = false))
    }

    @Test
    fun isHorizontalSwipe_usesVelocityXInsteadOfVelocityY() {
        val distThreshold = 100f
        val velThreshold = 100f

        // Pure horizontal right/left swipes have velocityY == 0 and MUST succeed
        assertTrue(
            TvModeRoutingPolicy.isHorizontalSwipe(
                diffX = 180f,
                diffY = 4f,
                velocityX = 450f,
                velocityY = 0f,
                distanceThreshold = distThreshold,
                velocityThreshold = velThreshold
            )
        )
        assertTrue(
            TvModeRoutingPolicy.isHorizontalSwipe(
                diffX = -180f,
                diffY = -8f,
                velocityX = -450f,
                velocityY = 0f,
                distanceThreshold = distThreshold,
                velocityThreshold = velThreshold
            )
        )

        // Regression test: high velocityY with slow velocityX must NOT count as horizontal swipe
        assertFalse(
            TvModeRoutingPolicy.isHorizontalSwipe(
                diffX = 180f,
                diffY = 10f,
                velocityX = 30f,
                velocityY = 900f,
                distanceThreshold = distThreshold,
                velocityThreshold = velThreshold
            )
        )

        // Vertical dominant movement must return false
        assertFalse(
            TvModeRoutingPolicy.isHorizontalSwipe(
                diffX = 150f,
                diffY = 200f,
                velocityX = 500f,
                velocityY = 500f,
                distanceThreshold = distThreshold,
                velocityThreshold = velThreshold
            )
        )

        // Below distance threshold or NaN inputs must return false
        assertFalse(
            TvModeRoutingPolicy.isHorizontalSwipe(
                diffX = 80f,
                diffY = 5f,
                velocityX = 500f,
                velocityY = 0f,
                distanceThreshold = distThreshold,
                velocityThreshold = velThreshold
            )
        )
        assertFalse(
            TvModeRoutingPolicy.isHorizontalSwipe(
                diffX = Float.NaN,
                diffY = 0f,
                velocityX = 500f,
                velocityY = 0f,
                distanceThreshold = distThreshold,
                velocityThreshold = velThreshold
            )
        )
    }
}
