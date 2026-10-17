package com.pishimovie.ui.theme

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * PishiMovie Coil ImageLoader configuration (Phase 6: "Smarter image cache").
 *
 * Optimizations over the baseline:
 *  - Memory cache: 20% of available app memory (slightly reduced to leave
 *    headroom for the API response cache + Compose's own bitmap pool).
 *  - Disk cache: 384 MB (up from 256 MB) — enough for ~2000 posters.
 *  - OkHttp connection pool: 5 idle connections, 5-minute keep-alive (reduces
 *    TLS handshake overhead for repeated poster loads).
 *  - Crossfade duration reduced to 200ms (from 300ms) for snappier feels.
 *  - Hardware bitmaps enabled on API 28+ (saves heap on OLED devices).
 *  - Both memory + disk policies enabled → previously-loaded posters are
 *    available offline (Phase 6: "Cached posters").
 */
object PishiImageLoader : ImageLoaderFactory {

    private const val DISK_CACHE_BYTES: Long = 384L * 1024 * 1024 // 384 MB
    private const val MEMORY_CACHE_FRACTION: Double = 0.20

    override fun newImageLoader(): ImageLoader {
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
                    .maxSizePercent(MEMORY_CACHE_FRACTION)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(PishiContextHolder.context.cacheDir.resolve("pishi_image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .crossfade(200)
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
