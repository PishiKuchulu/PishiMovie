package com.pishimovie.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pishimovie.R
import com.pishimovie.data.model.Poster
import com.pishimovie.data.repository.DiscoveryRepository
import com.pishimovie.navigation.navigateSafe
import com.pishimovie.ui.components.PishiPosterCard
import com.pishimovie.ui.components.PishiShimmerBox
import com.pishimovie.ui.theme.LocalPishiDimens
import com.pishimovie.utils.DeviceUtils
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Person / Studio search screen (Phase 5: Actor / Director / Studio pages).
 */
@Composable
fun PersonSearchScreen(
    entityType: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val dimens = LocalPishiDimens.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val repo = remember { DiscoveryRepository() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Poster>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchSeq by remember { mutableIntStateOf(0) }

    val gridColumns = DeviceUtils.getGridColumns(context.resources)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        // Cancel the previous search; stale responses must never
        // overwrite newer results.
        searchJob?.cancel()
        val seq = ++searchSeq
        loading = true
        searchFailed = false
        focusManager.clearFocus()
        searchJob = scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { repo.searchPerson(q) }
                if (seq == searchSeq && query.trim() == q) {
                    results = found
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seq == searchSeq && query.trim() == q) {
                    searchFailed = true
                }
            } finally {
                if (seq == searchSeq) {
                    loading = false
                }
            }
        }
    }

    val localizedEntity = when(entityType) {
        "Actor" -> stringResource(R.string.actor_label)
        "Director" -> stringResource(R.string.director_label)
        "Studio" -> stringResource(R.string.studio_label)
        else -> entityType
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = statusBarPadding)) {
            // Header + search bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.sm, vertical = dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(stringResource(R.string.search_person_placeholder, localizedEntity), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    label = {
                        Text(stringResource(R.string.search))
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                searchJob?.cancel()
                                query = ""
                                results = null
                                searchFailed = false
                                loading = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
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
                Spacer(modifier = Modifier.width(dimens.sm))
            }

            // Content
            when {
                loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
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
                searchFailed -> {
                    // A network failure is NOT "no results": show a real
                    // error state with a retry action.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = dimens.xl)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.e3_search_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { doSearch() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                results == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${stringResource(R.string.search)} $localizedEntity",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.search_for_person_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                        columns = GridCells.Fixed(gridColumns),
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
                                    // Save the detail file off the click
                                    // thread, then navigate.
                                    scope.launch {
                                        if (poster.isMovie()) {
                                            withContext(Dispatchers.IO) {
                                                StorageUtils.saveMovieToFile(context, poster.toMovie())
                                            }
                                            navController.navigateSafe("single_movie/${poster.id}")
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                StorageUtils.saveSeriesToFile(context, poster.toSeries())
                                            }
                                            navController.navigateSafe("single_series/${poster.id}")
                                        }
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
