package dev.xacnio.kciktv.mobile.ui.player

enum class RefreshTarget {
    LIVE,
    VOD,
    CLIP
}

data class RefreshDecision(
    val target: RefreshTarget,
    val resumePositionMs: Long,
    val playbackRate: Float
)

object PlaybackRefreshPolicy {

    private const val MIN_PLAYBACK_RATE = 0.25f
    private const val MAX_PLAYBACK_RATE = 3.0f

    fun resolve(
        isVodMode: Boolean,
        isClipMode: Boolean,
        hasCurrentVideo: Boolean,
        hasCurrentClip: Boolean,
        currentPositionMs: Long,
        currentPlaybackRate: Float
    ): RefreshDecision {
        val safeRate = if (currentPlaybackRate.isFinite()) {
            currentPlaybackRate.coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
        } else {
            1.0f
        }

        return when {
            isVodMode && hasCurrentVideo -> RefreshDecision(
                target = RefreshTarget.VOD,
                resumePositionMs = currentPositionMs.coerceAtLeast(0L),
                playbackRate = safeRate
            )
            isClipMode && hasCurrentClip -> RefreshDecision(
                target = RefreshTarget.CLIP,
                resumePositionMs = 0L,
                playbackRate = safeRate
            )
            else -> RefreshDecision(
                target = RefreshTarget.LIVE,
                resumePositionMs = 0L,
                playbackRate = 1.0f
            )
        }
    }
}
