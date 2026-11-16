package com.pishimovie.desktop

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.Poster
import com.pishimovie.data.model.Season
import com.pishimovie.data.repository.HomeRepository
import com.pishimovie.data.repository.SearchRepository
import com.pishimovie.data.repository.SeasonsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop UI over the shared data layer (:shared) — the same
 * repositories the Android app uses.
 *
 * Phase 2 slices landed: home (hero + rails), search, detail
 * (movie sources list; series seasons/episodes). The mpv player is
 * the remaining Phase 2 slice; playback entry points arrive with it.
 */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val movie: Movie) : Screen
    data class Player(val url: String, val title: String) : Screen
}

@Composable
fun App(context: Context) {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Home)) }
    val current = stack.last()
    val pop = { if (stack.size > 1) stack = stack.dropLast(1) }

    when (current) {
        is Screen.Home -> HomeScreen(
            context = context,
            onOpenDetail = { stack = stack + Screen.Detail(it) },
        )
        is Screen.Detail -> DetailScreen(
            movie = current.movie,
            onBack = pop,
            onPlay = { url, title -> stack = stack + Screen.Player(url, title) },
        )
        is Screen.Player -> PlayerScreen(
            url = current.url,
            title = current.title,
            onClose = pop,
        )
    }
}

@Composable
private fun HomeScreen(context: Context, onOpenDetail: (Movie) -> Unit) {
    val homeRepo = remember { HomeRepository(context) }
    val searchRepo = remember { SearchRepository() }
    val scope = rememberCoroutineScope()

    var hero by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var trending by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var topRated by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var series by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Poster>?>(null) }

    LaunchedEffect(reloadKey) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            HomeData(
                hero = homeRepo.getHeroBanners(),
                trending = homeRepo.getTrending(),
                topRated = homeRepo.getTopRated(),
                newReleases = homeRepo.getNewReleases(),
                series = homeRepo.getPopularSeries().map { it.toMovie() },
            )
        }
        hero = loaded.hero
        trending = loaded.trending
        topRated = loaded.topRated
        newReleases = loaded.newReleases
        series = loaded.series
        loading = false
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { searchRepo.search(q).posters }.getOrDefault(emptyList())
            }
            searchResults = result
            searching = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("پیشی‌مووی", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("جستجوی فیلم یا سریال…") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 24.dp, end = 12.dp),
            )
            Button(onClick = { runSearch() }, enabled = !searching) { Text("جستجو") }
            if (searchResults != null) {
                TextButton(onClick = { searchResults = null; query = "" }) { Text("خانه") }
            }
        }

        when {
            searching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            searchResults != null -> {
                val results = searchResults.orEmpty()
                if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("نتیجه‌ای پیدا نشد.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results) { poster ->
                            PosterCard(poster.toMovie(), width = 140.dp, onClick = onOpenDetail)
                        }
                    }
                }
            }
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            hero.isEmpty() && trending.isEmpty() && topRated.isEmpty() &&
                newReleases.isEmpty() && series.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("موردی بارگذاری نشد. اتصال اینترنت یا سرور را بررسی کنید.")
                        Button(
                            onClick = { reloadKey++ },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("تلاش دوباره") }
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hero.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(hero) { movie -> HeroCard(movie, onClick = onOpenDetail) }
                        }
                    }
                }
                item { Rail("ترندها", trending, onOpenDetail) }
                item { Rail("برترین‌ها", topRated, onOpenDetail) }
                item { Rail("تازه‌ها", newReleases, onOpenDetail) }
                item { Rail("سریال‌های محبوب", series, onOpenDetail) }
            }
        }
    }
}

private data class HomeData(
    val hero: List<Movie>,
    val trending: List<Movie>,
    val topRated: List<Movie>,
    val newReleases: List<Movie>,
    val series: List<Movie>,
)

@Composable
private fun Rail(title: String, movies: List<Movie>, onOpenDetail: (Movie) -> Unit) {
    if (movies.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(movies) { movie -> PosterCard(movie, onClick = onOpenDetail) }
        }
    }
}

@Composable
private fun PosterCard(movie: Movie, width: Dp = 120.dp, onClick: ((Movie) -> Unit)? = null) {
    Column(
        modifier = Modifier.width(width).then(
            if (onClick != null) Modifier.clickable { onClick(movie) } else Modifier
        )
    ) {
        AsyncImage(
            model = movie.image,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(width)
                .height(width * 1.5f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C22)),
        )
        Text(
            movie.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "${movie.year} • ★ ${movie.imdb}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroCard(movie: Movie, onClick: ((Movie) -> Unit)? = null) {
    Column(
        modifier = Modifier.width(360.dp).then(
            if (onClick != null) Modifier.clickable { onClick(movie) } else Modifier
        )
    ) {
        AsyncImage(
            model = movie.cover.ifEmpty { movie.image },
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(360.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1C22)),
        )
        Text(
            movie.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${movie.year} • ★ ${movie.imdb}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailScreen(movie: Movie, onBack: () -> Unit, onPlay: (String, String) -> Unit) {
    val isSeries = movie.type != "movie"
    var seasons by remember(movie.id) { mutableStateOf<List<Season>?>(null) }
    var expandedSeason by remember(movie.id) { mutableStateOf<Int?>(null) }
    var expandedEpisode by remember(movie.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(movie.id) {
        if (isSeries) {
            seasons = withContext(Dispatchers.IO) {
                runCatching { SeasonsRepository().getSeasons(movie.id) }.getOrDefault(emptyList())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = movie.cover.ifEmpty { movie.image },
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(340.dp).background(Color(0xFF1C1C22)),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp)),
            ) { Text("بازگشت", color = Color.White) }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(movie.title, style = MaterialTheme.typography.headlineMedium)
            val meta = buildList {
                if (movie.year > 0) add(movie.year.toString())
                movie.duration?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (movie.imdb > 0) add("★ ${movie.imdb}")
                if (movie.genres.isNotEmpty()) add(movie.genres.joinToString(" • ") { it.title })
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (movie.description.isNotBlank()) {
                Text(
                    movie.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            if (!isSeries && movie.sources.isNotEmpty()) {
                Text(
                    "منابع",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                movie.sources.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF1C1C22), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(source.quality, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            source.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        Box(modifier = Modifier.weight(1f))
                        Button(onClick = { onPlay(source.url, movie.title) }) { Text("پخش") }
                    }
                    Box(modifier = Modifier.height(8.dp))
                }
            }

            if (isSeries) {
                Text(
                    "فصل‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                when (val loaded = seasons) {
                    null -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    else -> {
                        if (loaded.isEmpty()) {
                            Text("فصلی ثبت نشده است.")
                        }
                        loaded.forEach { season ->
                            val expanded = expandedSeason == season.id
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFF1C1C22), RoundedCornerShape(8.dp))
                                    .clickable { expandedSeason = if (expanded) null else season.id }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    "${season.title} — ${season.episodes.size} قسمت",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (expanded) {
                                    season.episodes.forEach { episode ->
                                        val episodeExpanded = expandedEpisode == episode.id
                                        Column(
                                            modifier = Modifier
                                                .clickable {
                                                    expandedEpisode = if (episodeExpanded) null else episode.id
                                                }
                                                .padding(start = 10.dp, top = 6.dp),
                                        ) {
                                            Text(
                                                episode.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (episodeExpanded) {
                                                if (episode.sources.isEmpty()) {
                                                    Text(
                                                        "منبعی برای این قسمت ثبت نشده است.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(top = 4.dp),
                                                    )
                                                }
                                                episode.sources.forEach { source ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 4.dp),
                                                    ) {
                                                        Text(
                                                            source.quality,
                                                            style = MaterialTheme.typography.labelSmall,
                                                        )
                                                        Text(
                                                            source.type,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(start = 8.dp),
                                                        )
                                                        TextButton(
                                                            onClick = { onPlay(source.url, "${movie.title} — ${episode.title}") },
                                                        ) { Text("پخش") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            Box(modifier = Modifier.height(32.dp))
        }
    }
}
