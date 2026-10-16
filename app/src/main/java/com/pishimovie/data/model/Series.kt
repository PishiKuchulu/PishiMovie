package com.pishimovie.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Series(
    val id: Int,
    val type: String,
    val title: String,
    val description: String,
    val year: Int,
    val imdb: Double,
    val rating: Double,
    val duration: String?,
    val image: String,
    val cover: String,
    val genres: List<Genre>,
    val country: List<Country>
) {
    // Convert Series to Movie for compatibility with existing UI components
    fun toMovie(): Movie {
        return Movie(
            id = id,
            type = type,
            title = title,
            description = description,
            year = year,
            imdb = imdb,
            rating = rating,
            duration = duration,
            image = image,
            cover = cover,
            genres = genres,
            sources = emptyList(), // Series don't have sources in this model
            country = country
        )
    }

    /** Convert this Series to a [Poster] for mixed-type rows (e.g. Anime rail). */
    fun toPoster(): Poster = Poster(
        id = id,
        title = title,
        type = type.ifEmpty { "serie" },
        description = description,
        year = year,
        imdb = imdb,
        rating = rating,
        duration = duration,
        image = image,
        cover = cover,
        genres = genres,
        sources = emptyList(),
        country = country,
    )
}

@Serializable
data class Season(
    val id: Int,
    val title: String,
    val episodes: List<Episode>
)

@Serializable
data class Episode(
    val id: Int,
    val title: String,
    val description: String,
    val duration: String?,
    val image: String,
    val sources: List<Source>
)

@Serializable
data class WatchedEpisode(
    val seriesId: Int,
    val seasonId: Int,
    val episodeId: Int,
    val watchedAt: Long = System.currentTimeMillis()
)