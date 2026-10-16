package com.pishimovie.data.repository

import android.content.Context
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.PishiMood
import com.pishimovie.data.model.Poster
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * PishiMovie on-device recommendation engine (Phase 5: "AI Recommendations").
 *
 * NO external AI service. Uses existing movie metadata + the user's local
 * watch history / favorites / watchlist / ratings to score and rank candidate
 * titles fetched from the existing backend repositories.
 *
 * Scoring signals:
 *  - Genre match (highest weight — +3 per shared genre)
 *  - Country match (+1 per shared country)
 *  - IMDb tier bonus (≥7.0 → +2; ≥8.0 → +4)
 *  - User-rating affinity (titles similar to highly-rated content get +3)
 *  - Recency (newer titles → small +1 bonus)
 *  - Watchlist/favorite boost (if the candidate appears in the user's
 *    watchlist but hasn't been watched → +2)
 *  - Already-watched penalty (-5 so we don't recommend things they finished)
 */
class RecommendationEngine(private val context: Context) {

    private val movieRepo = MovieRepository()
    private val seriesRepo = SeriesRepository()
    private val searchRepo = SearchRepository()
    private val statsRepo = StatisticsRepository(context)
    private val continueWatchingRepo = ContinueWatchingRepository(context)

    /**
     * Top-level "Recommended For You" — pulls candidates from the user's
     * top-3 favorite genres, scores them, and returns the top 20.
     *
     * Falls back to trending if the user has no watch history.
     */
    suspend fun getRecommendedForYou(): List<Movie> = coroutineScope {
        val stats = statsRepo.getStatistics()
        if (stats.totalSessions == 0 && statsRepo.getWatchlist().isEmpty()) {
            // Cold start — return trending
            return@coroutineScope safeMovies { movieRepo.getMovies(0, 0, FilterType.BY_IMDB) }
        }

        // Collect favorite genres (from stats + watchlist + favorites)
        val favoriteGenres = collectFavoriteGenres()
        if (favoriteGenres.isEmpty()) {
            return@coroutineScope safeMovies { movieRepo.getMovies(0, 0, FilterType.BY_IMDB) }
        }

        // Fetch candidates from top 3 genres concurrently
        val topGenres = favoriteGenres.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val candidates = mutableListOf<Movie>()
        val deferred = topGenres.map { genreId ->
            async {
                safeMovies { movieRepo.getMovies(0, genreId, FilterType.BY_IMDB) }
            }
        }
        deferred.awaitAll().forEach { candidates.addAll(it) }

        // Score and rank
        val scored = scoreMovies(candidates.distinctBy { it.id })
        scored.take(20)
    }

    /**
     * "Because You Watched {title}" — finds titles that share genres with the
     * given [title], excluding the title itself.
     */
    suspend fun getBecauseYouWatched(title: String, genres: List<Int>): List<Poster> = coroutineScope {
        if (genres.isEmpty()) return@coroutineScope emptyList()
        try {
            // Search the backend for the first genre keyword (we don't have
            // the genre title here, so we use the movie title as a proxy).
            val results = withContext(Dispatchers.IO) { searchRepo.search(title) }
            results.posters
                .filter { LanguageUtils.shouldDisplayTitle(it.title) }
                .filter { it.title != title }
                .take(20)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * "Hidden Gems" — high-IMDb but lesser-known titles (year < 2015, IMDb ≥ 7.5).
     */
    suspend fun getHiddenGems(): List<Movie> = coroutineScope {
        val candidates = safeMovies { movieRepo.getMovies(0, 0, FilterType.BY_IMDB) }
        candidates
            .filter { it.imdb >= 7.5 && it.year < 2015 }
            .sortedByDescending { it.imdb }
            .take(20)
            .ifEmpty { candidates.filter { it.imdb >= 7.0 }.take(20) }
    }

    /**
     * "Editor's Picks" — curated across genres: top IMDb from a rotation of
     * genres. Returns 12 titles.
     */
    suspend fun getEditorsPicks(): List<Movie> = coroutineScope {
        val genreRepo = GenreRepository()
        val allGenres = try { genreRepo.getGenres() } catch (e: Exception) { emptyList() }
        if (allGenres.isEmpty()) {
            return@coroutineScope safeMovies { movieRepo.getMovies(0, 0, FilterType.BY_IMDB) }.take(12)
        }
        // Pick 6 genres, top 2 from each
        val selectedGenres = allGenres.shuffled().take(6)
        val picks = mutableListOf<Movie>()
        val deferred = selectedGenres.map { genre ->
            async {
                safeMovies { movieRepo.getMovies(0, genre.id, FilterType.BY_IMDB) }.take(2)
            }
        }
        deferred.awaitAll().forEach { picks.addAll(it) }
        picks.distinctBy { it.id }.take(12)
    }

    /**
     * "Trending Today" — most recent movies (default sort = created desc).
     */
    suspend fun getTrendingToday(): List<Movie> = coroutineScope {
        safeMovies { movieRepo.getMovies(0, 0, FilterType.DEFAULT) }.take(20)
    }

    /**
     * "Popular This Week" — top IMDb movies from this year.
     */
    suspend fun getPopularThisWeek(): List<Movie> = coroutineScope {
        val candidates = safeMovies { movieRepo.getMovies(0, 0, FilterType.BY_IMDB) }
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        candidates.filter { it.year >= currentYear - 1 }.take(20).ifEmpty { candidates.take(20) }
    }

    /**
     * Mood-based recommendations — searches the backend for each mood keyword
     * and returns a merged, deduplicated list.
     */
    suspend fun getMoodRecommendations(mood: PishiMood): List<Poster> = coroutineScope {
        val deferred = mood.keywords.map { keyword ->
            async {
                try {
                    val results = withContext(Dispatchers.IO) { searchRepo.search(keyword) }
                    results.posters.filter { LanguageUtils.shouldDisplayTitle(it.title) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        val all = deferred.awaitAll().flatten()
        all.distinctBy { "${it.type}_${it.id}" }.take(30)
    }

    // ─── Scoring ─────────────────────────────────────────────────────────

    /**
     * Scores a list of movies based on the user's preferences and history.
     * Higher score = better recommendation.
     */
    private fun scoreMovies(movies: List<Movie>): List<Movie> {
        val stats = statsRepo.getStatistics()
        val watchedIds = stats.recentlyWatched.map { it.contentId }.toSet()
        val watchlistIds = statsRepo.getWatchlist().map { it.id }.toSet()
        val favoriteGenres = collectFavoriteGenres()
        val favoriteCountries = collectFavoriteCountries()
        val highRatedContentIds = statsRepo.getAllRatings()
            .filter { it.rating >= 4 }
            .map { it.contentId }
            .toSet()

        // Build a set of genres from highly-rated content
        val highRatedGenres = stats.recentlyWatched
            .filter { it.contentId in highRatedContentIds }
            .flatMap { it.genres }
            .toSet()

        return movies.map { movie ->
            var score = 0

            // Genre match
            movie.genres.forEach { genre ->
                favoriteGenres[genre.id]?.let { weight ->
                    score += 3 * weight.coerceAtMost(3) // cap influence
                }
                if (genre.title in highRatedGenres) score += 3
            }

            // Country match
            movie.country.forEach { country ->
                favoriteCountries[country.title]?.let { weight ->
                    score += 1 * weight.coerceAtMost(2)
                }
            }

            // IMDb tier bonus
            when {
                movie.imdb >= 8.0 -> score += 4
                movie.imdb >= 7.0 -> score += 2
            }

            // Watchlist boost (want to watch but haven't)
            if (movie.id in watchlistIds && movie.id !in watchedIds) score += 2

            // Already-watched penalty
            if (movie.id in watchedIds) score -= 5

            // Recency bonus (last 3 years)
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (movie.year >= currentYear - 2) score += 1

            movie to score
        }.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Collects the user's favorite genres with weights, aggregated from:
     * watch sessions, favorites, and watchlist.
     */
    private fun collectFavoriteGenres(): Map<Int, Int> {
        val tally = mutableMapOf<Int, Int>()
        // From watch sessions
        statsRepo.getStatistics().favoriteGenres.forEach { _ ->
            // We only have genre titles in stats; skip here (genre ids needed)
        }
        // From favorites (FavoriteItem has Genre with ids)
        val favorites = com.pishimovie.utils.StorageUtils.loadAllFavorites(context)
        favorites.forEach { fav ->
            fav.genres.forEach { g ->
                tally[g.id] = (tally[g.id] ?: 0) + 2 // favorites weight = 2
            }
        }
        // From watchlist
        statsRepo.getWatchlist().forEach { item ->
            item.genres.forEach { g ->
                tally[g.id] = (tally[g.id] ?: 0) + 1 // watchlist weight = 1
            }
        }
        // From continue-watching (recently watched)
        continueWatchingRepo.getAll().forEach { cw ->
            cw.genres.forEach { g ->
                tally[g.id] = (tally[g.id] ?: 0) + 3 // watched weight = 3
            }
        }
        return tally
    }

    private fun collectFavoriteCountries(): Map<String, Int> {
        val tally = mutableMapOf<String, Int>()
        statsRepo.getStatistics().favoriteCountries.forEach { c ->
            tally[c.country] = c.count
        }
        return tally
    }

    private suspend inline fun safeMovies(crossinline block: suspend () -> List<Movie>): List<Movie> {
        return try {
            block()
                .filter { LanguageUtils.shouldDisplayTitle(it.title) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

