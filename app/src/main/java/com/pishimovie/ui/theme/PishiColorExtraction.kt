package com.pishimovie.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of cinematic color extraction from a movie poster / backdrop.
 *
 * The palette drives the entire "Cinematic Mode" experience (Part 11):
 *  - [dominant]     → main surface tint (gradient base)
 *  - [vibrant]      → brand-style accent (play button, chips)
 *  - [darkVibrant]  → deep gradient bottom
 *  - [lightVibrant] → highlight tint
 *  - [muted]        → secondary text accent
 *  - [onColor]      → best-contrast foreground (black or white) for [dominant]
 */
data class CinematicPalette(
    val dominant: Color = BrandRed,
    val vibrant: Color = BrandRed,
    val darkVibrant: Color = BrandRedDark,
    val lightVibrant: Color = BrandRedLight,
    val muted: Color = Color(0xFF6B6B6B),
    val onColor: Color = Color.White,
) {
    /**
     * Pre-computed gradient pair for the ambient backdrop:
     *  top    = darkened dominant
     *  bottom = near-black with a touch of vibrant
     */
    val gradientTop: Color get() = dominant.copy(alpha = 0.85f).darken(0.3f)
    val gradientBottom: Color get() = Color.Black
    val accentColor: Color get() = vibrant
}

/**
 * Extracts a [CinematicPalette] from any image URL using Coil + Palette.
 *
 * Runs on the IO dispatcher. Returns null on failure so callers can fall back
 * to the default brand palette.
 */
suspend fun extractCinematicPalette(
    context: Context,
    imageUrl: String,
): CinematicPalette? {
    if (imageUrl.isBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val loader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(Size(128, 128)) // small sample is plenty for palette extraction
                .allowHardware(false) // Palette needs a software bitmap
                .build()
            val drawable = loader.execute(request).drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                ?: return@withContext null
            val palette = Palette.from(bitmap).generate()
            palette.toCinematicPalette()
        } catch (e: Exception) {
            null
        }
    }
}

/** Maps an AndroidX [Palette] to our [CinematicPalette] token set. */
private fun Palette.toCinematicPalette(): CinematicPalette {
    val dominant = dominantSwatch?.rgb?.let { Color(it) }
        ?: BrandRed
    val vibrant = vibrantSwatch?.rgb?.let { Color(it) }
        ?: darkVibrantSwatch?.rgb?.let { Color(it) }
        ?: lightVibrantSwatch?.rgb?.let { Color(it) }
        ?: dominant
    val darkVibrant = darkVibrantSwatch?.rgb?.let { Color(it) }
        ?: dominant.darken(0.4f)
    val lightVibrant = lightVibrantSwatch?.rgb?.let { Color(it) }
        ?: vibrant.lighten(0.2f)
    val muted = mutedSwatch?.rgb?.let { Color(it) }
        ?: Color(0xFF6B6B6B)
    // Best-contrast text color for the dominant swatch
    val onColor = dominantSwatch?.let {
        if (it.bodyTextColor != 0) Color(it.bodyTextColor) else Color.White
    } ?: Color.White

    return CinematicPalette(
        dominant = dominant,
        vibrant = vibrant,
        darkVibrant = darkVibrant,
        lightVibrant = lightVibrant,
        muted = muted,
        onColor = onColor,
    )
}

// ─── Color arithmetic helpers ──────────────────────────────────────────────

/** Darken a [Color] by [fraction] (0..1) towards black. */
fun Color.darken(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red * (1 - f),
        green = green * (1 - f),
        blue = blue * (1 - f),
        alpha = alpha,
    )
}

/** Lighten a [Color] by [fraction] (0..1) towards white. */
fun Color.lighten(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (1 - red) * f,
        green = green + (1 - green) * f,
        blue = blue + (1 - blue) * f,
        alpha = alpha,
    )
}

/** Blend two colors by [ratio] (0 = entirely [other], 1 = entirely [this]). */
fun Color.blend(other: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = red * r + other.red * (1 - r),
        green = green * r + other.green * (1 - r),
        blue = blue * r + other.blue * (1 - r),
        alpha = alpha * r + other.alpha * (1 - r),
    )
}
