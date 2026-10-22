package com.pishimovie.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pishimovie.data.model.Country
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.SearchFilters
import com.pishimovie.data.model.SearchHistoryEntry
import com.pishimovie.data.repository.CountryRepository
import com.pishimovie.data.repository.GenreRepository
import com.pishimovie.data.repository.SearchHistoryRepository
import com.pishimovie.data.repository.SearchRepository
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the new PishiMovie search experience.
 */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<Poster> = emptyList(),
    /** Last fetched results before user filters/ranking — filter changes
     *  re-apply locally to this list instead of re-hitting the network. */
    val rawResults: List<Poster> = emptyList(),
    val suggestions: List<Poster> = emptyList(),   // live suggestions while typing
    val history: List<SearchHistoryEntry> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    val genres: List<Genre> = emptyList(),
    val countries: List<Country> = emptyList(),
    val errorMessage: String? = null,
) {
    /** True when the filter sheet should show a badge on its trigger chip. */
    val hasActiveFilters: Boolean get() = filters.isActive
}

/**
 * Drives the PishiMovie search screen.
 *
 * Behaviors:
 *  - Instant search: debounced 450ms after the user stops typing.
 *  - Live suggestions: lighter debounce (200ms) limited to 5 results, shown
 *    in a dropdown while the query is non-empty. Suggestions and the full
 *    search share ONE fetch per query (see fetchRaw) — no duplicate calls.
 *  - Search history: persisted via [SearchHistoryRepository]; added on every
 *    committed search (Enter / explicit submit).
 *  - Filters: applied locally to the backend results (no API change).
 */
class SearchViewModel : ViewModel() {

    private val searchRepo = SearchRepository()
    private val countryRepo = CountryRepository()
    private val genreRepo = GenreRepository()
    private var historyRepo: SearchHistoryRepository? = null
    private var appContext: Context? = null

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null
    private var searchJob: Job? = null

    /** The query [SearchUiState.rawResults] belongs to + cache validity. */
    private var lastRawQuery: String? = null
    private var rawCacheValid: Boolean = false
    private var referenceDataLoaded: Boolean = false

    /** Must be called once when the screen composes — passes the app context. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (historyRepo == null) {
            val repo = SearchHistoryRepository(context.applicationContext)
            historyRepo = repo
            // History file read off the main thread.
            viewModelScope.launch {
                val history = withContext(Dispatchers.IO) { repo.getAll() }
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
        loadGenresAndCountries(context)
    }

    private fun loadGenresAndCountries(context: Context) {
        if (referenceDataLoaded) return
        referenceDataLoaded = true
        viewModelScope.launch {
            val genres = runCatching { genreRepo.getGenres() }.getOrDefault(emptyList())
            val countries = runCatching { countryRepo.getAllCountries() }.getOrDefault(emptyList())
            if (genres.isEmpty() && countries.isEmpty()) referenceDataLoaded = false
            _uiState.value = _uiState.value.copy(genres = genres, countries = countries)
        }
    }

    /**
     * The display-filtered backend results for [query], fetched at most
     * once per query: whichever job (suggestions / full search) runs first
     * populates the cache, the other reuses it.
     */
    private suspend fun fetchRaw(query: String): List<Poster> {
        if (rawCacheValid && query == lastRawQuery) {
            return _uiState.value.rawResults
        }
        val ctx = appContext
        val raw = if (ctx != null) searchRepo.searchWithCache(ctx, query)
                  else searchRepo.search(query)
        val base = raw.posters.filter { LanguageUtils.shouldDisplayTitle(it.title) }
        lastRawQuery = query
        rawCacheValid = true
        _uiState.value = _uiState.value.copy(rawResults = base)
        return base
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query, errorMessage = null)
        if (query.isBlank()) {
            suggestionJob?.cancel()
            searchJob?.cancel()
            lastRawQuery = null
            rawCacheValid = false
            _uiState.value = _uiState.value.copy(
                suggestions = emptyList(),
                results = emptyList(),
                rawResults = emptyList(),
                isSearching = false,
                hasSearched = false,
            )
            return
        }
        // Suggestions — light debounce (fetch is shared with the full search)
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(200)
            runCatching {
                fetchRaw(query).take(5)
            }.onSuccess { suggestions ->
                _uiState.value = _uiState.value.copy(suggestions = suggestions)
            }
        }
        // Instant search — heavier debounce
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(450)
            performSearch(query, commitHistory = false)
        }
    }

    /** User pressed Enter / search icon — commit the query to history. */
    fun submitSearch() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        suggestionJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(q, commitHistory = true)
        }
    }

    private suspend fun performSearch(query: String, commitHistory: Boolean) {
        _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
        try {
            val base = fetchRaw(query)
            // Apply user filters first
            val filtered = _uiState.value.filters.apply(base, _uiState.value.genres)
            // Better ranking: score each result by query relevance
            val ranked = rankResults(filtered, query)
            _uiState.value = _uiState.value.copy(
                results = ranked,
                suggestions = emptyList(),
                isSearching = false,
                hasSearched = true,
            )
            if (commitHistory) {
                val repo = historyRepo
                if (repo != null) {
                    // History file write off the main thread.
                    val updated = withContext(Dispatchers.IO) { repo.add(query); repo.getAll() }
                    _uiState.value = _uiState.value.copy(history = updated)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                errorMessage = e.message ?: "Search failed",
            )
        }
    }

    /**
     * Ranks search results by relevance to the query (Phase 5: Better Ranking).
     *
     * Scoring signals:
     *  - Exact title match → +100
     *  - Title starts with query → +50
     *  - Title contains query → +30
     *  - Genre contains query → +10
     *  - IMDb ≥ 7.0 → +5; ≥ 8.0 → +10
     *  - Year recency → +2 (last 3 years)
     */
    private fun rankResults(posters: List<Poster>, query: String): List<Poster> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return posters
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return posters.map { poster ->
            var score = 0
            val titleLower = poster.title.lowercase()
            when {
                titleLower == q -> score += 100
                titleLower.startsWith(q) -> score += 50
                titleLower.contains(q) -> score += 30
            }
            poster.genres.forEach { genre ->
                if (genre.title.lowercase().contains(q)) score += 10
            }
            when {
                poster.imdb >= 8.0 -> score += 10
                poster.imdb >= 7.0 -> score += 5
            }
            if (poster.year >= currentYear - 2) score += 2
            poster to score
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun selectSuggestion(poster: Poster) {
        // Navigate directly to detail — clear suggestions, and record the
        // pick in history like a committed search.
        _uiState.value = _uiState.value.copy(suggestions = emptyList())
        val repo = historyRepo ?: return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repo.add(poster.title); repo.getAll() }
            _uiState.value = _uiState.value.copy(history = updated)
        }
    }

    fun clearQuery() {
        suggestionJob?.cancel()
        searchJob?.cancel()
        lastRawQuery = null
        rawCacheValid = false
        _uiState.value = _uiState.value.copy(
            query = "",
            suggestions = emptyList(),
            results = emptyList(),
            rawResults = emptyList(),
            hasSearched = false,
            errorMessage = null,
        )
    }

    fun removeHistoryEntry(query: String) {
        val repo = historyRepo ?: return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repo.remove(query); repo.getAll() }
            _uiState.value = _uiState.value.copy(history = updated)
        }
    }

    fun clearHistory() {
        val repo = historyRepo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearAll() }
            _uiState.value = _uiState.value.copy(history = emptyList())
        }
    }

    fun selectHistoryEntry(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        submitSearch()
    }

    fun updateFilters(filters: SearchFilters) {
        _uiState.value = _uiState.value.copy(filters = filters)
        // Re-apply filters LOCALLY to the last fetched raw results — no
        // network round-trip (works offline too). Only when we have never
        // fetched for the current query do we fall back to a real search.
        if (_uiState.value.hasSearched) {
            val q = _uiState.value.query
            if (q.isNotBlank()) {
                if (rawCacheValid && q == lastRawQuery) {
                    val filtered = filters.apply(_uiState.value.rawResults, _uiState.value.genres)
                    _uiState.value = _uiState.value.copy(results = rankResults(filtered, q))
                } else {
                    searchJob?.cancel()
                    searchJob = viewModelScope.launch {
                        performSearch(q, commitHistory = false)
                    }
                }
            }
        }
    }

    fun resetFilters() = updateFilters(SearchFilters())
}
