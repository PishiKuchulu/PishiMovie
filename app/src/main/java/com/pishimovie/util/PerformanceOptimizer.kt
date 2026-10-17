package com.pishimovie.util

import com.pishimovie.util.PLog
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import coil.Coil
import com.pishimovie.data.cache.ApiResponseCache
import com.pishimovie.data.cache.MetadataCache
import com.pishimovie.download.DownloadManager
import com.pishimovie.utils.DeviceUtils

/**
 * PishiMovie performance + lifecycle utilities (Phase 6).
 *
 * Centralizes:
 *  - App startup optimizations (warm caches, lazy init)
 *  - Memory pressure handling (trim caches on TRIM_MEMORY)
 *  - Periodic cache cleanup
 *  - Storage usage reporting
 *  - TV-aware tuning (Android TV devices share 1–2 GB RAM with the system,
 *    so they get a smaller bitmap pool, no image crossfade, and more
 *    aggressive cache eviction — see [isLowEndTv])
 */
object PerformanceOptimizer {

    private const val tag = "PishiPerf"

    /**
     * Called from [com.pishimovie.MainActivity.onCreate] to warm up caches
     * and kick off background cleanup. Runs on a background thread.
     */
    fun onAppStart(context: Context) {
        Thread {
            try {
                // Warm up the API cache singleton (reads index)
                ApiResponseCache.get(context)
                // Warm up the metadata cache singleton (reads index)
                MetadataCache.get(context)
                // Warm up the download manager singleton + auto-resume interrupted
                DownloadManager.get(context).resumeInterrupted()
                // Clean up expired API cache entries
                ApiResponseCache.get(context).cleanupExpired()
                PLog.d(tag, "App start optimization complete")
            } catch (e: Exception) {
                PLog.w(tag, "App start optimization failed", e)
            }
        }.also { it.isDaemon = true }.start()
    }

    /**
     * Called from [com.pishimovie.MainActivity.onTrimMemory] to release
     * memory under pressure.
     *
     * Actually releases memory (Coil bitmap memory cache), unlike the old
     * implementation which only purged already-expired API entries on every
     * level. Disk caches are intentionally left alone — wiping them would
     * destroy offline data.
     *
     * @param level the trim level from ComponentCallbacks2
     */
    fun onTrimMemory(context: Context, level: Int) {
        Thread {
            try {
                val appContext = context.applicationContext
                when {
                    level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        PLog.d(tag, "Trim memory (complete) — clearing memory caches")
                        clearMemoryCaches(appContext)
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                        PLog.d(tag, "Trim memory (moderate) — clearing memory caches")
                        clearMemoryCaches(appContext)
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                        PLog.d(tag, "Trim memory (background) — clearing memory caches")
                        clearMemoryCaches(appContext)
                    }
                    level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                        PLog.d(tag, "Trim memory (UI hidden) — clearing memory caches")
                        clearMemoryCaches(appContext)
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                        PLog.d(tag, "Trim memory (running critical) — clearing memory caches")
                        clearMemoryCaches(appContext)
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                        PLog.d(tag, "Trim memory (running low) — clearing memory caches")
                        clearMemoryCaches(appContext)
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                        // On low-end TVs the system kills backgrounded apps
                        // aggressively and the bitmap pool dominates memory —
                        // clear it one level earlier than on phones.
                        if (isLowEndTv(appContext)) {
                            PLog.d(tag, "Trim memory (running moderate, low-end TV) — clearing memory caches")
                            clearMemoryCaches(appContext)
                        } else {
                            PLog.d(tag, "Trim memory (running moderate) — purging expired entries")
                            ApiResponseCache.get(appContext).cleanupExpired()
                        }
                    }
                }
            } catch (e: Exception) {
                PLog.w(tag, "Trim memory failed", e)
            }
        }.also { it.isDaemon = true }.start()
    }

    /**
     * Releases in-memory caches under memory pressure.
     *
     * NOTE: [ApiResponseCache] exposes no memory-only clear (only
     * [ApiResponseCache.clearAll], which also wipes the disk cache) and is
     * owned by the data layer — so here we clear Coil's bitmap memory cache
     * (the dominant consumer) and purge expired API entries. A dedicated
     * ApiResponseCache.clearMemory() would make this stronger.
     */
    private fun clearMemoryCaches(context: Context) {
        try {
            Coil.imageLoader(context).memoryCache?.clear()
        } catch (e: Exception) {
            PLog.w(tag, "Failed to clear Coil memory cache", e)
        }
        try {
            ApiResponseCache.get(context).cleanupExpired()
        } catch (e: Exception) {
            PLog.w(tag, "Failed to purge expired API cache entries", e)
        }
    }

    /**
     * Returns a human-readable summary of cache + storage usage, for the
     * Settings screen.
     */
    fun storageSummary(context: Context): StorageSummary {
        val apiCache = ApiResponseCache.get(context).diskCacheSizeBytes()
        val metaCache = MetadataCache.get(context).sizeBytes()
        val downloads = DownloadManager.get(context).storageUsageBytes()
        return StorageSummary(
            apiCacheBytes = apiCache,
            metadataCacheBytes = metaCache,
            downloadsBytes = downloads,
            totalBytes = apiCache + metaCache + downloads,
        )
    }

    /** Clears all caches (NOT downloads). Called from Settings → Clear Cache. */
    fun clearCaches(context: Context) {
        ApiResponseCache.get(context).clearAll()
        MetadataCache.get(context).clearAll()
    }

    data class StorageSummary(
        val apiCacheBytes: Long,
        val metadataCacheBytes: Long,
        val downloadsBytes: Long,
        val totalBytes: Long,
    ) {
        fun formatted(): String {
            val mb = totalBytes / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }
    }

    /** Returns the available app memory in MB. */
    fun availableMemoryMb(context: Context): Float {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024f * 1024f)
    }

    /**
     * True when running on an Android TV device.
     *
     * Thin wrapper over [DeviceUtils.isTv] so engine code has a single
     * entry point for TV-specific tuning.
     */
    fun isTv(context: Context): Boolean =
        DeviceUtils.isTv(context.applicationContext)

    /**
     * True on TV devices with constrained RAM. TV boxes/sticks often share
     * 1–2 GB with the system, so these devices get the most conservative
     * tuning: smaller bitmap pool, no crossfade, and memory-cache eviction
     * at lower [onTrimMemory] pressure levels.
     */
    fun isLowEndTv(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!DeviceUtils.isTv(appContext)) return false
        return try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.isLowRamDevice) return true
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem < 2L * 1024 * 1024 * 1024
        } catch (_: Exception) {
            // If we can't tell, assume constrained — safer on TV.
            true
        }
    }

    /**
     * Bitmap memory-cache fraction for Coil: 15% on TV (RAM is shared with
     * the system and the TV UI keeps fewer screens alive), 20% on
     * phones/tablets.
     */
    fun imageMemoryCacheFraction(context: Context): Double =
        if (DeviceUtils.isTv(context.applicationContext)) 0.15 else 0.20
}
