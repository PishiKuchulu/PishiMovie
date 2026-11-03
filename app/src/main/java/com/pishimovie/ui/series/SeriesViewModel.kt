package com.pishimovie.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Series
import com.pishimovie.data.repository.GenreRepository
import com.pishimovie.data.repository.SeriesRepository
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SeriesViewModel : ViewModel() {
    private val repository = SeriesRepository()
    private val genreRepository = GenreRepository()

    private var seriesJob: Job? = null
    private var genresJob: Job? = null

    // Monotonic token identifying the newest load. A cancelled job's
    // results/finally must never touch state once a newer load has started.
    private var loadGeneration = 0

    // Page that was being loaded when the last error happened — retry()
    // must retry THIS page, not the last successful one.
    private var lastFailedPage: Int? = null

    var series by mutableStateOf<List<Series>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Genre-list failures are tracked separately so they never surface as
    // a page-level error.
    var genreError by mutableStateOf<String?>(null)
        private set

    var currentPage by mutableStateOf(0)
        private set

    var canLoadMore by mutableStateOf(true)
        private set

    var genres by mutableStateOf<List<Genre>>(emptyList())
        private set

    var selectedGenreId by mutableStateOf(0)
        private set

    var selectedFilterType by mutableStateOf(FilterType.DEFAULT)
        private set

    val hasActiveFilters: Boolean
        get() = selectedGenreId != 0 || selectedFilterType != FilterType.DEFAULT

    init {
        loadGenres()
        loadSeries()
    }

    fun loadGenres() {
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

    fun selectGenre(genreId: Int) {
        selectedGenreId = genreId
        refresh()
    }

    fun selectFilterType(filterType: FilterType) {
        selectedFilterType = filterType
        refresh()
    }

    fun clearFilters() {
        selectedGenreId = 0
        selectedFilterType = FilterType.DEFAULT
        refresh()
    }

    fun loadSeries(page: Int = 0) {
        val generation = ++loadGeneration
        // Set flags synchronously so loadMore is blocked immediately,
        // even before the coroutine starts.
        if (page == 0) {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            try {
                val newSeries = repository.getSeries(page, selectedGenreId, selectedFilterType)

                if (generation != loadGeneration) return@launch

                // Filter out series with Farsi titles
                val filteredSeries = newSeries.filter { seriesItem ->
                    LanguageUtils.shouldDisplayTitle(seriesItem.title)
                }

                // End of data: the server returned an empty page.
                // Checked on the RAW page — client-side filtering must not
                // decide pagination, or a fully-filtered page would stop it.
                canLoadMore = newSeries.isNotEmpty()

                if (page == 0) {
                    series = filteredSeries
                } else {
                    series = (series + filteredSeries).distinctBy { it.id }
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

    fun loadMoreSeries() {
        // Ignored while an initial load/refresh is in flight.
        if (!isLoading && !isLoadingMore && canLoadMore) {
            loadSeries(currentPage + 1)
        }
    }

    fun retry() {
        loadSeries(lastFailedPage ?: currentPage)
    }

    fun refresh() {
        loadSeries(0)
    }
}
