package com.pishimovie.data.repository

import com.pishimovie.util.PLog
import android.content.Context
import com.pishimovie.data.model.SearchHistoryEntry
import com.pishimovie.utils.StorageUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the user's search history locally so they can quickly re-run
 * previous queries. Mirrors the file-based pattern used by
 * [com.pishimovie.utils.StorageUtils] for favorites.
 */
class SearchHistoryRepository(private val context: Context) {

    private val tag = "SearchHistoryRepo"
    private val fileName = "search_history.json"
    private val maxEntries = 20
    /** Guards read-modify-write cycles on the search-history file. */
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            try {
                val current = getAll().toMutableList()
                // De-dupe case-insensitively, then prepend
                current.removeAll { it.query.equals(trimmed, ignoreCase = true) }
                current.add(0, SearchHistoryEntry(trimmed))
                write(current.take(maxEntries))
            } catch (e: Exception) {
                PLog.e(tag, "Error adding search history", e)
            }
        }
    }

    fun getAll(): List<SearchHistoryEntry> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<SearchHistoryEntry>>(file.readText())
        } catch (e: Exception) {
            PLog.e(tag, "Error loading search history", e)
            // Corrupt ≠ empty: move the bad file aside instead of letting a
            // later save silently cement the loss.
            try {
                val corrupt = File(context.filesDir, "$fileName.corrupt")
                if (corrupt.exists()) corrupt.delete()
                file.renameTo(corrupt)
            } catch (_: Exception) {}
            emptyList()
        }
    }

    fun remove(query: String) {
        synchronized(lock) {
            try {
                val current = getAll().toMutableList()
                current.removeAll { it.query.equals(query, ignoreCase = true) }
                write(current)
            } catch (e: Exception) {
                PLog.e(tag, "Error removing search history", e)
            }
        }
    }

    fun clearAll() {
        // Same lock as add/remove: without it, a save that already read the
        // old list could rewrite the file after the delete and resurrect
        // the cleared history.
        synchronized(lock) {
            try {
                File(context.filesDir, fileName).delete()
            } catch (e: Exception) {
                PLog.e(tag, "Error clearing search history", e)
            }
        }
    }

    private fun write(items: List<SearchHistoryEntry>) {
        StorageUtils.atomicWriteText(
            File(context.filesDir, fileName),
            json.encodeToString(items),
        )
    }
}
