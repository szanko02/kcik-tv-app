package dev.xacnio.kciktv.mobile.ui.player

import dev.xacnio.kciktv.mobile.ui.player.VodChatReplayBuffer.Companion.AHEAD_BUFFER_MS
import dev.xacnio.kciktv.mobile.ui.player.VodChatReplayBuffer.Companion.MAX_PAGES
import dev.xacnio.kciktv.mobile.ui.player.VodChatReplayBuffer.Companion.PAGE_STEP_MS
import dev.xacnio.kciktv.mobile.ui.player.VodChatReplayBuffer.Companion.SEEK_BACKFILL_MS
import dev.xacnio.kciktv.mobile.ui.player.VodChatReplayBuffer.Companion.TARGET_BUFFER_MS
import dev.xacnio.kciktv.shared.data.model.ChatMessage
import dev.xacnio.kciktv.shared.data.model.ChatSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VodChatReplayBufferTest {

    private val start = 1_700_000_000_000L
    private val channel = 42L
    private var now = 1_000_000L
    private var random = 0.0
    private lateinit var buf: VodChatReplayBuffer

    private val sender = ChatSender(id = 1L, username = "u", color = null, badges = null)
    private fun msg(id: String, offsetMs: Long) =
        ChatMessage(id = id, content = id, sender = sender, createdAt = start + offsetMs)

    @Before
    fun setUp() {
        now = 1_000_000L
        random = 0.0
        buf = VodChatReplayBuffer(nowMs = { now }, randomUnit = { random })
    }

    /** Drives success responses until no follow-up is returned; returns pages requested. */
    private fun drain(first: VodChatReplayBuffer.FetchRequest?, respond: (Long) -> List<ChatMessage> = { emptyList() }): List<Long> {
        val starts = mutableListOf<Long>()
        var req = first
        while (req != null) {
            starts += req.pageStartMs
            req = buf.onFetchSuccess(req, respond(req.pageStartMs))
            assertTrue("runaway fetch chain", starts.size < 200)
        }
        return starts
    }

    @Test
    fun invalidStartIsInert() {
        buf.start(0L, start)
        assertNull(buf.onPlaybackTick(0, true).nextFetch)
        buf.start(channel, 0L)
        assertNull(buf.onPlaybackTick(0, true).nextFetch)
    }

    @Test
    fun negativeProgressIgnored() {
        buf.start(channel, start)
        val r = buf.onPlaybackTick(-1, true)
        assertNull(r.nextFetch)
        assertFalse(r.didSeekReset)
    }

    @Test
    fun fixedFiveSecondStrideAcrossPopulatedAndEmptyPages() {
        buf.start(channel, start)
        val first = buf.onPlaybackTick(0, true).nextFetch
        assertNotNull(first)
        assertEquals(start, first!!.pageStartMs)
        // Alternate populated and empty pages
        val starts = drain(first) { s ->
            val idx = (s - start) / PAGE_STEP_MS
            if (idx % 2 == 0L) listOf(msg("m$idx", s - start + 1_000)) else emptyList()
        }
        for (i in 1 until starts.size) assertEquals(PAGE_STEP_MS, starts[i] - starts[i - 1])
        assertEquals(start + starts.size * PAGE_STEP_MS, buf.nextPageStartMs)
    }

    @Test
    fun emptyPagesNeverStall() {
        buf.start(channel, start)
        val starts = drain(buf.onPlaybackTick(0, true).nextFetch)
        assertTrue(starts.size >= 4)
        assertEquals(buf.nextPageStartMs, buf.bufferedUntilMs)
        assertTrue(buf.bufferedUntilMs - start >= TARGET_BUFFER_MS)
    }

    @Test
    fun burstCapAndAheadTrigger() {
        buf.start(channel, start)
        val starts = drain(buf.onPlaybackTick(0, true).nextFetch)
        // 20s target / 5s page = 4 pages, never more
        assertEquals((TARGET_BUFFER_MS / PAGE_STEP_MS).toInt(), starts.size)
        assertEquals(start + TARGET_BUFFER_MS, buf.bufferedUntilMs)
        // Ahead = 20s - p stays >= 15s through p = 5000 -> no fetch
        var p = 0L
        while (p < 5_000L) { p += 250; assertNull("p=$p", buf.onPlaybackTick(p, true).nextFetch) }
        // At 5250 ahead = 14.75s < 15s -> fetch exactly the next page
        val r = buf.onPlaybackTick(5_250L, true)
        assertEquals(start + TARGET_BUFFER_MS, r.nextFetch!!.pageStartMs)
        assertTrue(buf.bufferedUntilMs - (start + 5_250L) < AHEAD_BUFFER_MS)
        // Follow-up chain tops back up to 20s ahead of playback, then stops
        val more = drain(r.nextFetch)
        assertEquals(2, more.size)
        assertTrue(buf.bufferedUntilMs - (start + 5_250L) >= TARGET_BUFFER_MS)
    }

    @Test
    fun noFetchWhenPaused() {
        buf.start(channel, start)
        assertNull(buf.onPlaybackTick(0, false).nextFetch)
    }

    @Test
    fun onlyOneFetchInFlight() {
        buf.start(channel, start)
        assertNotNull(buf.onPlaybackTick(0, true).nextFetch)
        assertNull(buf.onPlaybackTick(250, true).nextFetch)
    }

    @Test
    fun forwardSeekResetsWithBackfill() {
        buf.start(channel, start)
        drain(buf.onPlaybackTick(0, true).nextFetch)
        buf.onPlaybackTick(250, true)
        val r = buf.onPlaybackTick(60_000, true)
        assertTrue(r.didSeekReset)
        assertEquals(start + 60_000 - SEEK_BACKFILL_MS, r.nextFetch!!.pageStartMs)
        assertEquals(0, buf.pageCount)
    }

    @Test
    fun smallForwardStepIsNotSeek() {
        buf.start(channel, start)
        buf.onPlaybackTick(0, true)
        assertFalse(buf.onPlaybackTick(1_900, true).didSeekReset)
    }

    @Test
    fun backwardSeekResetsWithBackfillClampedAtZero() {
        buf.start(channel, start)
        buf.onPlaybackTick(10_000, true)
        val r = buf.onPlaybackTick(1_000, true)
        assertTrue(r.didSeekReset)
        assertEquals(start, r.nextFetch!!.pageStartMs) // 1000 - 2500 clamped to 0
    }

    @Test
    fun startWithOffsetAppliesBackfill() {
        buf.start(channel, start, initialOffsetMs = 30_000)
        assertEquals(start + 30_000 - SEEK_BACKFILL_MS, buf.nextPageStartMs)
        val r = buf.onPlaybackTick(30_000, true)
        assertFalse(r.didSeekReset)
        assertEquals(start + 27_500, r.nextFetch!!.pageStartMs)
    }

    @Test
    fun slidingSuppressesThenResetsOnRelease() {
        buf.start(channel, start)
        buf.onPlaybackTick(0, true)
        buf.setSliding(true)
        val during = buf.onPlaybackTick(50_000, true)
        assertFalse(during.didSeekReset)
        assertNull(during.nextFetch)
        assertTrue(during.messagesToEmit.isEmpty())
        buf.onPlaybackTick(80_000, true)
        buf.setSliding(false)
        val release = buf.onPlaybackTick(80_100, true)
        assertTrue(release.didSeekReset)
        assertEquals(start + 80_100 - SEEK_BACKFILL_MS, release.nextFetch!!.pageStartMs)
        // Next normal tick is not a seek
        assertFalse(buf.onPlaybackTick(80_350, true).didSeekReset)
    }

    @Test
    fun slidingBlocksFollowUpChain() {
        buf.start(channel, start)
        val req = buf.onPlaybackTick(0, true).nextFetch!!
        buf.setSliding(true)
        assertNull(buf.onFetchSuccess(req, emptyList()))
    }

    @Test
    fun staleResponseDiscardedAfterSeek() {
        buf.start(channel, start)
        val old = buf.onPlaybackTick(0, true).nextFetch!!
        buf.onExplicitSeek(100_000)
        assertNull(buf.onFetchSuccess(old, listOf(msg("stale", 1_000))))
        assertEquals(0, buf.pendingCount)
        assertEquals(0, buf.pageCount)
        assertNull(buf.onFetchFailure(old))
        assertEquals(0, buf.retryCount)
        val fresh = buf.onPlaybackTick(100_000, true).nextFetch!!
        assertTrue(fresh.generation > old.generation)
        assertEquals(start + 100_000 - SEEK_BACKFILL_MS, fresh.pageStartMs)
    }

    @Test
    fun staleResponseDiscardedAfterStop() {
        buf.start(channel, start)
        val req = buf.onPlaybackTick(0, true).nextFetch!!
        buf.stop()
        assertNull(buf.onFetchSuccess(req, listOf(msg("a", 100))))
        assertNull(buf.onFetchFailure(req))
        assertFalse(buf.isActive)
        assertNull(buf.onPlaybackTick(250, true).nextFetch)
    }

    @Test
    fun exponentialBackoffWithCapAndJitter() {
        buf.start(channel, start)
        val expected = listOf(3_000L, 6_000L, 12_000L, 24_000L, 48_000L, 60_000L, 60_000L)
        for (e in expected) {
            now += 100_000 // past any retry window
            val req = buf.onPlaybackTick(0, true).nextFetch!!
            assertEquals(e, buf.onFetchFailure(req))
        }
        random = 0.5
        now += 100_000
        assertEquals(60_000L + 250L, buf.onFetchFailure(buf.onPlaybackTick(0, true).nextFetch!!))
        random = 5.0 // clamped to < 1
        now += 100_000
        assertEquals(60_000L + 499L, buf.onFetchFailure(buf.onPlaybackTick(0, true).nextFetch!!))
    }

    @Test
    fun retryWindowRespected() {
        buf.start(channel, start)
        val req = buf.onPlaybackTick(0, true).nextFetch!!
        assertEquals(3_000L, buf.onFetchFailure(req))
        now += 1_000
        val waiting = buf.onPlaybackTick(0, true)
        assertNull(waiting.nextFetch)
        assertEquals(2_000L, waiting.retryDelayMs)
        now += 2_000
        assertNotNull(buf.onPlaybackTick(0, true).nextFetch)
    }

    @Test
    fun backoffResetsOnSuccess() {
        buf.start(channel, start)
        buf.onFetchFailure(buf.onPlaybackTick(0, true).nextFetch!!)
        now += 10_000
        buf.onFetchFailure(buf.onPlaybackTick(0, true).nextFetch!!)
        assertEquals(2, buf.retryCount)
        now += 10_000
        buf.onFetchSuccess(buf.onPlaybackTick(0, true).nextFetch!!, emptyList())
        assertEquals(0, buf.retryCount)
    }

    @Test
    fun backoffResetsOnSeek() {
        buf.start(channel, start)
        buf.onFetchFailure(buf.onPlaybackTick(0, true).nextFetch!!)
        assertEquals(1, buf.retryCount)
        buf.onExplicitSeek(50_000)
        assertEquals(0, buf.retryCount)
        assertNotNull(buf.onPlaybackTick(50_000, true).nextFetch) // no retry wait
    }

    @Test
    fun chronologicalEmissionDedupAndNoTimestampMutation() {
        buf.start(channel, start)
        val req = buf.onPlaybackTick(0, true).nextFetch!!
        val a = msg("a", 1_000); val b = msg("b", 500); val c = msg("c", 3_000)
        buf.onFetchSuccess(req, listOf(a, c, b, a))
        // Overlapping next page repeats c
        val follow = VodChatReplayBuffer.FetchRequest(req.generation, channel, start + PAGE_STEP_MS)
        buf.onFetchSuccess(follow, listOf(c, msg("d", 6_000)))

        val emitted = mutableListOf<ChatMessage>()
        var p = 0L
        while (p <= 7_000) { emitted += buf.onPlaybackTick(p, true).messagesToEmit; p += 250 }
        assertEquals(listOf("b", "a", "c", "d"), emitted.map { it.id })
        assertEquals(start + 500, emitted[0].createdAt)
        assertEquals(start + 1_000, emitted[1].createdAt)
        assertEquals(start + 3_000, emitted[2].createdAt)
        assertEquals(start + 6_000, emitted[3].createdAt)
    }

    @Test
    fun messagesNotEmittedBeforeTheirTime() {
        buf.start(channel, start)
        val req = buf.onPlaybackTick(0, true).nextFetch!!
        buf.onFetchSuccess(req, listOf(msg("late", 2_000)))
        assertTrue(buf.onPlaybackTick(1_750, true).messagesToEmit.isEmpty())
        assertEquals(listOf("late"), buf.onPlaybackTick(2_000, true).messagesToEmit.map { it.id })
        assertTrue(buf.onPlaybackTick(2_250, true).messagesToEmit.isEmpty())
    }

    @Test
    fun maxPagesSlidingWindow() {
        buf.start(channel, start)
        val gen = buf.onPlaybackTick(0, true).nextFetch!!.generation
        for (i in 0 until 40) {
            val ps = start + i * PAGE_STEP_MS
            buf.onFetchSuccess(
                VodChatReplayBuffer.FetchRequest(gen, channel, ps),
                listOf(msg("p$i", i * PAGE_STEP_MS + 100))
            )
            assertTrue(buf.pageCount <= MAX_PAGES)
        }
        assertEquals(MAX_PAGES, buf.pageCount)
        assertTrue(buf.fetchedIdCount <= MAX_PAGES)
        assertTrue(buf.pendingCount <= MAX_PAGES)
        // A late duplicate from a pruned page is not re-buffered
        buf.onFetchSuccess(VodChatReplayBuffer.FetchRequest(gen, channel, start + 40 * PAGE_STEP_MS), listOf(msg("p0", 100)))
        assertTrue(buf.pendingCount <= MAX_PAGES)
    }
}
