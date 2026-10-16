package com.pishimovie.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pishimovie.ui.components.PishiBrandingFooter
import com.pishimovie.components.DownloadOptionsDialog
import com.pishimovie.components.ExpandableText
import com.pishimovie.components.PlayerPickerDialog
import com.pishimovie.player.PlayerLauncher
import com.pishimovie.R
import com.pishimovie.data.model.FavoriteItem
import com.pishimovie.data.model.Episode
import com.pishimovie.data.model.Season
import com.pishimovie.data.model.Series
import com.pishimovie.data.model.Source
import androidx.compose.material.icons.filled.Check
import com.pishimovie.ui.series.SeasonsViewModel
import com.pishimovie.utils.DownloadUtils
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSeriesScreen(
    seriesId: Int,
    navController: NavController
) {
    var series by remember { mutableStateOf<Series?>(null) }
    val context = LocalContext.current
    val seasonsViewModel: SeasonsViewModel = viewModel()
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showDownloadMenu by remember { mutableStateOf(false) }
    var downloadSources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var downloadEpisodeId by remember { mutableStateOf<Int?>(null) }
    var downloadEpisodeTitle by remember { mutableStateOf<String?>(null) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    // ── Issue 1: unified player picker (same as movies) ────────────────
    var playSource by remember { mutableStateOf<Source?>(null) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    
    LaunchedEffect(seriesId) {
        series = StorageUtils.loadSeriesFromFile(context, seriesId)
        seasonsViewModel.loadSeasons(seriesId)
    }
    
    // Source selection dialog
    val dialogSeason = seasonsViewModel.seasons.getOrNull(selectedSeasonIndex)
    if (showSourceDialog && selectedEpisode != null && series != null && dialogSeason != null) {
        SourceOptionsDialog(
            episode = selectedEpisode!!,
            series = series!!,
            season = dialogSeason,
            onDismiss = { showSourceDialog = false },
            onDownload = { source ->
                showSourceDialog = false
                // Issue 2: download uses DownloadMenu (quality → options → external app)
                downloadSources = selectedEpisode!!.sources
                downloadEpisodeId = selectedEpisode!!.id
                downloadEpisodeTitle = selectedEpisode!!.title
                showDownloadMenu = true
            },
            onPlay = { source ->
                // Shared launcher — Internal Player by default.
                showSourceDialog = false
                PlayerLauncher.launchPlayback(
                    context = context,
                    videoUrl = source.url,
                    title = series!!.title,
                    image = series!!.image,
                    seriesId = series!!.id,
                    seasonId = dialogSeason.id,
                    episodeId = selectedEpisode!!.id,
                    genres = series!!.genres
                )
            }
        )
    }

    // Issue 1: unified player picker for series episodes.
    val activePlaySource = playSource
    val activeSeries = series
    val activeEpisode = selectedEpisode
    val activeSeasonId = seasonsViewModel.seasons.getOrNull(selectedSeasonIndex)?.id
    if (showPlayerPicker && activePlaySource != null && activeSeries != null) {
        PlayerPickerDialog(
            videoUrl = activePlaySource.url,
            title = activeSeries.title,
            image = activeSeries.image,
            seriesId = activeSeries.id,
            seasonId = activeSeasonId,
            episodeId = activeEpisode?.id,
            onDismiss = {
                showPlayerPicker = false
                playSource = null
            },
        )
    }
    
    // Download menu — restored to the ORIGINAL working series flow:
    // quality selection → DownloadOptionsDialog → external app
    // (Browser/ADM/VLC/MXPlayer/KMPlayer/CopyLink). Both movies and series
    // now use this identical flow (see SingleMovieScreen).
    if (showDownloadMenu && downloadSources.isNotEmpty()) {
        DownloadMenu(
            sources = downloadSources,
            onDismiss = { showDownloadMenu = false },
            onDownload = { source ->
                // The actual download/playback is handled by DownloadOptionsDialog
                // inside DownloadMenu (it opens Browser/ADM/VLC/MXPlayer/KMPlayer).
                // This callback is invoked after the user picks a quality.
                showDownloadMenu = false
            },
        )
    }
    
    // Directly render content without Scaffold since it's already in MainScreen's Scaffold
    if (series != null) {
        SeriesDetailsContent(
            series = series!!,
            seasonsViewModel = seasonsViewModel,
            selectedSeasonIndex = selectedSeasonIndex,
            onSeasonSelected = { selectedSeasonIndex = it },
            onBackClick = { navController.popBackStack() },
            onEpisodeClick = { episode ->
                if (episode.sources.isNotEmpty() && series != null) {
                    val selectedSeason = seasonsViewModel.seasons.getOrNull(selectedSeasonIndex)
                    if (episode.sources.size > 1) {
                        // Show source selection dialog if there are multiple sources
                        selectedEpisode = episode
                        showSourceDialog = true
                    } else {
                        // Single source — use shared launcher.
                        selectedEpisode = episode
                        PlayerLauncher.launchPlayback(
                            context = context,
                            videoUrl = episode.sources[0].url,
                            title = series!!.title,
                            image = series!!.image,
                            seriesId = series!!.id,
                            seasonId = selectedSeason?.id,
                            episodeId = episode.id,
                            genres = series!!.genres
                        )
                    }
                }
            },
            onDownloadClick = { episode ->
                if (episode.sources.isNotEmpty()) {
                    downloadSources = episode.sources
                    downloadEpisodeId = episode.id
                    downloadEpisodeTitle = episode.title
                    showDownloadMenu = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Show loading or error state
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
                Text(
                    text = stringResource(R.string.series_not_found),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun SourceOptionsDialog(
    episode: Episode,
    series: Series,
    season: Season,
    onDismiss: () -> Unit,
    onDownload: (Source) -> Unit,
    onPlay: (Source) -> Unit
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf<Source?>(null) }
    var showDownloadOptions by remember { mutableStateOf(false) }
    
    if (showDownloadOptions && selectedSource != null) {
        DownloadOptionsDialog(
            source = selectedSource!!,
            onDismiss = { showDownloadOptions = false },
            onCopyLink = { DownloadUtils.copyToClipboard(context, selectedSource!!.url) },
            onDownloadWithBrowser = { DownloadUtils.openUrl(context, selectedSource!!.url) },
            onDownloadWithADM = { DownloadUtils.openWithADM(context, selectedSource!!.url) },
            onOpenInVLC = { DownloadUtils.openWithVLC(context, selectedSource!!.url) },
            onOpenInMXPlayer = { DownloadUtils.openWithMXPlayer(context, selectedSource!!.url) },
            onOpenInKMPlayer = { DownloadUtils.openWithKMPlayer(context, selectedSource!!.url) }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.episode)}: ${episode.title}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.choose_quality_to_play),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play buttons for each source/quality
                episode.sources.forEach { source ->
                    Button(
                        onClick = { onPlay(source) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = androidx.compose.material3.ButtonDefaults.elevatedButtonElevation()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${stringResource(R.string.play)} ${source.quality}")
                    }
                }
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp
    )
}

@Composable
fun DownloadMenu(
    sources: List<Source>,
    onDismiss: () -> Unit,
    onDownload: (Source) -> Unit,
    onPlayInternal: ((Source) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf<Source?>(null) }
    var showDownloadOptions by remember { mutableStateOf(false) }
    
    // If there's only one source, directly set it as selected and show download options
    LaunchedEffect(sources) {
        if (sources.size == 1) {
            selectedSource = sources[0]
            showDownloadOptions = true
        }
    }
    
    if (showDownloadOptions && selectedSource != null) {
        DownloadOptionsDialog(
            source = selectedSource!!,
            onDismiss = { 
                showDownloadOptions = false
                // If we only had one source, also dismiss the menu
                if (sources.size == 1) {
                    onDismiss()
                }
            },
            onCopyLink = { DownloadUtils.copyToClipboard(context, selectedSource!!.url) },
            onDownloadWithBrowser = { DownloadUtils.openUrl(context, selectedSource!!.url) },
            onDownloadWithADM = { DownloadUtils.openWithADM(context, selectedSource!!.url) },
            onOpenInVLC = { DownloadUtils.openWithVLC(context, selectedSource!!.url) },
            onOpenInMXPlayer = { DownloadUtils.openWithMXPlayer(context, selectedSource!!.url) },
            onOpenInKMPlayer = { DownloadUtils.openWithKMPlayer(context, selectedSource!!.url) },
            onPlayInternal = if (onPlayInternal != null) {
                { onPlayInternal(selectedSource!!) }
            } else null
        )
    }
    
    // Only show the quality selection if there are multiple sources
    if (sources.size > 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.choose_quality_to_download),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.download_play_options_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { source ->
                        Button(
                            onClick = { 
                                selectedSource = source
                                showDownloadOptions = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = androidx.compose.material3.ButtonDefaults.elevatedButtonElevation()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.download),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${source.quality}")
                        }
                    }
                    
                    // Cancel button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        )
    }
}

// Issue 2: SeriesDownloadQualityDialog removed — series and movies both use
// the original DownloadMenu (quality → DownloadOptionsDialog → external app).


@Composable
fun SeriesDetailsContent(
    series: Series,
    seasonsViewModel: SeasonsViewModel,
    selectedSeasonIndex: Int,
    onSeasonSelected: (Int) -> Unit,
    onBackClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onDownloadClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEpisodeImageDialog by remember { mutableStateOf(false) }
    var episodeImageUrl by remember { mutableStateOf("") }

    // Cinematic color extraction
    var cinematicPalette by remember { mutableStateOf<com.pishimovie.ui.theme.CinematicPalette?>(null) }
    LaunchedEffect(series.cover, series.image) {
        val url = series.cover.ifEmpty { series.image }
        if (url.isNotBlank()) {
            cinematicPalette = com.pishimovie.ui.theme.extractCinematicPalette(context, url)
        }
    }
    val accent = cinematicPalette?.accentColor ?: MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
    ) {
        item {
            // ── Cinematic series header ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                // Cinematic backdrop (blurred + ambient gradient + vignette)
                com.pishimovie.ui.components.CinematicBackdrop(
                    imageUrl = series.cover.ifEmpty { series.image },
                    palette = cinematicPalette ?: com.pishimovie.ui.theme.CinematicPalette(),
                    parallaxOffset = 0f,
                    backdropHeight = 420.dp,
                )

                // Row to contain poster and series details side by side
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Foreground series poster
                    var showImageDialog by remember { mutableStateOf(false) }

                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(series.image)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = series.title,
                        modifier = Modifier
                            .height(200.dp)
                            .clip(com.pishimovie.ui.theme.ShapeCardDefault)
                            .clickable { showImageDialog = true },
                        contentScale = ContentScale.Crop
                    )

                    // Image URL dialog
                    if (showImageDialog) {
                        AlertDialog(
                            onDismissRequest = { showImageDialog = false },
                            title = { Text(stringResource(R.string.image_options)) },
                            text = { Text(stringResource(R.string.choose_action_for_image)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        DownloadUtils.copyToClipboard(context, series.image)
                                        showImageDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.copy_image_url))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showImageDialog = false }
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    // Series details to the right of the poster
                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                    ) {
                        // Series title
                        Text(
                            text = series.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        // Country and year
                        val countryText = if (series.country.isNotEmpty()) {
                            "${series.country.joinToString(", ") { it.title }} (${series.year})"
                        } else {
                            "(${series.year})"
                        }

                        Text(
                            text = countryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        // Rating
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            com.pishimovie.ui.components.PishiRatingPill(rating = series.imdb)
                        }
                    }
                }

                // Back button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }

                // Favorite button
                var isFavorite by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val seriesId = series.id

                // Check if series is already favorite
                LaunchedEffect(seriesId) {
                    isFavorite = StorageUtils.isFavorite(context, seriesId, "series")
                }

                var showRemoveFavoriteDialog by remember { mutableStateOf(false) }

                // Confirmation dialog for removing from favorites
                if (showRemoveFavoriteDialog) {
                    AlertDialog(
                        onDismissRequest = { showRemoveFavoriteDialog = false },
                        title = { Text(stringResource(R.string.remove_from_favorites)) },
                        text = { Text(stringResource(R.string.remove_favorite_confirm, series.title)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    StorageUtils.removeFavorite(context, seriesId, "series")
                                    isFavorite = false
                                    showRemoveFavoriteDialog = false
                                    android.widget.Toast.makeText(context, context.getString(R.string.removed_from_favorites), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(stringResource(R.string.remove))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showRemoveFavoriteDialog = false }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                IconButton(
                    onClick = {
                        if (isFavorite) {
                            showRemoveFavoriteDialog = true
                        } else {
                            val favoriteItem = FavoriteItem(
                                id = series.id,
                                type = "series",
                                title = series.title,
                                description = series.description,
                                year = series.year,
                                imdb = series.imdb,
                                rating = series.rating,
                                duration = series.duration,
                                image = series.image,
                                cover = series.cover,
                                genres = series.genres,
                                country = series.country
                            )
                            StorageUtils.saveFavorite(context, favoriteItem)
                            isFavorite = true
                            android.widget.Toast.makeText(context, context.getString(R.string.added_to_favorites), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.favorite_label),
                        tint = if (isFavorite) accent else Color.White
                    )
                }
            }
        }
        
        item {
            // Genres
            if (series.genres.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.genres),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                
                // Improved genres display with better wrapping and styling
                LazyRow(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(series.genres) { genre ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(50.dp), // More rounded corners
                            modifier = Modifier
                                .height(32.dp) // Fixed height for consistency
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = genre.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item {
            // Description
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.synopsis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        item {
            // Set layout direction to RTL for the description text
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ExpandableText(
                    text = series.description,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                )
            }
        }
        
        // Seasons selection
        if (seasonsViewModel.seasons.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.seasons),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
            }
            
            item {
                LazyRow(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(seasonsViewModel.seasons, key = { _, season -> season.id }) { index, season ->
                        Card(
                            modifier = Modifier
                                .clickable { onSeasonSelected(index) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedSeasonIndex == index) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = season.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selectedSeasonIndex == index) 
                                        MaterialTheme.colorScheme.onPrimary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Episodes of selected season
        item {
            if (seasonsViewModel.isLoading) {
                Text(
                    text = stringResource(R.string.loading_seasons),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (seasonsViewModel.errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.error_loading_seasons, seasonsViewModel.errorMessage!!),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { seasonsViewModel.loadSeasons(series.id) },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else if (seasonsViewModel.seasons.isNotEmpty()) {
                val selectedSeason = seasonsViewModel.seasons.getOrNull(selectedSeasonIndex)
                if (selectedSeason != null) {
                    Text(
                        text = selectedSeason.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.no_seasons_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Episodes of the selected season — one lazy item per episode with a
        // stable key. The watched flag is loaded off the main thread via
        // produceState so composition never performs blocking I/O.
        val episodesSeason = seasonsViewModel.seasons.getOrNull(selectedSeasonIndex)
        if (!seasonsViewModel.isLoading && seasonsViewModel.errorMessage == null && episodesSeason != null) {
            items(episodesSeason.episodes, key = { it.id }) { episode ->
                val isEpisodeWatched by produceState(initialValue = false, episode.id, episodesSeason.id, series.id) {
                    value = withContext(Dispatchers.IO) {
                        StorageUtils.isEpisodeWatched(context, series.id, episodesSeason.id, episode.id)
                    }
                }
                EpisodeItem(
                    episode = episode,
                    isWatched = isEpisodeWatched,
                    onPlayClick = { onEpisodeClick(episode) },
                    onDownloadClick = { onDownloadClick(episode) },
                    onImageClick = { imageUrl ->
                        episodeImageUrl = imageUrl
                        showEpisodeImageDialog = true
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            PishiBrandingFooter()
        }
    }
}

@Composable
fun EpisodeItem(
    episode: Episode,
    isWatched: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWatched) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode image
                if (episode.image.isNotEmpty()) {
                    Box {
                        Image(
                            painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(LocalContext.current)
                                    .data(episode.image)
                                    .crossfade(true)
                                    .build()
                            ),
                            contentDescription = episode.title,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(episode.image) },
                            contentScale = ContentScale.Crop
                        )
                        
                        // Watched indicator overlay
                        if (isWatched) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.watched),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                // Episode details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Show source count if there are multiple sources
                    if (episode.sources.size > 1) {
                        Text(
                            text = stringResource(R.string.qualities_available, episode.sources.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Watched status text
                    if (isWatched) {
                        Text(
                            text = stringResource(R.string.watched),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // Download button
                if (episode.sources.isNotEmpty()) {
                    IconButton(
                        onClick = { onDownloadClick() },
                        modifier = Modifier
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.download),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Play button
                IconButton(
                    onClick = { onPlayClick() },
                    modifier = Modifier
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

fun openUrlSeries(context: Context, url: String) {
    DownloadUtils.openUrl(context, url)
}

