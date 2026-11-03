package com.pishimovie

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import com.pishimovie.R
import com.pishimovie.data.model.FontSettings
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.data.model.VideoPlayerSettings
import com.pishimovie.data.model.WatchedEpisode
import com.pishimovie.player.PlayerViewModel
import com.pishimovie.player.QualityMode
import com.pishimovie.player.SubtitleManager
import com.pishimovie.player.TrackManager
import com.pishimovie.utils.DeviceUtils
import com.pishimovie.utils.StorageUtils
import com.pishimovie.utils.SubtitleSettingsStorage
import com.pishimovie.ui.theme.FontManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.media.AudioManager
import android.provider.Settings as AndroidSettings
import android.util.TypedValue

/**
 * Phase 5: Complete Media Player Rewrite.
 *
 * Architecture:
 *   PlayerManager     — owns ExoPlayer + MediaSession, handles lifecycle.
 *   MediaSourceFactory — builds cache-aware HTTP/HLS/DASH/progressive sources.
 *   PlayerViewModel   — bridges player state to Compose via StateFlow.
 *   SubtitleManager   — applies subtitle appearance to PlayerView.
 *   TrackManager      — audio/subtitle/quality track selection.
 *   PlaybackSession   — ContinueWatching + Statistics persistence.
 *
 * The Activity is now thin — it delegates all player logic to the ViewModel
 * and only handles: fullscreen, PiP, key events, and the Compose UI.
 *
 * Supported formats: MP4, MKV, AVI, MOV, WEBM, TS, MPEG, HLS (M3U8),
 * DASH (MPD), HEVC, AVC, VP9, AV1 (device-dependent), 10-bit HEVC, HDR.
 * Supported audio: AAC, AAC+, AC3, EAC3, Dolby Digital/Atmos (passthrough),
 * FLAC, MP3, Opus, Vorbis.
 * Subtitles: SRT, ASS, SSA, WebVTT, embedded, external.
 *
 * The companion-object API is preserved so all call sites
 * (PlayerPickerDialog, HomeScreen, DownloadsScreen) work unchanged.
 */
class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SEASON_ID = "season_id"
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_RESUME_POSITION = "resume_position"

        fun start(context: Context, videoUrl: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
            }
            context.startActivity(intent)
        }

        fun startWithEpisodeInfo(
            context: Context,
            videoUrl: String,
            seriesId: Int,
            seasonId: Int,
            episodeId: Int,
        ) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_SERIES_ID, seriesId)
                putExtra(EXTRA_SEASON_ID, seasonId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
            }
            context.startActivity(intent)
        }

        fun startCinematic(
            context: Context,
            videoUrl: String,
            title: String? = null,
            image: String? = null,
            movieId: Int? = null,
            resumePositionMs: Long = 0L,
            seriesId: Int? = null,
            seasonId: Int? = null,
            episodeId: Int? = null,
        ) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IMAGE, image)
                if (movieId != null) putExtra("movie_id", movieId)
                putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
                if (seriesId != null) putExtra(EXTRA_SERIES_ID, seriesId)
                if (seasonId != null) putExtra(EXTRA_SEASON_ID, seasonId)
                if (episodeId != null) putExtra(EXTRA_EPISODE_ID, episodeId)
            }
            context.startActivity(intent)
        }
    }

    private val viewModel: PlayerViewModel by viewModels()

    private var videoUrl: String? = null
    private var seriesId: Int? = null
    private var seasonId: Int? = null
    private var episodeId: Int? = null
    private var videoTitle: String? = null
    private var videoImage: String? = null
    private var movieId: Int? = null
    private var resumePosition: Long = 0L

    // ── Chrome state mirrored from the Compose screen ─────────────────────
    // onKeyDown lives at Activity level but must respect the on-screen
    // chrome: D-pad left/right belong to the focus system while controls
    // are visible, and BACK must not exit while the player is locked. The
    // screen reports both states through onChromeStateChanged.
    @Volatile private var playerControlsVisible = true
    @Volatile private var playerLocked = false

    /** Incremented when BACK is pressed while locked; the screen observes it and shows the unlock affordance. */
    private val unlockSignal = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableFullScreenMode()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        seriesId = intent.getIntExtra(EXTRA_SERIES_ID, -1).takeIf { it != -1 }
        seasonId = intent.getIntExtra(EXTRA_SEASON_ID, -1).takeIf { it != -1 }
        episodeId = intent.getIntExtra(EXTRA_EPISODE_ID, -1).takeIf { it != -1 }
        videoTitle = intent.getStringExtra(EXTRA_TITLE)
        videoImage = intent.getStringExtra(EXTRA_IMAGE)
        movieId = intent.getIntExtra("movie_id", -1).takeIf { it != -1 }
        resumePosition = intent.getLongExtra(EXTRA_RESUME_POSITION, 0L)

        if (videoUrl != null) {
            // Prepare the player via the ViewModel.
            viewModel.prepare(
                context = applicationContext,
                videoUrl = videoUrl!!,
                title = videoTitle,
                image = videoImage,
                movieId = movieId,
                seriesId = seriesId,
                seasonId = seasonId,
                episodeId = episodeId,
                resumePositionMs = resumePosition,
            )

            setContent {
                VideoPlayerScreen(
                    viewModel = viewModel,
                    videoUrl = videoUrl!!,
                    title = videoTitle,
                    unlockSignal = unlockSignal,
                    onChromeStateChanged = { controlsVisible, locked ->
                        playerControlsVisible = controlsVisible
                        playerLocked = locked
                    },
                    onBack = { finish() },
                    onPip = { enterPipIfPossible() },
                )
            }
        } else {
            finish()
        }

        // The default activity transition stutters on weak TV GPUs (especially
        // with a video surface) — skip it on TV, keep it on mobile.
        if (DeviceUtils.isTv(this)) overridePendingTransition(0, 0)
    }

    /** PiP aspect ratio from the actual video size when known (16:9 fallback). */
    private fun pipAspectRatio(): android.util.Rational {
        val videoSize = viewModel.player?.videoSize
        return if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            android.util.Rational(videoSize.width, videoSize.height)
        } else {
            android.util.Rational(16, 9)
        }
    }

    private fun enterPipIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(pipAspectRatio())
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                // PiP not supported
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && viewModel.player?.isPlaying == true) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(pipAspectRatio())
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val p = viewModel.player ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                viewModel.playPause()
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { p.playWhenReady = true; return true }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { p.playWhenReady = false; return true }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                // While the controls are visible (or the player is locked),
                // left/right belong to the focus system so the user can move
                // between the on-screen controls; the seek shortcut only
                // applies while the chrome is hidden.
                if (playerLocked || playerControlsVisible) return super.onKeyDown(keyCode, event)
                viewModel.seekRelative(-10000); return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (playerLocked || playerControlsVisible) return super.onKeyDown(keyCode, event)
                viewModel.seekRelative(10000); return true
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                if (playerLocked) {
                    // Locked: don't exit — ask the screen to show the unlock
                    // affordance instead.
                    unlockSignal.value += 1
                    return true
                }
                finish(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun enableFullScreenMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        enableFullScreenMode()
    }

    override fun onPause() {
        super.onPause()
        // Use the framework source of truth: when the user leaves while
        // playing, onPause() runs BEFORE onPictureInPictureModeChanged(true),
        // so a cached field would still be false here and wrongly pause
        // playback (frozen PiP window).
        if (isInPictureInPictureMode) return // keep playing in PiP
        viewModel.player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        viewModel.player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.release()
        try { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Exception) {}
    }

    override fun finish() {
        super.finish()
        // Skip the exit animation on TV (see onCreate).
        if (DeviceUtils.isTv(this)) overridePendingTransition(0, 0)
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Compose UI
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoPlayerScreen(
    viewModel: PlayerViewModel,
    videoUrl: String,
    title: String?,
    unlockSignal: StateFlow<Int>,
    onChromeStateChanged: (controlsVisible: Boolean, locked: Boolean) -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit,
) {
    val context = LocalContext.current
    // TV GPUs (often weak SoCs driving 4K) stutter on fade/scale compositing —
    // show/hide the player chrome instantly on TV, keep animations on mobile.
    val isTv = remember(context) { DeviceUtils.isTv(context) }
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val showForwardChip by viewModel.showForwardChip.collectAsState()
    val qualityMode by viewModel.qualityMode.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSpeedDropdown by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // Focus: whenever the controls appear, the Play/Pause button takes
    // initial focus so D-pad navigation has a visible starting point.
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showControls, isLocked, playerError) {
        if (showControls && !isLocked && playerError == null) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    // Mirror chrome state to the Activity (D-pad seek gating + locked BACK).
    LaunchedEffect(showControls, isLocked) {
        onChromeStateChanged(showControls, isLocked)
    }
    // BACK pressed while locked → reveal the unlock affordance.
    val unlockTick by unlockSignal.collectAsState()
    LaunchedEffect(unlockTick) {
        if (unlockTick > 0 && isLocked) showControls = true
    }

    // Gesture state
    var brightness by remember { mutableFloatStateOf(-1f) }
    var volume by remember { mutableFloatStateOf(-1f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var gestureStartY by remember { mutableFloatStateOf(0f) }
    var gestureStartValue by remember { mutableFloatStateOf(0f) }
    // Which side the current drag started on (brightness = left, volume = right);
    // the target is fixed at drag start so crossing the middle doesn't flip it.
    var gestureIsBrightness by remember { mutableStateOf(true) }

    // Double-tap seek indicator ("+30s" / "−30s" with a direction icon)
    var seekIndicatorText by remember { mutableStateOf<String?>(null) }
    var seekIndicatorForward by remember { mutableStateOf(true) }
    LaunchedEffect(seekIndicatorText) {
        if (seekIndicatorText != null) {
            delay(800)
            seekIndicatorText = null
        }
    }

    val videoPlayerSettings = remember(context) {
        try { StorageUtils.loadVideoPlayerSettings(context) } catch (_: Exception) { VideoPlayerSettings.DEFAULT }
    }
    // Subtitle settings are lifecycle-aware: reloaded on every resume so the
    // user sees their changes immediately after returning from
    // SubtitleSettingsActivity (no need to restart playback). The EFFECTIVE
    // settings are loaded — global settings merged with this video's
    // per-video overrides. The settings UI keys overrides by URL (mediaId is
    // always ""), so the player reads with "" to hit the same keys.
    var subtitleSettings by remember(context) {
        mutableStateOf(
            try { SubtitleSettingsStorage.effectiveSettingsFor(context, videoUrl, "") } catch (_: Exception) { SubtitleSettings.getDefaultSettings(context) }
        )
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                subtitleSettings = try {
                    SubtitleSettingsStorage.effectiveSettingsFor(context, videoUrl, "")
                } catch (_: Exception) {
                    SubtitleSettings.getDefaultSettings(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val fontSettings = remember(context) {
        try { StorageUtils.loadFontSettings(context) } catch (_: Exception) { FontSettings.DEFAULT }
    }
    val customTypeface = remember(fontSettings.fontType) {
        try {
            when (fontSettings.fontType) {
                com.pishimovie.data.model.FontType.DEFAULT -> null
                com.pishimovie.data.model.FontType.VAZIRMATN -> {
                    Typeface.createFromAsset(context.assets, "font/vazirmatn_regular.ttf")
                }
            }
        } catch (_: Exception) { null }
    }

    // Initialize brightness/volume from system
    LaunchedEffect(Unit) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
        } catch (_: Exception) { volume = 0.5f }
        try {
            brightness = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                AndroidSettings.System.getFloat(context.contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS, 0.5f) / 255f
            } else 0.5f
        } catch (_: Exception) { brightness = 0.5f }
    }

    // Auto-hide controls (never while the user is dragging the seek bar)
    LaunchedEffect(showControls, isPlaying, isLocked, isSeeking) {
        if (showControls && isPlaying && !isLocked && !isSeeking) {
            delay(3000)
            showControls = false
        }
    }

    // Auto-hide gesture indicators
    LaunchedEffect(showBrightnessIndicator) { if (showBrightnessIndicator) { delay(1000); showBrightnessIndicator = false } }
    LaunchedEffect(showVolumeIndicator) { if (showVolumeIndicator) { delay(1000); showVolumeIndicator = false } }

    val p = viewModel.player

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectTapGestures(onTap = { showControls = !showControls })
                    return@pointerInput
                }
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val half = size.width / 2
                        val seekSeconds = videoPlayerSettings.seekTimeSeconds
                        if (offset.x < half) {
                            viewModel.seekRelative(-seekSeconds * 1000L)
                            seekIndicatorForward = false
                            seekIndicatorText = "−${seekSeconds}s"
                        } else {
                            viewModel.seekRelative(seekSeconds * 1000L)
                            seekIndicatorForward = true
                            seekIndicatorText = "+${seekSeconds}s"
                        }
                    },
                    onTap = { showControls = !showControls },
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        gestureStartY = offset.y
                        gestureIsBrightness = offset.x < size.width / 2
                        if (gestureIsBrightness) {
                            gestureStartValue = brightness
                            showBrightnessIndicator = true
                        } else {
                            gestureStartValue = volume
                            showVolumeIndicator = true
                        }
                    },
                    onDrag = { change, _ ->
                        // The fraction is computed from the TOTAL distance
                        // dragged since the gesture started (start Y vs the
                        // current Y) applied to the value captured at drag
                        // start — not from each event's own little delta,
                        // which made the value jitter around the start point.
                        val totalDelta = (gestureStartY - change.position.y) / size.height.toFloat()
                        val newValue = (gestureStartValue + totalDelta).coerceIn(0f, 1f)
                        if (gestureIsBrightness) {
                            brightness = newValue
                            applyBrightness(context, newValue)
                        } else {
                            volume = newValue
                            applyVolume(context, newValue)
                        }
                    },
                )
            }
    ) {
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (playerError != null) {
                    // Prepare itself failed (no player exists yet) — show the
                    // error here, since the main error overlay below only
                    // exists once a player does. Retry re-runs prepare from
                    // the ViewModel's stored arguments.
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.playback_error), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(playerError ?: "", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            androidx.compose.material3.Button(onClick = { viewModel.retry() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.retry))
                            }
                            androidx.compose.material3.OutlinedButton(onClick = onBack) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.initializing_player), color = Color.White, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            return@Box
        }

        // ── PlayerView ────────────────────────────────────────────────────
        // Plain (non-observable) holder so subtitle styling can be re-applied
        // from an effect only when the inputs change — writing Compose state
        // inside AndroidView's update block is not allowed.
        val playerViewHolder = remember { PlayerViewHolder() }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = p
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Apply subtitle settings via SubtitleManager.
                    viewModel.subtitleManager.applySettings(subtitleSettings)
                    viewModel.subtitleManager.applyToPlayerView(this, customTypeface)
                    playerViewHolder.view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pv ->
                if (pv is PlayerView) playerViewHolder.view = pv
            },
        )

        // Re-apply subtitle styling only when the settings or typeface
        // actually change (e.g., after returning from SubtitleSettingsActivity)
        // — NOT on every recomposition (position ticks recompose ~2x/sec, and
        // each apply reloads the typeface from assets on the main thread).
        LaunchedEffect(subtitleSettings, customTypeface) {
            playerViewHolder.view?.let { pv ->
                viewModel.subtitleManager.applySettings(subtitleSettings)
                viewModel.subtitleManager.applyToPlayerView(pv, customTypeface)
            }
        }

        // ── Error overlay ─────────────────────────────────────────────────
        if (playerError != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.playback_error), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(playerError ?: "Unknown error", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Button(onClick = { viewModel.retry() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.retry))
                        }
                        // "Open in browser" is only useful for direct media
                        // files: an adaptive manifest (.m3u8/.mpd) does
                        // nothing in a browser, and the raw stream URL can
                        // carry tokens that should not leak into browser
                        // history — so the option is hidden for manifests.
                        val isAdaptiveManifest = videoUrl.substringBefore('?').substringBefore('#')
                            .let { it.endsWith(".m3u8", ignoreCase = true) || it.endsWith(".mpd", ignoreCase = true) }
                        if (!isAdaptiveManifest) {
                            androidx.compose.material3.OutlinedButton(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.open_in_browser))
                            }
                        }
                    }
                }
            }
        }

        // ── Buffering indicator ───────────────────────────────────────────
        if (isBuffering && playerError == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        // ── Quick forward (+30s) ──────────────────────────────────────────
        // A plain seek shortcut offered early in the video. (It is NOT a
        // "skip intro" feature — there is no intro-range data — so it is
        // labeled honestly with exactly what it does.)
        AnimatedVisibility(
            visible = showForwardChip && !isLocked,
            enter = if (isTv) EnterTransition.None else fadeIn() + scaleIn(),
            exit = if (isTv) ExitTransition.None else fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Row(
                modifier = Modifier
                    .padding(end = 24.dp, bottom = 100.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { viewModel.seekRelative(30000) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.e5_forward_30_seconds), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ── Double-tap seek indicator ─────────────────────────────────────
        AnimatedVisibility(
            visible = seekIndicatorText != null,
            enter = if (isTv) EnterTransition.None else fadeIn(),
            exit = if (isTv) ExitTransition.None else fadeOut(),
        ) {
            GestureIndicator(
                icon = if (seekIndicatorForward) Icons.AutoMirrored.Filled.Forward else Icons.Default.Replay,
                text = seekIndicatorText ?: "",
            )
        }

        // ── Brightness / Volume indicators ────────────────────────────────
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = if (isTv) EnterTransition.None else fadeIn(),
            exit = if (isTv) ExitTransition.None else fadeOut(),
        ) {
            GestureIndicator(Icons.Default.BrightnessMedium, "${(brightness * 100).toInt()}%")
        }
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = if (isTv) EnterTransition.None else fadeIn(),
            exit = if (isTv) ExitTransition.None else fadeOut(),
        ) {
            GestureIndicator(Icons.AutoMirrored.Filled.VolumeUp, "${(volume * 100).toInt()}%")
        }

        // ── Controls overlay ──────────────────────────────────────────────
        if (showControls && !isLocked && playerError == null) {
            // Whether a subtitle track is currently active (for the top-bar
            // subtitle button's state tint).
            val subtitleActive = tracks.groups.any { group ->
                group.type == C.TRACK_TYPE_TEXT && group.isSelected
            }
            Column(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                // Top bar
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).playerFocusRing(CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    // Title of the playing video/episode.
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    IconButton(onClick = { isLocked = true; showControls = false }, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).playerFocusRing(CircleShape)) {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.lock), tint = Color.White)
                    }
                    IconButton(onClick = onPip, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).playerFocusRing(CircleShape)) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = stringResource(R.string.pip), tint = Color.White)
                    }
                    // Subtitle Settings — opens SubtitleSettingsActivity in
                    // per-video mode for the current video URL. The tint
                    // reflects whether a subtitle track is active.
                    IconButton(
                        onClick = {
                            com.pishimovie.SubtitleSettingsActivity.startForVideo(context, videoUrl)
                        },
                        modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).playerFocusRing(CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = stringResource(R.string.subtitle_settings),
                            tint = if (subtitleActive) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(onClick = { showTrackDialog = true }, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).playerFocusRing(CircleShape)) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
                    }
                }

                // Center play/pause
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { viewModel.playPause() },
                        modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .focusRequester(playFocusRequester).playerFocusRing(CircleShape),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.e5_pause) else stringResource(R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                // Bottom controls — the seek bar always stays visible; while
                // buffering, the center spinner (already shown) is the only
                // indicator, and while dragging, the slider + time label
                // follow the drag and preview the destination time.
                Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(16.dp)) {
                    Slider(
                        value = when {
                            isSeeking -> seekProgress
                            duration > 0 -> currentPosition.toFloat() / duration.toFloat()
                            else -> 0f
                        },
                        onValueChange = { progress ->
                            isSeeking = true
                            seekProgress = progress
                        },
                        onValueChangeFinished = {
                            val newPosition = (seekProgress * duration).toLong()
                            viewModel.seekTo(newPosition)
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(if (isSeeking) (seekProgress * duration).toLong() else currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Speed selector
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { showSpeedDropdown = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.playback_speed), tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(
                                    text = String.format("%.2fx", playbackSpeed),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            DropdownMenu(expanded = showSpeedDropdown, onDismissRequest = { showSpeedDropdown = false }) {
                                listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text(String.format("%.2fx", speed), color = if (speed == playbackSpeed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        onClick = { viewModel.setPlaybackSpeed(speed); showSpeedDropdown = false },
                                    )
                                }
                            }
                        }
                        Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ── Locked overlay ────────────────────────────────────────────────
        if (isLocked) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = if (isTv) EnterTransition.None else fadeIn(),
                    exit = if (isTv) ExitTransition.None else fadeOut(),
                ) {
                    IconButton(
                        onClick = { isLocked = false; showControls = true },
                        modifier = Modifier.padding(top = 48.dp).size(48.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).playerFocusRing(CircleShape),
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.unlock), tint = Color.White)
                    }
                }
            }
        }

        // ── Track selection dialog ────────────────────────────────────────
        if (showTrackDialog) {
            TrackSelectionDialog(
                tracks = tracks,
                trackSelector = viewModel.playerManager?.trackSelector,
                trackManager = viewModel.trackManager,
                qualityMode = qualityMode,
                onQualityModeSelected = { viewModel.setQualityMode(it) },
                onTrackSelected = { viewModel.onTrackSelected(it) },
                onDismiss = { showTrackDialog = false },
            )
        }
    }
}

/**
 * Visible focus state for the player's chrome buttons (D-pad navigation):
 * a light overlay + white ring while the control holds focus.
 */
@Composable
private fun Modifier.playerFocusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier
                    .background(Color.White.copy(alpha = 0.30f), shape)
                    .border(2.dp, Color.White, shape)
            } else {
                Modifier
            }
        )
}

// ───────────────────────────────────────────────────────────────────────────
// Helper composables + functions
// ───────────────────────────────────────────────────────────────────────────

/** Non-observable holder for the PlayerView (see the subtitle-style effect above). */
private class PlayerViewHolder {
    var view: PlayerView? = null
}

@Composable
private fun GestureIndicator(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrackSelectionDialog(
    tracks: Tracks,
    trackSelector: DefaultTrackSelector?,
    trackManager: TrackManager,
    qualityMode: QualityMode,
    onQualityModeSelected: (QualityMode) -> Unit,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val audioTracks = remember(tracks) { trackManager.getAudioTracks(tracks) }
    val subtitleTracks = remember(tracks) { trackManager.getSubtitleTracks(tracks) }
    val videoTracks = remember(tracks) { trackManager.getVideoTracks(tracks) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracks_quality), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Video quality — chips mirror the ViewModel's quality mode,
                // which is the same state applied to the track selector.
                if (videoTracks.isNotEmpty()) {
                    Text(stringResource(R.string.quality), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        androidx.compose.material3.FilterChip(selected = qualityMode == QualityMode.AUTO, onClick = { onQualityModeSelected(QualityMode.AUTO) }, label = { Text(stringResource(R.string.auto_quality)) })
                        androidx.compose.material3.FilterChip(selected = qualityMode == QualityMode.HIGHEST, onClick = { onQualityModeSelected(QualityMode.HIGHEST) }, label = { Text(stringResource(R.string.highest_quality)) })
                        androidx.compose.material3.FilterChip(selected = qualityMode == QualityMode.LOWEST, onClick = { onQualityModeSelected(QualityMode.LOWEST) }, label = { Text(stringResource(R.string.lowest_quality)) })
                    }
                }
                // Audio tracks
                if (audioTracks.isNotEmpty()) {
                    Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    audioTracks.forEach { track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                trackManager.selectAudioTrack(trackSelector, tracks, track.groupIndex, track.trackIndex)
                                // Persist per video (restored on next playback).
                                onTrackSelected("audio:embedded:${track.groupIndex}:${track.trackIndex}")
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(track.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (track.isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // Subtitles
                if (subtitleTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.subtitles), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            trackManager.disableSubtitles(trackSelector)
                            // Persist "off" per video ("embedded:-1:-1").
                            onTrackSelected("embedded:-1:-1")
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.off), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (subtitleTracks.none { it.isSelected }) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    subtitleTracks.forEach { track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                trackManager.selectSubtitleTrack(trackSelector, tracks, track.groupIndex, track.trackIndex)
                                // Persist per video (SubtitlePerVideo key format).
                                onTrackSelected("embedded:${track.groupIndex}:${track.trackIndex}")
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(track.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (track.isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Text(stringResource(R.string.no_subtitles), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun applyBrightness(context: Context, value: Float) {
    try {
        (context as? Activity)?.window?.let { w ->
            val params = w.attributes
            params.screenBrightness = value.coerceIn(0f, 1f)
            w.attributes = params
        }
    } catch (_: Exception) {}
}

private fun applyVolume(context: Context, value: Float) {
    try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (value * maxVol).toInt().coerceIn(0, maxVol), 0)
    } catch (_: Exception) {}
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
    } else {
        String.format("%02d:%02d", remainingMinutes, remainingSeconds)
    }
}
