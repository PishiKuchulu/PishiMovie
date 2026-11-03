package com.pishimovie.data.cache

import android.content.Context
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.SearchResult
import com.pishimovie.util.PLog
import com.pishimovie.utils.StorageUtils
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
    /** Serializes writes of individual result files so concurrent writers
     *  (instant search + suggestions) can never interleave bytes. */
    private val resultWriteLock = Any()
    @Volatile private var indexLoaded = false

    /**
     * Canonical query form used for hashing, indexing and lookup, so case /
     * whitespace variants ("Alien" vs " alien ") share one cache entry
     * instead of producing orphan files the index cannot find again.
     */
    private fun normalizeQuery(query: String): String = query.trim().lowercase()

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
            // Snapshot + write under the same lock so two concurrent saves
            // cannot interleave into an older snapshot winning.
            synchronized(indexLock) {
                StorageUtils.atomicWriteText(indexFile, json.encodeToString(index.toList()))
            }
        } catch (e: Exception) {
            PLog.w(tag, "Failed to save index", e)
        }
    }

    /** Stores a search result for [query]. */
    fun putSearchResult(query: String, result: SearchResult) {
        ensureIndexLoaded()
        val normalized = normalizeQuery(query)
        try {
            val fileName = "${sha256Hex(normalized)}.json"
            val file = cacheDir.resolve(fileName)
            // Guarded + atomic: concurrent instant-search/suggestion writers
            // writing the same query must not interleave-corrupt the file.
            synchronized(resultWriteLock) {
                StorageUtils.atomicWriteText(file, json.encodeToString(result))
            }

            synchronized(indexLock) {
                // Drop older entries for the same normalized query (incl.
                // legacy mixed-case ones) and delete their now-orphan files.
                val replaced = index.filter { normalizeQuery(it.query) == normalized }
                index.removeAll { normalizeQuery(it.query) == normalized }
                replaced.forEach { old ->
                    if (old.fileName != fileName) {
                        try { cacheDir.resolve(old.fileName).delete() } catch (_: Exception) {}
                    }
                }
                index.add(0, IndexEntry(normalized, System.currentTimeMillis(), fileName))
                while (index.size > maxEntries) {
                    val evicted = index.removeAt(index.size - 1)
                    try { cacheDir.resolve(evicted.fileName).delete() } catch (_: Exception) {}
                }
            }
            saveIndex()
        } catch (e: Exception) {
            // NOTE: never log the raw user query — it is personal data.
            PLog.w(tag, "Failed to cache search result", e)
        }
    }

    /** Returns the cached search result for [query], or null. */
    fun getSearchResult(query: String): SearchResult? {
        ensureIndexLoaded()
        val normalized = normalizeQuery(query)
        return try {
            val entry = synchronized(indexLock) {
                index.find { normalizeQuery(it.query) == normalized }
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
