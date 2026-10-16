package com.pishimovie.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.VideoCameraFront
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pishimovie.R
import com.pishimovie.data.model.Country
import com.pishimovie.data.model.Genre
import com.pishimovie.data.repository.DiscoveryRepository
import com.pishimovie.ui.components.PishiBrandingFooter
import com.pishimovie.ui.components.PishiChip
import com.pishimovie.ui.components.PishiSectionHeader
import com.pishimovie.ui.theme.BrandRed
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.ui.theme.ShapeCardDefault

/**
 * PishiMovie Smart Discovery hub (Phase 5).
 *
 * Entry points to:
 *  - Franchise / Movie Universe pages (curated collections)
 *  - Actor / Director / Studio pages (search-driven)
 *  - Genre Explorer
 *  - Country Explorer
 *  - Year Explorer (decades)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryHubScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val repo = remember { DiscoveryRepository() }

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var countries by remember { mutableStateOf<List<Country>?>(null) }

    LaunchedEffect(Unit) {
        genres = repo.getGenres()
        countries = repo.getCountries()
    }

    val decades = remember { repo.getDecades() }
    val franchises = remember { repo.getFranchises() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = dimens.bottomNavHeight + dimens.xxl),
        verticalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        // Header
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
                    text = stringResource(R.string.discover),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Explorer tiles (Actor / Director / Studio / Year) ─────────
        item(key = "explorer_tiles") {
            Column(modifier = Modifier.padding(horizontal = dimens.lg), verticalArrangement = Arrangement.spacedBy(dimens.sm)) {
                ExplorerTile(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.actors),
                    subtitle = stringResource(R.string.browse_by_actor),
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigate("person_search/Actor") },
                )
                ExplorerTile(
                    icon = Icons.Default.VideoCameraFront,
                    title = stringResource(R.string.directors),
                    subtitle = stringResource(R.string.browse_by_director),
                    accent = Color(0xFF7C3AED),
                    onClick = { navController.navigate("person_search/Director") },
                )
                ExplorerTile(
                    icon = Icons.Default.TheaterComedy,
                    title = stringResource(R.string.studios),
                    subtitle = stringResource(R.string.browse_by_studio),
                    accent = Color(0xFF006D32),
                    onClick = { navController.navigate("person_search/Studio") },
                )
                ExplorerTile(
                    icon = Icons.Default.CalendarMonth,
                    title = stringResource(R.string.year_explorer),
                    subtitle = stringResource(R.string.browse_by_decade),
                    accent = Color(0xFF006A6A),
                    onClick = { navController.navigate("year_explorer") },
                )
            }
        }

        // ── Franchises / Movie Universes ──────────────────────────────
        item(key = "franchises_header") { PishiSectionHeader(title = stringResource(R.string.movie_universes)) }
        item(key = "franchises_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.lg),
                horizontalArrangement = Arrangement.spacedBy(dimens.md),
            ) {
                items(franchises, key = { it.id }) { franchise ->
                    FranchiseTile(
                        emoji = franchise.emoji,
                        title = franchise.title,
                        accent = Color(franchise.accentHex),
                        onClick = { navController.navigate("collection/${franchise.id}") },
                    )
                }
            }
        }

        // ── Genres ────────────────────────────────────────────────────
        if (genres == null) {
            item(key = "genres_loading") {
                Text(
                    text = stringResource(R.string.loading_genres),
                    modifier = Modifier.padding(horizontal = dimens.lg),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (genres!!.isNotEmpty()) {
            item(key = "genres_header") { PishiSectionHeader(title = stringResource(R.string.genres)) }
            item(key = "genres_flow") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = dimens.lg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(dimens.sm),
                ) {
                    genres!!.forEach { genre ->
                        PishiChip(
                            text = genre.title,
                            selected = false,
                            onClick = { navController.navigate("movies?genreId=${genre.id}") },
                        )
                    }
                }
            }
        }

        // ── Countries ─────────────────────────────────────────────────
        if (countries == null) {
            item(key = "countries_loading") {
                Text(
                    text = stringResource(R.string.loading_countries),
                    modifier = Modifier.padding(horizontal = dimens.lg),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (countries!!.isNotEmpty()) {
            item(key = "countries_header") { PishiSectionHeader(title = stringResource(R.string.countries)) }
            item(key = "countries_flow") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = dimens.lg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(dimens.sm),
                ) {
                    countries!!.forEach { country ->
                        PishiChip(
                            text = country.title,
                            selected = false,
                            onClick = { navController.navigate("country/${country.id}") },
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            PishiBrandingFooter()
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Explorer tile
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun ExplorerTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val dimens = LocalPishiDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCardDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.15f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
            )
            .clickable { onClick() }
            .padding(dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(dimens.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FranchiseTile(
    emoji: String,
    title: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp)
            .clip(ShapeCardDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent, accent.copy(alpha = 0.4f))
                )
            )
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
