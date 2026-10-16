package com.pishimovie.data.repository

import com.pishimovie.util.PLog
import android.content.Context
import com.pishimovie.data.model.ContinueWatchingItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists [ContinueWatchingItem] entries to a local JSON file.
 *
 * No backend involvement — this powers the "Continue Watching" rail on the
 * Home screen and the resume-playback prompt in the video player. Existing
 * streaming logic is unchanged.
 */
class ContinueWatchingRepository(private val context: Context) {

    private val tag = "ContinueWatchingRepo"
    private val fileName = "continue_watching.json"
    private val maxEntries = 30
    /** Guards read-modify-write cycles on the continue-watching file. */
    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(item: ContinueWatchingItem) {
        synchronized(lock) {
            try {
                val current = getAll().toMutableList()
                // De-dupe by (id, type) — replace any existing entry
                current.removeAll { it.id == item.id && it.type == item.type }
                current.add(0, item)
                // Trim to maxEntries (keep most recent)
                val trimmed = current.take(maxEntries)
                write(trimmed)
                PLog.d(tag, "Saved continue-watching: ${item.title} (${item.type})")
            } catch (e: Exception) {
                PLog.e(tag, "Error saving continue-watching", e)
            }
        }
    }

    fun getAll(): List<ContinueWatchingItem> {
        return try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                json.decodeFromString<List<ContinueWatchingItem>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            PLog.e(tag, "Error loading continue-watching", e)
            emptyList()
        }
    }

    fun get(id: Int, type: String): ContinueWatchingItem? =
        getAll().find { it.id == id && it.type == type }

    fun remove(id: Int, type: String) {
        synchronized(lock) {
            try {
                val current = getAll().toMutableList()
                current.removeAll { it.id == id && it.type == type }
                write(current)
            } catch (e: Exception) {
                PLog.e(tag, "Error removing continue-watching", e)
            }
        }
    }

    fun clearAll() {
        try {
            File(context.filesDir, fileName).delete()
        } catch (e: Exception) {
            PLog.e(tag, "Error clearing continue-watching", e)
        }
    }

    private fun write(items: List<ContinueWatchingItem>) {
        val file = File(context.filesDir, fileName)
        file.writeText(json.encodeToString(items))
    }
}
