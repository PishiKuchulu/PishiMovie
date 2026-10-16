package com.pishimovie.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pishimovie.components.ExpandableText
import com.pishimovie.R
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.FavoriteItem
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.ContinueWatchingRepository
import com.pishimovie.data.repository.SimilarContentRepository
import com.pishimovie.ui.components.CinematicBackdrop
import com.pishimovie.ui.components.CinematicReveal
import com.pishimovie.ui.components.PishiBrandingFooter
import com.pishimovie.ui.components.PishiChip
import com.pishimovie.ui.components.PishiGlassTopScrim
import com.pishimovie.player.PlayerLauncher
import com.pishimovie.ui.components.PishiMetadataBadge
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiRatingPill
import com.pishimovie.ui.components.PishiSectionHeader
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.theme.CinematicPalette
import com.pishimovie.ui.theme.PishiMotion
import com.pishimovie.ui.theme.extractCinematicPalette
import com.pishimovie.ui.theme.ShapeCardDefault
import com.pishimovie.utils.DownloadUtils
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.CancellationException

/**
 * PishiMovie premium cinematic movie detail screen.
 *
 * Layers (back to front):
 *  1. [CinematicBackdrop] — blurred cover + ambient gradient + vignette
 *  2. Collapsing header — poster + title + metadata + floating Play button
 *  3. Action row — Play, Favorite, Watchlist, Share, Download
 *  4. Genres chips
 *  5. Synopsis (expandable)
 *  6. Rich metadata grid — year, runtime, rating, language, country, genres
 *  7. Cast / Director / Studio (derived from description if not available)
 *  8. Similar Movies rail
 *  9. Recommended Movies rail
 *
 * The entire screen reacts to scroll position via a parallax effect on the
 * backdrop and a dynamic top-bar alpha.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleMovieScreen(
    movieId: Int,
    navController: NavController,
) {
    val context = LocalContext.current
    var movie by remember { mutableStateOf<Movie?>(null) }
    var palette by remember { mutableStateOf<CinematicPalette?>(null) }
    var similar by remember { mutableStateOf<List<Poster>?>(null) }
    var recommended by remember { mutableStateOf<List<Poster>?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    // Issue 2: Movies now use the SAME download flow as series — a quality
    // selection dialog (DownloadMenu) followed by DownloadOptionsDialog which
    // hands off to an external app (Browser/ADM/VLC/MXPlayer/KMPlayer/Copy).
    // The broken in-app DownloadManager is no longer used for either.
    var showMovieDownloadDialog by remember { mutableStateOf(false) }
    // Phase 1/Part 6: resume-playback affordance. Loaded from the existing
    // ContinueWatchingRepository — no new persistence layer added.
    var resumeItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    // 2.8: load/error states for the movie itself. A null movie (cleared
    // cache, corrupt file, deep link) previously rendered an endless spinner.
    // movieLoadKey re-runs the load below when the user taps Retry.
    var movieLoadKey by remember { mutableStateOf(0) }
    var isLoadingMovie by remember { mutableStateOf(true) }
    var movieLoadError by remember { mutableStateOf<String?>(null) }
    // 2.8: related-content (similar/recommended) load failures.
    var similarError by remember { mutableStateOf(false) }
    var recommendedError by remember { mutableStateOf(false) }

    // Load movie from local cache. Retry bumps movieLoadKey to re-run.
    LaunchedEffect(movieId, movieLoadKey) {
        isLoadingMovie = true
        movieLoadError = null
        try {
            movie = StorageUtils.loadMovieFromFile(context, movieId)
            movie?.let {
                isFavorite = StorageUtils.isFavorite(context, it.id, "movie")
            }
            // Look up any saved resume position for this movie.
            resumeItem = ContinueWatchingRepository(context).get(movieId, "movie")
            if (movie == null) {
                movieLoadError = context.getString(R.string.movie_error_message)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            movie = null
            movieLoadError = context.getString(R.string.movie_error_message)
        } finally {
            isLoadingMovie = false
        }
    }

    // Extract cinematic palette once the movie loads
    LaunchedEffect(movie?.cover, movie?.image) {
        val url = movie?.cover?.ifEmpty { movie?.image }
        if (url != null) {
            palette = extractCinematicPalette(context, url)
        }
    }

    // Fetch similar + recommended in parallel (reuses SearchRepository).
    // 2.8: network/parse failures must not escape the coroutine and crash.
    LaunchedEffect(movie?.title, movie?.genres) {
        val m = movie ?: return@LaunchedEffect
        val repo = SimilarContentRepository()
        try {
            similar = repo.getSimilar(m.title, m.genres)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            similar = emptyList()
            similarError = true
        }
        try {
            recommended = repo.getRecommended(m.title, excludeIds = similar?.map { it.id }?.toSet() ?: emptySet())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            recommended = emptyList()
            recommendedError = true
        }
    }

    val currentMovie = movie
    if (currentMovie == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingMovie) {
                // Genuine loading state
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                // 2.8: load failed (missing/corrupt cache entry) — real error state with retry
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.movie_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = movieLoadError ?: stringResource(R.string.movie_error_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { movieLoadKey++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        return
    }

    val scrollState = rememberLazyListState()
    // Parallax: 0 when at top, → 1 as the user scrolls past the header.
    // firstVisibleItemIndex must be checked: once the header scrolls off,
    // the new first item's offset resets to ~0 and the top bar would vanish.
    val parallax by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) {
                (scrollState.firstVisibleItemScrollOffset.toFloat() / 800f).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val accent = palette?.accentColor ?: MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            // ── Cinematic backdrop + header ──────────────────────────────
            item(key = "header") {
                CinematicMovieHeader(
                    movie = currentMovie,
                    palette = palette,
                    parallax = parallax,
                    onBackClick = { navController.popBackStack() },
                )
            }

            // ── Action row ────────────────────────────────────────────────
            item(key = "actions") {
                MovieActionRow(
                    movie = currentMovie,
                    isFavorite = isFavorite,
                    accent = accent,
                    resumeItem = resumeItem,
                    onResumeClick = { ri ->
                        // Shared launcher for resume too.
                        val url = ri.sourceUrl ?: return@MovieActionRow
                        PlayerLauncher.launchPlayback(
                            context = context,
                            videoUrl = url,
                            title = currentMovie.title,
                            image = currentMovie.image,
                            movieId = currentMovie.id,
                            resumePositionMs = ri.positionMs,
                            genres = currentMovie.genres
                        )
                    },
                    onFavoriteToggle = {
                        if (isFavorite) {
                            StorageUtils.removeFavorite(context, currentMovie.id, "movie")
                            isFavorite = false
                            Toast.makeText(context, context.getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show()
                        } else {
                            val fav = FavoriteItem(
                                id = currentMovie.id,
                                type = "movie",
                                title = currentMovie.title,
                                description = currentMovie.description,
                                year = currentMovie.year,
                                imdb = currentMovie.imdb,
                                rating = currentMovie.rating,
                                duration = currentMovie.duration,
                                image = currentMovie.image,
                                cover = currentMovie.cover,
                                genres = currentMovie.genres,
                                country = currentMovie.country,
                                sources = currentMovie.sources,
                            )
                            StorageUtils.saveFavorite(context, fav)
                            isFavorite = true
                            Toast.makeText(context, context.getString(R.string.added_to_favorites), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShareClick = {
                        DownloadUtils.copyToClipboard(context, currentMovie.title)
                        Toast.makeText(context, context.getString(R.string.title_copied), Toast.LENGTH_SHORT).show()
                    },
                    onDownloadClick = {
                        // Phase 9: open the quality-selection dialog instead of
                        // immediately enqueuing the first source. This mirrors
                        // the existing series flow (Season → Episode → Quality
                        // → Download) and lets the user pick a quality.
                        showMovieDownloadDialog = true
                    },
                )
            }

            // ── Genres ───────────────────────────────────────────────────
            if (currentMovie.genres.isNotEmpty()) {
                item(key = "genres") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(currentMovie.genres, key = { it.id }) { genre ->
                            PishiChip(
                                text = genre.title,
                                selected = false,
                                onClick = {
                                    navController.navigate("movies?genreId=${genre.id}")
                                },
                            )
                        }
                    }
                }
            }

            // ── Synopsis ────────────────────────────────────────────────
            item(key = "synopsis") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.synopsis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ExpandableText(
                        text = currentMovie.description,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Rich metadata grid ───────────────────────────────────────
            item(key = "metadata") {
                MovieMetadataGrid(movie = currentMovie)
            }

            // ── Similar Movies ──────────────────────────────────────────
            if (similar == null) {
                item(key = "similar_skeleton") {
                    PishiShimmerBox(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(180.dp),
                        cornerRadius = 20.dp,
                    )
                }
            } else if (similar!!.isNotEmpty()) {
                item(key = "similar_header") {
                    PishiSectionHeader(title = stringResource(R.string.more_like_this))
                }
                item(key = "similar_row") {
                    SimilarMoviesRow(
                        posters = similar!!,
                        navController = navController,
                        context = context,
                    )
                }
            } else if (similarError) {
                item(key = "similar_error") {
                    Text(
                        text = stringResource(R.string.movie_related_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // ── Recommended Movies ──────────────────────────────────────
            if (recommended == null) {
                item(key = "rec_skeleton") {
                    PishiShimmerBox(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(180.dp),
                        cornerRadius = 20.dp,
                    )
                }
            } else if (recommended!!.isNotEmpty()) {
                item(key = "rec_header") {
                    PishiSectionHeader(title = stringResource(R.string.recommended_for_you))
                }
                item(key = "rec_row") {
                    SimilarMoviesRow(
                        posters = recommended!!,
                        navController = navController,
                        context = context,
                    )
                }
            } else if (recommendedError) {
                item(key = "rec_error") {
                    Text(
                        text = stringResource(R.string.movie_related_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            item(key = "footer") {
                PishiBrandingFooter()
            }
        }

        // Issue 2: Movies use the SAME DownloadMenu as series — quality
        // selection → DownloadOptionsDialog → external app. No in-app
        // DownloadManager. This is the single unified download flow.
        if (showMovieDownloadDialog && currentMovie.sources.isNotEmpty()) {
            DownloadMenu(
                sources = currentMovie.sources,
                onDismiss = { showMovieDownloadDialog = false },
                onDownload = { _ ->
                    // DownloadOptionsDialog (rendered inside DownloadMenu) handles
                    // the actual hand-off to Browser/ADM/VLC/MXPlayer/KMPlayer.
                    showMovieDownloadDialog = false
                },
                onPlayInternal = { source ->
                    showMovieDownloadDialog = false
                    PlayerLauncher.launchPlayback(
                        context = context,
                        videoUrl = source.url,
                        title = currentMovie.title,
                        image = currentMovie.image,
                        movieId = currentMovie.id,
                        resumePositionMs = resumeItem?.positionMs ?: 0L,
                        genres = currentMovie.genres,
                        forceInternal = true
                    )
                }
            )
        }

        // Top bar — fades in as the user scrolls past the header
        val topBarAlpha by animateFloatAsState(
            targetValue = if (parallax > 0.5f) 1f else 0f,
            animationSpec = PishiMotion.tweenMedium(),
            label = "top_bar_alpha",
        )
        if (topBarAlpha > 0f) {
            PishiGlassTopScrim(modifier = Modifier.align(Alignment.TopCenter), height = 80.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .alpha(topBarAlpha)
                    .padding(top = statusBarHeight)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text = currentMovie.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Cinematic header
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun CinematicMovieHeader(
    movie: Movie,
    palette: CinematicPalette?,
    parallax: Float,
    onBackClick: () -> Unit,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val accent = palette?.accentColor ?: MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxWidth()) {
        // Blurred cinematic backdrop (parallax-driven)
        CinematicBackdrop(
            imageUrl = movie.cover.ifEmpty { movie.image },
            palette = palette ?: CinematicPalette(),
            parallaxOffset = parallax,
            backdropHeight = 480.dp,
        )

        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarHeight + 48.dp),
        ) {
            // Back button row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }
            }

            // Poster + title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Poster
                CinematicReveal(visible = palette != null) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(movie.image)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = movie.title,
                        modifier = Modifier
                            .width(140.dp)
                            .height(210.dp)
                            .clip(ShapeCardDefault),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Title + metadata
                Column(
                    modifier = Modifier.weight(1f).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Year • Rating • Duration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PishiMetadataBadge(text = movie.year.toString())
                        PishiRatingPill(rating = movie.imdb)
                        if (movie.duration != null) {
                            PishiMetadataBadge(text = movie.duration!!)
                        }
                    }
                    // Country
                    if (movie.country.isNotEmpty()) {
                        Text(
                            text = movie.country.joinToString(", ") { it.title },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Action row
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun MovieActionRow(
    movie: Movie,
    isFavorite: Boolean,
    accent: Color,
    resumeItem: ContinueWatchingItem?,
    onResumeClick: (ContinueWatchingItem) -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val context = LocalContext.current
    val firstSource = movie.sources.firstOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Resume
        if (resumeItem != null && resumeItem.positionMs > 0 && resumeItem.sourceUrl != null) {
            ActionChip(
                icon = Icons.Default.PlayArrow,
                label = stringResource(R.string.resume_percent, (resumeItem.progress * 100).toInt()),
                active = true,
                accent = accent,
                onClick = { onResumeClick(resumeItem) },
            )
        }
        
        // Favorite
        ActionChip(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (isFavorite) stringResource(R.string.saved_label) else stringResource(R.string.favorite_label),
            active = isFavorite,
            accent = accent,
            onClick = onFavoriteToggle,
        )

        // Share
        ActionChip(
            icon = Icons.Default.Share,
            label = stringResource(R.string.share),
            active = false,
            accent = accent,
            onClick = onShareClick,
        )
        // Download
        if (firstSource != null) {
            ActionChip(
                icon = Icons.Default.Download,
                label = stringResource(R.string.download),
                active = false,
                accent = accent,
                onClick = onDownloadClick,
            )
        }
    }
}

// Issue 2: MovieDownloadQualityDialog removed — movies now use the SAME
// DownloadMenu (quality → DownloadOptionsDialog → external app) as series.

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = PishiMotion.springPress(),
        label = "chip_scale",
    )
    Column(
        modifier = Modifier
            .scale(scale)
            .clip(ShapeCardDefault)
            .background(
                if (active) accent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) accent else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Metadata grid
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun MovieMetadataGrid(movie: Movie) {
    val dimens = com.pishimovie.ui.theme.LocalPishiDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(ShapeCardDefault)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.movie_details),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        MetadataRow(label = stringResource(R.string.release_year), value = movie.year.toString())
        if (movie.duration != null) {
            MetadataRow(label = stringResource(R.string.runtime), value = movie.duration!!)
        }
        MetadataRow(label = stringResource(R.string.imdb_rating), value = String.format("%.1f / 10", movie.imdb))
        MetadataRow(label = stringResource(R.string.user_rating), value = String.format("%.1f / 10", movie.rating))
        if (movie.country.isNotEmpty()) {
            MetadataRow(label = stringResource(R.string.country_label), value = movie.country.joinToString(", ") { it.title })
        }
        if (movie.genres.isNotEmpty()) {
            MetadataRow(label = stringResource(R.string.genres_label), value = movie.genres.joinToString(", ") { it.title })
        }
        MetadataRow(label = stringResource(R.string.language), value = stringResource(R.string.language_original))
        MetadataRow(label = stringResource(R.string.studio), value = stringResource(R.string.pishimovie_library))
        MetadataRow(label = stringResource(R.string.director), value = stringResource(R.string.not_available))
        MetadataRow(label = stringResource(R.string.cast), value = stringResource(R.string.not_available))
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Similar / Recommended movies row
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SimilarMoviesRow(
    posters: List<Poster>,
    navController: NavController,
    context: Context,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                cardWidth = 140.dp,
                typeLabel = if (poster.isMovie()) stringResource(R.string.movies_label) else stringResource(R.string.tv_series_label),
                genres = poster.genres.firstOrNull()?.title,
            )
        }
    }
}
