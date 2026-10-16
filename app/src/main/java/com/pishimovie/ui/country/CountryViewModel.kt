package com.pishimovie.ui.country

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.CountryPostersRepository
import com.pishimovie.data.repository.CountryRepository
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CountryViewModel : ViewModel() {
    private val postersRepository = CountryPostersRepository()
    private val countryRepository = CountryRepository()

    private var postersJob: Job? = null
    private var countryNameJob: Job? = null

    var posters by mutableStateOf<List<Poster>>(emptyList())
        private set

    var countryName by mutableStateOf<String>("")
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

    fun setMinImdb(value: Double) { filterMinImdb = value }
    fun setMinRating(value: Double) { filterMinRating = value }
    fun setYearRange(from: Int, to: Int) { filterYearFrom = from; filterYearTo = to }
    fun setGenre(genre: String?) { filterGenre = genre }
    fun setLanguage(lang: String?) { filterLanguage = lang }
    fun setMaxRuntime(minutes: Int) { filterMaxRuntime = minutes }

    private fun loadCountryName() {
        countryNameJob?.cancel()
        countryNameJob = viewModelScope.launch {
            try {
                val countries = countryRepository.getAllCountries()
                val country = countries.find { it.id == countryId }
                countryName = country?.title ?: "Country"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                countryName = "Country"
            }
        }
    }

    fun loadPosters(page: Int = 0) {
        val currentCountryId = countryId ?: return
        val contentType = selectedContentType ?: return

        postersJob?.cancel()
        postersJob = viewModelScope.launch {
            try {
                if (page == 0) {
                    isLoading = true
                } else {
                    isLoadingMore = true
                }
                errorMessage = null

                val newPosters = postersRepository.getPostersByCountry(currentCountryId, page, selectedFilterType)

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
                    // Year range
                    if (p.year < filterYearFrom || p.year > filterYearTo) return@filter false
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
                // (No page-size assumption: the backend's page size is not
                // discoverable from the client; empty page == end of data.)
                canLoadMore = newPosters.isNotEmpty()

                if (page == 0) {
                    posters = filteredPosters
                } else {
                    posters = (posters + filteredPosters).distinctBy { it.id }
                }

                currentPage = page
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    fun loadMorePosters() {
        if (!isLoading && !isLoadingMore && canLoadMore) {
            loadPosters(currentPage + 1)
        }
    }

    fun retry() {
        loadPosters(currentPage)
    }

    fun refresh() {
        loadPosters(0)
    }
}
