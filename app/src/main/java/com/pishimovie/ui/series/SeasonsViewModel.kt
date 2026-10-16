package com.pishimovie.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.Season
import com.pishimovie.data.repository.SeasonsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SeasonsViewModel : ViewModel() {
    private val repository = SeasonsRepository()

    private var seasonsJob: Job? = null

    var seasons by mutableStateOf<List<Season>>(emptyList())
        private set

    /** Id of the most recently requested load — guards against stale responses. */
    var currentSeriesId by mutableStateOf<Int?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun loadSeasons(seriesId: Int) {
        val requestedId = seriesId
        currentSeriesId = requestedId
        // Drop the previous series' seasons so they don't linger during the load.
        seasons = emptyList()
        seasonsJob?.cancel()
        seasonsJob = viewModelScope.launch {
            try {
                isLoading = true
                errorMessage = null

                val result = repository.getSeasons(seriesId)
                // A newer load may have started while this one was in flight;
                // only the latest request may write the seasons list.
                if (requestedId != currentSeriesId) return@launch
                seasons = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }
}