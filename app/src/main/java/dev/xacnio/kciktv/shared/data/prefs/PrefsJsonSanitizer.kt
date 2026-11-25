package dev.xacnio.kciktv.shared.data.prefs

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class SanitizedList<T>(
    val items: List<T>,
    val droppedAny: Boolean
)

/**
 * Validates raw persisted JSON arrays element-by-element *before* Gson deserializes into Kotlin
 * data classes. Previous release builds (< v2.5.6) obfuscated inner preference model fields under
 * R8, leaving legacy JSON with unmapped keys (`{"a":...}`). Deserializing those directly via
 * Gson's `UnsafeAllocator` injects `null` into Kotlin non-null `String` properties, which can
 * survive Kotlin null-checks stripped by R8 and crash UI consumers.
 */
internal object PrefsJsonSanitizer {

    private fun JsonObject.nonBlankString(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive || !el.asJsonPrimitive.isString) return null
        val value = el.asString
        return value.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.childObject(key: String): JsonObject? {
        val el = get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    private inline fun <T> parseJsonArray(
        raw: String?,
        keySelector: (T) -> Any?,
        crossinline parseItem: (JsonObject) -> T?
    ): SanitizedList<T> {
        if (raw.isNullOrBlank()) return SanitizedList(emptyList(), droppedAny = false)
        val root = try {
            JsonParser.parseString(raw)
        } catch (_: Exception) {
            return SanitizedList(emptyList(), droppedAny = true)
        }
        if (!root.isJsonArray) return SanitizedList(emptyList(), droppedAny = true)

        val items = mutableListOf<T>()
        val seenKeys = HashSet<Any>()
        var droppedAny = false

        for (elem in root.asJsonArray) {
            if (!elem.isJsonObject) {
                droppedAny = true
                continue
            }
            val parsed = try {
                parseItem(elem.asJsonObject)
            } catch (_: Exception) {
                null
            }
            if (parsed == null) {
                droppedAny = true
                continue
            }
            val dedupKey = keySelector(parsed)
            if (dedupKey != null && !seenKeys.add(dedupKey)) {
                droppedAny = true
                continue
            }
            items.add(parsed)
        }
        return SanitizedList(items, droppedAny)
    }

    fun parseStoredAccounts(raw: String?, gson: Gson): SanitizedList<AppPreferences.StoredAccount> {
        return parseJsonArray(
            raw = raw,
            keySelector = { acc -> if (acc.userId > 0L) acc.userId else "name:${acc.username}" }
        ) { obj ->
            obj.nonBlankString("username") ?: return@parseJsonArray null
            obj.nonBlankString("authToken") ?: return@parseJsonArray null
            gson.fromJson(obj, AppPreferences.StoredAccount::class.java)
        }
    }

    fun parseVodWatchHistory(raw: String?, gson: Gson): SanitizedList<AppPreferences.VodWatchState> {
        return parseJsonArray(
            raw = raw,
            keySelector = { state -> state.videoId }
        ) { obj ->
            obj.nonBlankString("videoId") ?: return@parseJsonArray null
            gson.fromJson(obj, AppPreferences.VodWatchState::class.java)
        }
    }

    fun parseSearchHistory(raw: String?, gson: Gson): SanitizedList<AppPreferences.HistoryEntry> {
        return parseJsonArray(
            raw = raw,
            keySelector = { null }
        ) { obj ->
            when (obj.nonBlankString("type")) {
                "query" -> obj.nonBlankString("query") ?: return@parseJsonArray null
                "channel" -> {
                    val ch = obj.childObject("channelItem") ?: return@parseJsonArray null
                    ch.nonBlankString("slug") ?: return@parseJsonArray null
                    ch.nonBlankString("username") ?: return@parseJsonArray null
                }
                "category" -> {
                    val cat = obj.childObject("categoryItem") ?: return@parseJsonArray null
                    cat.nonBlankString("slug") ?: return@parseJsonArray null
                    cat.nonBlankString("name") ?: return@parseJsonArray null
                }
                else -> return@parseJsonArray null
            }
            gson.fromJson(obj, AppPreferences.HistoryEntry::class.java)
        }
    }
}
