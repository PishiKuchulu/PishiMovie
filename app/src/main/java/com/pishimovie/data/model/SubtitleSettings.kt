package com.pishimovie.data.model

import android.content.Context
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color as ComposeColor
import com.pishimovie.utils.DeviceUtils
import kotlinx.serialization.Serializable

/**
 * Subtitle appearance + timing + advanced rendering settings.
 *
 * Three logical groups, all persisted as a single JSON file:
 *  - **Basic** — enable / language / encoding / position / delay
 *  - **Appearance** — font family, size, scale, color, bold/italic, border,
 *    shadow, background, corner radius, alignment, bottom margin
 *  - **Advanced** — SSA/ASS rendering, stroke/complex-script/RTL/Unicode
 *    optimizations, embedded-font override, fit-to-video, fade, scale, spacing
 *
 * Backward compatibility: the original fields (textColor, borderColor, textSize,
 * fontWeight, textOpacity, backgroundOpacity, delayMs, offsetVertical) are kept
 * with their original names and defaults. New fields are appended with safe
 * defaults so older JSON files decode without error and existing call sites
 * (SettingsScreen, VideoPlayerActivity, PlayerManager) continue to work.
 */
@Serializable
data class SubtitleSettings(
    // ── Basic (control) ───────────────────────────────────────────────────
    /** Global enable/disable. Per-video selection still controls playback. */
    val enabled: Boolean = true,
    /** Preferred subtitle language: "en", "fa", "ar", "tr", "es", "ja", "ko", "auto". */
    val language: String = "auto",
    /** Character encoding for external SRT/SUB files. */
    val characterEncoding: String = "UTF-8",
    /** Vertical placement of the subtitle region. */
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    /** Subtitle delay in ms. Negative = earlier, positive = later. */
    val delayMs: Long = 0L,

    // ── Appearance ─────────────────────────────────────────────────────────
    /** Font family override for subtitles. */
    val fontFamily: SubtitleFont = SubtitleFont.DEFAULT,
    /** Base text size in sp. */
    val textSize: Float = 17f,
    /** Additional font scale multiplier (0.5..3.0). */
    val fontScale: Float = 1.0f,
    /** Text color (ARGB int). */
    val textColor: Int = Color.YELLOW,
    /** Text opacity (0.3..1.0). */
    val textOpacity: Float = 1f,
    /** Render text in bold. */
    val bold: Boolean = false,
    /** Render text in italic. */
    val italic: Boolean = false,
    /** Border (outline) color (ARGB int). */
    val borderColor: Int = Color.argb(255, 0, 0, 0),
    /** Border thickness in dp (0..10). */
    val borderThickness: Float = 2f,
    /** Enable drop shadow under text. */
    val shadow: Boolean = true,
    /** Shadow strength multiplier (0..2.0). */
    val shadowStrength: Float = 1.0f,
    /** Enable solid background behind subtitle text. */
    val backgroundEnabled: Boolean = false,
    /** Background color (ARGB int). */
    val backgroundColor: Int = Color.argb(255, 0, 0, 0),
    /** Background opacity (0..1.0). */
    val backgroundOpacity: Float = 0.5f,
    /** Background corner radius in dp (0..32). */
    val cornerRadius: Float = 4f,
    /** Text alignment inside the subtitle region. */
    val textAlignment: SubtitleAlignment = SubtitleAlignment.CENTER,
    /** Bottom margin as a fraction of player height (0..0.5). */
    val bottomMargin: Float = 0.08f,

    // ── Advanced ───────────────────────────────────────────────────────────
    /** Use SSA/ASS native rendering (embedded styles, positioning, effects). */
    val ssaAssRendering: Boolean = true,
    /** Improve stroke rendering for sharper outlines. */
    val improveStroke: Boolean = true,
    /** Improve complex script rendering (Persian/Arabic ligatures, shaping). */
    val improveComplexScript: Boolean = true,
    /** Ignore fonts embedded in SSA/ASS files; use the user-selected family. */
    val ignoreEmbeddedFont: Boolean = false,
    /** Auto-scale subtitle to fit inside the video frame. */
    val fitSubtitleIntoVideo: Boolean = false,
    /** Fade in/out animation between cues. */
    val fadeAnimation: Boolean = true,
    /** Global subtitle scale multiplier independent of fontScale (0.5..3.0). */
    val subtitleScale: Float = 1.0f,
    /** Line spacing multiplier (0.8..2.0). */
    val lineSpacing: Float = 1.0f,
    /** RTL rendering optimization for Persian/Arabic. */
    val rtlOptimization: Boolean = true,
    /** Unicode rendering optimization. */
    val unicodeOptimization: Boolean = true,

    // ── Legacy / backward-compat fields ────────────────────────────────────
    /** Legacy font weight 100..900 (Normal=400, Bold=700). Replaced by [bold]. */
    val fontWeight: Int = 400,
    /** Legacy vertical offset fraction. Replaced by [bottomMargin]. */
    val offsetVertical: Float = 0f,
) {
    /** Effective font weight derived from the [bold] flag for media3 Typeface. */
    val effectiveFontWeight: Int
        get() = if (bold) 700 else fontWeight

    /** Combined scale multiplier (fontScale × subtitleScale). */
    val effectiveScale: Float
        get() = (fontScale * subtitleScale).coerceIn(0.25f, 5f)

    companion object {
        val DEFAULT = SubtitleSettings()

        fun getDefaultSettings(context: Context): SubtitleSettings {
            val textSize = if (DeviceUtils.isTv(context)) 25f else 17f
            return SubtitleSettings(textSize = textSize)
        }

        fun Int.toComposeColor(): ComposeColor = ComposeColor(this)
        fun ComposeColor.toInt(): Int = this.toArgb()
    }
}

/** Vertical placement of the subtitle region. */
@Serializable
enum class SubtitlePosition {
    TOP, CENTER, BOTTOM
}

/** Horizontal text alignment inside the subtitle region. */
@Serializable
enum class SubtitleAlignment {
    START, CENTER, END
}

/** Font family choices for subtitle text. */
@Serializable
enum class SubtitleFont {
    DEFAULT, SANS, SERIF, MONOSPACE, VAZIRMATN, COMPACT
}
