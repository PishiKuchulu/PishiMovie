package com.pishimovie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pishimovie.ui.theme.LocalPishiDimens

/**
 * A premium translucent top scrim used at the top of screens with edge-to-edge
 * content (home, detail pages). Renders a vertical gradient from semi-opaque
 * surface → transparent so status-bar text remains legible without a hard
 * background line.
 *
 * Part 2 / Part 6: "Glass navigation", "Frosted glass overlays".
 */
@Composable
fun PishiGlassTopScrim(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = LocalPishiDimens.current.huge,
    color: Color = Color.Black,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.65f),
                        color.copy(alpha = 0.30f),
                        Color.Transparent,
                    )
                )
            ),
    )
}

/**
 * A premium translucent bottom scrim used above the bottom navigation bar so
 * cards scrolling underneath stay legible.
 */
@Composable
fun PishiGlassBottomScrim(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = LocalPishiDimens.current.xxl,
    color: Color = Color.Black,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.30f),
                        color.copy(alpha = 0.65f),
                    )
                )
            ),
    )
}
