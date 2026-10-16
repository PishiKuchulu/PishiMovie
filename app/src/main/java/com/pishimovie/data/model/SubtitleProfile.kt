package com.pishimovie.data.model

import android.graphics.Color
import kotlinx.serialization.Serializable

/**
 * A saved subtitle preset.
 *
 * Built-in presets ([BUILT_IN]) are immutable and always available; they
 * appear alongside the user's custom presets in the Subtitle Settings screen.
 * Custom presets are persisted in `subtitle_profiles.json` via
 * [com.pishimovie.utils.SubtitleSettingsStorage].
 *
 * The preset only carries the [SubtitleSettings] payload — non-style state
 * (enable/disable, language, delay, per-video overrides) is intentionally
 * excluded so applying a preset never resets the user's playback choices.
 */
@Serializable
data class SubtitleProfile(
    /** Stable identifier. Built-ins use the `builtin_*` prefix. */
    val id: String,
    /** Display name shown on the preset chip. */
    val name: String,
    /** Style payload applied when the preset is selected. */
    val settings: SubtitleSettings,
    /** True for presets shipped with the app (cannot be deleted). */
    val isBuiltIn: Boolean = false,
    /** Optional short description shown under the name. */
    val description: String? = null,
) {
    companion object {
        /** All built-in presets, in display order. */
        val BUILT_IN: List<SubtitleProfile> = listOf(
            netflix(),
            cinema(),
            anime(),
            amoled(),
            classicWhite(),
            persianTV(),
        )

        /** Netflix-style: white, bold, with a subtle black outline. */
        fun netflix() = SubtitleProfile(
            id = "builtin_netflix",
            name = "Netflix",
            isBuiltIn = true,
            description = "Bold white with outline",
            settings = SubtitleSettings(
                textColor = Color.WHITE,
                textSize = 18f,
                fontScale = 1.0f,
                bold = true,
                italic = false,
                borderColor = Color.BLACK,
                borderThickness = 2f,
                shadow = false,
                shadowStrength = 0f,
                backgroundEnabled = false,
                textAlignment = SubtitleAlignment.CENTER,
            ),
        )

        /** Cinema-style: yellow text, bold outline, drop shadow. */
        fun cinema() = SubtitleProfile(
            id = "builtin_cinema",
            name = "Cinema",
            isBuiltIn = true,
            description = "Classic yellow with shadow",
            settings = SubtitleSettings(
                textColor = Color.YELLOW,
                textSize = 19f,
                fontScale = 1.0f,
                bold = false,
                italic = false,
                borderColor = Color.BLACK,
                borderThickness = 3f,
                shadow = true,
                shadowStrength = 1.0f,
                backgroundEnabled = false,
                textAlignment = SubtitleAlignment.CENTER,
            ),
        )

        /** Anime-style: white text with blue accent outline. */
        fun anime() = SubtitleProfile(
            id = "builtin_anime",
            name = "Anime",
            isBuiltIn = true,
            description = "White text, blue outline",
            settings = SubtitleSettings(
                textColor = Color.WHITE,
                textSize = 17f,
                fontScale = 1.0f,
                bold = false,
                italic = false,
                borderColor = 0xFF1E88E5.toInt(),
                borderThickness = 2f,
                shadow = true,
                shadowStrength = 1.5f,
                backgroundEnabled = false,
                textAlignment = SubtitleAlignment.CENTER,
            ),
        )

        /** AMOLED-friendly: pure white, heavy outline, no background. */
        fun amoled() = SubtitleProfile(
            id = "builtin_amoled",
            name = "AMOLED",
            isBuiltIn = true,
            description = "Bold white on true black",
            settings = SubtitleSettings(
                textColor = Color.WHITE,
                textSize = 18f,
                fontScale = 1.0f,
                bold = true,
                italic = false,
                borderColor = Color.BLACK,
                borderThickness = 4f,
                shadow = false,
                shadowStrength = 0f,
                backgroundEnabled = false,
                textAlignment = SubtitleAlignment.CENTER,
            ),
        )

        /** Classic white: minimal, no outline, no shadow. */
        fun classicWhite() = SubtitleProfile(
            id = "builtin_classic_white",
            name = "Classic White",
            isBuiltIn = true,
            description = "Minimal white text",
            settings = SubtitleSettings(
                textColor = Color.WHITE,
                textSize = 17f,
                fontScale = 1.0f,
                bold = false,
                italic = false,
                borderColor = Color.BLACK,
                borderThickness = 1f,
                shadow = false,
                shadowStrength = 0f,
                backgroundEnabled = false,
                textAlignment = SubtitleAlignment.CENTER,
            ),
        )

        /** Persian TV: Vazirmatn font, RTL, semi-transparent background. */
        fun persianTV() = SubtitleProfile(
            id = "builtin_persian_tv",
            name = "Persian TV",
            isBuiltIn = true,
            description = "Vazirmatn, RTL, boxed",
            settings = SubtitleSettings(
                textColor = Color.WHITE,
                textSize = 18f,
                fontScale = 1.0f,
                bold = false,
                italic = false,
                borderColor = Color.BLACK,
                borderThickness = 2f,
                shadow = true,
                shadowStrength = 1.0f,
                backgroundEnabled = true,
                backgroundColor = Color.BLACK,
                backgroundOpacity = 0.6f,
                cornerRadius = 6f,
                textAlignment = SubtitleAlignment.CENTER,
                rtlOptimization = true,
                improveComplexScript = true,
                unicodeOptimization = true,
                fontFamily = SubtitleFont.VAZIRMATN,
            ),
        )

        /** Look up a preset by id (built-in or supplied custom list). */
        fun byId(id: String, custom: List<SubtitleProfile> = emptyList()): SubtitleProfile? {
            return BUILT_IN.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }
        }
    }
}
