package com.pishimovie.player

import android.content.Context
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.WatchedEpisode
import com.pishimovie.data.repository.ContinueWatchingRepository
import com.pishimovie.data.repository.StatisticsRepository
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase 5: PlaybackSession.
 *
 * Tracks the metadata for the current playback session:
 *  - Content ID, type, title, image.
 *  - Series/season/episode IDs (if applicable).
 *  - Source URL.
 *  - Saved position (updated periodically by the ViewModel).
 *
 * Handles:
 *  - Saving ContinueWatching entries (for "Resume" on the Home screen).
 *  - Recording Statistics sessions (for the Profile/Watch History screen).
 *  - Marking episodes as watched (for series episode tracking).
 *
 * No player reference — pure data + persistence.
 */
class PlaybackSession(
    private val context: Context,
    val videoUrl: String,
    val title: String?,
    val image: String?,
    val movieId: Int?,
    val seriesId: Int?,
    val seasonId: Int?,
    val episodeId: Int?,
    val resumePositionMs: Long,
) {
    private val continueWatchingRepo = ContinueWatchingRepository(context)
    private val statsRepo = StatisticsRepository(context)

    private var lastSavedPosition: Long = 0L
    private var lastSavedDuration: Long = 0L

    /**
     * Fire-and-forget scope for persistence (ContinueWatching JSON, statistics,
     * watched-episode flags). These do file I/O and must not run on the main
     * thread — savePosition() is called from viewModelScope (Main) every 5s,
     * and finalize() runs synchronously from Activity.onDestroy().
     *
     * The scope is owned by this session: once the session is dropped it
     * becomes unreachable and is GC'd (Dispatchers.IO threads are shared, so
     * no leak). In-flight writes are allowed to complete.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The content type: "movie" or "series". */
    val contentType: String get() = if (seriesId != null) "series" else "movie"

    /** The content ID (movie ID or series ID). */
    val contentId: Int get() = movieId ?: seriesId ?: 0

    /**
     * Saves the current playback position to ContinueWatching + Statistics.
     * Called periodically (every 5s) by the ViewModel.
     *
     * The file write is dispatched to Dispatchers.IO — this method itself is
     * safe to call from the main thread.
     */
    fun savePosition(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0 || durationMs <= 0) return
        lastSavedPosition = positionMs
        lastSavedDuration = durationMs

        ioScope.launch {
            try {
                val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val item = ContinueWatchingItem(
                    id = contentId,
                    type = contentType,
                    title = title ?: "",
                    image = image ?: "",
                    cover = image ?: "",
                    progress = progress,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    sourceUrl = videoUrl,
                    seasonId = seasonId,
                    episodeId = episodeId,
                )
                continueWatchingRepo.save(item)
            } catch (_: Exception) {}
        }
    }

    /** Marks the current episode as watched (for series). */
    fun markEpisodeWatched() {
        if (seriesId != null && seasonId != null && episodeId != null) {
            ioScope.launch {
                try {
                    StorageUtils.saveWatchedEpisode(
                        context,
                        WatchedEpisode(seriesId, seasonId, episodeId),
                    )
                } catch (_: Exception) {}
            }
        }
    }

    /** Records a completed watch session for the Statistics dashboard. */
    fun recordWatchSession(positionMs: Long, durationMs: Long) {
        if (title == null || contentId == 0) return
        ioScope.launch {
            try {
                statsRepo.recordWatch(
                    contentId = contentId,
                    contentType = contentType,
                    title = title,
                    image = image ?: "",
                    genres = emptyList(),
                    countries = emptyList(),
                    year = 0,
                    imdb = 0.0,
                    durationWatchedMs = positionMs,
                    totalDurationMs = durationMs,
                    startedAt = System.currentTimeMillis() - positionMs,
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Final save on playback end. An episode counts as "watched" only when it
     * was actually watched through: a valid duration and at least 90% of it
     * played. Backing out early (or an early error) only saves the position.
     */
    fun finalize(positionMs: Long, durationMs: Long) {
        savePosition(positionMs, durationMs)
        recordWatchSession(positionMs, durationMs)
        if (durationMs > 0 && positionMs >= (durationMs * 0.9).toLong()) {
            markEpisodeWatched()
        }
    }
}
