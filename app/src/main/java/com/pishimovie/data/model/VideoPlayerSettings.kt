package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * Video player preferences.
 *
 * Phase 1: extended with default playback speed, preferred external player,
 * and a "remember last position" flag — drives the redesigned player-settings
 * panel and read by [com.pishimovie.VideoPlayerActivity] on launch.
 */
@Serializable
data class VideoPlayerSettings(
    val seekTimeSeconds: Int = 10,
    // Phase 1 additions
    val defaultPlaybackSpeed: Float = 1.0f,
    val preferredExternalPlayer: ExternalPlayer = ExternalPlayer.NONE,
    val rememberLastPosition: Boolean = true,
    val defaultAudioTrack: String? = null,
) {
    companion object {
        val DEFAULT = VideoPlayerSettings()
    }
}

/**
 * Which external player to offer when the user taps "Open externally".
 * NONE = use the built-in ExoPlayer.
 */
@Serializable
enum class ExternalPlayer {
    NONE,
    VLC,
    MX_PLAYER,
    KM_PLAYER,
    SYSTEM_DEFAULT,
}
