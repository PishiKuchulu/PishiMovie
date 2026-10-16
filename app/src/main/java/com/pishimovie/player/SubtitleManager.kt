package com.pishimovie.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.pishimovie.data.model.SubtitleFont
import com.pishimovie.data.model.SubtitlePosition
import com.pishimovie.data.model.SubtitleSettings

/**
 * Phase 5 / Subtitle Center rewrite: SubtitleManager.
 *
 * Applies an extended [SubtitleSettings] payload to a [PlayerView]'s subtitle
 * view. Handles every property media3's [CaptionStyleCompat] exposes — font
 * size (with scale), text color + opacity, bold/italic typeface, edge type
 * (outline / drop-shadow / none), edge color, background color + opacity,
 * and vertical position — plus the new font-family override.
 *
 * Properties that media3 cannot honor directly (alignment, corner radius,
 * line spacing, RTL/Unicode flags, encoding) are stored on the settings
 * object for use by the live preview and any future custom subtitle
 * renderer.
 *
 * Thread-safety: all methods must be called on the main thread (ExoPlayer /
 * View requirement).
 */
class SubtitleManager {

    private var currentSettings: SubtitleSettings = SubtitleSettings.DEFAULT

    /**
     * Application context used to resolve font-family overrides (e.g. Vazirmatn
     * from assets). May be null when running in unit tests or before
     * [attachContext] is called — in that case, only system typefaces resolve.
     */
    private var context: Context? = null

    /** Optional context attachment — called from PlayerManager.preparePlayer. */
    fun attachContext(ctx: Context) {
        context = ctx.applicationContext
    }

    /** Stores the settings (applied to PlayerView via [applyToPlayerView]). */
    fun applySettings(settings: SubtitleSettings) {
        currentSettings = settings
    }

    /** Currently-applied settings (read-only). */
    fun currentSettings(): SubtitleSettings = currentSettings

    /** Applies the stored settings to the given [PlayerView]. */
    fun applyToPlayerView(playerView: PlayerView, fallbackTypeface: Typeface? = null) {
        val s = currentSettings
        val subView = playerView.subtitleView ?: return

        // ── Font size (with combined scale) ────────────────────────────────
        val effectiveSize = s.textSize * s.effectiveScale
        val density = playerView.context.resources.displayMetrics
        val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, effectiveSize, density)
        subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, pixels)

        // ── Typeface (font family + bold/italic) ───────────────────────────
        val baseTypeface = resolveTypeface(s.fontFamily, fallbackTypeface)
        val styledTypeface = applyTextStyle(baseTypeface, s.bold, s.italic)

        // ── Colors with opacity ────────────────────────────────────────────
        val textColor = applyAlpha(s.textColor, s.textOpacity)
        val bgColor = if (s.backgroundEnabled) {
            applyAlpha(s.backgroundColor, s.backgroundOpacity)
        } else {
            Color.TRANSPARENT
        }

        // ── Edge type (outline / shadow / none) ────────────────────────────
        // When both shadow and outline are requested, prefer outline (matches
        // MX Player / Nova behavior — outline is more readable).
        val edgeType = when {
            s.borderThickness > 0f -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            s.shadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_NONE
        }

        val style = CaptionStyleCompat(
            textColor,
            bgColor,
            Color.TRANSPARENT, // windowColor — kept transparent (no per-line box behind text region)
            edgeType,
            s.borderColor,
            styledTypeface,
        )
        subView.setStyle(style)

        // ── Position (vertical placement) ──────────────────────────────────
        val bottomInsetFraction = when (s.position) {
            SubtitlePosition.TOP -> 0.92f
            SubtitlePosition.CENTER -> 0.5f
            SubtitlePosition.BOTTOM -> s.bottomMargin.coerceIn(0f, 0.95f)
        }
        subView.setBottomPaddingFraction(bottomInsetFraction)

        // ── Embedded style handling ────────────────────────────────────────
        // SSA/ASS rendering = honor embedded positioning/styling from the file.
        // Ignore embedded font = force user font family (already handled above).
        subView.setApplyEmbeddedStyles(s.ssaAssRendering)
        // Fit subtitle into video = let the cue's own size win over user size.
        subView.setApplyEmbeddedFontSizes(s.fitSubtitleIntoVideo)
    }

    /** Resolves a [SubtitleFont] to a real [Typeface], falling back to [fallbackTypeface]. */
    private fun resolveTypeface(font: SubtitleFont, fallback: Typeface?): Typeface {
        if (font == SubtitleFont.DEFAULT) return fallback ?: Typeface.DEFAULT
        if (font == SubtitleFont.VAZIRMATN) {
            context?.let { ctx ->
                return try {
                    Typeface.createFromAsset(ctx.assets, "font/vazirmatn_regular.ttf")
                } catch (_: Exception) {
                    fallback ?: Typeface.DEFAULT
                }
            }
        }
        return when (font) {
            SubtitleFont.SANS -> Typeface.SANS_SERIF
            SubtitleFont.SERIF -> Typeface.SERIF
            SubtitleFont.MONOSPACE -> Typeface.MONOSPACE
            SubtitleFont.COMPACT -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            else -> fallback ?: Typeface.DEFAULT
        }
    }

    /** Applies bold/italic style to a typeface via [Typeface.create]. */
    private fun applyTextStyle(typeface: Typeface, bold: Boolean, italic: Boolean): Typeface {
        if (!bold && !italic) return typeface
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(typeface, style)
    }

    /** Blends the given color with transparency (alpha 0..1). */
    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
