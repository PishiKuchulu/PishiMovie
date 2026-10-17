package com.pishimovie.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pishimovie.R
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.PishiCollection
import com.pishimovie.data.model.PishiMood
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.Series
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiRatingPill
import com.pishimovie.ui.components.PishiMetadataBadge
import com.pishimovie.ui.components.PishiSectionHeader
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.components.PishiSkeletonRow
import com.pishimovie.ui.home.HomeViewModel
import com.pishimovie.ui.theme.BrandRed
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.ui.theme.PishiMotion
import com.pishimovie.ui.theme.ShapeCardDefault
import com.pishimovie.ui.theme.pishiAmbientGradient
import com.pishimovie.ui.theme.pishiBlur
import com.pishimovie.ui.theme.rememberReducedMotion
import com.pishimovie.ui.theme.tvSpringOrSnap
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout

/**
 * PishiMovie Home screen (Phase 5 revision).
 *
 * Layout order (per spec):
 *  1. Hero Header
 *  2. Movies / TV Series (compact cards)
 *  3. Country Categories (compact cards: Turkish/Korean/Indian/Spanish)
 *  4. Recently Added
 *  5. Featured Movies
 *  6. Featured TV Series
 *  7. Trending Today
 *  8. Popular Movies
 *  9. Popular TV Series
 * 10. Recommended
 * 11. Mood Mode
 * 12. Collections
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val uiState = viewModel.uiState.collectAsState().value

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(dimens.md),
        ) {
            // ── 1. Hero Header ────────────────────────────────────────────
            item(key = "hero") {
                val heroes = uiState.heroBanners
                if (heroes == null) {
                    PishiShimmerBox(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.lg),
                        height = dimens.heroHeight,
                        cornerRadius = 28.dp,
                    )
                } else if (heroes.isNotEmpty()) {
                    HeroCarousel(movies = heroes, navController = navController)
                }
            }

            // ── 2. Compact category cards: Movies / TV Series ────────────
            item(key = "quick_access") {
                CompactQuickAccessRow(navController = navController)
            }

            // ── 3. Compact country cards: Turkish / Korean / Indian / Spanish
            item(key = "country_cards") {
                CompactCountryCategoryRow(
                    navController = navController,
                    countryRails = uiState.countryRails,
                )
            }

            // ── 4. Recently Added ────────────────────────────────────────
            val newReleases = uiState.newReleases
            if (newReleases == null) {
                item(key = "new_skeleton") { PishiSkeletonRow() }
            } else if (newReleases.isNotEmpty()) {
                item(key = "new_header") { PishiSectionHeader(title = stringResource(R.string.recently_added)) }
                item(key = "new_row") { MovieRow(movies = newReleases, navController = navController) }
            }

            // ── 5. Featured Movies ───────────────────────────────────────
            val trending = uiState.trending
            if (trending == null) {
                item(key = "fm_skeleton") { PishiSkeletonRow() }
            } else if (trending.isNotEmpty()) {
                item(key = "fm_header") { PishiSectionHeader(title = stringResource(R.string.featured_movies)) }
                item(key = "fm_row") { MovieRow(movies = trending, navController = navController) }
            }

            // ── 6. Featured TV Series ────────────────────────────────────
            val featuredSeries = uiState.featuredSeries
            if (featuredSeries == null) {
                item(key = "fs_skeleton") { PishiSkeletonRow() }
            } else if (featuredSeries.isNotEmpty()) {
                item(key = "fs_header") { PishiSectionHeader(title = stringResource(R.string.featured_tv_series)) }
                item(key = "fs_row") { SeriesRow(series = featuredSeries, navController = navController) }
            }

            // ── 6.5. Featured Anime / Animation ─────────────────────────
            // Inserted between Featured TV Series and Trending Today per spec.
            // Mixed movies + TV shows tagged Animation / Anime on TMDB.
            val animeSelection = uiState.animeSelection
            if (animeSelection == null) {
                item(key = "an_skeleton") { PishiSkeletonRow() }
            } else if (animeSelection.isNotEmpty()) {
                item(key = "an_header") { PishiSectionHeader(title = stringResource(R.string.anime_section)) }
                item(key = "an_row") { PosterRow(posters = animeSelection, navController = navController) }
            }

            // ── 7. Trending Today ────────────────────────────────────────
            val trendingToday = uiState.trendingToday
            if (trendingToday == null) {
                item(key = "td_skeleton") { PishiSkeletonRow() }
            } else if (trendingToday.isNotEmpty()) {
                item(key = "td_header") { PishiSectionHeader(title = stringResource(R.string.trending_today)) }
                item(key = "td_row") { MovieRow(movies = trendingToday, navController = navController) }
            }

            // ── 8. Popular Movies ────────────────────────────────────────
            val topRated = uiState.topRated
            if (topRated == null) {
                item(key = "pm_skeleton") { PishiSkeletonRow() }
            } else if (topRated.isNotEmpty()) {
                item(key = "pm_header") { PishiSectionHeader(title = stringResource(R.string.popular_movies)) }
                item(key = "pm_row") { MovieRow(movies = topRated, navController = navController) }
            }

            // ── 9. Popular TV Series ─────────────────────────────────────
            val popularSeries = uiState.popularSeries
            if (popularSeries == null) {
                item(key = "ps_skeleton") { PishiSkeletonRow() }
            } else if (popularSeries.isNotEmpty()) {
                item(key = "ps_header") { PishiSectionHeader(title = stringResource(R.string.popular_tv_series)) }
                item(key = "ps_row") { SeriesRow(series = popularSeries, navController = navController) }
            }

            // ── 10. Recommended ──────────────────────────────────────────
            val recommended = uiState.recommendedForYou
            if (recommended == null) {
                item(key = "rec_skeleton") { PishiSkeletonRow() }
            } else if (recommended.isNotEmpty()) {
                item(key = "rec_header") { PishiSectionHeader(title = stringResource(R.string.recommended_for_you), subtitle = stringResource(R.string.recommended_subtitle)) }
                item(key = "rec_row") { MovieRow(movies = recommended, navController = navController) }
            }

            // ── 11. Mood Mode ────────────────────────────────────────────
            val moods = uiState.moods
            if (moods == null) {
                item(key = "mood_skeleton") { PishiSkeletonRow(itemWidth = 140.dp) }
            } else if (moods.isNotEmpty()) {
                item(key = "mood_header") { PishiSectionHeader(title = stringResource(R.string.mood_mode), subtitle = stringResource(R.string.mood_subtitle)) }
                item(key = "mood_row") { MoodRow(moods = moods, navController = navController) }
            }

            // ── 12. Collections ──────────────────────────────────────────
            val collections = uiState.collections
            if (collections == null) {
                item(key = "coll_skeleton") { PishiSkeletonRow(itemWidth = 180.dp) }
            } else if (collections.isNotEmpty()) {
                item(key = "coll_header") { PishiSectionHeader(title = stringResource(R.string.collections), subtitle = stringResource(R.string.collections_subtitle)) }
                item(key = "coll_row") { CollectionsRow(collections = collections, navController = navController) }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Compact Quick Access cards (Movies / TV Series) — ~48dp height
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactQuickAccessRow(navController: NavController) {
    val dimens = LocalPishiDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        CompactCategoryCard(
            icon = Icons.Default.Movie,
            label = stringResource(R.string.movies),
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = { navController.navigate("movies") },
        )
        CompactCategoryCard(
            icon = Icons.Default.Tv,
            label = stringResource(R.string.series),
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            onClick = { navController.navigate("series") },
        )
    }
}

@Composable
private fun CompactCategoryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        // TV: press feedback snaps instead of springing (see TvMotion).
        animationSpec = tvSpringOrSnap(),
        label = "cat_scale",
    )
    Row(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.7f),
                        accentColor.copy(alpha = 0.25f),
                    )
                )
            )
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Compact country category cards (Turkish / Korean / Indian / Spanish)
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactCountryCategoryRow(
    navController: NavController,
    countryRails: List<com.pishimovie.data.repository.CountryRail>?,
) {
    val dimens = LocalPishiDimens.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val categories = listOf(
        InternationalCategory(stringResource(R.string.country_turkish), "🇹🇷", listOf("turkish", "turkey", "türkiye", "turkiye", "ترکی", "ترکيه", "tc", "tr")),
        InternationalCategory(stringResource(R.string.country_korean), "🇰🇷", listOf("korean", "korea", "south korea", "south-korea", "کره", "한국", "kr")),
        InternationalCategory(stringResource(R.string.country_indian), "🇮🇳", listOf("indian", "india", "bollywood", "hindi", "هند", "بالیوود", "in")),
        InternationalCategory(stringResource(R.string.country_spanish), "🇪🇸", listOf("spanish", "spain", "español", "espanol", "اسپانیا", "es")),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        items(categories, key = { it.displayName }) { category ->
            // Try to find the countryId from the loaded country rails.
            val rail = countryRails?.find { it.title == category.displayName }
            val countryId = rail?.countryId ?: 0
            CompactCountryCard(
                emoji = category.emoji,
                label = category.displayName,
                onClick = {
                    if (countryId > 0) {
                        // Country ID is known — navigate directly.
                        navController.navigate("country/$countryId")
                    } else {
                        // Country rails haven't resolved this country yet.
                        // Look it up from the API on the fly so navigation
                        // always works.
                        scope.launch {
                            try {
                                val repo = com.pishimovie.data.repository.CountryRepository()
                                val allCountries = repo.getAllCountries()
                                val match = allCountries.firstOrNull { c ->
                                    val title = c.title.trim().lowercase()
                                    category.synonyms.any { syn ->
                                        title == syn ||
                                        title.contains(syn) ||
                                        syn.contains(title) ||
                                        title.startsWith("$syn ") ||
                                        title.contains(" $syn")
                                    }
                                }
                                if (match != null) {
                                    navController.navigate("country/${match.id}")
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.country_not_available),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.failed_load_country),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
            )
        }
    }
}

private data class InternationalCategory(
    val displayName: String,
    val emoji: String,
    val synonyms: List<String>,
)

@Composable
private fun CompactCountryCard(
    emoji: String,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        // TV: press feedback snaps instead of springing (see TvMotion).
        animationSpec = tvSpringOrSnap(),
        label = "country_scale",
    )
    Row(
        modifier = Modifier
            .height(48.dp)
            .width(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    )
                )
            )
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Hero carousel (Phase 11: Premium Netflix/Disney+ style)
// ───────────────────────────────────────────────────────────────────────────


// ──────────────────────────────────────────────────────────────────────────
// HERO CAROUSEL — Premium Netflix/Disney+ style (Phase 15)
// Full-width blurred background, centered poster, adjacent previews,
// auto-scroll every 5s, premium scale animation, rounded posters.
// ──────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(movies: List<Movie>, navController: NavController) {
    if (movies.isEmpty()) return

    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    // Increase hero height to match premium feel
    val carouselHeight = 560.dp

    // Infinite page setup: start in the middle of a huge range
    val infiniteCount = Int.MAX_VALUE
    val startIndex = infiniteCount / 2 - (infiniteCount / 2 % movies.size)
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { infiniteCount }
    )

    // Track which movie is "current" for the blurred backdrop
    var currentIndex by remember { mutableIntStateOf(pagerState.currentPage % movies.size) }

    // TV / reduced motion policy for this carousel (see TvMotion).
    val reducedMotion = rememberReducedMotion()

    // Auto-advance every 5 seconds — disabled on TV: D-pad users advance
    // manually, and the perpetual animation churn stutters on weak TV GPUs.
    if (!reducedMotion) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(5000)
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1,
                    animationSpec = PishiMotion.tweenXl()
                )
            }
        }
    }

    // Update current index as user swipes
    LaunchedEffect(pagerState.currentPage) {
        currentIndex = pagerState.currentPage % movies.size
    }

    // ── Background: blurred poster ──
    val currentMovie = remember(currentIndex) { movies[currentIndex] }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(carouselHeight)
    ) {
        // Blurred wallpaper layer
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(currentMovie.cover.ifEmpty { currentMovie.image })
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .pishiBlur(radius = 60.dp)
                .scale(1.15f),
            contentScale = ContentScale.Crop,
        )

        // Dark gradient overlay (top + bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pishiAmbientGradient(
                    surfaceColor = MaterialTheme.colorScheme.background,
                    accentColor = MaterialTheme.colorScheme.primary,
                    accentAlpha = 0.20f,
                )
        )

        // ── HorizontalPager for the carousel ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            contentPadding = PaddingValues(horizontal = 60.dp),
            pageSpacing = (-20).dp,  // slight overlap to show adjacent posters
        ) { page ->
            val movie = movies[page % movies.size]
            val pageOffset = pagerState.calculateCurrentOffsetForPage(page)

            // Scale: center item = 1.0, adjacent = ~0.85
            val scale = 1f - (0.15f * abs(pageOffset))
            val alpha = 1f - (0.30f * abs(pageOffset))
            val elevation = (12f - 6f * abs(pageOffset)).dp

            // Poster shape
            val posterShape = RoundedCornerShape(20.dp)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 24.dp, bottom = 160.dp)
                    .width(260.dp)  // poster width
                    .scale(scale)
                    .alpha(alpha)
                    .graphicsLayer {
                        // Subtle 3D rotation effect for non-center items —
                        // disabled on TV (offscreen 3D layers are expensive
                        // on weak TV GPUs).
                        rotationY = if (reducedMotion) 0f else pageOffset * 15f
                        cameraDistance = 12f * density
                        shadowElevation = elevation.toPx()
                        shape = posterShape
                        clip = true
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (scale > 0.95f) {
                            // Navigate to detail — only center item
                            if (movie.type == "series") {
                                StorageUtils.saveSeriesToFile(context, movie.toSeries())
                                navController.navigate("single_series/${movie.id}")
                            } else {
                                StorageUtils.saveMovieToFile(context, movie)
                                navController.navigate("single_movie/${movie.id}")
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Poster image with rounded corners + shadow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = elevation + 4.dp,
                            shape = posterShape,
                            ambientColor = Color.Black.copy(alpha = 0.5f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        )
                        .clip(posterShape)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(movie.image)
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        // ── Bottom info overlay (always visible) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        )

        // Movie info — title, rating, year, genres
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title with drop shadow for readability
            Text(
                text = currentMovie.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                ),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pishiTitleShadow(),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Meta row: IMDb ● Year ● Genre
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // IMDb badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF5C518),  // IMDb yellow
                    modifier = Modifier.height(20.dp)
                ) {
                    Text(
                        text = "IMDb ${currentMovie.imdb}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "${currentMovie.year}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )

                if (currentMovie.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentMovie.genres.take(2).joinToString(" / ") { it.title },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Buttons: Play + More Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Play Button
                val isSeries = currentMovie.type == "series"
                Button(
                    onClick = {
                        if (isSeries) {
                            StorageUtils.saveSeriesToFile(context, currentMovie.toSeries())
                            navController.navigate("single_series/${currentMovie.id}")
                        } else {
                            StorageUtils.saveMovieToFile(context, currentMovie)
                            navController.navigate("single_movie/${currentMovie.id}")
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .width(140.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.play),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // More Info Button (glass-style)
                Surface(
                    onClick = {
                        if (isSeries) {
                            StorageUtils.saveSeriesToFile(context, currentMovie.toSeries())
                            navController.navigate("single_series/${currentMovie.id}")
                        } else {
                            StorageUtils.saveMovieToFile(context, currentMovie)
                            navController.navigate("single_movie/${currentMovie.id}")
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.more),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // ── Page indicators (dots) ──
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            movies.forEachIndexed { idx, _ ->
                val isActive = idx == currentIndex
                Box(
                    modifier = Modifier
                        .size(if (isActive) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) Color.White
                            else Color.White.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

/** Drop shadow on text for readability over any poster brightness. */
private fun Modifier.pishiTitleShadow(): Modifier = this
    .shadow(
        elevation = 4.dp,
        shape = RoundedCornerShape(4.dp),
        ambientColor = Color.Black.copy(alpha = 0.6f),
        spotColor = Color.Black.copy(alpha = 0.4f),
    )
/**
 * Pager extension to calculate the offset for a specific page.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun PagerState.calculateCurrentOffsetForPage(page: Int): Float {
    return (currentPage - page) + currentPageOffsetFraction
}

// ───────────────────────────────────────────────────────────────────────────
// Movie / Series rows
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun MovieRow(movies: List<Movie>, navController: NavController) {
    val dimens = LocalPishiDimens.current
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(movies, key = { it.id }) { movie ->
            PishiPosterCard(
                title = movie.title,
                year = movie.year,
                rating = movie.imdb,
                imageUrl = movie.image,
                onClick = {
                    StorageUtils.saveMovieToFile(context, movie)
                    navController.navigate("single_movie/${movie.id}")
                },
                cardWidth = dimens.posterWidth,
                genres = movie.genres.firstOrNull()?.title,
                typeLabel = stringResource(R.string.movies),
            )
        }
    }
}

@Composable
private fun SeriesRow(series: List<Series>, navController: NavController) {
    val dimens = LocalPishiDimens.current
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(series, key = { it.id }) { s ->
            PishiPosterCard(
                title = s.title,
                year = s.year,
                rating = s.imdb,
                imageUrl = s.image,
                onClick = {
                    StorageUtils.saveSeriesToFile(context, s)
                    navController.navigate("single_series/${s.id}")
                },
                cardWidth = dimens.posterWidth,
                genres = s.genres.firstOrNull()?.title,
                typeLabel = stringResource(R.string.series),
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Poster row (for cross-type rails)
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun PosterRow(
    posters: List<Poster>,
    navController: NavController,
) {
    val dimens = LocalPishiDimens.current
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(posters, key = { "${it.type}_${it.id}" }) { poster ->
            PishiPosterCard(
                title = poster.title,
                year = poster.year,
                rating = poster.imdb,
                imageUrl = poster.image,
                onClick = {
                    if (poster.isMovie()) {
                        StorageUtils.saveMovieToFile(context, poster.toMovie())
                        navController.navigate("single_movie/${poster.id}")
                    } else {
                        StorageUtils.saveSeriesToFile(context, poster.toSeries())
                        navController.navigate("single_series/${poster.id}")
                    }
                },
                cardWidth = dimens.posterWidth,
                genres = poster.genres.firstOrNull()?.title,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Mood Mode row
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun MoodRow(
    moods: List<PishiMood>,
    navController: NavController,
) {
    val dimens = LocalPishiDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(moods, key = { it.id }) { mood ->
            MoodCard(mood = mood, onClick = {
                navController.navigate("mood/${mood.id}")
            })
        }
    }
}

@Composable
private fun MoodCard(mood: PishiMood, onClick: () -> Unit) {
    val dimens = LocalPishiDimens.current
    val accent = Color(mood.accentHex)
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .clip(ShapeCardDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent, accent.copy(alpha = 0.4f))
                )
            )
            .clickable { onClick() }
            .padding(dimens.md),
    ) {
        Text(
            text = mood.emoji,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = mood.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mood.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Collections
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun CollectionsRow(collections: List<PishiCollection>, navController: NavController) {
    val dimens = LocalPishiDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(collections, key = { it.id }) { collection ->
            CollectionCard(collection = collection, onClick = {
                navController.navigate("collection/${collection.id}")
            })
        }
    }
}

@Composable
private fun CollectionCard(collection: PishiCollection, onClick: () -> Unit) {
    val dimens = LocalPishiDimens.current
    val accent = Color(collection.accentHex)
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(120.dp)
            .clip(ShapeCardDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent, accent.copy(alpha = 0.4f))
                )
            )
            .clickable { onClick() }
            .padding(dimens.md),
    ) {
        // Big emoji decoration
        Text(
            text = collection.emoji,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 0.dp),
        )
        // Title + subtitle
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = collection.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
