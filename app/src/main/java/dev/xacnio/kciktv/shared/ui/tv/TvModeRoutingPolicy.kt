package dev.xacnio.kciktv.shared.ui.tv

import java.util.Locale
import kotlin.math.abs

/**
 * Pure-Kotlin routing, channel-navigation, and touch-gesture policy for Android TV mode.
 */
object TvModeRoutingPolicy {
    const val UI_MODE_AUTO = "auto"
    const val UI_MODE_TV = "tv"
    const val UI_MODE_MOBILE = "mobile"

    fun normalizeUiMode(raw: String?): String {
        return when (raw?.trim()?.lowercase(Locale.ROOT)) {
            UI_MODE_TV -> UI_MODE_TV
            UI_MODE_MOBILE -> UI_MODE_MOBILE
            else -> UI_MODE_AUTO
        }
    }

    fun shouldRouteToTv(savedUiMode: String?, isTelevisionDevice: Boolean): Boolean {
        return when (normalizeUiMode(savedUiMode)) {
            UI_MODE_TV -> true
            UI_MODE_MOBILE -> false
            else -> isTelevisionDevice
        }
    }

    fun nextChannelIndex(currentIndex: Int, channelCount: Int, isSearchMode: Boolean): Int? {
        if (isSearchMode || channelCount <= 0) return null
        return if (currentIndex in 0 until channelCount) {
            (currentIndex + 1) % channelCount
        } else {
            0
        }
    }

    fun previousChannelIndex(currentIndex: Int, channelCount: Int, isSearchMode: Boolean): Int? {
        if (isSearchMode || channelCount <= 0) return null
        return if (currentIndex in 1 until channelCount) {
            currentIndex - 1
        } else {
            channelCount - 1
        }
    }

    fun isHorizontalSwipe(
        diffX: Float,
        diffY: Float,
        velocityX: Float,
        velocityY: Float,
        distanceThreshold: Float,
        velocityThreshold: Float
    ): Boolean {
        if (diffX.isNaN() || diffY.isNaN() || velocityX.isNaN() || velocityY.isNaN()) {
            return false
        }
        return abs(diffX) > abs(diffY) &&
            abs(diffX) > distanceThreshold &&
            abs(velocityX) > velocityThreshold
    }
}
