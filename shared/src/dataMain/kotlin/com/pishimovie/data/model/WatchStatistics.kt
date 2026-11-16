package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * Tracks a single completed watch session. Persisted locally and aggregated
 * by [com.pishimovie.data.repository.StatisticsRepository] to produce the
 * user's watch statistics (Phase 5: Profile / Watch Statistics).
 *
 * One entry is created each time the user finishes (or gets >90% through) a
 * movie or episode.
 */
@Serializable
data class WatchSession(
    val id: String,                      // UUID-ish
    val contentId: Int,
    val contentType: String,             // "movie" | "series"
    val title: String,
    val image: String,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val year: Int = 0,
    val imdb: Double = 0.0,
    val durationWatchedMs: Long,         // actual watch time
    val totalDurationMs: Long,           // content duration
    val completed: Boolean,              // ≥90% watched
    val userRating: Int? = null,         // 1..5 (optional)
    val startedAt: Long,
    val finishedAt: Long,
)

/**
 * Aggregated watch statistics for the Profile screen.
 */
@Serializable
data class WatchStatistics(
    val totalSessions: Int = 0,
    val completedMovies: Int = 0,
    val completedSeries: Int = 0,
    val totalWatchTimeMs: Long = 0L,
    val favoriteGenres: List<GenreTally> = emptyList(),
    val favoriteCountries: List<CountryTally> = emptyList(),
    val recentlyWatched: List<WatchSession> = emptyList(),
    val averageRating: Double = 0.0,
    val streakDays: Int = 0,
) {
    val totalWatchTimeHours: Float get() = totalWatchTimeMs / 3_600_000f
    val totalCompleted: Int get() = completedMovies + completedSeries
}

@Serializable
data class GenreTally(
    val genre: String,
    val count: Int,
)

@Serializable
data class CountryTally(
    val country: String,
    val count: Int,
)
