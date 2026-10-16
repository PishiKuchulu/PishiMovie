package com.pishimovie.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.PishiCollection
import com.pishimovie.data.model.PishiMood
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.Series
import com.pishimovie.data.repository.CountryRail
import com.pishimovie.data.repository.HomeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the PishiMovie Home screen.
 *
 * Sections are stored as nullable lists so the UI can distinguish "still
 * loading" from "loaded but empty" — enabling skeleton placeholders per row.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,

    val heroBanners: List<Movie>? = null,
    val continueWatching: List<ContinueWatchingItem>? = null,
    val becauseYouWatched: List<Poster>? = null,
    val recommendedForYou: List<Movie>? = null,
    val trendingToday: List<Movie>? = null,
    val popularThisWeek: List<Movie>? = null,
    val trending: List<Movie>? = null,
    val topRated: List<Movie>? = null,
    val newReleases: List<Movie>? = null,
    val hiddenGems: List<Movie>? = null,
    val editorsPicks: List<Movie>? = null,
    val popularSeries: List<Series>? = null,
    val featuredSeries: List<Series>? = null,
    /** Featured Anime / Animation section — mixed Movies + Series posters. */
    val animeSelection: List<Poster>? = null,
    val genres: List<Genre>? = null,
    val collections: List<PishiCollection>? = null,
    val moods: List<PishiMood>? = null,
    // Phase 1 / Part 8: country-category rails (Turkish/Korean/Indian/Japanese/Spanish)
    val countryRails: List<CountryRail>? = null,
)

/**
 * Drives the PishiMovie Home screen.
 *
 * On refresh, all sections load concurrently via `async` for fastest perceived
 * load time. Individual section failures are swallowed (section stays empty)
 * so a single network hiccup doesn't blank the whole screen.
 */
class HomeViewModel : ViewModel() {

    private lateinit var repository: HomeRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadAllJob: Job? = null

    fun initialize(context: android.content.Context) {
        if (::repository.isInitialized) return
        repository = HomeRepository(context.applicationContext)
        loadAll()
    }

    fun loadAll(refresh: Boolean = false) {
        if (!::repository.isInitialized) return
        loadAllJob?.cancel()
        loadAllJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !refresh,
                isRefreshing = refresh,
                errorMessage = null,
            )
            val state = coroutineScope {
                val heroDeferred = async { runCatching { repository.getHeroBanners() }.getOrDefault(emptyList()) }
                val trendingDeferred = async { runCatching { repository.getTrending() }.getOrDefault(emptyList()) }
                val topRatedDeferred = async { runCatching { repository.getTopRated() }.getOrDefault(emptyList()) }
                val newReleasesDeferred = async { runCatching { repository.getNewReleases() }.getOrDefault(emptyList()) }
                val popularSeriesDeferred = async { runCatching { repository.getPopularSeries() }.getOrDefault(emptyList()) }
                val featuredSeriesDeferred = async { runCatching { repository.getFeaturedSeries() }.getOrDefault(emptyList()) }
                val animeSelectionDeferred = async { runCatching { repository.getAnimeSelection() }.getOrDefault(emptyList()) }
                val genresDeferred = async { runCatching { repository.getGenres() }.getOrDefault(emptyList()) }
                val recommendedDeferred = async { runCatching { repository.getRecommendedForYou() }.getOrDefault(emptyList()) }
                val becauseYouWatchedDeferred = async { runCatching { repository.getBecauseYouWatched() }.getOrDefault(null) }
                val trendingTodayDeferred = async { runCatching { repository.getTrendingToday() }.getOrDefault(emptyList()) }
                val popularThisWeekDeferred = async { runCatching { repository.getPopularThisWeek() }.getOrDefault(emptyList()) }
                val hiddenGemsDeferred = async { runCatching { repository.getHiddenGems() }.getOrDefault(emptyList()) }
                val editorsPicksDeferred = async { runCatching { repository.getEditorsPicks() }.getOrDefault(emptyList()) }
                val countryRailsDeferred = async { runCatching { repository.getCountryRails() }.getOrDefault(emptyList()) }

                HomeUiState(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    heroBanners = heroDeferred.await(),
                    continueWatching = withContext(Dispatchers.IO) { repository.getContinueWatching() },
                    becauseYouWatched = becauseYouWatchedDeferred.await(),
                    recommendedForYou = recommendedDeferred.await(),
                    trendingToday = trendingTodayDeferred.await(),
                    popularThisWeek = popularThisWeekDeferred.await(),
                    trending = trendingDeferred.await(),
                    topRated = topRatedDeferred.await(),
                    newReleases = newReleasesDeferred.await(),
                    hiddenGems = hiddenGemsDeferred.await(),
                    editorsPicks = editorsPicksDeferred.await(),
                    popularSeries = popularSeriesDeferred.await(),
                    featuredSeries = featuredSeriesDeferred.await(),
                    animeSelection = animeSelectionDeferred.await(),
                    genres = genresDeferred.await(),
                    collections = withContext(Dispatchers.IO) { repository.getCollections() },
                    moods = withContext(Dispatchers.IO) { repository.getMoods() },
                    countryRails = countryRailsDeferred.await(),
                )
            }
            _uiState.value = state
        }
    }

    fun refresh() = loadAll(refresh = true)

    fun removeContinueWatching(id: Int, type: String) {
        if (!::repository.isInitialized) return
        repository.removeContinueWatching(id, type)
        _uiState.value = _uiState.value.copy(
            continueWatching = _uiState.value.continueWatching
                ?.filterNot { it.id == id && it.type == type }
        )
    }
}
