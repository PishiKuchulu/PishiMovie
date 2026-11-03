package com.pishimovie.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.pishimovie.R
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.data.model.VideoPlayerSettings
import com.pishimovie.utils.StorageUtils
import com.pishimovie.utils.SubtitleSettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 5: PlayerViewModel.
 *
 * Bridges [PlayerManager] (player lifecycle) with the Compose UI.
 * Exposes observable state (position, duration, isPlaying, error, tracks)
 * via [StateFlow]s so the Composable can react without holding a direct
 * reference to ExoPlayer.
 *
 * Responsibilities:
 *  - Create / prepare / release the player via [PlayerManager].
 *  - Poll player position every 500ms (for the seek bar + position save).
 *  - Save ContinueWatching every 5s via [PlaybackSession].
 *  - Surface errors via [playerError] state (localized category messages —
 *    raw exception text is never shown to the user).
 *  - Expose track info via [TrackManager].
 *  - Apply subtitle settings to the PlayerView via [SubtitleManager].
 *
 * No UI code — pure state + player orchestration.
 */
class PlayerViewModel : ViewModel() {

    // ── Observable state ──────────────────────────────────────────────────

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    /**
     * Whether the quick "+30s" forward chip is offered: shown early in the
     * video (3s–60s) as a plain seek shortcut.
     */
    private val _showForwardChip = MutableStateFlow(false)
    val showForwardChip: StateFlow<Boolean> = _showForwardChip.asStateFlow()

    /** Current quality mode, kept in sync with the track selector. */
    private val _qualityMode = MutableStateFlow(QualityMode.AUTO)
    val qualityMode: StateFlow<QualityMode> = _qualityMode.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────

    var playerManager: PlayerManager? = null
        private set
    private var session: PlaybackSession? = null
    private var positionJob: Job? = null
    private var saveJob: Job? = null
    private var retryJob: Job? = null

    /** Auto-retry budget: at most 2 automatic retries per playback incident. */
    private var autoRetryCount = 0
    private companion object {
        const val MAX_AUTO_RETRIES = 2
        const val AUTO_RETRY_BASE_DELAY_MS = 3000L
    }

    var trackManager = TrackManager()
        private set
    var subtitleManager = SubtitleManager()
        private set

    /** The ExoPlayer instance (null if not prepared). */
    val player: ExoPlayer? get() = playerManager?.player

    // ── Prepare bookkeeping ───────────────────────────────────────────────

    /** Arguments of the most recent [prepare] call (for retry after a prepare failure). */
    private data class PrepareArgs(
        val videoUrl: String,
        val title: String?,
        val image: String?,
        val movieId: Int?,
        val seriesId: Int?,
        val seasonId: Int?,
        val episodeId: Int?,
        val resumePositionMs: Long,
    )

    private var appContext: Context? = null
    private var prepareArgs: PrepareArgs? = null
    private var currentVideoUrl: String? = null

    /**
     * The resume position the current session effectively started from
     * (after the remember-last-position gate / recreation restore). Used as
     * the retry target until playback first becomes ready.
     */
    private var sessionResumePositionMs: Long = 0L

    /** True once the current prepare reached STATE_READY at least once. */
    private var hasPlaybackReady = false

    /**
     * Last known playback position + its URL. Survives [release] (which an
     * Activity recreation triggers via onDestroy) so a re-prepare of the
     * same video resumes where playback actually stopped instead of the
     * stale position baked into the original Intent.
     */
    private var lastPositionMs: Long = 0L
    private var lastVideoUrl: String? = null

    /**
     * Saved per-video track selection waiting to be re-applied once the
     * player's tracks are known (see SubtitlePerVideo.selectedTrackKey).
     */
    private var pendingTrackKey: String? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Creates and prepares the player. Called from the Activity's onCreate.
     */
    fun prepare(
        context: Context,
        videoUrl: String,
        title: String?,
        image: String?,
        movieId: Int?,
        seriesId: Int?,
        seasonId: Int?,
        episodeId: Int?,
        resumePositionMs: Long,
    ) {
        val appCtx = context.applicationContext
        appContext = appCtx
        currentVideoUrl = videoUrl
        prepareArgs = PrepareArgs(
            videoUrl, title, image, movieId, seriesId, seasonId, episodeId, resumePositionMs,
        )

        // Release any player left over from a previous prepare (e.g. an
        // Activity recreation where onDestroy's release was skipped or a
        // second prepare on the same ViewModel) before creating a new one —
        // otherwise the old ExoPlayer/MediaSession leaks and keeps playing.
        positionJob?.cancel()
        saveJob?.cancel()
        playerManager?.releasePlayer()
        playerManager = null

        // Fresh playback session → fresh auto-retry budget.
        retryJob?.cancel()
        autoRetryCount = 0
        hasPlaybackReady = false
        _playerError.value = null
        _qualityMode.value = QualityMode.AUTO
        _showForwardChip.value = false

        // This is the SubtitleManager instance actually applied to the
        // PlayerView (see VideoPlayerActivity) — it must hold a context so
        // asset fonts (e.g. Vazirmatn) resolve. PlayerManager no longer keeps
        // its own (dead) instance.
        subtitleManager.attachContext(context)

        val vps = try { StorageUtils.loadVideoPlayerSettings(context) } catch (_: Exception) { VideoPlayerSettings.DEFAULT }

        // Effective subtitle settings = global + this video's overrides.
        // The settings UI keys per-video overrides by URL (it never passes a
        // mediaId), so the player reads with mediaId "" to hit the same keys.
        val ss = try {
            SubtitleSettingsStorage.effectiveSettingsFor(context, videoUrl, "")
        } catch (_: Exception) {
            SubtitleSettings.getDefaultSettings(context)
        }
        val perVideo = try {
            SubtitleSettingsStorage.loadPerVideo(context, videoUrl, "")
        } catch (_: Exception) { null }
        pendingTrackKey = perVideo?.selectedTrackKey

        // Resolve the effective resume position:
        //  1. A re-prepare of the same video (Activity recreation) resumes
        //     from the last known position, not the stale Intent extra.
        //  2. The remember-last-position setting gates all of it: when off,
        //     playback always starts from the beginning.
        var effectiveResumeMs = resumePositionMs
        if (videoUrl == lastVideoUrl && lastPositionMs > 0) {
            effectiveResumeMs = lastPositionMs
        }
        if (!vps.rememberLastPosition) {
            effectiveResumeMs = 0L
        }
        sessionResumePositionMs = effectiveResumeMs

        val pm = PlayerManager.get(context)
        playerManager = pm

        val prepared = pm.preparePlayer(
            videoUrl = videoUrl,
            resumePositionMs = effectiveResumeMs,
            videoPlayerSettings = vps,
            subtitleSettings = ss,
            externalSubtitleUri = perVideo?.externalSubtitleUri,
        )
        _playbackSpeed.value = vps.defaultPlaybackSpeed

        session = PlaybackSession(
            context = context,
            videoUrl = videoUrl,
            title = title,
            image = image,
            movieId = movieId,
            seriesId = seriesId,
            seasonId = seasonId,
            episodeId = episodeId,
            resumePositionMs = effectiveResumeMs,
        )

        if (!prepared || pm.player == null) {
            // Player creation failed: surface a real (localized) error so the
            // error overlay + Retry actually appear instead of a dead screen.
            _playerError.value = appCtx.getString(R.string.e5_player_prepare_error)
            return
        }

        // Listen to player events.
        pm.player?.addListener(playerListener)

        // Start position polling (every 500ms).
        startPositionPolling()

        // Start position saving (every 5s).
        startPositionSaving()
    }

    /** Releases the player. Called from the Activity's onDestroy. */
    fun release() {
        positionJob?.cancel()
        saveJob?.cancel()
        retryJob?.cancel()
        // Final save.
        val p = player
        if (p != null && session != null) {
            try {
                val pos = p.currentPosition
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                // Remember where playback stopped in case the same video is
                // prepared again on this ViewModel (Activity recreation).
                lastPositionMs = pos
                lastVideoUrl = session?.videoUrl
                session?.finalize(pos, dur)
            } catch (_: Exception) {}
        }
        playerManager?.releasePlayer()
        playerManager = null
        session = null
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    // ── Player controls ───────────────────────────────────────────────────

    fun playPause() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        p.seekTo(positionMs.coerceAtLeast(0L))
        _currentPosition.value = positionMs
        lastPositionMs = positionMs
    }

    fun seekRelative(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        // getDuration() returns C.TIME_UNSET (negative) while the duration is
        // unknown — clamping to it would seek to TIME_UNSET and crash.
        val newPosition = if (duration > 0) {
            (p.currentPosition + deltaMs).coerceAtLeast(0L).coerceAtMost(duration)
        } else {
            (p.currentPosition + deltaMs).coerceAtLeast(0L)
        }
        p.seekTo(newPosition)
        lastPositionMs = newPosition
    }

    fun setPlaybackSpeed(speed: Float) {
        val p = player ?: return
        p.playbackParameters = PlaybackParameters(speed)
        _playbackSpeed.value = speed
    }

    /** Applies a quality mode chosen in the track dialog and mirrors it in [qualityMode]. */
    fun setQualityMode(mode: QualityMode) {
        _qualityMode.value = mode
        val selector = playerManager?.trackSelector ?: return
        trackManager.applyQualityMode(selector, mode)
    }

    /**
     * Persists a track selection for the current video so it is restored on
     * the next playback. Key format follows SubtitlePerVideo.selectedTrackKey
     * ("embedded:<group>:<track>" for text tracks); audio selections are
     * stored with an "audio:" prefix ("audio:embedded:<group>:<track>").
     */
    fun onTrackSelected(trackKey: String) {
        val ctx = appContext ?: return
        val url = currentVideoUrl ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SubtitleSettingsStorage.mergePerVideo(ctx, url, patch = {
                        copy(selectedTrackKey = trackKey)
                    })
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Manual retry (error-overlay button). Restores the auto-retry budget so
     * a user-initiated retry gets its own bounded automatic follow-ups.
     */
    fun retry() {
        retryJob?.cancel()
        autoRetryCount = 0
        _playerError.value = null
        val p = player
        if (p == null) {
            // Prepare itself failed — rebuild the player from the stored
            // arguments (which re-resolves settings and resume position).
            val args = prepareArgs ?: return
            val ctx = appContext ?: return
            prepare(
                context = ctx,
                videoUrl = args.videoUrl,
                title = args.title,
                image = args.image,
                movieId = args.movieId,
                seriesId = args.seriesId,
                seasonId = args.seasonId,
                episodeId = args.episodeId,
                resumePositionMs = args.resumePositionMs,
            )
            return
        }
        playerManager?.retry(retryTargetPosition(p))
    }

    /**
     * Where a retry should resume: before the first successful playback the
     * player's current position is meaningless (0 after a failed initial
     * load), so retries go back to the session's resume position; afterwards
     * they continue from the live position.
     */
    private fun retryTargetPosition(p: ExoPlayer): Long =
        if (hasPlaybackReady) p.currentPosition else sessionResumePositionMs

    /**
     * Schedules one bounded automatic retry with backoff (3s, then 6s).
     * After [MAX_AUTO_RETRIES] the error stays terminal until the user
     * retries manually.
     */
    private fun scheduleAutoRetry() {
        if (autoRetryCount >= MAX_AUTO_RETRIES) return
        autoRetryCount++
        val backoffMs = AUTO_RETRY_BASE_DELAY_MS * autoRetryCount
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(backoffMs)
            _playerError.value = null
            val p = player ?: return@launch
            playerManager?.retry(retryTargetPosition(p))
        }
    }

    // ── Position polling + saving ─────────────────────────────────────────

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val p = player ?: break
                try {
                    // Read position/duration regardless of play state: while
                    // paused (or right after a seek) the seek bar and time
                    // labels must still reflect reality.
                    val pos = p.currentPosition
                    _currentPosition.value = pos
                    _duration.value = p.duration.takeIf { it > 0 } ?: _duration.value
                    lastPositionMs = pos
                    // Offer the +30s shortcut early in the video (3s–60s).
                    _showForwardChip.value = pos in 3000..60000
                } catch (_: Exception) {}
            }
        }
    }

    private fun startPositionSaving() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                val p = player ?: break
                val s = session ?: continue
                try {
                    val pos = p.currentPosition
                    val dur = p.duration.takeIf { it > 0 } ?: 0L
                    if (dur > 0 && pos > 0) {
                        s.savePosition(pos, dur)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ── Saved track selection ─────────────────────────────────────────────

    /**
     * Re-applies the per-video track selection saved by [onTrackSelected]
     * once the player's tracks are known. Runs at most once per prepare.
     */
    private fun applySavedTrackKey(key: String, tracks: Tracks) {
        val selector = playerManager?.trackSelector ?: return
        try {
            when {
                key.startsWith("audio:embedded:") -> {
                    val parts = key.removePrefix("audio:embedded:").split(":")
                    val group = parts.getOrNull(0)?.toIntOrNull() ?: return
                    val track = parts.getOrNull(1)?.toIntOrNull() ?: return
                    trackManager.selectAudioTrack(selector, tracks, group, track)
                }
                key.startsWith("embedded:") -> {
                    val parts = key.removePrefix("embedded:").split(":")
                    val group = parts.getOrNull(0)?.toIntOrNull() ?: return
                    val track = parts.getOrNull(1)?.toIntOrNull() ?: return
                    if (group < 0) {
                        // "embedded:-1:-1" = subtitles were turned off.
                        trackManager.disableSubtitles(selector)
                    } else {
                        trackManager.selectSubtitleTrack(selector, tracks, group, track)
                    }
                }
                // "external:<uri>" keys need no action: the external subtitle
                // is already part of the media item built in prepare.
                else -> Unit
            }
        } catch (_: Exception) {}
    }

    /** Maps a playback failure to a localized category message (never raw exception text). */
    private fun localizedErrorMessage(error: PlaybackException): String {
        val ctx = appContext ?: return "خطا در پخش ویدیو"
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                ctx.getString(R.string.e5_playback_error_network)
            else -> ctx.getString(R.string.e5_playback_error_generic)
        }
    }

    // ── Player listener ──────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                val p = player ?: return
                _duration.value = p.duration.takeIf { it > 0 } ?: _duration.value
                hasPlaybackReady = true
                // Playback (re)started successfully — restore the auto-retry
                // budget for the next incident.
                autoRetryCount = 0
                // Clear any error — playback recovered.
                _playerError.value = null
            } else if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                // Finished: the content leaves Continue Watching (episodes
                // are additionally marked watched) instead of being parked
                // at ~100% by the final save.
                try {
                    val p = player
                    if (p != null) {
                        lastPositionMs = p.currentPosition
                        lastVideoUrl = session?.videoUrl
                    }
                } catch (_: Exception) {}
                session?.onContentEnded()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _tracks.value = tracks
            val key = pendingTrackKey
            if (key != null && tracks.groups.isNotEmpty()) {
                pendingTrackKey = null
                applySavedTrackKey(key, tracks)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playerError.value = localizedErrorMessage(error)
            scheduleAutoRetry()
        }
    }
}
