package com.pishimovie.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Phase 5: TrackManager.
 *
 * Manages audio + subtitle track selection on behalf of the player.
 * Provides:
 *  - List of available audio tracks (language, codec, channels).
 *  - List of available subtitle tracks (language, type).
 *  - Select / disable a specific audio track.
 *  - Select / disable subtitles.
 *  - Quality override for adaptive video tracks.
 *
 * Uses [DefaultTrackSelector] parameters — no direct ExoPlayer API calls.
 */
class TrackManager {

    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val name: String,
        val language: String?,
        val codec: String?,
        val isSelected: Boolean,
    )

    /** Returns all audio tracks from the current [Tracks]. */
    fun getAudioTracks(tracks: Tracks): List<TrackInfo> {
        val result = mutableListOf<TrackInfo>()
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEachIndexed { groupIndex, group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result.add(
                    TrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        name = format.label ?: format.language ?: "Track ${groupIndex + 1}.${i + 1}",
                        language = format.language,
                        codec = format.sampleMimeType,
                        isSelected = group.isTrackSelected(i),
                    )
                )
            }
        }
        return result
    }

    /** Returns all subtitle/text tracks from the current [Tracks]. */
    fun getSubtitleTracks(tracks: Tracks): List<TrackInfo> {
        val result = mutableListOf<TrackInfo>()
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEachIndexed { groupIndex, group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result.add(
                    TrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        name = format.label ?: format.language ?: "Subtitle ${groupIndex + 1}.${i + 1}",
                        language = format.language,
                        codec = format.sampleMimeType,
                        isSelected = group.isTrackSelected(i),
                    )
                )
            }
        }
        return result
    }

    /** Returns all video tracks (for quality display). */
    fun getVideoTracks(tracks: Tracks): List<TrackInfo> {
        val result = mutableListOf<TrackInfo>()
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEachIndexed { groupIndex, group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val height = format.height
                val name = if (height > 0) "${height}p" else format.label ?: "Video ${groupIndex + 1}"
                result.add(
                    TrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        name = name,
                        language = null,
                        codec = format.sampleMimeType,
                        isSelected = group.isTrackSelected(i),
                    )
                )
            }
        }
        return result
    }

    /** Selects a specific audio track (by group + track index). */
    fun selectAudioTrack(trackSelector: DefaultTrackSelector?, tracks: Tracks, groupIndex: Int, trackIndex: Int) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groupIndex >= audioGroups.size) return
        val group = audioGroups[groupIndex]
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        )
    }

    /** Disables all audio tracks. */
    fun disableAudio(trackSelector: DefaultTrackSelector?) {
        trackSelector?.setParameters(
            trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        )
    }

    /** Selects a specific subtitle track. */
    fun selectSubtitleTrack(trackSelector: DefaultTrackSelector?, tracks: Tracks, groupIndex: Int, trackIndex: Int) {
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groupIndex >= textGroups.size) return
        val group = textGroups[groupIndex]
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        )
    }

    /** Disables all subtitles. */
    fun disableSubtitles(trackSelector: DefaultTrackSelector?) {
        trackSelector?.setParameters(
            trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }

    /** Forces the highest available video quality. */
    fun forceHighestQuality(trackSelector: DefaultTrackSelector?) {
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .setForceLowestBitrate(false)
        )
    }

    /** Forces the lowest available video quality (saves data). */
    fun forceLowestQuality(trackSelector: DefaultTrackSelector?) {
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setForceLowestBitrate(true)
                .setForceHighestSupportedBitrate(false)
        )
    }

    /** Resets to auto quality selection. */
    fun autoQuality(trackSelector: DefaultTrackSelector?) {
        trackSelector?.setParameters(
            trackSelector.buildUponParameters()
                .setForceLowestBitrate(false)
                .setForceHighestSupportedBitrate(false)
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        )
    }
}
