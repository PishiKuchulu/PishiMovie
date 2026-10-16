package com.pishimovie.data.repository

import com.pishimovie.util.PLog
import android.content.Context
import com.pishimovie.data.model.SearchHistoryEntry
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
        return try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                json.decodeFromString<List<SearchHistoryEntry>>(file.readText())
            } else emptyList()
        } catch (e: Exception) {
            PLog.e(tag, "Error loading search history", e)
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
        try {
            File(context.filesDir, fileName).delete()
        } catch (e: Exception) {
            PLog.e(tag, "Error clearing search history", e)
        }
    }

    private fun write(items: List<SearchHistoryEntry>) {
        File(context.filesDir, fileName).writeText(json.encodeToString(items))
    }
}
