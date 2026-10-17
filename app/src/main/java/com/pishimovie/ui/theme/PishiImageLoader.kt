package com.pishimovie.ui.theme

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.pishimovie.util.PerformanceOptimizer
import com.pishimovie.utils.DeviceUtils
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * PishiMovie Coil ImageLoader configuration (Phase 6: "Smarter image cache").
 *
 * Optimizations over the baseline:
 *  - Memory cache: 20% of available app memory on phones/tablets, 15% on TV
 *    (TVs share 1–2 GB RAM with the system — see
 *    [PerformanceOptimizer.imageMemoryCacheFraction]).
 *  - Disk cache: 384 MB (up from 256 MB) — enough for ~2000 posters.
 *  - OkHttp connection pool: 5 idle connections, 5-minute keep-alive (reduces
 *    TLS handshake overhead for repeated poster loads).
 *  - Crossfade (200ms) on phones/tablets; DISABLED on TV — the fade forces
 *    an extra GPU-composited layer per image and stutters on weak TV chipsets.
 *  - Hardware bitmaps enabled on API 28+ (saves heap on OLED devices).
 *  - Both memory + disk policies enabled → previously-loaded posters are
 *    available offline (Phase 6: "Cached posters").
 */
object PishiImageLoader : ImageLoaderFactory {

    private const val DISK_CACHE_BYTES: Long = 384L * 1024 * 1024 // 384 MB
    // Memory-cache fraction now comes from PerformanceOptimizer
    // (TV-aware: 15% on TV, 20% elsewhere).

    override fun newImageLoader(): ImageLoader {
        val appContext = PishiContextHolder.context
        // TV chipsets (often older GPUs driving 4K with 1–2 GB of shared RAM)
        // stutter on crossfade compositing — disable the fade on TV, keep the
        // 200ms fade on phones/tablets.
        val isTv = DeviceUtils.isTv(appContext)
        return ImageLoader.Builder(PishiContextHolder.context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .build()
            }
            .memoryCache {
                // NOTE: Coil 2.7.0's MemoryCache.Builder REQUIRES a Context
                // (the no-arg form only exists in Coil 3) — verified against
                // the real compiler; do not "fix" this to no-arg.
                MemoryCache.Builder(PishiContextHolder.context)
                    .maxSizePercent(PerformanceOptimizer.imageMemoryCacheFraction(appContext))
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("pishi_image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .apply {
                // Crossfade is disabled by default in Coil; enable the 200ms
                // fade only off-TV.
                if (!isTv) {
                    crossfade(true)
                    crossfade(200)
                }
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // Allow hardware bitmaps on Pie+ (saves RAM on OLED devices)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .allowRgb565(true) // Use RGB_565 for thumbnails to halve memory
            .build()
    }
}

/** Holds the application context so the ImageLoader can be built lazily. */
object PishiContextHolder {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        if (!::context.isInitialized) {
            this.context = context.applicationContext
        }
    }
}
