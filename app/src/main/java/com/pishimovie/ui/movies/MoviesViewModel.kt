package com.pishimovie.ui.movies

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Movie
import com.pishimovie.data.repository.GenreRepository
import com.pishimovie.data.repository.MovieRepository
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MoviesViewModel : ViewModel() {
    private val repository = MovieRepository()
    private val genreRepository = GenreRepository()

    private var moviesJob: Job? = null
    private var genresJob: Job? = null

    // Monotonic token identifying the newest load. A cancelled job's
    // results/finally must never touch state once a newer load has started.
    private var loadGeneration = 0

    // Page that was being loaded when the last error happened — retry()
    // must retry THIS page, not the last successful one.
    private var lastFailedPage: Int? = null

    var movies by mutableStateOf<List<Movie>>(emptyList())
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
        loadMovies()
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

    fun loadMovies(page: Int = 0) {
        val generation = ++loadGeneration
        // Set flags synchronously so loadMore is blocked immediately,
        // even before the coroutine starts.
        if (page == 0) {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        errorMessage = null

        moviesJob?.cancel()
        moviesJob = viewModelScope.launch {
            try {
                val newMovies = repository.getMovies(page, selectedGenreId, selectedFilterType)

                if (generation != loadGeneration) return@launch

                // Filter out movies with Farsi titles
                val filteredMovies = newMovies.filter { movie ->
                    LanguageUtils.shouldDisplayTitle(movie.title)
                }

                // End of data: the server returned an empty page.
                // Checked on the RAW page — client-side filtering must not
                // decide pagination, or a fully-filtered page would stop it.
                canLoadMore = newMovies.isNotEmpty()

                if (page == 0) {
                    movies = filteredMovies
                } else {
                    movies = (movies + filteredMovies).distinctBy { it.id }
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

    fun loadMoreMovies() {
        // Ignored while an initial load/refresh is in flight.
        if (!isLoading && !isLoadingMore && canLoadMore) {
            loadMovies(currentPage + 1)
        }
    }

    fun retry() {
        loadMovies(lastFailedPage ?: currentPage)
    }

    fun refresh() {
        loadMovies(0)
    }
}
