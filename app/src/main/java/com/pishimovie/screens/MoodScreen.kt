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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pishimovie.ui.components.PishiBrandingFooter
import androidx.compose.ui.res.stringResource
import com.pishimovie.R
import com.pishimovie.data.model.PishiMood
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.RecommendationEngine
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PishiMovie Mood Mode detail screen (Phase 5).
 *
 * Loads dynamic recommendations for the selected mood via the
 * [RecommendationEngine] — no external AI service required.
 */
@Composable
fun MoodScreen(
    moodId: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val mood = remember(moodId) { PishiMood.byId(moodId) }

    var results by remember(moodId) { mutableStateOf<List<Poster>?>(null) }
    var loading by remember(moodId) { mutableStateOf(true) }
    var hasError by remember(moodId) { mutableStateOf(false) }
    var retryTick by remember(moodId) { mutableStateOf(0) }

    LaunchedEffect(moodId, retryTick) {
        if (mood == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        hasError = false
        try {
            val engine = RecommendationEngine(context)
            results = withContext(Dispatchers.IO) { engine.getMoodRecommendations(mood) }
        } catch (e: Exception) {
            results = emptyList()
            hasError = true
        } finally {
            loading = false
        }
    }

    val accent = mood?.let { Color(it.accentHex) } ?: MaterialTheme.colorScheme.primary

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
                    text = mood?.title ?: "Mood",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (mood != null) {
                    Text(
                        text = mood.emoji,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            // Accent banner
            if (mood != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.lg, vertical = dimens.sm)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.4f))
                            )
                        )
                        .padding(dimens.lg),
                ) {
                    Column {
                        Text(
                            text = mood.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mood.subtitle,
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
                            text = stringResource(R.string.no_titles_found_mood),
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
