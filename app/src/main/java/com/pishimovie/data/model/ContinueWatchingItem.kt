package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a single "continue watching" entry — a movie or series episode the
 * user has partially watched.
 *
 * Persisted locally via [com.pishimovie.data.repository.ContinueWatchingRepository]
 * (no backend involvement — the existing streaming APIs are unchanged).
 */
@Serializable
data class ContinueWatchingItem(
    val id: Int,
    val type: String,            // "movie" or "series"
    val title: String,
    val description: String = "",
    val image: String,           // poster
    val cover: String = "",      // backdrop
    val year: Int = 0,
    val imdb: Double = 0.0,
    val progress: Float,         // 0f..1f
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),

    // Series-only fields
    val seasonId: Int? = null,
    val episodeId: Int? = null,
    val episodeTitle: String? = null,

    // Movie-only field (resume playback URL)
    val sourceUrl: String? = null,

    // Genre list (for "Recommended For You" inference)
    val genres: List<Genre> = emptyList(),
) {
    val isMovie: Boolean get() = type == "movie"
    val isSeries: Boolean get() = type == "series"

    /**
     * Convert to [FavoriteItem] so existing detail-screen navigation logic
     * (which expects either a Movie or Series file) keeps working unchanged.
     */
    fun toFavoriteItem(): FavoriteItem = FavoriteItem(
        id = id,
        type = type,
        title = title,
        description = description,
        year = year,
        imdb = imdb,
        rating = imdb,
        duration = null,
        image = image,
        cover = cover,
        genres = genres,
        country = emptyList(),
        sources = if (sourceUrl != null) listOf(Source(id = 0, quality = "auto", type = "mp4", url = sourceUrl)) else emptyList(),
    )
}
