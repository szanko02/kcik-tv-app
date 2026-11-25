package dev.xacnio.kciktv.mobile.ui.nav

/**
 * Pure-Kotlin visibility policy for the mobile bottom navigation bar.
 * Kept free of Android framework imports so it can be verified directly on the JVM.
 */
object BottomNavVisibilityPolicy {

    const val DEFAULT_HIDE_THRESHOLD_PX = 8
    const val DEFAULT_SHOW_THRESHOLD_PX = 8
    const val DEFAULT_TOP_ALWAYS_SHOW_PX = 12

    private val BOTTOM_NAV_SCREENS = setOf(
        "HOME",
        "BROWSE",
        "FOLLOWING"
    )

    fun shouldShowBottomNav(screenName: String): Boolean = screenName in BOTTOM_NAV_SCREENS

    fun initialScrollY(
        screenName: String,
        followingScrollY: Int,
        homeScrollY: Int
    ): Int = when (screenName) {
        "FOLLOWING" -> followingScrollY.coerceAtLeast(0)
        "HOME" -> homeScrollY.coerceAtLeast(0)
        else -> 0
    }

    fun resolveScrollDeltaVisibility(
        currentlyVisible: Boolean,
        currentScrollY: Int,
        lastScrollY: Int,
        hideThresholdPx: Int = DEFAULT_HIDE_THRESHOLD_PX,
        showThresholdPx: Int = DEFAULT_SHOW_THRESHOLD_PX,
        topAlwaysShowPx: Int = DEFAULT_TOP_ALWAYS_SHOW_PX
    ): Boolean {
        if (currentScrollY <= topAlwaysShowPx) return true
        val deltaY = currentScrollY - lastScrollY
        return when {
            deltaY > hideThresholdPx -> false
            deltaY < -showThresholdPx -> true
            else -> currentlyVisible
        }
    }
}
