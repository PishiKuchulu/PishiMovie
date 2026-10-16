package com.pishimovie.data.repository

import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.Series
import com.pishimovie.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Finds "Similar" and "Recommended" titles for a given movie or series,
 * reusing the existing [SearchRepository] / [MovieRepository] / [SeriesRepository].
 *
 * Strategy:
 *  - Similar: search the backend for the title's first genre keyword (e.g.
 *    "Action"). This returns cross-type results (movies + series) that share
 *    the genre. We exclude the source title itself.
 *  - Recommended: search for the title's name itself — the backend often
 *    returns sequels / same-franchise entries. We exclude the source title
 *    and de-duplicate against Similar.
 *
 * NO API changes — both calls reuse [SearchRepository.search].
 */
class SimilarContentRepository {

    private val searchRepo = SearchRepository()

    /**
     * Returns similar movies + series for the given [title] and [genres].
     * The first genre's title is used as the search keyword.
     */
    suspend fun getSimilar(title: String, genres: List<Genre>): List<com.pishimovie.data.model.Poster> =
        coroutineScope {
            val keyword = genres.firstOrNull()?.title?.takeIf { it.isNotBlank() }
                ?: return@coroutineScope emptyList()
            try {
                val results = withContext(Dispatchers.IO) { searchRepo.search(keyword) }
                results.posters
                    .filter { LanguageUtils.shouldDisplayTitle(it.title) }
                    .filter { it.title != title } // exclude self
                    .take(20)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Returns recommended titles — uses the title itself as the search
     * keyword (often returns sequels, franchise entries, and same-franchise
     * spin-offs).
     */
    suspend fun getRecommended(title: String, excludeIds: Set<Int> = emptySet()): List<com.pishimovie.data.model.Poster> =
        coroutineScope {
            if (title.isBlank()) return@coroutineScope emptyList()
            try {
                val results = withContext(Dispatchers.IO) { searchRepo.search(title) }
                results.posters
                    .filter { LanguageUtils.shouldDisplayTitle(it.title) }
                    .filter { it.id !in excludeIds }
                    .filter { it.title != title }
                    .take(20)
            } catch (e: Exception) {
                emptyList()
            }
        }
}
