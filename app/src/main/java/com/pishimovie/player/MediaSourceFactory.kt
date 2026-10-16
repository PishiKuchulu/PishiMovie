package com.pishimovie.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

/**
 * Phase 5: MediaSourceFactory.
 *
 * Builds a [DefaultMediaSourceFactory] configured for:
 *  - HTTP/HTTPS with custom User-Agent, Referer, cross-protocol redirects.
 *  - A [SimpleCache] so seeks don't re-download from the network.
 *  - Local file:// and content:// URIs (offline downloads).
 *  - HLS + DASH adaptive streaming.
 *  - Progressive containers (MP4/MKV/AVI/MOV/WEBM/TS/MPEG) via the
 *    media3-extractor library (BundledExtractorsAdapter).
 *
 * No business logic — pure factory.
 */
object MediaSourceFactory {

    private const val USER_AGENT = "PishiMovie/2.0 (Android; Media3/ExoPlayer)"
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB

    private var cache: SimpleCache? = null

    /** Lazily creates a singleton SimpleCache for streaming media. */
    @Synchronized
    private fun getCache(context: Context): SimpleCache? {
        if (cache != null) return cache
        return try {
            val cacheDir = File(context.cacheDir, "pishi_media_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            SimpleCache(
                cacheDir,
                androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                androidx.media3.database.StandaloneDatabaseProvider(context),
            ).also { cache = it }
        } catch (_: Exception) {
            null
        }
    }

    /** Releases the cache (called on app shutdown). */
    fun releaseCache() {
        try { cache?.release() } catch (_: Exception) {}
        cache = null
    }

    /**
     * Builds a [DefaultMediaSourceFactory] for the given [Context].
     * The factory wraps a [CacheDataSource] around a [DefaultDataSource] so
     * that HTTP/HTTPS/file/content URIs all work and repeated seeks hit the
     * cache instead of the network.
     */
    fun create(context: Context): MediaSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://server-hi-speed-iran.info/",
                    "Accept" to "*/*",
                )
            )

        val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)

        val cache = getCache(context)
        val dataSourceFactory = if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }

        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
    }

    /**
     * Builds a [MediaItem] with MIME-type inference from the URL extension.
     * Only hints adaptive formats (HLS/DASH) — progressive containers let
     * ExoPlayer sniff via the media3-extractor library.
     */
    fun buildMediaItem(videoUrl: String, subtitleUris: List<String> = emptyList()): MediaItem {
        val builder = MediaItem.Builder().setUri(videoUrl)
        val pathOnly = videoUrl.substringBefore('?').substringBefore('#')
        val ext = pathOnly.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        val mimeType = when (ext) {
            "m3u8", "m3u" -> MimeTypes.APPLICATION_M3U8
            "mpd" -> MimeTypes.APPLICATION_MPD
            "mp4", "m4v" -> MimeTypes.VIDEO_MP4
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "webm" -> MimeTypes.VIDEO_WEBM
            "ts" -> MimeTypes.VIDEO_MP2T
            "flac" -> MimeTypes.AUDIO_FLAC
            "mp3" -> MimeTypes.AUDIO_MPEG
            "aac" -> MimeTypes.AUDIO_AAC
            else -> null
        }
        if (mimeType != null) builder.setMimeType(mimeType)

        // External subtitle configuration
        if (subtitleUris.isNotEmpty()) {
            val subtitleConfigs = subtitleUris.map { url ->
                val subExt = url.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                val subMime = when (subExt) {
                    "srt" -> MimeTypes.APPLICATION_SUBRIP
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                    .setMimeType(subMime)
                    .setLanguage("en")
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }

        return builder.build()
    }
}
