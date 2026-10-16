package com.pishimovie.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.pishimovie.R
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.SearchFilters
import com.pishimovie.ui.components.PishiChip
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiRatingPill
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.search.SearchViewModel
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.utils.DeviceUtils
import com.pishimovie.utils.StorageUtils

/**
 * PishiMovie premium search screen.
 *
 * Features (Part 3 / Part 6 of the spec):
 *  - Instant search (debounced 450ms)
 *  - Live suggestions dropdown (debounced 200ms, 5 results max)
 *  - Search history (persistent, tappable, removable)
 *  - Trending searches (when no query)
 *  - Voice search placeholder (icon → toast)
 *  - Filter sheet: Genre (multi), Year (range), Rating (min), Language (single), Type
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    navController: NavController? = null,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val uiState = viewModel.uiState.collectAsState().value
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding)
                .imePadding(),
        ) {
            // ── Search bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.sm, vertical = dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController?.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                TextField(
                    value = uiState.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Row {
                            // Voice search placeholder
                            IconButton(onClick = {
                                Toast.makeText(context, context.getString(R.string.voice_search_soon), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.search_voice_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearQuery() }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.submitSearch()
                        focusManager.clearFocus()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(50),
                    singleLine = true,
                )
                // Filter button with badge
                Box {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.filters),
                            tint = if (uiState.hasActiveFilters) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (uiState.hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            // ── Active filter chips row ──────────────────────────────────
            if (uiState.hasActiveFilters) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.lg, vertical = dimens.xs),
                    horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                ) {
                    PishiChip(
                        text = stringResource(R.string.clear_filters),
                        selected = false,
                        onClick = viewModel::resetFilters,
                    )
                    if (uiState.filters.typeFilter != SearchFilters.TypeFilter.ALL) {
                        PishiChip(
                            text = when (uiState.filters.typeFilter) {
                                SearchFilters.TypeFilter.MOVIES_ONLY -> stringResource(R.string.movies_label)
                                SearchFilters.TypeFilter.SERIES_ONLY -> stringResource(R.string.tv_series_label)
                                else -> stringResource(R.string.all)
                            },
                            selected = true,
                            onClick = {
                                viewModel.updateFilters(
                                    uiState.filters.copy(typeFilter = SearchFilters.TypeFilter.ALL)
                                )
                            },
                        )
                    }
                    if (uiState.filters.minRating > 0f) {
                        PishiChip(
                            text = "★ ≥ ${uiState.filters.minRating.toInt()}",
                            selected = true,
                            onClick = {
                                viewModel.updateFilters(uiState.filters.copy(minRating = 0f))
                            },
                        )
                    }
                    if (uiState.filters.yearFrom > 1900 || uiState.filters.yearTo < 2100) {
                        PishiChip(
                            text = "${uiState.filters.yearFrom}–${uiState.filters.yearTo}",
                            selected = true,
                            onClick = {
                                viewModel.updateFilters(
                                    uiState.filters.copy(yearFrom = 1900, yearTo = 2100)
                                )
                            },
                        )
                    }
                    if (uiState.filters.language != null) {
                        PishiChip(
                            text = uiState.filters.language!!,
                            selected = true,
                            onClick = {
                                viewModel.updateFilters(uiState.filters.copy(language = null))
                            },
                        )
                    }
                }
            }

            // ── Live suggestions dropdown ───────────────────────────────
            AnimatedVisibility(
                visible = uiState.suggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.lg)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    uiState.suggestions.forEach { poster ->
                        SuggestionRow(
                            poster = poster,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.selectSuggestion(poster)
                                if (poster.isMovie()) {
                                    StorageUtils.saveMovieToFile(context, poster.toMovie())
                                    navController?.navigate("single_movie/${poster.id}")
                                } else {
                                    StorageUtils.saveSeriesToFile(context, poster.toSeries())
                                    navController?.navigate("single_series/${poster.id}")
                                }
                            },
                        )
                    }
                }
            }

            // ── Main content ─────────────────────────────────────────────
            when {
                uiState.isSearching -> LoadingGrid()
                uiState.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(dimens.lg),
                        ) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Button(onClick = { viewModel.submitSearch() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                uiState.results.isNotEmpty() -> {
                    val columns = DeviceUtils.getGridColumns(context.resources)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = dimens.lg,
                            end = dimens.lg,
                            top = dimens.sm,
                            bottom = dimens.bottomNavHeight + dimens.xxl + navBarPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimens.md),
                        verticalArrangement = Arrangement.spacedBy(dimens.md),
                    ) {
                        items(uiState.results, key = { "${it.type}_${it.id}" }) { poster ->
                            PishiPosterCard(
                                title = poster.title,
                                year = poster.year,
                                rating = poster.imdb,
                                imageUrl = poster.image,
                                onClick = {
                                    if (poster.isMovie()) {
                                        StorageUtils.saveMovieToFile(context, poster.toMovie())
                                        navController?.navigate("single_movie/${poster.id}")
                                    } else {
                                        StorageUtils.saveSeriesToFile(context, poster.toSeries())
                                        navController?.navigate("single_series/${poster.id}")
                                    }
                                },
                                cardWidth = 160.dp,
                                typeLabel = if (poster.isMovie()) stringResource(R.string.movies_label) else stringResource(R.string.tv_series_label),
                                genres = poster.genres.firstOrNull()?.title,
                            )
                        }
                    }
                }
                uiState.hasSearched -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .alpha(0.3f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_results),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.search_no_results_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    // Initial state — history + trending
                    SearchInitialContent(
                        viewModel = viewModel,
                        navController = navController,
                        context = context,
                    )
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        SearchFilterSheet(
            currentFilters = uiState.filters,
            genres = uiState.genres,
            countries = uiState.countries,
            onApply = { newFilters ->
                viewModel.updateFilters(newFilters)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Initial content — history + trending
// ───────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchInitialContent(
    viewModel: SearchViewModel,
    navController: NavController?,
    context: Context,
) {
    val dimens = LocalPishiDimens.current
    val uiState = viewModel.uiState.collectAsState().value
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.lg,
            end = dimens.lg,
            top = dimens.md,
            bottom = dimens.bottomNavHeight + dimens.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.lg),
    ) {
        // Recent searches
        if (uiState.history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recent_searches_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    androidx.compose.material3.TextButton(onClick = viewModel::clearHistory) {
                        Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(items = uiState.history, key = { it.query }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectHistoryEntry(entry.query) }
                        .padding(vertical = dimens.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.removeHistoryEntry(entry.query) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.trending_searches_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                verticalArrangement = Arrangement.spacedBy(dimens.sm),
            ) {
                uiState.trending.forEach { term ->
                    PishiChip(
                        text = term,
                        selected = false,
                        onClick = {
                            viewModel.updateQuery(term)
                            viewModel.submitSearch()
                        },
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Suggestion row
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SuggestionRow(poster: Poster, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = poster.image,
            contentDescription = poster.title,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = poster.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${if (poster.isMovie()) stringResource(R.string.movies_label) else stringResource(R.string.tv_series_label)} • ${poster.year}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PishiRatingPill(rating = poster.imdb)
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Loading grid
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingGrid() {
    val dimens = LocalPishiDimens.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
        verticalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(6) {
            PishiShimmerBox(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                cornerRadius = 20.dp,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Filter bottom sheet
// ───────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterSheet(
    currentFilters: SearchFilters,
    genres: List<com.pishimovie.data.model.Genre>,
    countries: List<com.pishimovie.data.model.Country>,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalPishiDimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local working copy
    var typeFilter by remember { mutableStateOf(currentFilters.typeFilter) }
    var selectedGenres by remember { mutableStateOf(currentFilters.genres) }
    var minRating by remember { mutableFloatStateOf(currentFilters.minRating) }
    var yearFrom by remember { mutableIntStateOf(currentFilters.yearFrom) }
    var yearTo by remember { mutableIntStateOf(currentFilters.yearTo) }
    var language by remember { mutableStateOf(currentFilters.language) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.xxl, vertical = dimens.lg)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(dimens.lg),
        ) {
            // Header
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
                androidx.compose.material3.TextButton(onClick = {
                    typeFilter = SearchFilters.TypeFilter.ALL
                    selectedGenres = emptySet()
                    minRating = 0f
                    yearFrom = 1900
                    yearTo = 2100
                    language = null
                }) {
                    Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.primary)
                }
            }

            Text(
                text = stringResource(R.string.type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
                PishiChip(text = stringResource(R.string.all), selected = typeFilter == SearchFilters.TypeFilter.ALL, onClick = {
                    typeFilter = SearchFilters.TypeFilter.ALL
                })
                PishiChip(text = stringResource(R.string.movies_label), selected = typeFilter == SearchFilters.TypeFilter.MOVIES_ONLY, onClick = {
                    typeFilter = SearchFilters.TypeFilter.MOVIES_ONLY
                })
                PishiChip(text = stringResource(R.string.tv_series_label), selected = typeFilter == SearchFilters.TypeFilter.SERIES_ONLY, onClick = {
                    typeFilter = SearchFilters.TypeFilter.SERIES_ONLY
                })
            }

            // Rating
            Text(
                text = stringResource(R.string.min_rating, minRating.toInt()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.material3.Slider(
                value = minRating,
                onValueChange = { minRating = it },
                valueRange = 0f..10f,
                steps = 9, // 10 intervals of 1.0 → snaps to integer ratings
            )

            // Year range
            Text(
                text = stringResource(R.string.year_range, yearFrom, yearTo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.material3.RangeSlider(
                value = yearFrom.toFloat()..yearTo.toFloat(),
                onValueChange = { range ->
                    yearFrom = range.start.toInt()
                    yearTo = range.endInclusive.toInt()
                },
                valueRange = 1900f..2100f,
            )

            Text(
                text = stringResource(R.string.genres),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                verticalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                genres.take(15).forEach { genre ->
                    PishiChip(
                        text = genre.title,
                        selected = genre.id in selectedGenres,
                        onClick = {
                            selectedGenres = if (genre.id in selectedGenres) {
                                selectedGenres - genre.id
                            } else {
                                selectedGenres + genre.id
                            }
                        }
                    )
                }
            }

            Text(
                text = "${stringResource(R.string.language)} / ${stringResource(R.string.country_label)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                verticalArrangement = Arrangement.spacedBy(minOf(dimens.xs, 8.dp)),
            ) {
                PishiChip(text = stringResource(R.string.language_any), selected = language == null, onClick = { language = null })
                countries.take(10).forEach { country ->
                    PishiChip(
                        text = country.title,
                        selected = language == country.title,
                        onClick = {
                            language = country.title
                        }
                    )
                }
            }

            // Apply button
            androidx.compose.material3.Button(
                onClick = {
                    onApply(
                        SearchFilters(
                            genres = selectedGenres,
                            yearFrom = yearFrom,
                            yearTo = yearTo,
                            minRating = minRating,
                            language = language,
                            typeFilter = typeFilter,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(R.string.apply_filters), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// (No helper needed — using standard collectAsState().value from Compose runtime)
// ───────────────────────────────────────────────────────────────────────────
