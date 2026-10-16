package com.pishimovie.data.cache

import android.content.Context
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.SearchResult
import com.pishimovie.util.PLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Caches search results and movie/series metadata for offline access
 * (Phase 6: "Offline search", "Cached metadata").
 *
 * Phase 8: All index access is now synchronized to prevent
 * ConcurrentModificationException when multiple coroutines access the
 * cache concurrently (e.g., instant search + suggestions running in parallel).
 *
 * Persists a map of query → SearchResult so the user can search and browse
 * detail pages even without a network connection. The cache is capped at
 * 200 entries with LRU eviction.
 */
class MetadataCache private constructor(private val context: Context) {

    private val tag = "MetadataCache"
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir by lazy {
        context.filesDir.resolve("pishi_metadata_cache").apply { mkdirs() }
    }
    private val indexFile by lazy { cacheDir.resolve("index.json") }
    private val maxEntries = 200

    @Serializable
    private data class IndexEntry(
        val query: String,
        val timestamp: Long,
        val fileName: String,
    )

    private val index = mutableListOf<IndexEntry>()
    private val indexLock = Any()
    @Volatile private var indexLoaded = false

    private fun ensureIndexLoaded() {
        if (indexLoaded) return
        synchronized(indexLock) {
            if (indexLoaded) return
            try {
                if (indexFile.exists()) {
                    val list = json.decodeFromString<List<IndexEntry>>(indexFile.readText())
                    index.clear()
                    index.addAll(list)
                }
            } catch (e: Exception) {
                PLog.w(tag, "Failed to load index", e)
            }
            indexLoaded = true
        }
    }

    private fun saveIndex() {
        try {
            val snapshot = synchronized(indexLock) { index.toList() }
            indexFile.writeText(json.encodeToString(snapshot))
        } catch (e: Exception) {
            PLog.w(tag, "Failed to save index", e)
        }
    }

    /** Stores a search result for [query]. */
    fun putSearchResult(query: String, result: SearchResult) {
        ensureIndexLoaded()
        try {
            val fileName = "${sha256Hex(query)}.json"
            val file = cacheDir.resolve(fileName)
            file.writeText(json.encodeToString(result))

            synchronized(indexLock) {
                index.removeAll { it.query.equals(query, ignoreCase = true) }
                index.add(0, IndexEntry(query, System.currentTimeMillis(), fileName))
                while (index.size > maxEntries) {
                    val evicted = index.removeAt(index.size - 1)
                    try { cacheDir.resolve(evicted.fileName).delete() } catch (_: Exception) {}
                }
            }
            saveIndex()
        } catch (e: Exception) {
            PLog.w(tag, "Failed to cache search result for $query", e)
        }
    }

    /** Returns the cached search result for [query], or null. */
    fun getSearchResult(query: String): SearchResult? {
        ensureIndexLoaded()
        return try {
            val entry = synchronized(indexLock) {
                index.find { it.query.equals(query, ignoreCase = true) }
            } ?: return null
            val file = cacheDir.resolve(entry.fileName)
            if (!file.exists()) return null
            json.decodeFromString<SearchResult>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /** Returns all cached search queries (for the offline library view). */
    fun getAllCachedQueries(): List<String> {
        ensureIndexLoaded()
        return synchronized(indexLock) { index.map { it.query } }
    }

    /** Returns all cached posters across all stored search results. */
    fun getAllCachedPosters(): List<Poster> {
        ensureIndexLoaded()
        val entries = synchronized(indexLock) { index.toList() }
        val all = mutableListOf<Poster>()
        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            try {
                val file = cacheDir.resolve(entry.fileName)
                if (file.exists()) {
                    val result = json.decodeFromString<SearchResult>(file.readText())
                    result.posters.forEach { poster ->
                        val key = "${poster.type}_${poster.id}"
                        if (key !in seen) {
                            seen.add(key)
                            all.add(poster)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return all
    }

    /** Clears the entire metadata cache. */
    fun clearAll() {
        ensureIndexLoaded()
        synchronized(indexLock) { index.clear() }
        // NOTE: no saveIndex() here — the index file itself is deleted below
        // along with everything else, so writing it first would be pointless.
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /** Returns the disk size in bytes. */
    fun sizeBytes(): Long {
        return try {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) { 0L }
    }

    companion object {
        @Volatile private var instance: MetadataCache? = null
        fun get(context: Context): MetadataCache =
            instance ?: synchronized(this) {
                instance ?: MetadataCache(context.applicationContext).also { instance = it }
            }
    }
}
