package dev.xacnio.kciktv.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRefreshPolicyTest {

    @Test
    fun resolve_liveMode_returnsLiveWithDefaults() {
        val decision = PlaybackRefreshPolicy.resolve(
            isVodMode = false,
            isClipMode = false,
            hasCurrentVideo = false,
            hasCurrentClip = false,
            currentPositionMs = 42_000L,
            currentPlaybackRate = 1.5f
        )

        assertEquals(RefreshTarget.LIVE, decision.target)
        assertEquals(0L, decision.resumePositionMs)
        assertEquals(1.0f, decision.playbackRate, 0.0001f)
    }

    @Test
    fun resolve_vodWithVideo_preservesPositionAndPlaybackRate() {
        val decision = PlaybackRefreshPolicy.resolve(
            isVodMode = true,
            isClipMode = false,
            hasCurrentVideo = true,
            hasCurrentClip = false,
            currentPositionMs = 123_456L,
            currentPlaybackRate = 1.5f
        )

        assertEquals(RefreshTarget.VOD, decision.target)
        assertEquals(123_456L, decision.resumePositionMs)
        assertEquals(1.5f, decision.playbackRate, 0.0001f)
    }

    @Test
    fun resolve_vodWithNegativePosition_coercesPositionToZero() {
        val decision = PlaybackRefreshPolicy.resolve(
            isVodMode = true,
            isClipMode = false,
            hasCurrentVideo = true,
            hasCurrentClip = false,
            currentPositionMs = -5_000L,
            currentPlaybackRate = 1.25f
        )

        assertEquals(RefreshTarget.VOD, decision.target)
        assertEquals(0L, decision.resumePositionMs)
        assertEquals(1.25f, decision.playbackRate, 0.0001f)
    }

    @Test
    fun resolve_vodWithMissingVideo_fallsBackToLive() {
        val decision = PlaybackRefreshPolicy.resolve(
            isVodMode = true,
            isClipMode = false,
            hasCurrentVideo = false,
            hasCurrentClip = false,
            currentPositionMs = 60_000L,
            currentPlaybackRate = 1.5f
        )

        assertEquals(RefreshTarget.LIVE, decision.target)
        assertEquals(0L, decision.resumePositionMs)
        assertEquals(1.0f, decision.playbackRate, 0.0001f)
    }

    @Test
    fun resolve_clipWithClip_returnsClipAndCoercesRate() {
        val decision = PlaybackRefreshPolicy.resolve(
            isVodMode = false,
            isClipMode = true,
            hasCurrentVideo = false,
            hasCurrentClip = true,
            currentPositionMs = 9_500L,
            currentPlaybackRate = 1.5f
        )

        assertEquals(RefreshTarget.CLIP, decision.target)
        assertEquals(0L, decision.resumePositionMs)
        assertEquals(1.5f, decision.playbackRate, 0.0001f)

        val clampedHigh = PlaybackRefreshPolicy.resolve(
            isVodMode = false,
            isClipMode = true,
            hasCurrentVideo = false,
            hasCurrentClip = true,
            currentPositionMs = 0L,
            currentPlaybackRate = 5.0f
        )
        assertEquals(3.0f, clampedHigh.playbackRate, 0.0001f)

        val clampedLow = PlaybackRefreshPolicy.resolve(
            isVodMode = false,
            isClipMode = true,
            hasCurrentVideo = false,
            hasCurrentClip = true,
            currentPositionMs = 0L,
            currentPlaybackRate = 0.1f
        )
        assertEquals(0.25f, clampedLow.playbackRate, 0.0001f)
    }

    @Test
    fun resolve_clipWithMissingClip_fallsBackToLive() {
        val decision = PlaybackRefreshPolicy.resolve(
            isVodMode = false,
            isClipMode = true,
            hasCurrentVideo = false,
            hasCurrentClip = false,
            currentPositionMs = 5_000L,
            currentPlaybackRate = 2.0f
        )

        assertEquals(RefreshTarget.LIVE, decision.target)
        assertEquals(0L, decision.resumePositionMs)
        assertEquals(1.0f, decision.playbackRate, 0.0001f)
    }
}
