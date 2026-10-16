package com.pishimovie.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pishimovie.R
import com.pishimovie.data.model.AchievementBadge
import com.pishimovie.data.model.WatchStatistics
import com.pishimovie.data.repository.StatisticsRepository
import com.pishimovie.ui.components.PishiSectionHeader
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.theme.BrandRed
import com.pishimovie.ui.theme.CinemaGoldColors
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.ui.theme.ShapeCardDefault
import coil.compose.AsyncImage

/**
 * PishiMovie Profile / Statistics screen (Phase 5).
 *
 * Shows:
 *  - Total watch time (hours)
 *  - Completed movies / series
 *  - Current streak (days)
 *  - Average rating given
 *  - Favorite genres (top 5, with counts)
 *  - Recently watched (horizontal rail)
 *  - Achievement badges (unlocked + locked with progress)
 */
@Composable
fun ProfileScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statsRepo = remember { StatisticsRepository(context) }

    var stats by remember { mutableStateOf<WatchStatistics?>(null) }
    var badges by remember { mutableStateOf<List<AchievementBadge>>(emptyList()) }

    LaunchedEffect(Unit) {
        stats = statsRepo.getStatistics()
        badges = statsRepo.getAchievements()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = dimens.bottomNavHeight + dimens.xxl),
        verticalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        // ── Header ────────────────────────────────────────────────────
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarPadding)
                    .padding(horizontal = dimens.sm, vertical = dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.your_profile),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Hero stats card ───────────────────────────────────────────
        item(key = "hero_stats") {
            val s = stats
            if (s != null) {
                HeroStatsCard(stats = s)
            } else {
                PishiShimmerBox(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.lg).height(140.dp),
                    cornerRadius = 20.dp,
                )
            }
        }

        // ── Quick stats row ───────────────────────────────────────────
        item(key = "quick_stats") {
            val s = stats
            if (s != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.lg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                ) {
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PlayArrow,
                        label = stringResource(R.string.completed),
                        value = s.totalCompleted.toString(),
                        color = BrandRed,
                    )
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalFireDepartment,
                        label = stringResource(R.string.streak),
                        value = stringResource(R.string.profile_streak_days, s.streakDays),
                        color = CinemaGoldColors.primary,
                    )
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Star,
                        label = stringResource(R.string.avg_rating),
                        value = if (s.averageRating > 0) String.format("%.1f", s.averageRating) else "—",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── Favorite genres ───────────────────────────────────────────
        val s = stats
        if (s != null && s.favoriteGenres.isNotEmpty()) {
            item(key = "genres_header") { PishiSectionHeader(title = stringResource(R.string.favorite_genres)) }
            item(key = "genres_list") {
                Column(modifier = Modifier.padding(horizontal = dimens.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.favoriteGenres.forEach { tally ->
                        GenreTallyRow(genre = tally.genre, count = tally.count, maxCount = s.favoriteGenres.first().count)
                    }
                }
            }
        }

        // ─── Recently watched ─────────────────────────────────────────
        if (s != null && s.recentlyWatched.isNotEmpty()) {
            item(key = "recent_header") { PishiSectionHeader(title = stringResource(R.string.recently_watched)) }
            item(key = "recent_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.lg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.md),
                ) {
                    items(s.recentlyWatched, key = { it.id }) { session ->
                        RecentlyWatchedCard(
                            title = session.title,
                            imageUrl = session.image,
                            onClick = {
                                if (session.contentType == "movie") {
                                    navController.navigate("single_movie/${session.contentId}")
                                } else {
                                    navController.navigate("single_series/${session.contentId}")
                                }
                            },
                        )
                    }
                }
            }
        }

        // ── Achievements ──────────────────────────────────────────────
        item(key = "achievements_header") { PishiSectionHeader(title = stringResource(R.string.achievements), subtitle = stringResource(R.string.achievements_subtitle, badges.count { it.unlocked }, badges.size)) }
        item(key = "achievements_grid") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.lg),
                horizontalArrangement = Arrangement.spacedBy(dimens.md),
            ) {
                items(badges, key = { it.id }) { badge ->
                    AchievementCard(badge = badge)
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Hero stats card
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroStatsCard(stats: WatchStatistics) {
    val dimens = LocalPishiDimens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.lg)
            .clip(ShapeCardDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(BrandRed, BrandRed.copy(alpha = 0.4f))
                )
            )
            .padding(dimens.xxl),
    ) {
        // App Logo branding in profile hero
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.TopEnd)
                .alpha(0.2f)
        )

        Column {
            Text(
                text = stringResource(R.string.total_watch_time),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%.1f", stats.totalWatchTimeHours),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.hours),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatPill(label = "${stats.completedMovies} ${stringResource(R.string.movies_label)}")
                StatPill(label = "${stats.completedSeries} ${stringResource(R.string.tv_series_label)}")
                StatPill(label = "${stats.totalSessions} ${stringResource(R.string.sessions)}")
            }
        }
    }
}

@Composable
private fun StatPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Quick stat card
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Column(
        modifier = modifier
            .clip(ShapeCardDefault)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Genre tally row (with progress bar)
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun GenreTallyRow(genre: String, count: Int, maxCount: Int) {
    val progress = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Recently watched card
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentlyWatchedCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
) {
    val dimens = LocalPishiDimens.current
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.cardHeight)
                .clip(ShapeCardDefault),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Achievement card
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun AchievementCard(badge: AchievementBadge) {
    val dimens = LocalPishiDimens.current
    val accent = Color(badge.accentHex)
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(ShapeCardDefault)
            .background(
                if (badge.unlocked) accent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = badge.emoji,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.alpha(if (badge.unlocked) 1f else 0.4f),
        )
        Text(
            text = badge.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (badge.unlocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = badge.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!badge.unlocked && badge.progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(badge.progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

// ─── End of ProfileScreen ──────────────────────────────────────────────────
