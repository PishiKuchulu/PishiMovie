package com.pishimovie.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PishiMovie glassmorphism modifiers.
 */

/** A frosted-glass surface. Use on top of a sibling that renders the blurred backdrop. */
fun Modifier.pishiGlass(
    shape: Shape = ShapeCardDefault,
    tint: Color = Color.White.copy(alpha = 0.06f),
    borderAlpha: Float = 0.10f,
    borderColor: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(tint)
    .border(width = 1.dp, color = borderColor.copy(alpha = borderAlpha), shape = shape)

/**
 * Edge-lit surface — a thin rim of brand color around a card. Pass a higher
 * `intensity` (0..1) on focused/hovered state to amplify the glow.
 */
fun Modifier.pishiEdgeLit(
    color: Color,
    intensity: Float = 0.0f,
    shape: Shape = ShapeCardDefault,
    width: Dp = 1.5.dp,
): Modifier {
    val alpha = (0.15f + intensity * 0.6f).coerceIn(0f, 1f)
    return this.border(width = width, color = color.copy(alpha = alpha), shape = shape)
}

/**
 * Ambient gradient — used on hero banners and detail backdrops to create the
 * "ambient UI matching current movie artwork" effect.
 */
fun Modifier.pishiAmbientGradient(
    surfaceColor: Color = Color.Black,
    accentColor: Color,
    accentAlpha: Float = 0.25f,
): Modifier = this.drawWithCache {
    val brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            surfaceColor.copy(alpha = 0.4f),
            surfaceColor.copy(alpha = 0.92f),
            surfaceColor,
        )
    )
    val accentBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = accentAlpha),
            Color.Transparent,
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width * 0.6f, size.height * 0.6f),
        tileMode = TileMode.Clamp,
    )
    onDrawBehind {
        drawRect(brush)
        drawRect(accentBrush)
    }
}

/**
 * Premium shadow — soft, wide, low-opacity. Mimics the elevation feel on
 * AMOLED surfaces where traditional Material shadows would be invisible.
 */
@Composable
fun Modifier.pishiSoftShadow(
    shape: Shape = ShapeCardDefault,
    elevation: Dp = 12.dp,
    ambientColor: Color = Color.Black,
    spotColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = ambientColor.copy(alpha = 0.45f),
    spotColor = spotColor,
)

/**
 * Blur helper that gracefully degrades on older Android versions.
 * Use on background layers (not foreground content).
 */
fun Modifier.pishiBlur(radius: Dp = 24.dp): Modifier {
    return if (radius > 0.dp) this.blur(radius) else this
}
