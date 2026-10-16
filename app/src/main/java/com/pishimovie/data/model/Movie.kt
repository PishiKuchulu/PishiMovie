package com.pishimovie.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Movie(
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
    val sources: List<Source>,
    val country: List<Country>
) {
    /** Convert this Movie to a [Poster] for mixed-type rows (e.g. Anime rail). */
    fun toPoster(): Poster = Poster(
        id = id,
        title = title,
        type = type.ifEmpty { "movie" },
        description = description,
        year = year,
        imdb = imdb,
        rating = rating,
        duration = duration,
        image = image,
        cover = cover,
        genres = genres,
        sources = sources,
        country = country,
    )

    /** Convert this Movie to a [Series] for compatibility. */
    fun toSeries(): Series = Series(
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
        country = country,
    )
}

@Serializable
data class Genre(
    val id: Int,
    val title: String
)

@Serializable
data class Source(
    val id: Int,
    val quality: String,
    val type: String,
    val url: String
)

@Serializable
data class Country(
    val id: Int,
    val title: String,
    val image: String
)