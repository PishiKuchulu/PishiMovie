package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * Per-video subtitle adjustments, keyed by video URL.
 *
 * The global [SubtitleSettings] holds the user's default style. When the user
 * tweaks subtitle delay / size / language / selected track for a specific
 * video, those overrides are stored here so re-watching the video restores
 * the per-video choices without touching the global defaults.
 *
 * Any `null` field means "use the global default".
 */
@Serializable
data class SubtitlePerVideo(
    /** The video URL this override applies to. */
    val videoUrl: String,
    /**
     * Stable content id (movie/series/episode id) when known. When non-blank,
     * the override is keyed by this instead of [videoUrl], so it survives
     * signed-URL token rotation. Defaults to "" for backward compatibility.
     */
    val mediaId: String = "",
    /** Per-video subtitle delay override in ms. */
    val delayMs: Long? = null,
    /** Per-video font size override in sp. */
    val textSize: Float? = null,
    /** Per-video preferred language override. */
    val language: String? = null,
    /** Selected track key: "embedded:<groupIndex>:<trackIndex>" or "external:<uri>". */
    val selectedTrackKey: String? = null,
    /** External subtitle file URI loaded by the user for this video. */
    val externalSubtitleUri: String? = null,
    /** External subtitle MIME type. */
    val externalSubtitleMime: String? = null,
    /** Last-used subtitle profile id (so re-watching restores the chosen look). */
    val activeProfileId: String? = null,
) {
    companion object {
        val NO_OVERRIDE = emptyMap<String, SubtitlePerVideo>()

        /** Builds an override object that records just the selected track. */
        fun forTrack(
            videoUrl: String,
            trackKey: String,
            externalUri: String? = null,
            externalMime: String? = null,
            mediaId: String = "",
        ): SubtitlePerVideo = SubtitlePerVideo(
            videoUrl = videoUrl,
            mediaId = mediaId,
            selectedTrackKey = trackKey,
            externalSubtitleUri = externalUri,
            externalSubtitleMime = externalMime,
        )
    }
}
