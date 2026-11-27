/**
 * File: ChatMessageListOps.kt
 *
 * Description: Pure Kotlin list operations for deduplicating, ordering, and capping chat messages.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.shared.ui.adapter

import dev.xacnio.kciktv.shared.data.model.ChatMessage

internal object ChatMessageListOps {

    /**
     * Merges a live batch of messages into [cache] using O(1) deduplication via [idSet].
     * Appends in-order messages directly to the tail and trims excess head items in one shot.
     *
     * @return true if at least one new message was added, false otherwise.
     */
    fun mergeLiveBatch(
        cache: MutableList<ChatMessage>,
        idSet: MutableSet<String>,
        newMessages: List<ChatMessage>,
        effectiveLimit: Int
    ): Boolean {
        var addedAny = false
        for (msg in newMessages) {
            if (!idSet.add(msg.id)) continue
            addedAny = true
            if (cache.isEmpty() || msg.createdAt >= cache.last().createdAt) {
                cache.add(msg)
            } else {
                val insertIndex = cache.indexOfLast { it.createdAt <= msg.createdAt } + 1
                cache.add(insertIndex, msg)
            }
        }

        if (!addedAny) return false

        val safeLimit = effectiveLimit.coerceAtLeast(0)
        val excess = cache.size - safeLimit
        if (excess > 0) {
            for (i in 0 until excess) {
                idSet.remove(cache[i].id)
            }
            cache.subList(0, excess).clear()
        }
        return true
    }

    /**
     * Replaces a pending local draft at [draftIndex] with [serverMessage] (preserving the draft's
     * original [ChatMessage.createdAt] so the row doesn't jump), while evicting any pre-existing
     * duplicate entry that already carries [serverMessage]'s ID from a WebSocket/history race.
     */
    fun replaceConfirmedMessageAt(
        cache: MutableList<ChatMessage>,
        idSet: MutableSet<String>,
        draftIndex: Int,
        serverMessage: ChatMessage
    ) {
        if (draftIndex !in cache.indices) return
        val oldMsg = cache[draftIndex]
        val preservedTimestamp = oldMsg.createdAt

        var targetIndex = draftIndex
        val existingIndex = cache.indexOfFirst { it.id == serverMessage.id }
        if (existingIndex != -1 && existingIndex != draftIndex) {
            cache.removeAt(existingIndex)
            if (existingIndex < targetIndex) {
                targetIndex--
            }
        }

        idSet.remove(oldMsg.id)
        idSet.add(serverMessage.id)
        cache[targetIndex] = serverMessage.copy(createdAt = preservedTimestamp)
    }

    /**
     * Merges [newMessages] (e.g. history load) with [cache], preserving insertion order during
     * deduplication, sorting by [ChatMessage.createdAt], trimming to the newest [effectiveLimit]
     * items, and rebuilding [idSet] to match [cache].
     */
    fun mergeDeduplicated(
        cache: MutableList<ChatMessage>,
        idSet: MutableSet<String>,
        newMessages: List<ChatMessage>,
        effectiveLimit: Int
    ) {
        cache.addAll(newMessages)

        val seen = HashSet<String>(cache.size)
        val workingList = cache.filter { seen.add(it.id) }
        val sortedList = workingList.sortedBy { it.createdAt }

        val safeLimit = effectiveLimit.coerceAtLeast(0)
        val trimmedList = if (sortedList.size > safeLimit) {
            sortedList.subList(sortedList.size - safeLimit, sortedList.size)
        } else {
            sortedList
        }

        cache.clear()
        cache.addAll(trimmedList)
        idSet.clear()
        idSet.addAll(cache.map { it.id })
    }
}
