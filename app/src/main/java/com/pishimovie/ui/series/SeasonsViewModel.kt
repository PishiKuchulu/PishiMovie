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

    /**
     * Monotonically increasing load counter. Every async write (result,
     * error, loading flag) is guarded by "am I still the latest load?" —
     * comparing series ids alone is not enough, because a retry of the
     * SAME series must also supersede the previous attempt.
     */
    private var loadGeneration = 0

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
        val generation = ++loadGeneration
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
                if (generation != loadGeneration || requestedId != currentSeriesId) return@launch
                seasons = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A stale (superseded) load must not surface its error over
                // the newer request's state.
                if (generation == loadGeneration && requestedId == currentSeriesId) {
                    errorMessage = e.message
                }
            } finally {
                // Only the latest request may clear the loading flag; a
                // cancelled stale job must not switch off the spinner of the
                // load that replaced it.
                if (generation == loadGeneration && requestedId == currentSeriesId) {
                    isLoading = false
                }
            }
        }
    }
}
