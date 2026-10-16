package com.pishimovie.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.pishimovie.R

/**
 * All PishiMovie navigation destinations.
 */
sealed class AppScreens(
    val route: String,
    @StringRes val resourceId: Int,
    val icon: ImageVector? = null,
    val showBottomBar: Boolean = false,
    val showSidebar: Boolean = true,
) {
    data object Home : AppScreens(
        route = "home",
        resourceId = R.string.home,
        icon = Icons.Default.Home,
        showBottomBar = true,
    )

    data object Movies : AppScreens(
        route = "movies",
        resourceId = R.string.movies,
        icon = Icons.Default.Movie,
        showBottomBar = true,
    )

    data object Series : AppScreens(
        route = "series",
        resourceId = R.string.series,
        icon = Icons.Default.Tv,
        showBottomBar = true,
    )

    data object Search : AppScreens(
        route = "search",
        resourceId = R.string.search,
        icon = Icons.Default.Search,
        showBottomBar = true,
    )

    data object Settings : AppScreens(
        route = "settings",
        resourceId = R.string.settings,
        icon = Icons.Default.Settings,
        showBottomBar = false,
        showSidebar = false,
    )

    data object SubtitleSettings : AppScreens(
        route = "subtitle_settings?videoUrl={videoUrl}",
        resourceId = R.string.subtitle_settings,
        icon = Icons.Default.Subtitles,
        showBottomBar = false,
        showSidebar = false,
    ) {
        fun buildRoute(videoUrl: String? = null): String {
            return if (videoUrl.isNullOrBlank()) {
                "subtitle_settings?videoUrl="
            } else {
                "subtitle_settings?videoUrl=${android.net.Uri.encode(videoUrl)}"
            }
        }
    }

    data object SingleMovie : AppScreens(
        route = "single_movie/{movieId}",
        resourceId = R.string.movie_details,
        icon = Icons.Default.Movie,
        showBottomBar = false,
        showSidebar = false,
    )

    data object SingleSeries : AppScreens(
        route = "single_series/{seriesId}",
        resourceId = R.string.series_details,
        icon = Icons.Default.Tv,
        showBottomBar = false,
        showSidebar = false,
    )

    data object Favorites : AppScreens(
        route = "favorites",
        resourceId = R.string.favorites,
        icon = Icons.Default.Favorite,
        showBottomBar = true,
    )

    data object About : AppScreens(
        route = "about",
        resourceId = R.string.about,
        icon = Icons.Default.Settings,
        showBottomBar = false,
        showSidebar = false,
    )

    data object Country : AppScreens(
        route = "country/{countryId}",
        resourceId = R.string.country,
        showBottomBar = false,
        showSidebar = false,
    )

    data object Collection : AppScreens(
        route = "collection/{collectionId}",
        resourceId = R.string.collections,
        showBottomBar = false,
        showSidebar = false,
    )

    data object Mood : AppScreens(
        route = "mood/{moodId}",
        resourceId = R.string.mood_mode,
        showBottomBar = false,
        showSidebar = false,
    )

    data object Profile : AppScreens(
        route = "profile",
        resourceId = R.string.statistics,
        icon = Icons.Default.Person,
        showBottomBar = true,
    )

    data object DiscoveryHub : AppScreens(
        route = "discovery_hub",
        resourceId = R.string.discover,
        icon = Icons.Default.Explore,
        showBottomBar = false,
        showSidebar = false,
    )

    data object WatchHistory : AppScreens(
        route = "watch_history",
        resourceId = R.string.history,
        icon = Icons.Default.History,
        showBottomBar = false,
        showSidebar = false,
    )

    data object PersonSearch : AppScreens(
        route = "person_search/{entityType}",
        resourceId = R.string.search,
        showBottomBar = false,
        showSidebar = false,
    )

    data object YearExplorer : AppScreens(
        route = "year_explorer",
        resourceId = R.string.search,
        showBottomBar = false,
        showSidebar = false,
    )

    data object Discovery : AppScreens(
        route = "discovery/{mode}/{query}",
        resourceId = R.string.search,
        showBottomBar = false,
        showSidebar = false,
    )

    companion object {
        val topBarScreens: List<AppScreens> = listOf(
            Home,
            Search,
            DiscoveryHub,
            Favorites,
            Profile,
            Settings,
        )

        val screens: List<AppScreens> = topBarScreens

        /**
         * Destinations shown in the TV sidebar ([com.pishimovie.navigation.SidebarNavigation]).
         * Kept separate from [topBarScreens] (mobile top bar) so TV can surface
         * Movies & Series, which intentionally aren't part of the mobile top bar.
         * The [AppScreens.showSidebar] flag still gates which entries render.
         */
        val sidebarScreens: List<AppScreens> = listOf(
            Home,
            Search,
            Movies,
            Series,
            DiscoveryHub,
            Favorites,
            Profile,
            Settings,
        )
    }
}
