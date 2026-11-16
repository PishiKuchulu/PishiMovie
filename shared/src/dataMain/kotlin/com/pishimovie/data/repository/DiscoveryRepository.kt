package com.pishimovie.data.repository

import com.pishimovie.data.model.Country
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.PishiCollection
import com.pishimovie.data.model.Poster
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Powers the PishiMovie "Smart Discovery" surface (Phase 5):
 *  - Franchise / Movie Universe pages → reuses [PishiCollection.CATALOG]
 *  - Actor / Director / Studio pages → saved-search against the existing
 *    backend search API (no API changes)
 *  - Genre explorer → uses [GenreRepository.getGenres]
 *  - Country explorer → uses [CountryRepository.getAllCountries]
 *  - Year explorer → static curated list of decades
 *
 * All calls reuse existing repositories — zero API surface changes.
 */
class DiscoveryRepository {

    private val searchRepo = SearchRepository()
    private val genreRepo = GenreRepository()
    private val countryRepo = CountryRepository()

    /** All movie universes / franchises. */
    fun getFranchises(): List<PishiCollection> = PishiCollection.CATALOG

    /**
     * Search the backend for an actor / director / studio name and return
     * cross-type results (movies + series).
     */
    suspend fun searchPerson(name: String): List<Poster> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext emptyList()
        try {
            searchRepo.search(name).posters
                .filter { LanguageUtils.shouldDisplayTitle(it.title) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Alias for [searchPerson] — used by the Studio page. */
    suspend fun searchStudio(name: String): List<Poster> = searchPerson(name)

    /** All genres. */
    suspend fun getGenres(): List<Genre> = withContext(Dispatchers.IO) {
        try { genreRepo.getGenres() } catch (e: Exception) { emptyList() }
    }

    /** All countries. */
    suspend fun getCountries(): List<Country> = withContext(Dispatchers.IO) {
        try { countryRepo.getAllCountries() } catch (e: Exception) { emptyList() }
    }

    /**
     * Curated decade list for the Year Explorer. The user can tap a decade
     * and we filter the backend's BY_YEAR results.
     */
    fun getDecades(): List<Decade> {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentDecadeStart = (currentYear / 10) * 10
        return listOf(
            Decade("2020s", currentDecadeStart.coerceAtMost(2020), 2029),
            Decade("2010s", 2010, 2019),
            Decade("2000s", 2000, 2009),
            Decade("1990s", 1990, 1999),
            Decade("1980s", 1980, 1989),
            Decade("1970s", 1970, 1979),
            Decade("Classics", 1900, 1969),
        ).filter { it.from <= currentYear }
    }

    /**
     * Returns posters released within [from]..[to]. We fetch a few pages of
     * BY_YEAR results and filter locally (no API change).
     */
    suspend fun getByYearRange(from: Int, to: Int, maxPages: Int = 3): List<com.pishimovie.data.model.Movie> {
        val movieRepo = MovieRepository()
        val results = mutableListOf<com.pishimovie.data.model.Movie>()
        for (page in 0 until maxPages) {
            try {
                val pageResults = movieRepo.getMovies(page, 0, com.pishimovie.data.model.FilterType.BY_YEAR)
                if (pageResults.isEmpty()) break
                results.addAll(pageResults.filter { it.year in from..to })
                if (results.size >= 40) break
            } catch (e: Exception) {
                break
            }
        }
        return results
            .filter { LanguageUtils.shouldDisplayTitle(it.title) }
            .distinctBy { it.id }
            .take(40)
    }
}

data class Decade(
    val label: String,
    val from: Int,
    val to: Int,
)
