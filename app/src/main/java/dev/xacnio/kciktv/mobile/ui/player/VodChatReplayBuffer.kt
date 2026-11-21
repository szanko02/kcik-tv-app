/**
 * File: VodChatReplayBuffer.kt
 *
 * Description: Pure-Kotlin (Android-free) state machine for Kick VOD/Clip chat replay.
 * Modeled on the szanko02/kick-vod-chat-fix replay template: chat history is requested in
 * fixed 5 second slices (Kick's replay page contract), buffered a bounded distance ahead of
 * the playhead, and emitted strictly by the message's original timestamp.
 *
 * Threading: every method must be called from the same (Main) thread. Async fetches are
 * correlated through a generation token so stale responses after seek/stop are discarded.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.player

import dev.xacnio.kciktv.shared.data.model.ChatMessage
import java.util.ArrayDeque
import java.util.TreeMap

class VodChatReplayBuffer(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val randomUnit: () -> Double = { Math.random() }
) {

    companion object {
        /** Kick replay history is served in 5 second slices. */
        const val PAGE_STEP_MS = 5_000L
        /** Start fetching when less than this much chat is buffered ahead of playback. */
        const val AHEAD_BUFFER_MS = 15_000L
        /** Chain page fetches until this much chat is buffered ahead of playback. */
        const val TARGET_BUFFER_MS = 20_000L
        /** On seek, start fetching this much before the target so context is visible. */
        const val SEEK_BACKFILL_MS = 2_500L
        /** Forward progress jumps larger than this are treated as seeks. */
        const val SEEK_FORWARD_THRESHOLD_MS = 2_000L
        /** Display clock granularity. */
        const val DISPLAY_CADENCE_MS = 250L
        /** Sliding window of retained pages (memory bound for long VODs). */
        const val MAX_PAGES = 24
        const val BACKOFF_BASE_MS = 3_000L
        const val BACKOFF_MAX_MS = 60_000L
        const val BACKOFF_JITTER_MS = 500L
    }

    data class FetchRequest(val generation: Long, val channelId: Long, val pageStartMs: Long)

    data class TickResult(
        val didSeekReset: Boolean,
        val messagesToEmit: List<ChatMessage>,
        val nextFetch: FetchRequest?,
        val retryDelayMs: Long?
    )

    private data class PageEntry(val pageStartMs: Long, val messages: List<ChatMessage>)

    private val emptyTick = TickResult(false, emptyList(), null, null)

    // --- State (Main thread only) ---
    var isActive: Boolean = false
        private set
    private var isValid: Boolean = false
    private var channelId: Long = 0L
    private var videoStartMs: Long = 0L

    var generation: Long = 0L
        private set
    private var inFlightGeneration: Long? = null

    var nextPageStartMs: Long = 0L
        private set
    var bufferedUntilMs: Long = 0L
        private set
    var displayMs: Long = 0L
        private set
    private var lastProgressMs: Long? = null

    private var isSliding = false
    private var wasSliding = false
    private var isPlayingOrBufferingLastKnown = false

    var retryCount: Int = 0
        private set
    private var retryAfterMs: Long = 0L

    /** Messages created before this absolute time belong to pruned pages and are dropped. */
    private var prunedBeforeMs: Long = Long.MIN_VALUE

    private val pages = ArrayDeque<PageEntry>()
    private val fetchedIds = HashSet<String>()
    private val shownIds = HashSet<String>()
    private val pendingByTimestamp = TreeMap<Long, MutableList<ChatMessage>>()

    // --- Introspection helpers (used by tests / logging) ---
    val pageCount: Int get() = pages.size
    val pendingCount: Int get() = pendingByTimestamp.values.sumOf { it.size }
    val fetchedIdCount: Int get() = fetchedIds.size
    val isFetchInFlight: Boolean get() = inFlightGeneration != null

    fun start(channelId: Long, videoStartMs: Long, initialOffsetMs: Long = 0L) {
        clearBuffers()
        generation++
        inFlightGeneration = null
        retryCount = 0
        retryAfterMs = 0L
        isSliding = false
        wasSliding = false
        isPlayingOrBufferingLastKnown = false

        this.channelId = channelId
        this.videoStartMs = videoStartMs
        isValid = channelId > 0L && videoStartMs > 0L
        isActive = true

        nextPageStartMs = videoStartMs + (initialOffsetMs - SEEK_BACKFILL_MS).coerceAtLeast(0L)
        bufferedUntilMs = nextPageStartMs
        displayMs = initialOffsetMs.coerceAtLeast(0L)
        lastProgressMs = if (initialOffsetMs > 0) initialOffsetMs else null
    }

    fun stop() {
        isActive = false
        generation++
        clearBuffers()
        inFlightGeneration = null
        isSliding = false
        wasSliding = false
    }

    fun setSliding(sliding: Boolean) {
        isSliding = sliding
        if (sliding) wasSliding = true
    }

    fun onExplicitSeek(targetPositionMs: Long) {
        if (!isActive) return
        seekReset(targetPositionMs)
    }

    fun onPlaybackTick(progressMs: Long, isPlayingOrBuffering: Boolean): TickResult {
        if (!isActive || !isValid || progressMs < 0L) return emptyTick
        isPlayingOrBufferingLastKnown = isPlayingOrBuffering

        val last = lastProgressMs
        val seeking = last != null &&
            (progressMs < last || progressMs - last > SEEK_FORWARD_THRESHOLD_MS)

        if (isSliding) {
            wasSliding = true
            lastProgressMs = progressMs
            return emptyTick
        }

        if (wasSliding || seeking) {
            seekReset(progressMs)
            val (fetch, retry) = maybeStartFetch(progressMs, isPlayingOrBuffering)
            return TickResult(true, emptyList(), fetch, retry)
        }

        if (last == null || progressMs - displayMs >= DISPLAY_CADENCE_MS) {
            displayMs = progressMs
        }
        lastProgressMs = progressMs

        val toEmit = drainUpTo(videoStartMs + displayMs)
        val (fetch, retry) = maybeStartFetch(progressMs, isPlayingOrBuffering)
        return TickResult(false, toEmit, fetch, retry)
    }

    fun onFetchSuccess(request: FetchRequest, messages: List<ChatMessage>): FetchRequest? {
        if (!isActive || request.generation != generation) return null
        inFlightGeneration = null
        retryCount = 0
        retryAfterMs = 0L

        val newMessages = ArrayList<ChatMessage>()
        for (msg in messages) {
            if (msg.createdAt < prunedBeforeMs) continue
            if (!fetchedIds.add(msg.id)) continue
            newMessages.add(msg) // original createdAt preserved
            pendingByTimestamp.getOrPut(msg.createdAt) { ArrayList() }.add(msg)
        }
        pages.addLast(PageEntry(request.pageStartMs, newMessages))

        // Always advance by exactly one page, even on empty pages (never stall).
        nextPageStartMs = request.pageStartMs + PAGE_STEP_MS
        bufferedUntilMs = maxOf(bufferedUntilMs, nextPageStartMs)

        pruneIfNeeded()

        val ahead = bufferedUntilMs - (videoStartMs + (lastProgressMs ?: 0L))
        if (isActive && isPlayingOrBufferingLastKnown && !isSliding && ahead < TARGET_BUFFER_MS) {
            inFlightGeneration = generation
            return FetchRequest(generation, channelId, nextPageStartMs)
        }
        return null
    }

    fun onFetchFailure(request: FetchRequest): Long? {
        if (!isActive || request.generation != generation) return null
        inFlightGeneration = null
        val delay = minOf(BACKOFF_MAX_MS, BACKOFF_BASE_MS * (1L shl retryCount.coerceAtMost(10))) +
            (randomUnit().coerceIn(0.0, 0.9999) * BACKOFF_JITTER_MS).toLong()
        retryCount++
        retryAfterMs = nowMs() + delay
        return delay
    }

    // --- Internals ---

    private fun seekReset(targetPositionMs: Long) {
        val target = targetPositionMs.coerceAtLeast(0L)
        generation++
        inFlightGeneration = null
        retryCount = 0
        retryAfterMs = 0L
        clearBuffers()
        nextPageStartMs = videoStartMs + (target - SEEK_BACKFILL_MS).coerceAtLeast(0L)
        bufferedUntilMs = nextPageStartMs
        displayMs = target
        lastProgressMs = target
        wasSliding = false
    }

    private fun maybeStartFetch(progressMs: Long, isPlayingOrBuffering: Boolean): Pair<FetchRequest?, Long?> {
        val aheadMs = bufferedUntilMs - (videoStartMs + progressMs)
        val needsData = pages.isEmpty() || aheadMs < AHEAD_BUFFER_MS
        if (!isPlayingOrBuffering || inFlightGeneration != null || !needsData) return null to null
        val now = nowMs()
        if (now < retryAfterMs) return null to (retryAfterMs - now)
        inFlightGeneration = generation
        return FetchRequest(generation, channelId, nextPageStartMs) to null
    }

    private fun drainUpTo(absoluteMs: Long): List<ChatMessage> {
        val head = pendingByTimestamp.headMap(absoluteMs, true)
        if (head.isEmpty()) return emptyList()
        val out = ArrayList<ChatMessage>()
        for (bucket in head.values) {
            for (msg in bucket) {
                if (shownIds.add(msg.id)) out.add(msg)
            }
        }
        head.clear() // drained entries leave memory
        out.sortBy { it.createdAt } // stable: keeps API order for equal timestamps
        return out
    }

    private fun pruneIfNeeded() {
        while (pages.size > MAX_PAGES) {
            val removed = pages.removeFirst()
            for (msg in removed.messages) {
                fetchedIds.remove(msg.id)
                shownIds.remove(msg.id)
            }
        }
        val oldest = pages.peekFirst() ?: return
        if (oldest.pageStartMs > prunedBeforeMs && pages.size >= MAX_PAGES) {
            prunedBeforeMs = oldest.pageStartMs
            pendingByTimestamp.headMap(oldest.pageStartMs, false).clear()
        }
    }

    private fun clearBuffers() {
        pages.clear()
        fetchedIds.clear()
        shownIds.clear()
        pendingByTimestamp.clear()
        prunedBeforeMs = Long.MIN_VALUE
    }
}
