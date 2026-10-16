package com.pishimovie.data.repository

import com.pishimovie.util.PLog
import android.content.Context
import com.pishimovie.data.model.AchievementBadge
import com.pishimovie.data.model.CountryTally
import com.pishimovie.data.model.GenreTally
import com.pishimovie.data.model.UserRating
import com.pishimovie.data.model.WatchSession
import com.pishimovie.data.model.WatchStatistics
import com.pishimovie.data.model.WatchlistItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import java.util.UUID

/**
 * Persists watch sessions, watchlist, and user ratings locally, and aggregates
 * them into [WatchStatistics] + [AchievementBadge] unlocks for the Profile
 * screen.
 *
 * No backend involvement — all local. The ContinueWatchingRepository still
 * drives "resume playback"; this repository tracks *completed* watches.
 */
class StatisticsRepository(private val context: Context) {

    private val tag = "StatisticsRepo"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sessionsFile get() = File(context.filesDir, "watch_sessions.json")
    private val watchlistFile get() = File(context.filesDir, "watchlist.json")
    private val ratingsFile get() = File(context.filesDir, "user_ratings.json")
    /** Guards read-modify-write cycles on the sessions file. */
    private val sessionsLock = Any()
    /** Guards read-modify-write cycles on the watchlist file. */
    private val watchlistLock = Any()
    /** Guards read-modify-write cycles on the ratings file. */
    private val ratingsLock = Any()

    // ─── Watch sessions ──────────────────────────────────────────────────

    fun recordSession(session: WatchSession) {
        synchronized(sessionsLock) {
            try {
                val sessions = getAllSessions().toMutableList()
                sessions.add(0, session)
                // Keep last 500 sessions to bound storage
                val trimmed = sessions.take(500)
                sessionsFile.writeText(json.encodeToString(trimmed))
            } catch (e: Exception) {
                PLog.e(tag, "Error recording session", e)
            }
        }
    }

    /**
     * Convenience: record a session from the player's live state.
     */
    fun recordWatch(
        contentId: Int,
        contentType: String,
        title: String,
        image: String,
        genres: List<String>,
        countries: List<String>,
        year: Int,
        imdb: Double,
        durationWatchedMs: Long,
        totalDurationMs: Long,
        startedAt: Long,
        finishedAt: Long = System.currentTimeMillis(),
    ): WatchSession {
        val completed = totalDurationMs > 0 &&
            (durationWatchedMs.toFloat() / totalDurationMs.toFloat()) >= 0.9f
        val session = WatchSession(
            id = UUID.randomUUID().toString(),
            contentId = contentId,
            contentType = contentType,
            title = title,
            image = image,
            genres = genres,
            countries = countries,
            year = year,
            imdb = imdb,
            durationWatchedMs = durationWatchedMs,
            totalDurationMs = totalDurationMs,
            completed = completed,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
        recordSession(session)
        return session
    }

    fun getAllSessions(): List<WatchSession> {
        return try {
            if (sessionsFile.exists()) {
                json.decodeFromString<List<WatchSession>>(sessionsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            PLog.e(tag, "Error loading sessions", e)
            emptyList()
        }
    }

    fun clearSessions() {
        try { sessionsFile.delete() } catch (e: Exception) {}
    }

    // ─── Aggregated statistics ───────────────────────────────────────────

    fun getStatistics(): WatchStatistics {
        val sessions = getAllSessions()
        if (sessions.isEmpty()) return WatchStatistics()

        val completed = sessions.filter { it.completed }
        val completedMovies = completed.count { it.contentType == "movie" }
        val completedSeries = completed.count { it.contentType == "series" }
        val totalWatchTime = sessions.sumOf { it.durationWatchedMs }

        // Genre tally
        val genreTally = mutableMapOf<String, Int>()
        sessions.forEach { s ->
            s.genres.forEach { g ->
                genreTally[g] = (genreTally[g] ?: 0) + 1
            }
        }
        val favoriteGenres = genreTally.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { GenreTally(it.key, it.value) }

        // Country tally
        val countryTally = mutableMapOf<String, Int>()
        sessions.forEach { s ->
            s.countries.forEach { c ->
                countryTally[c] = (countryTally[c] ?: 0) + 1
            }
        }
        val favoriteCountries = countryTally.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { CountryTally(it.key, it.value) }

        // Average rating
        val ratings = getAllRatings()
        val avgRating = if (ratings.isNotEmpty()) {
            ratings.map { it.rating }.average()
        } else 0.0

        // Streak: consecutive days with at least one session ending
        val streak = calculateStreak(sessions)

        return WatchStatistics(
            totalSessions = sessions.size,
            completedMovies = completedMovies,
            completedSeries = completedSeries,
            totalWatchTimeMs = totalWatchTime,
            favoriteGenres = favoriteGenres,
            favoriteCountries = favoriteCountries,
            recentlyWatched = sessions.take(20),
            averageRating = avgRating,
            streakDays = streak,
        )
    }

    private fun calculateStreak(sessions: List<WatchSession>): Int {
        if (sessions.isEmpty()) return 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // Set of days (as truncated-midnight millis) on which a session ended
        val activeDays = sessions.map { session ->
            val c = Calendar.getInstance().apply {
                timeInMillis = session.finishedAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            c.timeInMillis
        }.toSet()
        var streak = 0
        // Walk back one calendar day at a time (Calendar.add handles DST
        // transitions; fixed 24h-millis arithmetic would skip/duplicate a day).
        val cursor = today.clone() as Calendar
        while (cursor.timeInMillis in activeDays) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    // ─── Achievement badges ──────────────────────────────────────────────

    fun getAchievements(): List<AchievementBadge> {
        val stats = getStatistics()
        val sessions = getAllSessions()
        return AchievementBadge.CATALOG.map { badge ->
            when (badge.id) {
                "first_watch" -> badge.copy(unlocked = sessions.isNotEmpty())
                "binge_5" -> badge.copy(
                    unlocked = stats.totalCompleted >= 5,
                    progress = (stats.totalCompleted / 5f).coerceIn(0f, 1f),
                )
                "binge_25" -> badge.copy(
                    unlocked = stats.totalCompleted >= 25,
                    progress = (stats.totalCompleted / 25f).coerceIn(0f, 1f),
                )
                "binge_100" -> badge.copy(
                    unlocked = stats.totalCompleted >= 100,
                    progress = (stats.totalCompleted / 100f).coerceIn(0f, 1f),
                )
                "night_owl" -> {
                    val unlocked = sessions.any {
                        val hour = Calendar.getInstance().apply {
                            timeInMillis = it.finishedAt
                        }.get(Calendar.HOUR_OF_DAY)
                        hour in 0..4
                    }
                    badge.copy(unlocked = unlocked)
                }
                "genre_explorer" -> {
                    val distinctGenres = sessions.flatMap { it.genres }.distinct().size
                    badge.copy(
                        unlocked = distinctGenres >= 5,
                        progress = (distinctGenres / 5f).coerceIn(0f, 1f),
                    )
                }
                "critic" -> {
                    val ratedCount = getAllRatings().size
                    badge.copy(
                        unlocked = ratedCount >= 10,
                        progress = (ratedCount / 10f).coerceIn(0f, 1f),
                    )
                }
                "marathon" -> {
                    // 3+ hours in a single day
                    val byDay = sessions.groupBy {
                        val c = Calendar.getInstance().apply { timeInMillis = it.finishedAt }
                        c.set(Calendar.HOUR_OF_DAY, 0)
                        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        c.timeInMillis
                    }
                    val unlocked = byDay.values.any { daySessions ->
                        daySessions.sumOf { it.durationWatchedMs } >= 3 * 3_600_000L
                    }
                    badge.copy(unlocked = unlocked)
                }
                "completionist" -> badge.copy(
                    unlocked = stats.completedMovies >= 10,
                    progress = (stats.completedMovies / 10f).coerceIn(0f, 1f),
                )
                "series_binger" -> badge.copy(
                    unlocked = stats.completedSeries >= 5,
                    progress = (stats.completedSeries / 5f).coerceIn(0f, 1f),
                )
                else -> badge
            }
        }
    }

    // ─── Watchlist ───────────────────────────────────────────────────────

    fun addToWatchlist(item: WatchlistItem) {
        synchronized(watchlistLock) {
            try {
                val list = getWatchlist().toMutableList()
                list.removeAll { it.id == item.id && it.type == item.type }
                list.add(0, item)
                watchlistFile.writeText(json.encodeToString(list))
            } catch (e: Exception) {
                PLog.e(tag, "Error adding to watchlist", e)
            }
        }
    }

    fun removeFromWatchlist(id: Int, type: String) {
        synchronized(watchlistLock) {
            try {
                val list = getWatchlist().toMutableList()
                list.removeAll { it.id == id && it.type == type }
                watchlistFile.writeText(json.encodeToString(list))
            } catch (e: Exception) {
                PLog.e(tag, "Error removing from watchlist", e)
            }
        }
    }

    fun getWatchlist(): List<WatchlistItem> {
        return try {
            if (watchlistFile.exists()) {
                json.decodeFromString<List<WatchlistItem>>(watchlistFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            PLog.e(tag, "Error loading watchlist", e)
            emptyList()
        }
    }

    fun isInWatchlist(id: Int, type: String): Boolean =
        getWatchlist().any { it.id == id && it.type == type }

    // ─── User ratings ────────────────────────────────────────────────────

    fun setRating(contentId: Int, contentType: String, rating: Int) {
        synchronized(ratingsLock) {
            try {
                val ratings = getAllRatings().toMutableList()
                ratings.removeAll { it.contentId == contentId && it.contentType == contentType }
                ratings.add(0, UserRating(contentId, contentType, rating.coerceIn(1, 5)))
                ratingsFile.writeText(json.encodeToString(ratings))
            } catch (e: Exception) {
                PLog.e(tag, "Error saving rating", e)
            }
        }
    }

    fun getRating(contentId: Int, contentType: String): Int? =
        getAllRatings().find { it.contentId == contentId && it.contentType == contentType }?.rating

    fun getAllRatings(): List<UserRating> {
        return try {
            if (ratingsFile.exists()) {
                json.decodeFromString<List<UserRating>>(ratingsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            PLog.e(tag, "Error loading ratings", e)
            emptyList()
        }
    }

    fun removeRating(contentId: Int, contentType: String) {
        synchronized(ratingsLock) {
            try {
                val ratings = getAllRatings().toMutableList()
                ratings.removeAll { it.contentId == contentId && it.contentType == contentType }
                ratingsFile.writeText(json.encodeToString(ratings))
            } catch (e: Exception) {
                PLog.e(tag, "Error removing rating", e)
            }
        }
    }
}
