package com.pishimovie.player

import android.content.Context
import com.pishimovie.VideoPlayerActivity
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.ExternalPlayer
import com.pishimovie.data.model.Genre
import com.pishimovie.data.repository.ContinueWatchingRepository
import com.pishimovie.utils.DownloadUtils
import com.pishimovie.utils.StorageUtils

/**
 * Shared utility for launching playback.
 * 
 * Logic:
 *  1. Save ContinueWatching entry.
 *  2. Check if an external player is preferred in Settings.
 *  3. Launch internal or external player accordingly.
 */
object PlayerLauncher {

    fun launchPlayback(
        context: Context,
        videoUrl: String,
        title: String,
        image: String,
        movieId: Int? = null,
        seriesId: Int? = null,
        seasonId: Int? = null,
        episodeId: Int? = null,
        resumePositionMs: Long = 0L,
        genres: List<Genre> = emptyList(),
        forceInternal: Boolean = false
    ) {
        // 1. Save ContinueWatching entry first — MERGE into any existing
        // entry so a half-watched title keeps its progress/position/duration
        // and metadata (replacing it with zeros made the Home-screen progress
        // regress on every launch).
        val repo = ContinueWatchingRepository(context)
        val contentId = movieId ?: seriesId ?: 0
        val contentType = if (seriesId != null) "series" else "movie"
        val existing = repo.get(contentId, contentType)
        val item = if (existing != null) {
            existing.copy(
                title = title,
                image = image,
                cover = image,
                sourceUrl = videoUrl,
                seasonId = seasonId,
                episodeId = episodeId,
                updatedAt = System.currentTimeMillis(),
            )
        } else {
            ContinueWatchingItem(
                id = contentId,
                type = contentType,
                title = title,
                description = "", // Not strictly needed for resume
                image = image,
                cover = image,
                year = 0,
                imdb = 0.0,
                progress = 0f,
                positionMs = resumePositionMs,
                durationMs = 0L,
                sourceUrl = videoUrl,
                genres = genres,
                seasonId = seasonId,
                episodeId = episodeId,
            )
        }
        repo.save(item)

        // 2. Check settings for preferred player
        val settings = try {
            StorageUtils.loadVideoPlayerSettings(context)
        } catch (_: Exception) {
            com.pishimovie.data.model.VideoPlayerSettings.DEFAULT
        }

        val preferred = settings.preferredExternalPlayer

        if (forceInternal || preferred == ExternalPlayer.NONE) {
            // Launch Internal Player
            VideoPlayerActivity.startCinematic(
                context = context,
                videoUrl = videoUrl,
                title = title,
                image = image,
                movieId = movieId,
                resumePositionMs = resumePositionMs,
                seriesId = seriesId,
                seasonId = seasonId,
                episodeId = episodeId,
            )
        } else {
            // Launch External Player
            when (preferred) {
                ExternalPlayer.VLC -> DownloadUtils.openWithVLC(context, videoUrl)
                ExternalPlayer.MX_PLAYER -> DownloadUtils.openWithMXPlayer(context, videoUrl)
                ExternalPlayer.KM_PLAYER -> DownloadUtils.openWithKMPlayer(context, videoUrl)
                else -> DownloadUtils.openUrl(context, videoUrl)
            }
        }
    }
}
