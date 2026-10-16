package com.pishimovie.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.pishimovie.R
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.ui.theme.PishiMotion
import com.pishimovie.ui.theme.ShapeCardDefault
import com.pishimovie.ui.theme.pishiAmbientGradient
import com.pishimovie.ui.theme.pishiEdgeLit

/**
 * Premium poster card — the workhorse of PishiMovie's browse experience.
 *
 * Features:
 *  - 20dp rounded corners (signature card from Part 9)
 *  - Subtle scale-up + edge-lit glow on press (premium micro interaction)
 *  - IMDb rating pill at top-end, optional "HD"/quality badge at bottom-end
 *  - Title + year + genres below the poster
 *  - Coil async loading with crossfade
 *
 * Used in:
 *  - Home screen horizontal rows
 *  - Search results grid
 *  - "Because You Watched" rails
 */
@Composable
fun PishiPosterCard(
    title: String,
    year: Int,
    rating: Double,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 140.dp,
    genres: String? = null,
    typeLabel: String? = null, // "Movie" / "Series"
    showMetadata: Boolean = true,
) {
    val dimens = LocalPishiDimens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            hovered -> 1.04f
            else -> 1f
        },
        animationSpec = PishiMotion.springPress(),
        label = "poster_scale",
    )
    val edgeIntensity by animateFloatAsState(
        targetValue = if (hovered || pressed) 1f else 0f,
        animationSpec = PishiMotion.springPress(),
        label = "poster_edge",
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .clickable(interaction, indication = null, role = Role.Button) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.cardHeight),
        ) {
            // Poster image with signature 20dp corners
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl.ifEmpty { null })
                        .size(420)
                        .crossfade(true)
                        .build()
                ),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ShapeCardDefault)
                    .pishiEdgeLit(
                        color = MaterialTheme.colorScheme.primary,
                        intensity = edgeIntensity,
                        shape = ShapeCardDefault,
                    ),
                contentScale = ContentScale.Crop,
            )

            // Bottom gradient for legibility of any bottom-anchored badge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ShapeCardDefault)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                            )
                        )
                    )
            )

            // Rating pill — top-end
            PishiRatingPill(
                rating = rating,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )

            // Type / quality label — bottom-end
            if (typeLabel != null) {
                PishiMetadataBadge(
                    text = typeLabel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        if (showMetadata) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (genres != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = genres,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Premium hero card — used in the home screen's autoplay carousel (Part 2:
 * "Hero banner with autoplay carousel", Part 6: "Hero transitions").
 *
 * Renders a full-bleed backdrop with a darkened ambient gradient, the title,
 * a metadata row (year • rating • genres), and a floating Play button.
 */
@Composable
fun PishiHeroCard(
    title: String,
    description: String,
    imageUrl: String,
    rating: Double,
    year: Int,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val dimens = LocalPishiDimens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.heroHeight),
    ) {
        // Backdrop
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl.ifEmpty { null })
                    .size(420)
                    .crossfade(true)
                    .build()
            ),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Ambient gradient overlay (Part 2: Ambient UI matching artwork)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pishiAmbientGradient(
                    surfaceColor = Color.Black,
                    accentColor = accentColor,
                    accentAlpha = 0.30f,
                )
        )

        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = dimens.xl, vertical = dimens.lg),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Metadata row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PishiRatingPill(rating = rating)
                PishiMetadataBadge(text = year.toString())
                if (metadata != null) {
                    PishiMetadataBadge(text = metadata)
                }
            }

            // Title — Phase 5: slightly increased from headlineMedium (28sp) to
            // headlineLarge (32sp) per user request. Still smaller than the
            // original displaySmall (36sp) for a cleaner feel.
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Phase 9: subtitle / description text removed from the Home
            // hero header per spec — only Poster, Title, Rating, Genre and
            // Buttons remain. The `description` parameter is still accepted
            // by this composable to preserve the API, but is no longer
            // rendered.
            Spacer(modifier = Modifier.height(4.dp))
            // Floating Play button
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PishiPlayButton(
                    text = stringResource(R.string.play),
                    onClick = onPlayClick,
                    accentColor = accentColor,
                )
                PishiSecondaryButton(
                    text = stringResource(R.string.more),
                    onClick = onDetailsClick,
                )
            }
        }
    }
}

/**
 * Floating Play button — premium filled pill with a play icon.
 */
@Composable
fun PishiPlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.PlayArrow,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = PishiMotion.springPress(),
        label = "play_btn_scale",
    )

    val isPishiTheme = accentColor == com.pishimovie.ui.theme.PishiColors.primary
    val backgroundBrush = if (isPishiTheme) {
        Brush.linearGradient(
            colors = listOf(
                com.pishimovie.ui.theme.PishiColors.primary,
                com.pishimovie.ui.theme.PishiColors.secondary
            )
        )
    } else {
        null
    }

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .then(
                if (backgroundBrush != null) Modifier.background(backgroundBrush)
                else Modifier.background(accentColor)
            )
            .clickable(interaction, indication = null, role = Role.Button) { onClick() }
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPishiTheme) Color.Black else Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPishiTheme) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Secondary "glass" button — used alongside [PishiPlayButton] in hero cards.
 */
@Composable
fun PishiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = PishiMotion.springPress(),
        label = "secondary_btn_scale",
    )
    
    val isPishiTheme = MaterialTheme.colorScheme.primary == com.pishimovie.ui.theme.PishiColors.primary
    
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                if (isPishiTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.15f)
            )
            .clickable(interaction, indication = null, role = Role.Button) { onClick() }
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPishiTheme) MaterialTheme.colorScheme.onSurface else Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
