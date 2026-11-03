package com.pishimovie.ui.country

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.Country
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.CountryPostersRepository
import com.pishimovie.data.repository.CountryRepository
import com.pishimovie.data.repository.GenreRepository
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CountryViewModel : ViewModel() {
    private val postersRepository = CountryPostersRepository()
    private val countryRepository = CountryRepository()
    private val genreRepository = GenreRepository()

    private var postersJob: Job? = null
    private var countryNameJob: Job? = null
    private var genresJob: Job? = null

    // Monotonic token identifying the newest load. A cancelled job's
    // results/finally must never touch state once a newer load has started.
    private var loadGeneration = 0

    // Page that was being loaded when the last error happened — retry()
    // must retry THIS page, not the last successful one.
    private var lastFailedPage: Int? = null

    var posters by mutableStateOf<List<Poster>>(emptyList())
        private set

    var countryName by mutableStateOf<String>("")
        private set

    // Full country list (also used as the language options in the filter
    // sheet, since the language filter matches against poster countries).
    var countries by mutableStateOf<List<Country>>(emptyList())
        private set

    var genres by mutableStateOf<List<Genre>>(emptyList())
        private set

    var genreError by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentPage by mutableStateOf(0)
        private set

    var canLoadMore by mutableStateOf(true)
        private set

    var countryId by mutableStateOf<Int?>(null)
        private set

    var selectedFilterType by mutableStateOf(FilterType.DEFAULT)
        private set

    // Phase 4 / Part 4: content-type selection ("all" | "movie" | "serie").
    // The country page shows Movies / TV Series buttons first; the user picks
    // one before any content is loaded.
    var selectedContentType by mutableStateOf<String?>(null) // null = not chosen yet
        private set

    // Phase 4 / Part 5: expanded filter options (applied client-side after
    // the API returns results, since the backend only supports 3 sort modes).
    var filterMinImdb by mutableStateOf(0.0)
        private set
    var filterMinRating by mutableStateOf(0.0)
        private set
    var filterYearFrom by mutableStateOf(1900)
        private set
    var filterYearTo by mutableStateOf(2100)
        private set
    var filterGenre by mutableStateOf<String?>(null)
        private set
    var filterLanguage by mutableStateOf<String?>(null)
        private set
    var filterMaxRuntime by mutableStateOf(0) // 0 = no limit (minutes)
        private set

    // True only when the user has actually narrowed the year range via the
    // filter sheet. Mirrors SearchFilters.apply: without this, the default
    // 1900..2100 range silently dropped every poster with year = 0.
    var yearFilterNarrowed by mutableStateOf(false)
        private set

    val hasActiveExtendedFilters: Boolean
        get() = filterMinImdb > 0.0 ||
            filterMinRating > 0.0 ||
            yearFilterNarrowed ||
            filterGenre != null ||
            filterLanguage != null ||
            filterMaxRuntime > 0

    init {
        loadGenres()
    }

    private fun loadGenres() {
        genresJob?.cancel()
        genresJob = viewModelScope.launch {
            try {
                genres = genreRepository.getGenres()
                genreError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                genreError = e.message
            }
        }
    }

    fun setCountryId(id: Int) {
        if (countryId != id) {
            countryId = id
            loadCountryName()
            // Phase 5: auto-default to "movie" so content loads immediately.
            if (selectedContentType == null) {
                selectedContentType = "movie"
            }
            refresh()
        }
    }

    fun selectContentType(type: String) {
        if (selectedContentType != type) {
            selectedContentType = type
            refresh()
        }
    }

    fun selectFilterType(filterType: FilterType) {
        if (selectedFilterType != filterType) {
            selectedFilterType = filterType
            refresh()
        }
    }

    // Each extended-filter setter restarts pagination from page 1 with the
    // new criteria (the fetch-then-filter model is unchanged).
    fun setMinImdb(value: Double) { filterMinImdb = value; refresh() }
    fun setMinRating(value: Double) { filterMinRating = value; refresh() }
    fun setYearRange(from: Int, to: Int) {
        filterYearFrom = from
        filterYearTo = to
        yearFilterNarrowed = from > 1900 || to < 2100
        refresh()
    }
    fun setGenre(genre: String?) { filterGenre = genre; refresh() }
    fun setLanguage(lang: String?) { filterLanguage = lang; refresh() }
    fun setMaxRuntime(minutes: Int) { filterMaxRuntime = minutes; refresh() }

    /** Applies a whole sheet of filter values with a single reload. */
    fun applyExtendedFilters(
        minImdb: Double,
        minRating: Double,
        yearFrom: Int,
        yearTo: Int,
        genre: String?,
        language: String?,
        maxRuntime: Int
    ) {
        filterMinImdb = minImdb
        filterMinRating = minRating
        filterYearFrom = yearFrom
        filterYearTo = yearTo
        yearFilterNarrowed = yearFrom > 1900 || yearTo < 2100
        filterGenre = genre
        filterLanguage = language
        filterMaxRuntime = maxRuntime
        refresh()
    }

    fun clearExtendedFilters() {
        filterMinImdb = 0.0
        filterMinRating = 0.0
        filterYearFrom = 1900
        filterYearTo = 2100
        yearFilterNarrowed = false
        filterGenre = null
        filterLanguage = null
        filterMaxRuntime = 0
        refresh()
    }

    private fun loadCountryName() {
        countryNameJob?.cancel()
        countryNameJob = viewModelScope.launch {
            try {
                val allCountries = countryRepository.getAllCountries()
                countries = allCountries
                val country = allCountries.find { it.id == countryId }
                // Empty = not resolved yet / lookup failed; the screen
                // substitutes a localized fallback label.
                countryName = country?.title ?: ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                countryName = ""
            }
        }
    }

    fun loadPosters(page: Int = 0) {
        val currentCountryId = countryId ?: return
        val contentType = selectedContentType ?: return

        val generation = ++loadGeneration
        // Set flags synchronously so loadMore is blocked immediately,
        // even before the coroutine starts.
        if (page == 0) {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        postersJob?.cancel()
        postersJob = viewModelScope.launch {
            try {
                val newPosters = postersRepository.getPostersByCountry(currentCountryId, page, selectedFilterType)

                if (generation != loadGeneration) return@launch

                // Filter out posters with Farsi titles
                var filteredPosters = newPosters.filter { poster ->
                    LanguageUtils.shouldDisplayTitle(poster.title)
                }

                // Phase 4 / Part 4: filter by content type (movie / serie)
                filteredPosters = filteredPosters.filter { poster ->
                    when (contentType) {
                        "movie" -> poster.isMovie()
                        "serie" -> poster.isSeries()
                        else -> true
                    }
                }

                // Phase 4 / Part 5: apply expanded client-side filters
                filteredPosters = filteredPosters.filter { p ->
                    // IMDb rating filter
                    if (p.imdb < filterMinImdb) return@filter false
                    // TMDB rating filter
                    if (p.rating < filterMinRating) return@filter false
                    // Year range — only when the user actually narrowed it;
                    // with the full default range, posters with an unknown
                    // year (0) must survive like any other poster.
                    if (yearFilterNarrowed && (p.year < filterYearFrom || p.year > filterYearTo)) return@filter false
                    // Genre filter
                    if (filterGenre != null && p.genres.none { it.title.equals(filterGenre, ignoreCase = true) }) return@filter false
                    // Language filter (matched against country list)
                    if (filterLanguage != null) {
                        val countries = p.country.map { it.title.lowercase() }
                        if (countries.none { filterLanguage!!.lowercase() in it || it in filterLanguage!!.lowercase() }) {
                            return@filter false
                        }
                    }
                    // Runtime filter (parse "120 min" → 120)
                    if (filterMaxRuntime > 0 && p.duration != null) {
                        val runtimeMin = p.duration!!.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (runtimeMin > filterMaxRuntime) return@filter false
                    }
                    true
                }

                // End of data: the server returned an empty page.
                // Checked on the RAW page — client-side filtering must not
                // decide pagination, or a fully-filtered page would stop it.
                canLoadMore = newPosters.isNotEmpty()

                if (page == 0) {
                    posters = filteredPosters
                } else {
                    posters = (posters + filteredPosters).distinctBy { it.id }
                }

                currentPage = page
                lastFailedPage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    errorMessage = e.message
                    lastFailedPage = page
                }
            } finally {
                if (generation == loadGeneration) {
                    isLoading = false
                    isLoadingMore = false
                }
            }
        }
    }

    fun loadMorePosters() {
        // Ignored while an initial load/refresh is in flight.
        if (!isLoading && !isLoadingMore && canLoadMore) {
            loadPosters(currentPage + 1)
        }
    }

    fun retry() {
        loadPosters(lastFailedPage ?: currentPage)
    }

    fun refresh() {
        loadPosters(0)
    }
}
