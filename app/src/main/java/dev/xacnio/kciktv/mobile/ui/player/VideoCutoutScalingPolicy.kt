package dev.xacnio.kciktv.mobile.ui.player

enum class VideoScaleMode(val prefValue: String) {
    FIT("FIT"),
    ZOOM("ZOOM"),
    FILL("FILL");

    companion object {
        fun fromPref(raw: String?): VideoScaleMode = VideoCutoutScalingPolicy.fromPref(raw)

        fun nextCycleMode(current: VideoScaleMode): VideoScaleMode =
            VideoCutoutScalingPolicy.nextCycleMode(current)
    }
}

object VideoCutoutScalingPolicy {
    const val PREF_FIT = "FIT"
    const val PREF_ZOOM = "ZOOM"
    const val PREF_FILL = "FILL"
    const val PINCH_ZOOM_IN_THRESHOLD = 1.15f
    const val PINCH_ZOOM_OUT_THRESHOLD = 0.85f

    fun fromPref(raw: String?): VideoScaleMode {
        return when (raw?.trim()?.uppercase()) {
            PREF_ZOOM -> VideoScaleMode.ZOOM
            PREF_FILL -> VideoScaleMode.FILL
            else -> VideoScaleMode.FIT
        }
    }

    fun nextCycleMode(current: VideoScaleMode): VideoScaleMode {
        return when (current) {
            VideoScaleMode.FIT -> VideoScaleMode.ZOOM
            VideoScaleMode.ZOOM -> VideoScaleMode.FILL
            VideoScaleMode.FILL -> VideoScaleMode.FIT
        }
    }

    fun shouldDrawUnderCutout(
        isFullscreen: Boolean,
        isTheatreMode: Boolean,
        isInPip: Boolean,
        drawUnderCutoutPref: Boolean
    ): Boolean {
        return isFullscreen && !isTheatreMode && !isInPip && drawUnderCutoutPref
    }

    fun resolveEffectiveScaleMode(
        isFullscreen: Boolean,
        isTheatreMode: Boolean,
        isInPip: Boolean,
        isMiniPlayer: Boolean,
        isSideChatVisible: Boolean,
        preferredMode: VideoScaleMode,
        transparentChatOverlayEnabled: Boolean = false
    ): VideoScaleMode {
        val isDockedSideChat = ChatOverlayStyleUtils.isDockedSideChat(
            isSideChatVisible = isSideChatVisible,
            transparentChatOverlayEnabled = transparentChatOverlayEnabled
        )
        return if (isFullscreen && !isTheatreMode && !isInPip && !isMiniPlayer && !isDockedSideChat) {
            preferredMode
        } else {
            VideoScaleMode.FIT
        }
    }

    fun resolvePinchScaleMode(
        cumulativeScaleFactor: Float,
        currentMode: VideoScaleMode
    ): VideoScaleMode? {
        if (!cumulativeScaleFactor.isFinite() || cumulativeScaleFactor <= 0f) return null
        return when {
            cumulativeScaleFactor >= PINCH_ZOOM_IN_THRESHOLD && currentMode != VideoScaleMode.ZOOM -> VideoScaleMode.ZOOM
            cumulativeScaleFactor <= PINCH_ZOOM_OUT_THRESHOLD && currentMode != VideoScaleMode.FIT -> VideoScaleMode.FIT
            else -> null
        }
    }
}
