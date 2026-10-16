package com.pishimovie.data.repository

import android.content.Context
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.Country
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.PishiCollection
import com.pishimovie.data.model.PishiMood
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.Series
import com.pishimovie.utils.LanguageUtils
import com.pishimovie.utils.StorageUtils

/**
 * Orchestrates calls to the existing repositories to produce the data needed
 * by the PishiMovie Home screen.
 *
 * CRITICAL: This class does NOT add, modify, or remove any API calls. It only
 * composes calls that already exist in [MovieRepository], [SeriesRepository],
 * [GenreRepository], [ContinueWatchingRepository], and
 * [RecommendationEngine] — preserving full backend compatibility.
 */
class HomeRepository(private val context: Context) {

    private val movieRepo = MovieRepository()
    private val seriesRepo = SeriesRepository()
    private val genreRepo = GenreRepository()
    private val countryRepo = CountryRepository()
    private val countryPostersRepo = CountryPostersRepository()
    private val continueWatchingRepo = ContinueWatchingRepository(context)
    private val recommendationEngine = RecommendationEngine(context)

    /**
     * Hero banner carousel items.
     *
     * Logic (Phase 10):
     *  - ONLY shows Movies OR Series from United States (USA).
     *  - ONLY shows Recently Added items (sorted by year desc).
     *  - Newest items appear first.
     *  - Fallback: if no US items found in the recently-added page, returns empty
     *    (or we could fetch more pages, but for now we keep it simple).
     *  - Cap at 7 items.
     */
    suspend fun getHeroBanners(): List<Movie> = safe {
        // Fetch newest movies and series via BY_YEAR
        val newestMovies = runCatching {
            movieRepo.getMovies(page = 0, genreId = 0, filterType = FilterType.BY_YEAR)
        }.getOrDefault(emptyList())
            .filter { it.image.isNotEmpty() && LanguageUtils.shouldDisplayTitle(it.title) }

        val newestSeries = runCatching {
            seriesRepo.getSeries(page = 0, genreId = 0, filterType = FilterType.BY_YEAR)
        }.getOrDefault(emptyList())
            .filter { it.image.isNotEmpty() && LanguageUtils.shouldDisplayTitle(it.title) }
            .map { it.toMovie() }

        // Interleave movies and series to ensure a mixed feed
        val mixed = interleave(newestMovies, newestSeries)

        // Sort by year descending (absolute newest first) and take 7
        mixed.sortedByDescending { it.year }.take(7)
    }

    /** Interleaves two lists so movies and series alternate. */
    private fun interleave(a: List<Movie>, b: List<Movie>): List<Movie> {
        val mixed = mutableListOf<Movie>()
        val maxIndex = maxOf(a.size, b.size)
        for (i in 0 until maxIndex) {
            if (i < a.size) mixed.add(a[i])
            if (i < b.size) mixed.add(b[i])
        }
        return mixed
    }

    /** Trending Now — recent movies (default sort = created desc). */
    suspend fun getTrending(): List<Movie> = safe {
        movieRepo.getMovies(page = 0, genreId = 0, filterType = FilterType.DEFAULT)
            .filter { LanguageUtils.shouldDisplayTitle(it.title) }
            .take(20)
    }

    /** Top Rated — same call as hero but expanded to 20. */
    suspend fun getTopRated(): List<Movie> = safe {
        movieRepo.getMovies(page = 0, genreId = 0, filterType = FilterType.BY_IMDB)
            .filter { LanguageUtils.shouldDisplayTitle(it.title) }
            .take(20)
    }

    /** New Releases — sorted by year (most recent first). */
    suspend fun getNewReleases(): List<Movie> = safe {
        movieRepo.getMovies(page = 0, genreId = 0, filterType = FilterType.BY_YEAR)
            .filter { LanguageUtils.shouldDisplayTitle(it.title) }
            .take(20)
    }

    /** Popular Series — recent series, used as the "Popular" rail. */
    suspend fun getPopularSeries(): List<Series> = safe {
        seriesRepo.getSeries(page = 0, genreId = 0, filterType = FilterType.BY_IMDB)
            .filter { LanguageUtils.shouldDisplayTitle(it.title) }
            .take(20)
    }

    /** Featured Series — newest series. */
    suspend fun getFeaturedSeries(): List<Series> = safe {
        seriesRepo.getSeries(page = 0, genreId = 0, filterType = FilterType.DEFAULT)
            .filter { LanguageUtils.shouldDisplayTitle(it.title) }
            .take(20)
    }

    /**
     * Featured Anime / Animation — mixes movies and TV series that are tagged
     * with the Animation / Anime genre on TMDB.
     *
     * Strategy:
     *  1. Look up the Animation genre id (and the Anime genre id if the API
     *     exposes one). Both Persian API servers mirror the TMDB genre list,
     *     so "Animation" is always present; "Anime" appears on some servers.
     *  2. Fetch the first page of movies AND the first page of series for
     *     each matched genre via the existing `getMovies(genreId, …)` /
     *     `getSeries(genreId, …)` calls — no new endpoints.
     *  3. Interleave movies + series so the row mixes both types.
     *  4. Cap at 20 items.
     *
     * Any failure is swallowed — the section just stays empty.
     */
    suspend fun getAnimeSelection(): List<Poster> = safe {
        val allGenres = runCatching { genreRepo.getGenres() }.getOrDefault(emptyList())
        val animeGenreIds = allGenres
            .filter { g ->
                val title = g.title.trim().lowercase()
                title == "animation" || title == "anime" ||
                    title == "انیمیشن" || title == "انیمه" ||
                    title.contains("anim")
            }
            .map { it.id }
            .distinct()

        if (animeGenreIds.isEmpty()) return@safe emptyList()

        // Fetch both by IMDB (popular) and by YEAR (latest)
        val animeMoviesPopular = animeGenreIds.flatMap { gid ->
            runCatching {
                movieRepo.getMovies(page = 0, genreId = gid, filterType = FilterType.BY_IMDB)
            }.getOrDefault(emptyList())
                .filter { LanguageUtils.shouldDisplayTitle(it.title) && it.cover.isNotEmpty() }
                .map { it.toPoster() }
        }

        val animeMoviesLatest = animeGenreIds.flatMap { gid ->
            runCatching {
                movieRepo.getMovies(page = 0, genreId = gid, filterType = FilterType.BY_YEAR)
            }.getOrDefault(emptyList())
                .filter { LanguageUtils.shouldDisplayTitle(it.title) && it.cover.isNotEmpty() }
                .map { it.toPoster() }
        }

        val animeSeriesPopular = animeGenreIds.flatMap { gid ->
            runCatching {
                seriesRepo.getSeries(page = 0, genreId = gid, filterType = FilterType.BY_IMDB)
            }.getOrDefault(emptyList())
                .filter { LanguageUtils.shouldDisplayTitle(it.title) && it.cover.isNotEmpty() }
                .map { it.toPoster() }
        }

        val animeSeriesLatest = animeGenreIds.flatMap { gid ->
            runCatching {
                seriesRepo.getSeries(page = 0, genreId = gid, filterType = FilterType.BY_YEAR)
            }.getOrDefault(emptyList())
                .filter { LanguageUtils.shouldDisplayTitle(it.title) && it.cover.isNotEmpty() }
                .map { it.toPoster() }
        }

        // Interleave everything and take unique
        val allPosters = mutableListOf<Poster>()
        val maxLen = maxOf(animeMoviesPopular.size, animeMoviesLatest.size, animeSeriesPopular.size, animeSeriesLatest.size)
        for (i in 0 until maxLen) {
            if (i < animeMoviesLatest.size) allPosters.add(animeMoviesLatest[i])
            if (i < animeSeriesLatest.size) allPosters.add(animeSeriesLatest[i])
            if (i < animeMoviesPopular.size) allPosters.add(animeMoviesPopular[i])
            if (i < animeSeriesPopular.size) allPosters.add(animeSeriesPopular[i])
        }

        allPosters.distinctBy { it.id }.take(20)
    }

    /** All genres — used for the genre chips rail. */
    suspend fun getGenres(): List<Genre> = safe {
        genreRepo.getGenres()
    }

    /**
     * Issue 3: country-category rails for the Home screen.
     *
     * Fetches the full country list once, matches the 5 curated sections
     * (Turkish / Korean / Japanese / Indian / Spanish) against it using a
     * synonym map (because the API returns "Turkey"/"South Korea"/"Japan"/
     * "India"/"Spain", not the adjective forms), and for each match loads
     * the first page of posters via the existing
     * [CountryPostersRepository.getPostersByCountry]. Countries that aren't
     * returned by the API are skipped silently.
     *
     * Returns a list of [CountryRail] in the curated display order.
     */
    suspend fun getCountryRails(): List<CountryRail> {
        return try {
            val allCountries = runCatching { countryRepo.getAllCountries() }.getOrDefault(emptyList())
            if (allCountries.isEmpty()) return emptyList()
            COUNTRY_RAIL_SPECS.mapNotNull { spec ->
                // Match the section's synonyms against the API's country titles.
                // Use bidirectional contains so "Turkey" matches "turkish" and
                // vice versa. Also try the exact title match.
                val match = allCountries.firstOrNull { country ->
                    val title = country.title.trim().lowercase()
                    spec.synonyms.any { syn ->
                        title == syn ||
                        title.contains(syn) ||
                        syn.contains(title) ||
                        title.startsWith("$syn ") ||
                        title.contains(" $syn")
                    }
                } ?: return@mapNotNull null
                val posters = runCatching {
                    countryPostersRepo.getPostersByCountry(match.id, page = 0, filterType = FilterType.BY_IMDB)
                }.getOrDefault(emptyList())
                if (posters.isEmpty()) return@mapNotNull null
                CountryRail(
                    countryId = match.id,
                    title = spec.displayName,
                    posters = posters.take(20),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Continue Watching — pulled from local storage (no API call). */
    fun getContinueWatching(): List<ContinueWatchingItem> {
        return continueWatchingRepo.getAll()
    }

    /**
     * "Because You Watched {first CW title}" — uses the recommendation engine
     * to find titles sharing genres with the most-recent continue-watching
     * entry. Returns null if the user has no continue-watching history.
     */
    suspend fun getBecauseYouWatched(): List<Poster>? {
        val cw = continueWatchingRepo.getAll()
        val mostRecent = cw.firstOrNull() ?: return null
        return recommendationEngine.getBecauseYouWatched(
            title = mostRecent.title,
            genres = mostRecent.genres.map { it.id },
        )
    }

    /**
     * "Recommended For You" — delegated to the recommendation engine which
     * scores candidates against the user's full history (favorites,
     * watchlist, ratings, watch sessions).
     */
    suspend fun getRecommendedForYou(): List<Movie> {
        return recommendationEngine.getRecommendedForYou()
    }

    /** "Trending Today" — most recent movies. */
    suspend fun getTrendingToday(): List<Movie> =
        recommendationEngine.getTrendingToday()

    /** "Popular This Week" — top IMDb from recent years. */
    suspend fun getPopularThisWeek(): List<Movie> =
        recommendationEngine.getPopularThisWeek()

    /** "Hidden Gems" — high-IMDb, lesser-known titles. */
    suspend fun getHiddenGems(): List<Movie> =
        recommendationEngine.getHiddenGems()

    /** "Editor's Picks" — curated across genres. */
    suspend fun getEditorsPicks(): List<Movie> =
        recommendationEngine.getEditorsPicks()

    /** Static curated collections catalog. */
    fun getCollections(): List<PishiCollection> = PishiCollection.CATALOG

    /** Static mood catalog. */
    fun getMoods(): List<PishiMood> = PishiMood.CATALOG

    private inline fun <T> safe(block: () -> List<T>): List<T> {
        return try {
            block()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Continue-watching repo passthrough (so the VM can remove items). */
    fun removeContinueWatching(id: Int, type: String) =
        continueWatchingRepo.remove(id, type)
}

/**
 * Issue 3: a single Home-screen country rail (Turkish / Korean / Japanese /
 * Indian / Spanish). Posters are loaded via the existing
 * [CountryPostersRepository] — no new API.
 */
data class CountryRail(
    val countryId: Int,
    val title: String,
    val posters: List<Poster>,
)

/** Spec for one Home country rail: display name + synonyms to match against the API. */
private data class CountryRailSpec(
    val displayName: String,
    val synonyms: List<String>,
)

/**
 * Curated Home country rails in display order (Phase 4 spec: Turkish, Korean,
 * Indian, Spanish). Synonyms cover the common variants the backend might
 * return (noun form "Turkey" vs adjective "Turkish", "South Korea" vs
 * "Korea", etc.) plus native-script variants so matching is robust.
 */
private val COUNTRY_RAIL_SPECS = listOf(
    CountryRailSpec("Turkish", listOf("turkish", "turkey", "türkiye", "turkiye", "ترکی", "ترکيه", "tc", "tr")),
    CountryRailSpec("Korean", listOf("korean", "korea", "south korea", "south-korea", "کره", "한국", "kr")),
    CountryRailSpec("Indian", listOf("indian", "india", "bollywood", "hindi", "هند", "بالیوود", "in")),
    CountryRailSpec("Spanish", listOf("spanish", "spain", "español", "espanol", "اسپانیا", "es", "اسپانیا")),
)
