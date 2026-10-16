package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * A single entry in the user's search history. Persisted locally so the user
 * can quickly re-run previous queries.
 */
@Serializable
data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
)
