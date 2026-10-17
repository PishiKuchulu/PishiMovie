package com.pishimovie.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pishimovie.ui.theme.CinematicPalette
import com.pishimovie.ui.theme.PishiMotion
import com.pishimovie.ui.theme.darken
import com.pishimovie.ui.theme.rememberReducedMotion
import com.pishimovie.ui.theme.tvTween

/**
 * The signature PishiMovie cinematic backdrop.
 *
 * Renders three layers stacked behind the detail-screen content:
 *
 *  1. Blurred full-bleed backdrop image (the movie's cover) — gives a sense
 *     of the movie's world without competing with the foreground.
 *  2. Ambient gradient driven by [palette] — a vertical wash from
 *     palette.dominant (darkened) at the top to pure black at the bottom,
 *     plus a diagonal accent wash of palette.vibrant at top-start. This is
 *     the "Ambient UI matching current movie artwork" effect (Part 11).
 *  3. A subtle vignette to focus attention on the center content.
 *
 * The [parallaxOffset] (0f..1f, derived from scroll position) slides the
 * backdrop vertically and fades it out as the user scrolls down — delivering
 * the "Parallax scrolling" requirement (Part 11 / Part 6).
 *
 * On API < 31 the blur gracefully degrades to a no-op (the gradient layers
 * still deliver the cinematic feel).
 */
@Composable
fun CinematicBackdrop(
    imageUrl: String,
    palette: CinematicPalette,
    parallaxOffset: Float,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 32.dp,
    backdropHeight: Dp = 420.dp,
) {
    val context = LocalContext.current
    // TV / reduced motion: static backdrop. The parallax translation and fade
    // recompose every scroll frame — expensive on weak TV GPUs for zero
    // benefit on a D-pad UI. The cinematic look (blur, gradients, vignette)
    // is fully kept.
    val reducedMotion = rememberReducedMotion()
    // Clamp parallax to [0,1]
    val clampedParallax = if (reducedMotion) 0f else parallaxOffset.coerceIn(0f, 1f)

    // Parallax travel distance in px (density-aware): up to 120dp upward as the user scrolls
    val density = LocalDensity.current
    val parallaxPx = remember(density) { with(density) { 120.dp.toPx() } }
    // Fade out as we scroll past 50%
    val backdropAlpha = (1f - clampedParallax * 1.5f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(backdropHeight),
    ) {
        // Layer 1: blurred backdrop
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context)
                    .data(imageUrl.ifEmpty { null })
                    .crossfade(true)
                    .build()
            ),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // Parallax translation via graphicsLayer (state-driven, updates every frame)
                .graphicsLayer { translationY = -parallaxPx * clampedParallax }
                .alpha(backdropAlpha)
                .blur(blurRadius),
            contentScale = ContentScale.Crop,
        )

        // Layer 2: ambient gradient (driven by extracted palette)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val vertical = Brush.verticalGradient(
                        colors = listOf(
                            palette.gradientTop,
                            palette.dominant.darken(0.6f).copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.92f),
                            Color.Black,
                        )
                    )
                    val accent = Brush.linearGradient(
                        colors = listOf(
                            palette.accentColor.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.7f, size.height * 0.5f),
                    )
                    onDrawBehind {
                        drawRect(vertical)
                        drawRect(accent)
                    }
                }
        )

        // Layer 3: vignette — radial darkening at the edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val vignette = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = maxOf(size.width, size.height) * 0.75f,
                    )
                    onDrawBehind { drawRect(vignette) }
                }
        )
    }
}

/**
 * Animated reveal wrapper — fades its content in once the cinematic palette
 * has been extracted. Used to deliver the "Smooth entrance animations"
 * requirement (Part 11).
 */
@Composable
fun CinematicReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // TV: instant reveal (0ms) instead of an animated fade.
        animationSpec = tvTween(PishiMotion.DurationShort),
        label = "cinematic_reveal",
    )
    Box(modifier = modifier.alpha(alpha)) {
        content()
    }
}
