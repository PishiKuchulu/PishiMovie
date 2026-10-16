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
    
    var movies by mutableStateOf<List<Movie>>(emptyList())
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
    
    var genres by mutableStateOf<List<Genre>>(emptyList())
        private set
    
    var selectedGenreId by mutableStateOf(0)
        private set
    
    var selectedFilterType by mutableStateOf(FilterType.DEFAULT)
        private set
    
    init {
        loadGenres()
        loadMovies()
    }
    
    fun loadGenres() {
        genresJob?.cancel()
        genresJob = viewModelScope.launch {
            try {
                genres = genreRepository.getGenres()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
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
    
    fun loadMovies(page: Int = 0) {
        moviesJob?.cancel()
        moviesJob = viewModelScope.launch {
            try {
                if (page == 0) {
                    isLoading = true
                } else {
                    isLoadingMore = true
                }
                errorMessage = null
                
                val newMovies = repository.getMovies(page, selectedGenreId, selectedFilterType)
                
                // Filter out movies with Farsi titles
                val filteredMovies = newMovies.filter { movie ->
                    LanguageUtils.shouldDisplayTitle(movie.title)
                }
                
                // End of data: the server returned an empty page.
                // Checked on the RAW page — client-side filtering must not
                // decide pagination, or a fully-filtered page would stop it.
                // (No page-size assumption: the backend's page size is not
                // discoverable from the client; empty page == end of data,
                // same semantics as before, now filter-proof.)
                canLoadMore = newMovies.isNotEmpty()
                
                if (page == 0) {
                    movies = filteredMovies
                } else {
                    movies = (movies + filteredMovies).distinctBy { it.id }
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
    
    fun loadMoreMovies() {
        if (!isLoading && !isLoadingMore && canLoadMore) {
            loadMovies(currentPage + 1)
        }
    }
    
    fun retry() {
        loadMovies(currentPage)
    }
    
    fun refresh() {
        loadMovies(0)
    }
}