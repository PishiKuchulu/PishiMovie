package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * A watchlist entry — distinct from favorites. Favorites = "I love this";
 * Watchlist = "I want to watch this later". Both power the recommendation
 * engine but with different weights.
 */
@Serializable
data class WatchlistItem(
    val id: Int,
    val type: String,            // "movie" | "series"
    val title: String,
    val description: String = "",
    val image: String,
    val cover: String = "",
    val year: Int = 0,
    val imdb: Double = 0.0,
    val genres: List<Genre> = emptyList(),
    val country: List<Country> = emptyList(),
    val sources: List<Source> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * A user rating for a piece of content (1..5 stars).
 */
@Serializable
data class UserRating(
    val contentId: Int,
    val contentType: String,     // "movie" | "series"
    val rating: Int,             // 1..5
    val ratedAt: Long = System.currentTimeMillis(),
)
