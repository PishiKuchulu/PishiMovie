package com.pishimovie.utils

import android.content.Context
import com.pishimovie.data.model.SubtitlePerVideo
import com.pishimovie.data.model.SubtitleProfile
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.util.PLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Centralized persistence for subtitle state.
 *
 * Three concerns, three files — each independently versioned and reloadable:
 *  - **Global settings** (`subtitle_settings.json`) — the user's default
 *    [SubtitleSettings]. Backward-compatible with the legacy
 *    [StorageUtils.saveSubtitleSettings] / [StorageUtils.loadSubtitleSettings]
 *    methods, which now delegate here.
 *  - **Custom profiles** (`subtitle_profiles.json`) — user-saved presets on
 *    top of the built-in [SubtitleProfile.BUILT_IN] list.
 *  - **Per-video overrides** (`subtitle_per_video.json`) — a
 *    `Map<videoUrl, SubtitlePerVideo>` for per-video delay/size/track.
 *
 *  - **Active profile id** (`subtitle_active_profile.txt`) — the currently
 *    selected preset (built-in id or custom id), or null for "Custom".
 *
 * All methods are best-effort: any IO error is logged and falls back to an
 * empty/default value so playback never breaks.
 */
object SubtitleSettingsStorage {

    private const val TAG = "SubtitleSettingsStorage"
    private const val GLOBAL_FILE = "subtitle_settings.json"
    private const val PROFILES_FILE = "subtitle_profiles.json"
    private const val PER_VIDEO_FILE = "subtitle_per_video.json"
    private const val ACTIVE_PROFILE_FILE = "subtitle_active_profile.txt"

    /** Tolerant JSON — old files with missing keys still decode. */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // ── Global settings ─────────────────────────────────────────────────────

    fun saveGlobal(context: Context, settings: SubtitleSettings) {
        try {
            val file = File(context.filesDir, GLOBAL_FILE)
            file.writeText(json.encodeToString(settings))
            PLog.d(TAG, "Global subtitle settings saved")
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving global subtitle settings", e)
        }
    }

    fun loadGlobal(context: Context): SubtitleSettings {
        return try {
            val file = File(context.filesDir, GLOBAL_FILE)
            if (file.exists()) {
                json.decodeFromString<SubtitleSettings>(file.readText())
            } else {
                SubtitleSettings.getDefaultSettings(context)
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading global subtitle settings", e)
            SubtitleSettings.getDefaultSettings(context)
        }
    }

    /** Reset global settings to platform defaults. */
    fun resetGlobal(context: Context) {
        saveGlobal(context, SubtitleSettings.getDefaultSettings(context))
        saveActiveProfileId(context, null)
    }

    // ── Custom profiles ─────────────────────────────────────────────────────

    fun loadCustomProfiles(context: Context): List<SubtitleProfile> {
        return try {
            val file = File(context.filesDir, PROFILES_FILE)
            if (file.exists()) {
                json.decodeFromString<List<SubtitleProfile>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading custom profiles", e)
            emptyList()
        }
    }

    fun saveCustomProfiles(context: Context, profiles: List<SubtitleProfile>) {
        try {
            val file = File(context.filesDir, PROFILES_FILE)
            file.writeText(json.encodeToString(profiles))
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving custom profiles", e)
        }
    }

    /** Insert or update a single custom profile by id. */
    fun upsertCustomProfile(context: Context, profile: SubtitleProfile) {
        synchronized(profilesLock) {
            val current = loadCustomProfiles(context).toMutableList()
            current.removeAll { it.id == profile.id }
            current.add(profile)
            saveCustomProfiles(context, current)
        }
    }

    fun deleteCustomProfile(context: Context, profileId: String) {
        synchronized(profilesLock) {
            val current = loadCustomProfiles(context).toMutableList()
            current.removeAll { it.id == profileId }
            saveCustomProfiles(context, current)
        }
    }

    // ── Active profile id ───────────────────────────────────────────────────

    fun saveActiveProfileId(context: Context, profileId: String?) {
        try {
            val file = File(context.filesDir, ACTIVE_PROFILE_FILE)
            if (profileId == null) {
                if (file.exists()) file.delete()
            } else {
                file.writeText(profileId)
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving active profile id", e)
        }
    }

    fun loadActiveProfileId(context: Context): String? {
        return try {
            val file = File(context.filesDir, ACTIVE_PROFILE_FILE)
            if (file.exists()) {
                file.readText().trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading active profile id", e)
            null
        }
    }

    // ── Per-video overrides ─────────────────────────────────────────────────

    private val perVideoSerializer = MapSerializer(String.serializer(), SubtitlePerVideo.serializer())
    /** Guards read-modify-write cycles on the custom-profiles file. */
    private val profilesLock = Any()
    /** Guards read-modify-write cycles on the per-video overrides file. */
    private val perVideoLock = Any()
    /** Max per-video overrides kept; oldest-inserted entries are evicted first. */
    private const val PER_VIDEO_MAX_ENTRIES = 200

    /**
     * Storage key for a per-video override. Prefers the stable [mediaId]
     * ("id:<mediaId>") so overrides survive signed-URL token rotation;
     * otherwise falls back to the URL-based key ("url:<videoUrl>").
     */
    private fun perVideoKey(mediaId: String, videoUrl: String): String =
        if (mediaId.isNotBlank()) "id:$mediaId" else "url:$videoUrl"

    fun loadAllPerVideo(context: Context): Map<String, SubtitlePerVideo> {
        return try {
            val file = File(context.filesDir, PER_VIDEO_FILE)
            if (file.exists()) {
                json.decodeFromString(perVideoSerializer, file.readText())
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading per-video overrides", e)
            emptyMap()
        }
    }

    fun loadPerVideo(
        context: Context,
        videoUrl: String,
        mediaId: String = "",
    ): SubtitlePerVideo? {
        val all = loadAllPerVideo(context)
        // New keyed lookup first, then the raw-URL key written by older versions.
        return all[perVideoKey(mediaId, videoUrl)] ?: all[videoUrl]
    }

    /** Insert or update a single per-video override. */
    fun savePerVideo(
        context: Context,
        override: SubtitlePerVideo,
        mediaId: String = "",
    ) {
        synchronized(perVideoLock) {
            val all = loadAllPerVideo(context).toMutableMap()
            val effectiveMediaId = mediaId.ifBlank { override.mediaId }
            val key = perVideoKey(effectiveMediaId, override.videoUrl)
            val toStore = if (effectiveMediaId.isNotBlank() && override.mediaId.isBlank()) {
                override.copy(mediaId = effectiveMediaId)
            } else {
                override
            }
            // Drop stale duplicates under the other key schemes for the same video.
            if (key.startsWith("id:")) {
                all.remove("url:${override.videoUrl}")
                all.remove(override.videoUrl) // legacy raw-URL key
            }
            all[key] = toStore
            // Cap the map: evict oldest-inserted entries first.
            while (all.size > PER_VIDEO_MAX_ENTRIES) {
                val oldest = all.keys.firstOrNull() ?: break
                all.remove(oldest)
            }
            writePerVideo(context, all)
        }
    }

    private fun writePerVideo(context: Context, all: Map<String, SubtitlePerVideo>) {
        try {
            val file = File(context.filesDir, PER_VIDEO_FILE)
            file.writeText(json.encodeToString(perVideoSerializer, all))
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving per-video overrides", e)
        }
    }

    /** Merge [patch] into the existing override for [videoUrl] (creates if absent). */
    fun mergePerVideo(
        context: Context,
        videoUrl: String,
        patch: SubtitlePerVideo.() -> SubtitlePerVideo,
        mediaId: String = "",
    ) {
        val current = loadPerVideo(context, videoUrl, mediaId)
            ?: SubtitlePerVideo(videoUrl = videoUrl, mediaId = mediaId)
        savePerVideo(context, current.patch(), mediaId)
    }

    fun clearPerVideo(context: Context, videoUrl: String, mediaId: String = "") {
        synchronized(perVideoLock) {
            val all = loadAllPerVideo(context).toMutableMap()
            all.remove(perVideoKey(mediaId, videoUrl))
            all.remove(videoUrl) // legacy raw-URL key
            writePerVideo(context, all)
        }
    }

    /** Returns the effective settings for a video: global defaults + per-video overrides. */
    fun effectiveSettingsFor(
        context: Context,
        videoUrl: String?,
        mediaId: String = "",
    ): SubtitleSettings {
        val global = loadGlobal(context)
        if (videoUrl.isNullOrBlank()) return global
        val perVideo = loadPerVideo(context, videoUrl, mediaId) ?: return global
        return global.copy(
            delayMs = perVideo.delayMs ?: global.delayMs,
            textSize = perVideo.textSize ?: global.textSize,
            language = perVideo.language ?: global.language,
        )
    }
}
