package com.pishimovie.download

import kotlinx.serialization.Serializable

/**
 * State of a PishiMovie download (Phase 6: Download Manager).
 */
enum class DownloadState {
    QUEUED,       // waiting to start
    DOWNLOADING,  // in progress
    PAUSED,       // user-paused
    COMPLETED,    // finished successfully
    FAILED,       // failed (retryable)
    CANCELLED,    // user-cancelled
}

/**
 * A single downloadable item.
 *
 * @param sourceUrl the stream URL to download
 * @param savePath  absolute path where the file should be saved
 * @param quality   e.g. "1080p", "720p" — for display
 */
@Serializable
data class DownloadItem(
    val id: String,                     // UUID
    val title: String,
    val type: String,                   // "movie" | "series"
    val contentId: Int,
    val posterUrl: String,
    val sourceUrl: String,
    val savePath: String,
    val quality: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val state: String = DownloadState.QUEUED.name,
    val addedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val episodeId: Int? = null,         // series only
    val seasonId: Int? = null,          // series only
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val isTerminal: Boolean
        get() = state == DownloadState.COMPLETED.name || state == DownloadState.CANCELLED.name
}
