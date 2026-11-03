package com.pishimovie.player

import android.content.Context
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.Genre
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

    // ── Detail-cache metadata ─────────────────────────────────────────────
    // Loaded lazily (once, on the IO scope) from the cached detail file for
    // this content. Statistics tallies and the "Because You Watched" rail
    // are computed from these fields — they used to be recorded empty,
    // which left genre/country statistics permanently blank.
    private var metaLoaded = false
    private var metaGenres: List<Genre> = emptyList()
    private var metaGenreNames: List<String> = emptyList()
    private var metaCountries: List<String> = emptyList()
    private var metaYear: Int = 0
    private var metaImdb: Double = 0.0

    /** True while the current ≥95% stint has already removed the CW entry. */
    private var finishedRemovalHandled = false

    private companion object {
        /** Progress at/above which content counts as finished. */
        const val FINISHED_PROGRESS = 0.95f
        /** Minimum watched delta for a statistics session to be recorded. */
        const val MIN_STATISTICS_SESSION_MS = 30_000L
    }

    /** Must be called on the IO scope (does file I/O). */
    private fun loadMetaIfNeeded() {
        if (metaLoaded) return
        metaLoaded = true
        try {
            when {
                movieId != null -> StorageUtils.loadMovieFromFile(context, movieId)?.let { movie ->
                    metaGenres = movie.genres
                    metaGenreNames = movie.genres.map { it.title }
                    metaCountries = movie.country.map { it.title }
                    metaYear = movie.year
                    metaImdb = movie.imdb
                }
                seriesId != null -> StorageUtils.loadSeriesFromFile(context, seriesId)?.let { series ->
                    metaGenres = series.genres
                    metaGenreNames = series.genres.map { it.title }
                    metaCountries = series.country.map { it.title }
                    metaYear = series.year
                    metaImdb = series.imdb
                }
            }
        } catch (_: Exception) {}
    }

    /** Removes this content's Continue Watching entry (IO scope only). */
    private fun removeContinueWatching() {
        try {
            continueWatchingRepo.remove(contentId, contentType)
        } catch (_: Exception) {}
    }

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
                if (progress >= FINISHED_PROGRESS) {
                    // Finished content does not belong in Continue Watching:
                    // drop the entry instead of parking it at ~100%.
                    if (!finishedRemovalHandled) {
                        finishedRemovalHandled = true
                        removeContinueWatching()
                    }
                    return@launch
                }
                finishedRemovalHandled = false
                loadMetaIfNeeded()
                val item = ContinueWatchingItem(
                    id = contentId,
                    type = contentType,
                    title = title ?: "",
                    image = image ?: "",
                    cover = image ?: "",
                    year = metaYear,
                    imdb = metaImdb,
                    progress = progress,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    sourceUrl = videoUrl,
                    seasonId = seasonId,
                    episodeId = episodeId,
                    genres = metaGenres,
                )
                continueWatchingRepo.save(item)
            } catch (_: Exception) {}
        }
    }

    /**
     * Playback reached the end (STATE_ENDED): the content leaves Continue
     * Watching, and an episode is marked watched.
     */
    fun onContentEnded() {
        ioScope.launch { removeContinueWatching() }
        markEpisodeWatched()
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

    /**
     * Records a watch session for the Statistics dashboard.
     *
     * Only the part actually watched in THIS session counts: when playback
     * resumed mid-content, the absolute end position overstates the watch
     * time (and inflates total watch time / marathon badges on every
     * resume), so the recorded duration is the delta from the position
     * this session started at. Sessions shorter than
     * [MIN_STATISTICS_SESSION_MS] of actual watching are not recorded.
     */
    fun recordWatchSession(positionMs: Long, durationMs: Long) {
        if (title == null || contentId == 0) return
        val deltaMs = (positionMs - resumePositionMs).coerceAtLeast(0L)
        if (deltaMs < MIN_STATISTICS_SESSION_MS) return
        ioScope.launch {
            try {
                loadMetaIfNeeded()
                statsRepo.recordWatch(
                    contentId = contentId,
                    contentType = contentType,
                    title = title,
                    image = image ?: "",
                    genres = metaGenreNames,
                    countries = metaCountries,
                    year = metaYear,
                    imdb = metaImdb,
                    durationWatchedMs = deltaMs,
                    totalDurationMs = durationMs,
                    startedAt = System.currentTimeMillis() - deltaMs,
                    endPositionMs = positionMs,
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Final save on playback end. An episode counts as "watched" only when it
     * was actually watched through: a valid duration and at least 90% of it
     * played. Backing out early (or an early error) only saves the position.
     * Content finished to ≥95% leaves Continue Watching instead of being
     * saved at ~100%.
     */
    fun finalize(positionMs: Long, durationMs: Long) {
        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
        if (progress >= FINISHED_PROGRESS) {
            ioScope.launch { removeContinueWatching() }
        } else {
            savePosition(positionMs, durationMs)
        }
        recordWatchSession(positionMs, durationMs)
        if (durationMs > 0 && positionMs >= (durationMs * 0.9).toLong()) {
            markEpisodeWatched()
        }
    }
}
