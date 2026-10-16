package com.pishimovie.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.data.model.VideoPlayerSettings
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 *  - Surface errors via [playerError] state.
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

    private val _showSkipIntro = MutableStateFlow(false)
    val showSkipIntro: StateFlow<Boolean> = _showSkipIntro.asStateFlow()

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
        val pm = PlayerManager.get(context)
        playerManager = pm

        // Fresh playback session → fresh auto-retry budget.
        retryJob?.cancel()
        autoRetryCount = 0

        // This is the SubtitleManager instance actually applied to the
        // PlayerView (see VideoPlayerActivity) — it must hold a context so
        // asset fonts (e.g. Vazirmatn) resolve. PlayerManager no longer keeps
        // its own (dead) instance.
        subtitleManager.attachContext(context)

        val vps = try { StorageUtils.loadVideoPlayerSettings(context) } catch (_: Exception) { VideoPlayerSettings.DEFAULT }
        val ss = try { StorageUtils.loadSubtitleSettings(context) } catch (_: Exception) { SubtitleSettings.getDefaultSettings(context) }

        pm.preparePlayer(videoUrl, resumePositionMs, vps, ss)
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
            resumePositionMs = resumePositionMs,
        )

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
    }

    fun setPlaybackSpeed(speed: Float) {
        val p = player ?: return
        p.playbackParameters = PlaybackParameters(speed)
        _playbackSpeed.value = speed
    }

    /**
     * Manual retry (error-overlay button). Restores the auto-retry budget so
     * a user-initiated retry gets its own bounded automatic follow-ups.
     */
    fun retry() {
        retryJob?.cancel()
        autoRetryCount = 0
        _playerError.value = null
        val p = player ?: return
        playerManager?.retry(p.currentPosition)
    }

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
            playerManager?.retry(p.currentPosition)
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
                    if (p.isPlaying) {
                        _currentPosition.value = p.currentPosition
                        _duration.value = p.duration.takeIf { it > 0 } ?: _duration.value
                        // Show skip intro button 3s–60s.
                        _showSkipIntro.value = p.currentPosition in 3000..60000
                    }
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
                // Playback (re)started successfully — restore the auto-retry
                // budget for the next incident.
                autoRetryCount = 0
                // Clear any error — playback recovered.
                _playerError.value = null
            } else if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _tracks.value = tracks
        }

        override fun onPlayerError(error: PlaybackException) {
            _playerError.value = error.message ?: "Playback error"
            scheduleAutoRetry()
        }
    }
}
