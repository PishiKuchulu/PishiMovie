package com.pishimovie.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.data.model.VideoPlayerSettings

/**
 * Phase 5: PlayerManager.
 *
 * Owns the [ExoPlayer] instance and its [MediaSession]. All player lifecycle
 * operations go through here — no Activity or Composable creates or releases
 * ExoPlayer directly.
 *
 * Responsibilities:
 *  - Create / configure / release ExoPlayer.
 *  - Wire [MediaSourceFactory] + [DefaultTrackSelector] + [LoadControl].
 *  - Manage a [MediaSession] for Android TV / notifications / BT controls.
 *  - Apply [VideoPlayerSettings] (seek time, default speed, audio attributes).
 *
 * Note: subtitle appearance and track selection are owned by the
 * [PlayerViewModel]'s [SubtitleManager]/[TrackManager] instances (the ones
 * actually bound to the PlayerView). PlayerManager keeps no instances of its
 * own — an earlier revision had dead instances here that were never rendered.
 *
 * Thread-safety: all methods must be called on the main thread (ExoPlayer
 * requirement). The Activity/ViewModel is responsible for this.
 */
class PlayerManager(private val context: Context) {

    var player: ExoPlayer? = null
        private set

    var trackSelector: DefaultTrackSelector? = null
        private set

    var mediaSession: MediaSession? = null
        private set

    private var currentVideoUrl: String? = null
    private var currentResumePosition: Long = 0L

    /**
     * Creates and configures a new [ExoPlayer] for the given URL.
     * If a previous player exists, it is released first.
     * All exceptions are caught — this method never crashes the app.
     */
    fun preparePlayer(
        videoUrl: String,
        resumePositionMs: Long = 0L,
        videoPlayerSettings: VideoPlayerSettings,
        subtitleSettings: SubtitleSettings,
    ) {
        releasePlayer()

        try {
            val selector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setForceLowestBitrate(false)
                        .setForceHighestSupportedBitrate(true)
                        .setExceedRendererCapabilitiesIfNecessary(false)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                )
            }
            trackSelector = selector

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val mediaItem = MediaSourceFactory.buildMediaItem(videoUrl)

            val exoPlayer = ExoPlayer.Builder(context)
                .setTrackSelector(selector)
                .setMediaSourceFactory(MediaSourceFactory.create(context))
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_OFF
            exoPlayer.playWhenReady = true
            exoPlayer.playbackParameters = PlaybackParameters(videoPlayerSettings.defaultPlaybackSpeed)

            if (resumePositionMs > 0) {
                exoPlayer.seekTo(resumePositionMs)
            }

            exoPlayer.prepare()

            player = exoPlayer
            currentVideoUrl = videoUrl
            currentResumePosition = resumePositionMs

            createMediaSession(exoPlayer)
        } catch (_: Exception) {
            // If player creation fails, leave player as null — the UI will
            // show an error overlay instead of crashing.
        }
    }

    /** Creates a [MediaSession] so external controllers (TV remote, BT, lock screen) can control playback. */
    private fun createMediaSession(exoPlayer: ExoPlayer) {
        try {
            mediaSession = MediaSession.Builder(context, exoPlayer)
                .setId("PishiMovie_${System.currentTimeMillis()}")
                .build()
        } catch (_: Exception) {
            // MediaSession is optional — playback still works without it.
        }
    }

    /** Retries playback from the given position (used after an error). */
    fun retry(positionMs: Long) {
        val url = currentVideoUrl ?: return
        val p = player ?: return
        try {
            val mediaItem = MediaSourceFactory.buildMediaItem(url)
            p.setMediaItem(mediaItem)
            p.prepare()
            p.seekTo(positionMs)
            p.playWhenReady = true
        } catch (_: Exception) {}
    }

    /** Releases the player + MediaSession. Must be called in onDestroy. */
    fun releasePlayer() {
        try { mediaSession?.release() } catch (_: Exception) {}
        mediaSession = null
        try { player?.release() } catch (_: Exception) {}
        player = null
        trackSelector = null
    }

    companion object {
        fun get(context: Context): PlayerManager = PlayerManager(context.applicationContext)
    }
}
