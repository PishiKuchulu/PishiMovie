package com.pishimovie.data.cache

import com.pishimovie.BuildConfig
import com.pishimovie.util.PLog
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * SHA-256 hex digest of [input], used as a disk-cache file identity.
 * (32-bit String.hashCode() is collision-prone; SHA-256 is not.)
 * Internal to the data.cache package — shared by [ApiResponseCache] and [MetadataCache].
 */
internal fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) append('0')
            append(v.toString(16))
        }
    }
}

/**
 * PishiMovie API response cache (Phase 6: "API response cache").
 *
 * Wraps the existing OkHttp client with a time-based in-memory + disk-backed
 * cache layer. This does NOT modify any repository — instead, repositories
 * can opt into caching by calling [cachedGet] instead of building their own
 * request. The existing [com.pishimovie.data.repository.BaseRepository.executeRequest]
 * is untouched and remains the source of truth for uncached calls.
 *
 * Cache strategy:
 *  - Memory: LRU map keyed by URL, TTL-driven expiry (default 5 min)
 *  - Disk: file-based, keyed by URL SHA-256, TTL-driven expiry (fixed 1 hour)
 *  - On cache hit (memory or disk): returns immediately without network
 *  - On cache miss: fetches via OkHttp, stores in both layers
 *  - On non-2xx HTTP responses: throws HttpStatusException (never serves
 *    stale data as if it were fresh)
 *  - On network failure (no response at all): falls back to expired disk
 *    cache if available (enables offline browsing — Phase 6: "Offline
 *    search", "Sync when online again")
 *
 * Debug logs redact the API key from logged URLs.
 *
 * The cache is safe for concurrent access (synchronized).
 */
class ApiResponseCache private constructor(private val context: Context) {

    private val tag = "ApiResponseCache"
    private val memoryCache = LinkedHashMap<String, CacheEntry>(64, 0.75f, true)
    private val memoryMaxEntries = 64
    /** Disk TTL is fixed at 1 hour, independent of the (shorter) memory TTL. */
    private val diskTtlMs = 3_600_000L
    private val diskCacheDir by lazy {
        context.cacheDir.resolve("pishi_api_cache").apply { mkdirs() }
    }

    /** Redacts the API key from a URL before it is written to logcat. */
    private fun redact(url: String): String =
        url.replace(BuildConfig.API_KEY, "***")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(
        val body: String,
        val timestamp: Long,
        val ttlMs: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
    }

    /** Non-2xx HTTP responses surface as this (never as silent stale-cache hits). */
    private class HttpStatusException(val code: Int, url: String) :
        IOException("HTTP $code for $url")

    /**
     * Returns the cached response for [url] if available and not expired,
     * otherwise fetches via OkHttp and caches the result.
     *
     * @param ttlSeconds  Time-to-live for this specific entry. Default 300s (5 min).
     * @param forceRefresh If true, bypasses the cache and fetches fresh.
     */
    suspend fun cachedGet(
        url: String,
        ttlSeconds: Int = 300,
        forceRefresh: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            // 1. Check memory cache
            synchronized(memoryCache) {
                memoryCache[url]?.let { entry ->
                    if (!entry.isExpired()) {
                        PLog.d(tag, "Memory cache HIT: ${redact(url)}")
                        return@withContext entry.body
                    }
                }
            }
            // 2. Check disk cache
            val diskEntry = readDiskCache(url)
            if (diskEntry != null && !diskEntry.isExpired()) {
                PLog.d(tag, "Disk cache HIT: ${redact(url)}")
                synchronized(memoryCache) { putMemory(url, diskEntry) }
                return@withContext diskEntry.body
            }
        }

        // 3. Network fetch
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val entry = CacheEntry(body, System.currentTimeMillis(), ttlSeconds * 1000L)
                    synchronized(memoryCache) { putMemory(url, entry) }
                    // Disk entries always get the fixed 1-hour TTL, independent
                    // of the (shorter) memory TTL passed by the caller.
                    writeDiskCache(url, entry.copy(ttlMs = diskTtlMs))
                    PLog.d(tag, "Network fetch + cache store: ${redact(url)}")
                    return@withContext body
                } else {
                    // Surface HTTP errors instead of silently serving stale data:
                    // a 401/403/404/500 must never masquerade as a cache hit.
                    PLog.w(tag, "Network error ${response.code}: ${redact(url)}")
                    throw HttpStatusException(response.code, redact(url))
                }
            }
        } catch (e: HttpStatusException) {
            // HTTP error responses propagate to the caller; only genuine
            // transport failures below may fall back to the disk cache.
            throw e
        } catch (e: Exception) {
            PLog.w(tag, "Network failure for ${redact(url)} — falling back to disk cache", e)
            return@withContext readDiskCache(url)?.body
        }
    }

    private fun putMemory(url: String, entry: CacheEntry) {
        while (memoryCache.size >= memoryMaxEntries && memoryCache.isNotEmpty()) {
            val oldest = memoryCache.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            } else break
        }
        memoryCache[url] = entry
    }

    private fun diskCacheFile(url: String) =
        diskCacheDir.resolve(sha256Hex(url).plus(".json"))

    private fun readDiskCache(url: String): CacheEntry? {
        return try {
            val file = diskCacheFile(url)
            if (!file.exists()) return null
            val parts = file.readText().split("\n", limit = 3)
            if (parts.size != 3) return null
            CacheEntry(
                body = parts[2],
                timestamp = parts[0].toLong(),
                ttlMs = parts[1].toLong(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun writeDiskCache(url: String, entry: CacheEntry) {
        try {
            val file = diskCacheFile(url)
            file.writeText("${entry.timestamp}\n${entry.ttlMs}\n${entry.body}")
        } catch (e: Exception) {
            PLog.w(tag, "Failed to write disk cache for ${redact(url)}", e)
        }
    }

    /** Clears all cached entries (memory + disk). Called from Settings. */
    fun clearAll() {
        synchronized(memoryCache) { memoryCache.clear() }
        try {
            diskCacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            PLog.w(tag, "Failed to clear disk cache", e)
        }
    }

    /** Returns the total disk cache size in bytes. */
    fun diskCacheSizeBytes(): Long {
        return try {
            diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Removes expired entries from both memory and disk. */
    fun cleanupExpired() {
        synchronized(memoryCache) {
            val it = memoryCache.entries.iterator()
            while (it.hasNext()) {
                if (it.next().value.isExpired()) it.remove()
            }
        }
        try {
            diskCacheDir.listFiles()?.forEach { file ->
                val parts = file.readText().split("\n", limit = 3)
                if (parts.size == 3) {
                    val timestamp = parts[0].toLongOrNull() ?: return@forEach
                    val ttl = parts[1].toLongOrNull() ?: return@forEach
                    if (System.currentTimeMillis() - timestamp > ttl) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            PLog.w(tag, "Disk cleanup failed", e)
        }
    }

    companion object {
        @Volatile private var instance: ApiResponseCache? = null
        fun get(context: Context): ApiResponseCache =
            instance ?: synchronized(this) {
                instance ?: ApiResponseCache(context.applicationContext).also { instance = it }
            }
    }
}
