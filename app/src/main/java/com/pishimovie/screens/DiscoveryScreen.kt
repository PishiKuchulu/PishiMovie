package com.pishimovie.screens

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.pishimovie.R
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.DiscoveryRepository
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified discovery screen for Actor / Director / Studio / Year pages.
 *
 * - For person (actor/director/studio): runs a backend search using the
 *   person's name as the query.
 * - For year: fetches BY_YEAR movies and filters to the decade range.
 *
 * Both reuse existing repositories — no API changes.
 *
 * @param mode  "person" or "year"
 * @param query the person name (person mode) or decade label (year mode)
 */
@Composable
fun DiscoveryScreen(
    mode: String,
    query: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val repo = remember { DiscoveryRepository() }

    var results by remember(mode, query) { mutableStateOf<List<Poster>?>(null) }
    var loading by remember(mode, query) { mutableStateOf(true) }
    var hasError by remember(mode, query) { mutableStateOf(false) }
    var retryTick by remember(mode, query) { mutableStateOf(0) }

    LaunchedEffect(mode, query, retryTick) {
        loading = true
        hasError = false
        try {
            results = withContext(Dispatchers.IO) {
                when (mode) {
                    "person" -> repo.searchPerson(query)
                    "year" -> {
                        val decade = repo.getDecades().find { it.label == query }
                        if (decade != null) {
                            repo.getByYearRange(decade.from, decade.to).map { movie ->
                                Poster(
                                    id = movie.id,
                                    title = movie.title,
                                    type = movie.type,
                                    description = movie.description,
                                    year = movie.year,
                                    imdb = movie.imdb,
                                    rating = movie.rating,
                                    duration = movie.duration,
                                    image = movie.image,
                                    cover = movie.cover,
                                    genres = movie.genres,
                                    sources = movie.sources,
                                    country = movie.country,
                                )
                            }
                        } else emptyList()
                    }
                    else -> emptyList()
                }
            }
        } catch (e: Exception) {
            results = emptyList()
            hasError = true
        } finally {
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                    text = query,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                loading -> {
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
                hasError -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.failed_load_content),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { retryTick++ }) {
                                Text(text = stringResource(R.string.retry))
                            }
                        }
                    }
                }
                results.isNullOrEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(dimens.lg),
                        horizontalArrangement = Arrangement.spacedBy(dimens.md),
                        verticalArrangement = Arrangement.spacedBy(dimens.md),
                    ) {
                        items(results!!, key = { "${it.type}_${it.id}" }) { poster ->
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
                                cardWidth = 160.dp,
                                typeLabel = if (poster.isMovie()) stringResource(R.string.movies) else stringResource(R.string.series),
                                genres = poster.genres.firstOrNull()?.title,
                            )
                        }
                    }
                }
            }
        }
    }
}
