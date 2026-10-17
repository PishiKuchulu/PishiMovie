package com.pishimovie.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.ui.theme.PishiMotion
import com.pishimovie.ui.theme.rememberReducedMotion

/**
 * PishiMovie premium shimmer block.
 *
 * Renders a shimmering placeholder used by skeleton loaders (Part 4 / Part 13).
 * The animation is a moving linear gradient that sweeps across the surface,
 * giving a "loading" feel without jarring opacity flickers.
 *
 * @param shimmerOffset when provided, the box reuses this externally-driven
 *   sweep offset (e.g. a single shared transition for a whole row); when null,
 *   it runs its own infinite transition.
 */
@Composable
fun PishiShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 6.dp,
    shimmerOffset: Float? = null,
) {
    // TV / reduced motion: static placeholder — no infinite transition is
    // composed at all (see TvMotion).
    if (rememberReducedMotion()) {
        Box(
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }

    val translateAnim = shimmerOffset ?: rememberShimmerOffset()

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f),
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush),
    )
}

/**
 * Shared shimmer sweep driver — ONE infinite transition whose offset is
 * passed down to every shimmer box in a row, instead of each box creating
 * its own concurrent animation.
 */
@Composable
private fun rememberShimmerOffset(): Float {
    val transition = rememberInfiniteTransition(label = "pishi_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PishiMotion.ShimmerPeriod),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    return translateAnim
}

/**
 * Skeleton card matching the dimensions of [PishiPosterCard] — used while
 * the home / list screens load their first page (Part 4: Skeleton loading).
 */
@Composable
fun PishiSkeletonPosterCard(
    modifier: Modifier = Modifier,
    cardWidth: Dp = 140.dp,
    shimmerOffset: Float? = null,
) {
    val dimens = LocalPishiDimens.current
    Column(
        modifier = modifier
            .width(cardWidth)
            .padding(dimens.xs),
    ) {
        PishiShimmerBox(
            modifier = Modifier.fillMaxWidth(),
            height = dimens.cardHeight,
            cornerRadius = 12.dp,
            shimmerOffset = shimmerOffset,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PishiShimmerBox(
            modifier = Modifier.fillMaxWidth(0.85f),
            height = 14.dp,
            shimmerOffset = shimmerOffset,
        )
        Spacer(modifier = Modifier.height(6.dp))
        PishiShimmerBox(
            modifier = Modifier.fillMaxWidth(0.45f),
            height = 10.dp,
            shimmerOffset = shimmerOffset,
        )
    }
}

/** Skeleton for a horizontal row (used on the home screen while sections load). */
@Composable
fun PishiSkeletonRow(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
    itemWidth: Dp = 140.dp,
) {
    val dimens = LocalPishiDimens.current
    // ONE shared sweep for the whole row (title + all cards) — none at all
    // on TV / reduced motion.
    val shimmerOffset = if (rememberReducedMotion()) null else rememberShimmerOffset()
    Column(modifier = modifier.fillMaxWidth().padding(vertical = dimens.md)) {
        // Section title skeleton
        Box(modifier = Modifier.padding(horizontal = dimens.lg)) {
            PishiShimmerBox(
                modifier = Modifier.width(160.dp),
                height = 18.dp,
                shimmerOffset = shimmerOffset,
            )
        }
        Spacer(modifier = Modifier.height(dimens.md))
        Row(
            modifier = Modifier.padding(horizontal = dimens.lg),
            horizontalArrangement = Arrangement.spacedBy(dimens.md),
        ) {
            repeat(itemCount) {
                PishiSkeletonPosterCard(cardWidth = itemWidth, shimmerOffset = shimmerOffset)
            }
        }
    }
}
