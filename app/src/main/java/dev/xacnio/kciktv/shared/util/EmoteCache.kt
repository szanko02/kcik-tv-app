package dev.xacnio.kciktv.shared.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.xacnio.kciktv.shared.data.model.EmoteCategory
import java.io.File

object EmoteCache {
    private val gson = Gson()
    private val listType = object : TypeToken<List<EmoteCategory>>() {}.type

    private const val MAX_CACHED_CHANNELS = 60

    fun save(context: Context, slug: String, categories: List<EmoteCategory>) {
        try {
            cacheFile(context, slug).writeText(gson.toJson(categories))
            pruneIfNeeded(cacheDir(context))
        } catch (_: Exception) {}
    }

    fun load(context: Context, slug: String): List<EmoteCategory>? {
        return try {
            val file = cacheFile(context, slug)
            if (!file.exists()) null
            else gson.fromJson(file.readText(), listType)
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "unknown" }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "emotes").apply { mkdirs() }

    private fun cacheFile(context: Context, slug: String) =
        File(cacheDir(context), "emotes_${sanitize(slug)}.json")

    private fun pruneIfNeeded(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_CACHED_CHANNELS) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED_CHANNELS)
            .forEach { it.delete() }
    }
}
