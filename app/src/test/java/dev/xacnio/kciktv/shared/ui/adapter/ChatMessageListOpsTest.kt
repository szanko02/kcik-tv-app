package dev.xacnio.kciktv.shared.ui.adapter

import dev.xacnio.kciktv.shared.data.model.ChatMessage
import dev.xacnio.kciktv.shared.data.model.ChatSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageListOpsTest {

    private val defaultSender = ChatSender(
        id = 1L,
        username = "tester",
        color = "#53FC18",
        badges = null
    )

    private fun msg(id: String, createdAt: Long): ChatMessage =
        ChatMessage(
            id = id,
            content = "msg-$id",
            sender = defaultSender,
            createdAt = createdAt
        )

    @Test
    fun mergeLiveBatch_returnsFalseWhenEmptyOrAllDuplicates() {
        val cache = mutableListOf(msg("1", 100L), msg("2", 200L))
        val idSet = mutableSetOf("1", "2")

        assertFalse(ChatMessageListOps.mergeLiveBatch(cache, idSet, emptyList(), 10))
        assertFalse(ChatMessageListOps.mergeLiveBatch(cache, idSet, listOf(msg("1", 100L), msg("2", 200L)), 10))
        assertEquals(listOf("1", "2"), cache.map { it.id })
        assertEquals(setOf("1", "2"), idSet)
    }

    @Test
    fun mergeLiveBatch_appendsInOrderAndDeduplicatesIntraBatch() {
        val cache = mutableListOf(msg("1", 100L))
        val idSet = mutableSetOf("1")

        val added = ChatMessageListOps.mergeLiveBatch(
            cache,
            idSet,
            listOf(msg("2", 200L), msg("2", 200L), msg("3", 300L)),
            10
        )

        assertTrue(added)
        assertEquals(listOf("1", "2", "3"), cache.map { it.id })
        assertEquals(setOf("1", "2", "3"), idSet)
    }

    @Test
    fun mergeLiveBatch_insertsOutOfOrderAndTrimsHeadWithIdSetSync() {
        val cache = mutableListOf(msg("1", 100L), msg("3", 300L), msg("4", 400L))
        val idSet = mutableSetOf("1", "3", "4")

        val added = ChatMessageListOps.mergeLiveBatch(
            cache,
            idSet,
            listOf(msg("2", 200L), msg("5", 500L), msg("6", 600L)),
            effectiveLimit = 4
        )

        assertTrue(added)
        assertEquals(listOf("3", "4", "5", "6"), cache.map { it.id })
        assertEquals(setOf("3", "4", "5", "6"), idSet)
    }

    @Test
    fun mergeDeduplicated_dedupsSortsTrimsTailAndRebuildsIdSet() {
        val cache = mutableListOf(msg("3", 300L), msg("5", 500L))
        val idSet = mutableSetOf("stale-id", "3", "5")

        ChatMessageListOps.mergeDeduplicated(
            cache,
            idSet,
            listOf(msg("1", 100L), msg("3", 300L), msg("2", 200L), msg("4", 400L)),
            effectiveLimit = 3
        )

        assertEquals(listOf("3", "4", "5"), cache.map { it.id })
        assertEquals(setOf("3", "4", "5"), idSet)
    }

    @Test
    fun replaceConfirmedMessageAt_preservesTimestampAndEvictsRaceDuplicate() {
        // Case 1: Normal draft confirmation
        val cache1 = mutableListOf(msg("draft-1", 150L), msg("other", 200L))
        val idSet1 = mutableSetOf("draft-1", "other")
        ChatMessageListOps.replaceConfirmedMessageAt(cache1, idSet1, 0, msg("srv-1", 999L))
        assertEquals(listOf("srv-1", "other"), cache1.map { it.id })
        assertEquals(150L, cache1[0].createdAt)
        assertEquals(setOf("srv-1", "other"), idSet1)

        // Case 2: Server message already arrived earlier in cache before draft confirmation
        val cache2 = mutableListOf(msg("srv-2", 140L), msg("draft-2", 150L), msg("tail", 300L))
        val idSet2 = mutableSetOf("srv-2", "draft-2", "tail")
        ChatMessageListOps.replaceConfirmedMessageAt(cache2, idSet2, 1, msg("srv-2", 999L))
        assertEquals(listOf("srv-2", "tail"), cache2.map { it.id })
        assertEquals(150L, cache2[0].createdAt)
        assertEquals(setOf("srv-2", "tail"), idSet2)
    }
}
