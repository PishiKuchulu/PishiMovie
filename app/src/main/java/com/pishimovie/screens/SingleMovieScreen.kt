package com.pishimovie.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pishimovie.components.PlayerPickerDialog
import com.pishimovie.R
import com.pishimovie.data.model.ContinueWatchingItem
import com.pishimovie.data.model.FavoriteItem
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.Source
import com.pishimovie.data.repository.ContinueWatchingRepository
import com.pishimovie.data.repository.SimilarContentRepository
import com.pishimovie.data.repository.StatisticsRepository
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
import com.pishimovie.ui.theme.tvSpringOrSnap
import com.pishimovie.ui.theme.tvTween
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PishiMovie premium cinematic movie detail screen.
 *
 * Layers (back to front):
 *  1. [CinematicBackdrop] — blurred cover + ambient gradient + vignette
 *  2. Collapsing header — poster + title + metadata + floating Play button
 *  3. Action row — Play, Favorite, Watchlist, Share, Download
 *  4. Genres chips
 *  5. Synopsis (expandable)
 *  6. Rich metadata grid — year, runtime, rating, country, genres
 *     (only rows backed by real model data are rendered)
 *  7. Similar Movies rail
 *  8. Recommended Movies rail
 *
 * The entire screen reacts to scroll position via a parallax effect on the
 * backdrop and a dynamic top-bar alpha.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleMovieScreen(
    movieId: Int,
    navController: NavController,
    // Set by the navigation layer when the user arrives via a Play action
    // (e.g. the Home hero Play button passes ?autoplay=true): once the movie
    // and its sources are loaded, the player picker opens once automatically.
    autoplay: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    // Play flow: the Play action (and autoplay) ends in the unified
    // PlayerPickerDialog for a chosen source. With several sources the user
    // first picks a quality (showPlayQualityDialog).
    var playSource by remember { mutableStateOf<Source?>(null) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    var showPlayQualityDialog by remember { mutableStateOf(false) }
    var autoplayHandled by remember { mutableStateOf(false) }
    // Favorite removal asks for confirmation first (same as the series
    // screen); adding stays instant.
    var showRemoveFavoriteDialog by remember { mutableStateOf(false) }
    // The user's own 1..5 star rating (StatisticsRepository), distinct from
    // the IMDb/site ratings shown in the header and metadata grid.
    var userRating by remember { mutableStateOf<Int?>(null) }
    // Phase 1/Part 6: resume-playback affordance. Loaded from the existing
    // ContinueWatchingRepository — no new persistence layer added.
    var resumeItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    // 2.8: load/error states for the movie itself. A null movie (cleared
    // cache, corrupt file, deep link) previously rendered an endless spinner.
    // There is deliberately no Retry here: the only data source is the local
    // cache file, so re-reading it after a failure cannot succeed — the
    // error state offers Back instead (see the null-movie branch below).
    var isLoadingMovie by remember { mutableStateOf(true) }
    var movieLoadError by remember { mutableStateOf<String?>(null) }
    // 2.8: related-content (similar/recommended) load failures. Each rail
    // has its own reload key so its error row can retry just that rail.
    var similarError by remember { mutableStateOf(false) }
    var recommendedError by remember { mutableStateOf(false) }
    var similarReloadKey by remember { mutableStateOf(0) }
    var recommendedReloadKey by remember { mutableStateOf(0) }

    // Load movie from local cache. All file I/O runs on the IO dispatcher —
    // LaunchedEffect itself is main-thread.
    LaunchedEffect(movieId) {
        isLoadingMovie = true
        movieLoadError = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                StorageUtils.loadMovieFromFile(context, movieId)
            }
            movie = loaded
            if (loaded != null) {
                isFavorite = withContext(Dispatchers.IO) {
                    StorageUtils.isFavorite(context, loaded.id, "movie")
                }
                // Look up any saved resume position for this movie.
                resumeItem = withContext(Dispatchers.IO) {
                    ContinueWatchingRepository(context).get(movieId, "movie")
                }
                userRating = withContext(Dispatchers.IO) {
                    StatisticsRepository(context).getRating(movieId, "movie")
                }
            } else {
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

    // Fetch similar + recommended (reuses SearchRepository via
    // SimilarContentRepository, which now propagates failures instead of
    // swallowing them into empty lists — so a rail error is a real network
    // failure and its retry below is meaningful).
    // 2.8: network/parse failures must not escape the coroutine and crash.
    LaunchedEffect(movie?.title, movie?.genres, similarReloadKey) {
        val m = movie ?: return@LaunchedEffect
        similarError = false
        similar = null
        try {
            similar = SimilarContentRepository().getSimilar(m.title, m.genres)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            similar = emptyList()
            similarError = true
        }
    }
    LaunchedEffect(movie?.title, recommendedReloadKey) {
        val m = movie ?: return@LaunchedEffect
        recommendedError = false
        recommended = null
        try {
            recommended = SimilarContentRepository().getRecommended(
                m.title,
                excludeIds = similar?.map { it.id }?.toSet() ?: emptySet(),
            )
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
                // 2.8: load failed (missing/corrupt cache entry) — real error
                // state. The primary action is Back: retrying would only
                // re-read the same missing/corrupt local file and fail again,
                // because this screen has no network source for the movie.
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
                    Button(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
        }
        return
    }

    // Autoplay (arrived via a Play action with ?autoplay=true): once the
    // movie is loaded and has sources, open the play flow exactly once —
    // straight to the PlayerPickerDialog for a single source, or to the
    // quality chooser when the user still has to pick one.
    LaunchedEffect(currentMovie, autoplay) {
        if (autoplay && !autoplayHandled && currentMovie.sources.isNotEmpty()) {
            autoplayHandled = true
            if (currentMovie.sources.size == 1) {
                playSource = currentMovie.sources.first()
                showPlayerPicker = true
            } else {
                showPlayQualityDialog = true
            }
        }
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
                    onPlayClick = {
                        // Play: single source goes straight to the player
                        // picker; several sources ask for a quality first.
                        val sources = currentMovie.sources
                        if (sources.size == 1) {
                            playSource = sources.first()
                            showPlayerPicker = true
                        } else if (sources.size > 1) {
                            showPlayQualityDialog = true
                        }
                    },
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
                            // Removal asks for confirmation first — the same
                            // behavior as the series screen. Adding is instant.
                            showRemoveFavoriteDialog = true
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
                            // File I/O off the UI thread.
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    StorageUtils.saveFavorite(context, fav)
                                }
                                isFavorite = true
                                Toast.makeText(context, context.getString(R.string.added_to_favorites), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShareClick = {
                        // Real share: ACTION_SEND chooser with the title and
                        // the app name (previously this chip only copied the
                        // title to the clipboard despite being labelled Share).
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${currentMovie.title} — ${context.getString(R.string.app_name)}",
                            )
                        }
                        try {
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.share),
                                )
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.e4_share_error), Toast.LENGTH_SHORT).show()
                        }
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

            // ── User rating (own 1..5 stars, StatisticsRepository) ───────
            item(key = "user_rating") {
                UserRatingRow(
                    userRating = userRating,
                    onRate = { stars ->
                        scope.launch {
                            if (userRating == stars) {
                                // Tapping the current rating clears it.
                                withContext(Dispatchers.IO) {
                                    StatisticsRepository(context).removeRating(currentMovie.id, "movie")
                                }
                                userRating = null
                            } else {
                                withContext(Dispatchers.IO) {
                                    StatisticsRepository(context).setRating(currentMovie.id, "movie", stars)
                                }
                                userRating = stars
                            }
                        }
                    },
                    onClear = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                StatisticsRepository(context).removeRating(currentMovie.id, "movie")
                            }
                            userRating = null
                        }
                    },
                )
            }

            // ── No-source status ─────────────────────────────────────────
            if (currentMovie.sources.isEmpty()) {
                item(key = "no_source") {
                    Text(
                        text = stringResource(R.string.e4_no_source_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
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
                    // Tappable error row — retries only this rail.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { similarReloadKey++ }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.movie_related_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.retry),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                    // Tappable error row — retries only this rail.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { recommendedReloadKey++ }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.movie_related_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.retry),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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

        // Play flow: quality chooser (several sources) → PlayerPickerDialog.
        // The option list lives in the scrollable content area so it fits
        // small screens, like the other dialogs.
        if (showPlayQualityDialog) {
            AlertDialog(
                onDismissRequest = { showPlayQualityDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.choose_quality_to_play),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        currentMovie.sources.forEach { source ->
                            Button(
                                onClick = {
                                    showPlayQualityDialog = false
                                    playSource = source
                                    showPlayerPicker = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${stringResource(R.string.play)} ${source.quality}")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlayQualityDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
            )
        }

        // Play flow: the unified player picker for the chosen source (the
        // same dialog the series screen uses).
        val activePlaySource = playSource
        if (showPlayerPicker && activePlaySource != null) {
            PlayerPickerDialog(
                videoUrl = activePlaySource.url,
                title = currentMovie.title,
                image = currentMovie.image,
                movieId = currentMovie.id,
                resumePositionMs = resumeItem?.positionMs ?: 0L,
                onDismiss = {
                    showPlayerPicker = false
                    playSource = null
                },
            )
        }

        // Favorite removal confirmation (same behavior as the series screen).
        if (showRemoveFavoriteDialog) {
            AlertDialog(
                onDismissRequest = { showRemoveFavoriteDialog = false },
                title = { Text(stringResource(R.string.remove_from_favorites)) },
                text = { Text(stringResource(R.string.remove_favorite_confirm, currentMovie.title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRemoveFavoriteDialog = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    StorageUtils.removeFavorite(context, currentMovie.id, "movie")
                                }
                                isFavorite = false
                                Toast.makeText(context, context.getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveFavoriteDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Top bar — fades in as the user scrolls past the header
        val topBarAlpha by animateFloatAsState(
            targetValue = if (parallax > 0.5f) 1f else 0f,
            // TV: instant instead of a 300ms fade (see TvMotion).
            animationSpec = tvTween(PishiMotion.DurationMedium),
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
    onPlayClick: () -> Unit,
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
        // Play — the primary action. Previously first playback was only
        // reachable through the Download dialog. With no sources there is
        // no Play chip; the screen shows a no-source status row instead.
        if (firstSource != null) {
            ActionChip(
                icon = Icons.Default.PlayArrow,
                label = stringResource(R.string.play),
                active = true,
                accent = accent,
                onClick = onPlayClick,
            )
        }

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
    // D-pad focus (TV): without this the chips had no visible focus state at
    // all (indication = null, press-only scale). Follows the poster-card
    // pattern in PishiCards: focused → slight scale-up + accent highlight.
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.95f
            focused -> 1.06f
            else -> 1f
        },
        // TV: press feedback snaps instead of spring-bouncing (see TvMotion).
        animationSpec = tvSpringOrSnap(),
        label = "chip_scale",
    )
    Column(
        modifier = Modifier
            .scale(scale)
            .clip(ShapeCardDefault)
            .background(
                if (active || focused) accent.copy(alpha = if (focused) 0.25f else 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accent else Color.Transparent,
                shape = ShapeCardDefault,
            )
            .clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active || focused) accent else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active || focused) accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// User rating row (own 1..5 stars — StatisticsRepository)
// ───────────────────────────────────────────────────────────────────────────

/**
 * The signed-in user's own rating for this title, kept visibly distinct
 * from the IMDb pill (header) and the site aggregate (metadata grid).
 * Tapping a star sets it; tapping the current star again — or the remove
 * affordance — clears it.
 */
@Composable
private fun UserRatingRow(
    userRating: Int?,
    onRate: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.e4_your_rating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        for (star in 1..5) {
            val filled = userRating != null && star <= userRating
            IconButton(
                onClick = { onRate(star) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        if (userRating != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.remove))
            }
        }
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
        // NOTE: no Studio / Language / Director / Cast rows — the Movie
        // model carries none of those fields, and the previous rows rendered
        // fabricated values (the app's own name as "studio", a hard-coded
        // "original version" language, and permanent "not available").
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
    val scope = rememberCoroutineScope()
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
                    // The detail screens read the title from a local cache
                    // file — write it off the UI thread, then navigate.
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (poster.isMovie()) {
                                StorageUtils.saveMovieToFile(context, poster.toMovie())
                            } else {
                                StorageUtils.saveSeriesToFile(context, poster.toSeries())
                            }
                        }
                        navController.navigate(
                            if (poster.isMovie()) {
                                "single_movie/${poster.id}"
                            } else {
                                "single_series/${poster.id}"
                            }
                        )
                    }
                },
                cardWidth = 140.dp,
                typeLabel = if (poster.isMovie()) stringResource(R.string.movies_label) else stringResource(R.string.tv_series_label),
                genres = poster.genres.firstOrNull()?.title,
            )
        }
    }
}
