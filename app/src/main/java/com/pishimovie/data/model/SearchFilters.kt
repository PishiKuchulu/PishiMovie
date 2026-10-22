package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * State of the user's search filters. Applied locally to backend search
 * results (no API changes).
 */
@Serializable
data class SearchFilters(
    val genres: Set<Int> = emptySet(),          // selected genre ids
    val yearFrom: Int = 1900,
    val yearTo: Int = 2100,
    val minRating: Float = 0f,                   // 0..10
    val language: String? = null,                // null = any
    val typeFilter: TypeFilter = TypeFilter.ALL,
) {
    enum class TypeFilter { ALL, MOVIES_ONLY, SERIES_ONLY }

    val isActive: Boolean
        get() = genres.isNotEmpty() ||
            yearFrom > 1900 ||
            yearTo < 2100 ||
            minRating > 0f ||
            language != null ||
            typeFilter != TypeFilter.ALL

    /** Apply the filters to a list of [Poster] search results. */
    fun apply(posters: List<Poster>, allGenres: List<Genre>): List<Poster> {
        return posters.filter { p ->
            // Type filter
            if (typeFilter != TypeFilter.ALL) {
                when (typeFilter) {
                    TypeFilter.MOVIES_ONLY -> if (!p.isMovie()) return@filter false
                    TypeFilter.SERIES_ONLY -> if (!p.isSeries()) return@filter false
                    else -> {}
                }
            }
            // Year range — only when the user actually narrowed it.
            // (Previously this ran unconditionally, so posters with an
            // unknown year — backend default 0 — vanished whenever ANY
            // filter was applied.)
            if (yearFrom > 1900 || yearTo < 2100) {
                if (p.year < yearFrom || p.year > yearTo) return@filter false
            }
            // Min rating
            if (p.imdb.toFloat() < minRating) return@filter false
            // Genre filter (poster must contain ALL selected genres)
            if (genres.isNotEmpty()) {
                val posterGenreIds = p.genres.map { it.id }.toSet()
                if (genres.any { it !in posterGenreIds }) return@filter false
            }
            // Language filter (matched against country list for now, since the
            // backend doesn't expose audio language)
            if (language != null) {
                val countries = p.country.map { it.title.lowercase() }
                if (countries.none { language.lowercase() in it || it in language.lowercase() }) {
                    return@filter false
                }
            }
            true
        }
    }
}
