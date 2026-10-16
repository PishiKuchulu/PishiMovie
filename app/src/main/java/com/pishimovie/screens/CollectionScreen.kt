package com.pishimovie.screens

import android.content.Context
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
import com.pishimovie.ui.components.PishiBrandingFooter
import androidx.compose.ui.res.stringResource
import com.pishimovie.R
import com.pishimovie.data.model.PishiCollection
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.SearchRepository
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.utils.LanguageUtils
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collection detail screen — loads search results for the collection's query
 * using the existing [SearchRepository]. No API changes.
 *
 * Renders a colored header card (matching the collection's accent color) plus
 * a responsive grid of results.
 */
@Composable
fun CollectionScreen(
    collectionId: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val collection = remember(collectionId) {
        PishiCollection.CATALOG.find { it.id == collectionId }
    }
    val accent = collection?.let { Color(it.accentHex) } ?: MaterialTheme.colorScheme.primary

    // Local state — we drive the search directly since the collection flow is
    // independent from the main search screen's history.
    var results by remember(collectionId) { mutableStateOf<List<Poster>?>(null) }
    var loading by remember(collectionId) { mutableStateOf(true) }
    var error by remember(collectionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(collectionId) {
        if (collection == null) {
            error = context.getString(R.string.collection_not_found)
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val searchRepo = SearchRepository()
            val raw = withContext(Dispatchers.IO) { searchRepo.search(collection.query) }
            results = raw.posters.filter { LanguageUtils.shouldDisplayTitle(it.title) }
        } catch (e: Exception) {
            error = e.message ?: context.getString(R.string.failed_load_content)
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
                    text = collection?.title ?: stringResource(R.string.collection_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                // Big emoji decoration in the header
                if (collection != null) {
                    Text(
                        text = collection.emoji,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            // Subtitle banner with accent gradient
            if (collection != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.lg, vertical = dimens.sm)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.4f))
                            )
                        )
                        .padding(dimens.lg),
                ) {
                    Column {
                        Text(
                            text = collection.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = collection.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Content
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
                error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                results.isNullOrEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_titles_found_collection),
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

                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            PishiBrandingFooter()
                        }
                    }
                }
            }
        }
    }
}
