package com.pishimovie.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pishimovie.R
import com.pishimovie.data.model.Country
import com.pishimovie.data.model.FilterType
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Poster
import com.pishimovie.navigation.navigateSafe
import com.pishimovie.ui.components.PishiChip
import com.pishimovie.ui.country.CountryViewModel
import com.pishimovie.ui.theme.rememberReducedMotion
import com.pishimovie.ui.theme.rememberShimmerSweepOrStatic
import com.pishimovie.utils.DeviceUtils
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CountryScreen(
    countryId: Int,
    viewModel: CountryViewModel = viewModel(),
    navController: NavController? = null
) {
    // Set the country ID when the screen is first loaded
    LaunchedEffect(countryId) {
        viewModel.setCountryId(countryId)
    }

    val posters = viewModel.posters
    val countryName = viewModel.countryName
    val isLoading = viewModel.isLoading
    val isLoadingMore = viewModel.isLoadingMore
    val errorMessage = viewModel.errorMessage
    val selectedFilterType = viewModel.selectedFilterType
    val selectedContentType = viewModel.selectedContentType

    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController?.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }

            Text(
                text = countryName.ifBlank { stringResource(R.string.e3_country_fallback) },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )

            // Filter icon — only shown after the user picks a content type
            if (selectedContentType != null) {
                var filterMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { filterMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.country_filter_cd),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        // Sort options (Phase 4 / Part 5: expanded)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.default_filter)) },
                            onClick = {
                                viewModel.selectFilterType(FilterType.DEFAULT)
                                filterMenuExpanded = false
                            },
                            trailingIcon = {
                                if (selectedFilterType == FilterType.DEFAULT) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.by_year)) },
                            onClick = {
                                viewModel.selectFilterType(FilterType.BY_YEAR)
                                filterMenuExpanded = false
                            },
                            trailingIcon = {
                                if (selectedFilterType == FilterType.BY_YEAR) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.by_imdb)) },
                            onClick = {
                                viewModel.selectFilterType(FilterType.BY_IMDB)
                                filterMenuExpanded = false
                            },
                            trailingIcon = {
                                if (selectedFilterType == FilterType.BY_IMDB) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                // Extended filters sheet (min IMDb/rating, year, genre,
                // language, runtime) — applied client-side by the VM.
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.e3_advanced_filters_cd),
                        tint = if (viewModel.hasActiveExtendedFilters) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Phase 5: segmented selector (Movies | TV Series) at the top.
        // Content displays immediately — no empty space.
        if (selectedContentType == null) {
            // Default to Movies immediately so content loads right away.
            LaunchedEffect(countryId) {
                if (countryId > 0) {
                    viewModel.selectContentType("movie")
                }
            }
        }

        // Segmented selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Movies segment
            SegmentedControlButton(
                label = stringResource(R.string.movies),
                isSelected = selectedContentType == "movie",
                onClick = { viewModel.selectContentType("movie") },
                modifier = Modifier.weight(1f),
            )
            // TV Series segment
            SegmentedControlButton(
                label = stringResource(R.string.series),
                isSelected = selectedContentType == "serie",
                onClick = { viewModel.selectContentType("serie") },
                modifier = Modifier.weight(1f),
            )
        }

        // Show selected filter type
        if (selectedFilterType != FilterType.DEFAULT && selectedContentType != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = when (selectedFilterType) {
                        FilterType.DEFAULT -> ""
                        FilterType.BY_YEAR -> stringResource(R.string.sorted_by_year_desc)
                        FilterType.BY_IMDB -> stringResource(R.string.sorted_by_imdb_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Content — shows immediately after the segmented selector.
        if (selectedContentType != null) {
            when {
                isLoading && posters.isEmpty() -> {
                    CountryLoadingScreen()
                }
                errorMessage != null && posters.isEmpty() -> {
                    CountryErrorScreen(
                        errorMessage = errorMessage,
                        onRetry = { viewModel.retry() }
                    )
                }
                !isLoading && posters.isEmpty() -> {
                    // Load succeeded but returned nothing (e.g. the extended
                    // filters exclude everything) — a real empty state
                    // instead of a blank grid.
                    CountryEmptyState(
                        hasActiveFilters = viewModel.hasActiveExtendedFilters,
                        onClearFilters = { viewModel.clearExtendedFilters() }
                    )
                }
                else -> {
                    CountryPosterGrid(
                        posters = posters,
                        isLoading = isLoading,
                        isLoadingMore = isLoadingMore,
                        errorMessage = errorMessage,
                        onRetry = { viewModel.retry() },
                        onRefresh = { viewModel.refresh() },
                        onLoadMore = { viewModel.loadMorePosters() },
                        navController = navController
                    )
                }
            }
        }

        if (showFilterSheet) {
            CountryFilterSheet(
                genres = viewModel.genres,
                countries = viewModel.countries,
                initialMinImdb = viewModel.filterMinImdb,
                initialMinRating = viewModel.filterMinRating,
                initialYearFrom = viewModel.filterYearFrom,
                initialYearTo = viewModel.filterYearTo,
                initialGenre = viewModel.filterGenre,
                initialLanguage = viewModel.filterLanguage,
                initialMaxRuntime = viewModel.filterMaxRuntime,
                onApply = { minImdb, minRating, yearFrom, yearTo, genre, language, maxRuntime ->
                    viewModel.applyExtendedFilters(
                        minImdb, minRating, yearFrom, yearTo, genre, language, maxRuntime
                    )
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

@Composable
fun CountryEmptyState(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.e3_no_items_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        if (hasActiveFilters) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClearFilters) {
                Text(stringResource(R.string.e3_clear_filters))
            }
        }
    }
}

/**
 * Phase 5: compact segmented control button for the Movies | TV Series selector.
 */
@Composable
private fun SegmentedControlButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .semantics {
                role = Role.Tab
                selected = isSelected
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun CountryLoadingScreen() {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColorShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Branding Logo in Loading state
        Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    // Subtle breathing animation could be added here
                }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Add a title while loading
        Text(
            text = stringResource(R.string.country_loading_content),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        val columns = DeviceUtils.getGridColumns(LocalContext.current.resources)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(6) { // Show 6 loading placeholders
                CountryShimmerPosterItem(shimmerColor, shimmerColorShade)
            }
        }
    }
}

@Composable
fun CountryShimmerPosterItem(
    shimmerColor: Color,
    shimmerColorShade: Color
) {
    // TV / reduced motion: static shimmer — no infinite transition is
    // composed at all (see TvMotion).
    val translateAnim = rememberShimmerSweepOrStatic()
    
    val brush = Brush.linearGradient(
        colors = listOf(
            shimmerColor,
            shimmerColorShade,
            shimmerColor,
            shimmerColorShade,
            shimmerColor
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Poster image shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Title shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(20.dp)
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Year shimmer
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(16.dp)
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Genres shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rating shimmer
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(16.dp)
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun CountryPosterGrid(
    posters: List<Poster>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    navController: NavController? = null
) {
    val postersList = posters.toList()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val columns = DeviceUtils.getGridColumns(LocalContext.current.resources)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(postersList, key = { _, poster -> poster.id }) { index, poster ->
            CountryPosterItem(
                poster = poster,
                onClick = {
                    // Save the detail file off the click thread, then navigate.
                    scope.launch {
                        if (poster.isMovie()) {
                            withContext(Dispatchers.IO) {
                                StorageUtils.saveMovieToFile(context, poster.toMovie())
                            }
                            navController?.navigateSafe("single_movie/${poster.id}")
                        } else if (poster.isSeries()) {
                            withContext(Dispatchers.IO) {
                                StorageUtils.saveSeriesToFile(context, poster.toSeries())
                            }
                            navController?.navigateSafe("single_series/${poster.id}")
                        }
                    }
                }
            )
            
            // Load more when we're near the end of the list
            if (index >= postersList.size - 3) {
                LaunchedEffect(Unit) {
                    onLoadMore()
                }
            }
        }
        
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Modern animated loading indicator
                    CountryModernCircularProgressIndicator()
                }
            }
        }
        
        if (errorMessage != null) {
            item {
                CountryErrorItem(
                    errorMessage = errorMessage,
                    onRetry = onRetry
                )
            }
        }
        
        // Add a small spacer at the bottom to avoid excessive padding
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun CountryModernCircularProgressIndicator() {
    // TV / reduced motion: static loading ring — no infinite progress or
    // rotation animation is composed at all (see TvMotion).
    if (rememberReducedMotion()) {
        CircularProgressIndicator(
            progress = 0.65f,
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.primary
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "progress")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            )
        ), label = "progress_anim"
    )
    
    // Add rotation animation for a more dynamic effect
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            )
        ), label = "rotation_anim"
    )
    
    CircularProgressIndicator(
        progress = progress,
        modifier = Modifier
            .size(48.dp)
            .rotate(rotation), // Add rotation
        strokeWidth = 4.dp,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun CountryPosterItem(
    poster: Poster,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Poster image with rating overlay
            Box {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(poster.image)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = poster.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Rating overlay at top-right corner
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.country_rating_cd),
                            tint = Color.Yellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", poster.imdb),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Type indicator at bottom-right corner
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (poster.isMovie()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (poster.isMovie()) stringResource(R.string.movies) else stringResource(R.string.series),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (poster.isMovie()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondary
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Poster details
            Column {
                Text(
                    text = poster.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = poster.year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Genres
                if (poster.genres.isNotEmpty()) {
                    Text(
                        text = poster.genres.joinToString(", ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CountryErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.failed_load_content),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.retry),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
fun CountryErrorItem(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.failed_load_more),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Button(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/**
 * Extended-filters sheet for the country page, modeled on the search
 * filter sheet: edits a local working copy and applies it in one go.
 * The values map 1:1 to CountryViewModel's client-side filter fields.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CountryFilterSheet(
    genres: List<Genre>,
    countries: List<Country>,
    initialMinImdb: Double,
    initialMinRating: Double,
    initialYearFrom: Int,
    initialYearTo: Int,
    initialGenre: String?,
    initialLanguage: String?,
    initialMaxRuntime: Int,
    onApply: (minImdb: Double, minRating: Double, yearFrom: Int, yearTo: Int, genre: String?, language: String?, maxRuntime: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var minImdb by remember { mutableFloatStateOf(initialMinImdb.toFloat()) }
    var minRating by remember { mutableFloatStateOf(initialMinRating.toFloat()) }
    var yearFrom by remember { mutableIntStateOf(initialYearFrom) }
    var yearTo by remember { mutableIntStateOf(initialYearTo) }
    var genre by remember { mutableStateOf(initialGenre) }
    var language by remember { mutableStateOf(initialLanguage) }
    var maxRuntime by remember { mutableIntStateOf(initialMaxRuntime) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filters),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = {
                    minImdb = 0f
                    minRating = 0f
                    yearFrom = 1900
                    yearTo = 2100
                    genre = null
                    language = null
                    maxRuntime = 0
                }) {
                    Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.primary)
                }
            }

            // Minimum IMDb rating
            Text(
                text = stringResource(R.string.e3_min_imdb_label, String.format("%.1f", minImdb)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = minImdb,
                onValueChange = { minImdb = it },
                valueRange = 0f..10f,
                steps = 19, // 0.5 steps
            )

            // Minimum user rating
            Text(
                text = stringResource(R.string.e3_min_user_rating_label, String.format("%.1f", minRating)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = minRating,
                onValueChange = { minRating = it },
                valueRange = 0f..10f,
                steps = 19, // 0.5 steps
            )

            // Year range
            Text(
                text = stringResource(R.string.year_range, yearFrom, yearTo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RangeSlider(
                value = yearFrom.toFloat()..yearTo.toFloat(),
                onValueChange = { range ->
                    yearFrom = range.start.toInt()
                    yearTo = range.endInclusive.toInt()
                },
                valueRange = 1900f..2100f,
            )

            // Genre (single selection, matched against poster genre titles)
            if (genres.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.genres),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PishiChip(
                        text = stringResource(R.string.all_genres),
                        selected = genre == null,
                        onClick = { genre = null }
                    )
                    genres.forEach { g ->
                        PishiChip(
                            text = g.title,
                            selected = genre == g.title,
                            onClick = { genre = g.title }
                        )
                    }
                }
            }

            // Language (matched against poster countries, like SearchFilters)
            if (countries.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.language)} / ${stringResource(R.string.country_label)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PishiChip(
                        text = stringResource(R.string.language_any),
                        selected = language == null,
                        onClick = { language = null }
                    )
                    countries.forEach { country ->
                        PishiChip(
                            text = country.title,
                            selected = language == country.title,
                            onClick = { language = country.title }
                        )
                    }
                }
            }

            // Maximum runtime
            Text(
                text = stringResource(R.string.e3_max_runtime_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PishiChip(
                    text = stringResource(R.string.e3_runtime_any),
                    selected = maxRuntime == 0,
                    onClick = { maxRuntime = 0 }
                )
                listOf(90, 120, 150, 180).forEach { minutes ->
                    PishiChip(
                        text = stringResource(R.string.e3_runtime_up_to, minutes),
                        selected = maxRuntime == minutes,
                        onClick = { maxRuntime = minutes }
                    )
                }
            }

            // Apply button
            Button(
                onClick = {
                    onApply(
                        minImdb.toDouble(),
                        minRating.toDouble(),
                        yearFrom,
                        yearTo,
                        genre,
                        language,
                        maxRuntime
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.apply_filters), fontWeight = FontWeight.Bold)
            }
        }
    }
}
