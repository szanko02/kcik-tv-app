package dev.xacnio.kciktv.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCutoutScalingPolicyTest {

    @Test
    fun fromPref_parsesKnownCaseInsensitiveValuesAndFallsBackToFit() {
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.fromPref("FIT"))
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.fromPref("fit"))
        assertEquals(VideoScaleMode.ZOOM, VideoCutoutScalingPolicy.fromPref("ZOOM"))
        assertEquals(VideoScaleMode.ZOOM, VideoCutoutScalingPolicy.fromPref(" zoom "))
        assertEquals(VideoScaleMode.FILL, VideoCutoutScalingPolicy.fromPref("FILL"))
        assertEquals(VideoScaleMode.FILL, VideoCutoutScalingPolicy.fromPref("Fill"))
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.fromPref(null))
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.fromPref(""))
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.fromPref("INVALID"))
        assertEquals(VideoScaleMode.ZOOM, VideoScaleMode.fromPref("ZOOM"))
    }

    @Test
    fun nextCycleMode_cyclesFitZoomFillFit() {
        assertEquals(VideoScaleMode.ZOOM, VideoCutoutScalingPolicy.nextCycleMode(VideoScaleMode.FIT))
        assertEquals(VideoScaleMode.FILL, VideoCutoutScalingPolicy.nextCycleMode(VideoScaleMode.ZOOM))
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.nextCycleMode(VideoScaleMode.FILL))
        assertEquals(VideoScaleMode.ZOOM, VideoScaleMode.nextCycleMode(VideoScaleMode.FIT))
    }

    @Test
    fun shouldDrawUnderCutout_trueOnlyInFullscreenNotTheatreNotPipWithPrefEnabled() {
        assertTrue(
            VideoCutoutScalingPolicy.shouldDrawUnderCutout(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = false,
                drawUnderCutoutPref = true
            )
        )
        assertFalse(
            VideoCutoutScalingPolicy.shouldDrawUnderCutout(
                isFullscreen = false,
                isTheatreMode = false,
                isInPip = false,
                drawUnderCutoutPref = true
            )
        )
        assertFalse(
            VideoCutoutScalingPolicy.shouldDrawUnderCutout(
                isFullscreen = true,
                isTheatreMode = true,
                isInPip = false,
                drawUnderCutoutPref = true
            )
        )
        assertFalse(
            VideoCutoutScalingPolicy.shouldDrawUnderCutout(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = true,
                drawUnderCutoutPref = true
            )
        )
        assertFalse(
            VideoCutoutScalingPolicy.shouldDrawUnderCutout(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = false,
                drawUnderCutoutPref = false
            )
        )
    }

    @Test
    fun resolveEffectiveScaleMode_returnsPreferredOnlyInUnobstructedFullscreen() {
        for (preferred in VideoScaleMode.values()) {
            assertEquals(
                preferred,
                VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                    isFullscreen = true,
                    isTheatreMode = false,
                    isInPip = false,
                    isMiniPlayer = false,
                    isSideChatVisible = false,
                    preferredMode = preferred
                )
            )
        }
    }

    @Test
    fun resolveEffectiveScaleMode_forcesFitWhenAnyBlockerIsActive() {
        val preferred = VideoScaleMode.ZOOM
        assertEquals(
            VideoScaleMode.FIT,
            VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                isFullscreen = false,
                isTheatreMode = false,
                isInPip = false,
                isMiniPlayer = false,
                isSideChatVisible = false,
                preferredMode = preferred
            )
        )
        assertEquals(
            VideoScaleMode.FIT,
            VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                isFullscreen = true,
                isTheatreMode = true,
                isInPip = false,
                isMiniPlayer = false,
                isSideChatVisible = false,
                preferredMode = preferred
            )
        )
        assertEquals(
            VideoScaleMode.FIT,
            VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = true,
                isMiniPlayer = false,
                isSideChatVisible = false,
                preferredMode = preferred
            )
        )
        assertEquals(
            VideoScaleMode.FIT,
            VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = false,
                isMiniPlayer = true,
                isSideChatVisible = false,
                preferredMode = preferred
            )
        )
        assertEquals(
            VideoScaleMode.FIT,
            VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = false,
                isMiniPlayer = false,
                isSideChatVisible = true,
                preferredMode = VideoScaleMode.FILL
            )
        )
    }

    @Test
    fun resolveEffectiveScaleMode_allowsZoomAndFillWhenTransparentSideChatOverlayIsEnabled() {
        for (preferred in VideoScaleMode.values()) {
            assertEquals(
                preferred,
                VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                    isFullscreen = true,
                    isTheatreMode = false,
                    isInPip = false,
                    isMiniPlayer = false,
                    isSideChatVisible = true,
                    preferredMode = preferred,
                    transparentChatOverlayEnabled = true
                )
            )
        }
        // Docked split-screen (transparentChatOverlayEnabled = false) still forces FIT
        assertEquals(
            VideoScaleMode.FIT,
            VideoCutoutScalingPolicy.resolveEffectiveScaleMode(
                isFullscreen = true,
                isTheatreMode = false,
                isInPip = false,
                isMiniPlayer = false,
                isSideChatVisible = true,
                preferredMode = VideoScaleMode.ZOOM,
                transparentChatOverlayEnabled = false
            )
        )
    }

    @Test
    fun resolvePinchScaleMode_respectsThresholdsCurrentModeAndInvalidFactors() {
        // Pinch out -> ZOOM when not already ZOOM
        assertEquals(VideoScaleMode.ZOOM, VideoCutoutScalingPolicy.resolvePinchScaleMode(1.15f, VideoScaleMode.FIT))
        assertEquals(VideoScaleMode.ZOOM, VideoCutoutScalingPolicy.resolvePinchScaleMode(1.40f, VideoScaleMode.FILL))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(1.25f, VideoScaleMode.ZOOM))

        // Pinch in -> FIT when not already FIT
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.resolvePinchScaleMode(0.85f, VideoScaleMode.ZOOM))
        assertEquals(VideoScaleMode.FIT, VideoCutoutScalingPolicy.resolvePinchScaleMode(0.70f, VideoScaleMode.FILL))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(0.80f, VideoScaleMode.FIT))

        // Dead zone
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(1.00f, VideoScaleMode.FIT))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(1.14f, VideoScaleMode.FIT))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(0.86f, VideoScaleMode.ZOOM))

        // Invalid scale factors
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(Float.NaN, VideoScaleMode.FIT))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(Float.POSITIVE_INFINITY, VideoScaleMode.FIT))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(0f, VideoScaleMode.ZOOM))
        assertNull(VideoCutoutScalingPolicy.resolvePinchScaleMode(-1f, VideoScaleMode.ZOOM))
    }
}
