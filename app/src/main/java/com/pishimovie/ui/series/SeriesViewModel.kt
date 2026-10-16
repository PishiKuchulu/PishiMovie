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
    
    var series by mutableStateOf<List<Series>>(emptyList())
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
        loadSeries()
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
    
    fun loadSeries(page: Int = 0) {
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            try {
                if (page == 0) {
                    isLoading = true
                } else {
                    isLoadingMore = true
                }
                errorMessage = null
                
                val newSeries = repository.getSeries(page, selectedGenreId, selectedFilterType)
                
                // Filter out series with Farsi titles
                val filteredSeries = newSeries.filter { seriesItem ->
                    LanguageUtils.shouldDisplayTitle(seriesItem.title)
                }
                
                // End of data: the server returned an empty page.
                // Checked on the RAW page — client-side filtering must not
                // decide pagination, or a fully-filtered page would stop it.
                // (No page-size assumption: the backend's page size is not
                // discoverable from the client; empty page == end of data.)
                canLoadMore = newSeries.isNotEmpty()
                
                if (page == 0) {
                    series = filteredSeries
                } else {
                    series = (series + filteredSeries).distinctBy { it.id }
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
    
    fun loadMoreSeries() {
        if (!isLoading && !isLoadingMore && canLoadMore) {
            loadSeries(currentPage + 1)
        }
    }
    
    fun retry() {
        loadSeries(currentPage)
    }
    
    fun refresh() {
        loadSeries(0)
    }
}