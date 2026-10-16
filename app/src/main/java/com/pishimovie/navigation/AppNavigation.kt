package com.pishimovie.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pishimovie.screens.AboutScreen
import com.pishimovie.screens.CollectionScreen
import com.pishimovie.screens.CountryScreen
import com.pishimovie.screens.DiscoveryHubScreen
import com.pishimovie.screens.DiscoveryScreen
import com.pishimovie.screens.FavoritesScreen
import com.pishimovie.screens.HomeScreen
import com.pishimovie.screens.MoodScreen
import com.pishimovie.screens.MoviesScreen
import com.pishimovie.screens.PersonSearchScreen
import com.pishimovie.screens.ProfileScreen
import com.pishimovie.screens.SearchScreen
import com.pishimovie.screens.SeriesScreen
import com.pishimovie.screens.SettingsScreen
import com.pishimovie.screens.SingleMovieScreen
import com.pishimovie.screens.SingleSeriesScreen
import com.pishimovie.screens.SubtitleSettingsScreen
import com.pishimovie.screens.WatchHistoryScreen
import com.pishimovie.screens.YearExplorerScreen
import com.pishimovie.ui.country.CountryViewModel
import com.pishimovie.ui.movies.MoviesViewModel
import com.pishimovie.ui.search.SearchViewModel
import com.pishimovie.ui.series.SeriesViewModel

/**
 * PishiMovie navigation graph.
 *
 * Phase 1 (refactor):
 *  - Splash / onboarding removed — start destination is now [AppScreens.Home].
 *  - Bottom navigation removed; top-level destinations live in the M3 top
 *    app bar (see [PishiTopAppBar]).
 *  - A dedicated [AppScreens.WatchHistory] destination was added.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
) {
    // Create ViewModels at the navigation level so their state survives tab switches.
    val moviesViewModel = viewModel<MoviesViewModel>()
    val seriesViewModel = viewModel<SeriesViewModel>()
    val searchViewModel = viewModel<SearchViewModel>()
    val countryViewModel = viewModel<CountryViewModel>()

    NavHost(
        navController = navController,
        startDestination = AppScreens.Home.route,
        // Default transitions for any destination that doesn't override them.
        enterTransition = { NavTransitions.fadeThroughEnter() },
        exitTransition = { NavTransitions.fadeThroughExit() },
        popEnterTransition = { NavTransitions.fadeThroughEnter() },
        popExitTransition = { NavTransitions.fadeThroughExit() },
    ) {
        // Home — premium home screen (app opens directly here, no splash)
        composable(route = AppScreens.Home.route) {
            HomeScreen(navController = navController)
        }

        // Movies — supports optional genreId from Home genre chips
        composable(
            route = "movies?genreId={genreId}",
            arguments = listOf(navArgument("genreId") {
                type = NavType.IntType
                defaultValue = 0
            }),
        ) { entry ->
            val genreId = entry.arguments?.getInt("genreId") ?: 0
            MoviesScreen(
                viewModel = moviesViewModel,
                navController = navController,
                initialGenreId = genreId,
            )
        }
        // Also keep the plain "movies" route for direct nav from the top bar
        composable(route = AppScreens.Movies.route) {
            MoviesScreen(
                viewModel = moviesViewModel,
                navController = navController,
                initialGenreId = 0,
            )
        }

        composable(route = AppScreens.Series.route) {
            SeriesScreen(viewModel = seriesViewModel, navController = navController)
        }

        composable(route = AppScreens.Search.route) {
            SearchScreen(viewModel = searchViewModel, navController = navController)
        }

        composable(route = AppScreens.Settings.route) {
            SettingsScreen(navController)
        }

        // Subtitle Settings — separate page (accessed from Settings → Playback
        // → Subtitle Settings, or from the player via SubtitleSettingsActivity).
        // Accepts an optional videoUrl for per-video mode.
        composable(
            route = AppScreens.SubtitleSettings.route,
            arguments = listOf(navArgument("videoUrl") {
                type = NavType.StringType
                defaultValue = ""
            }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { entry ->
            val rawVideoUrl = entry.arguments?.getString("videoUrl").orEmpty()
            val videoUrl = rawVideoUrl.takeIf { it.isNotBlank() }
            SubtitleSettingsScreen(
                navController = navController,
                videoUrl = videoUrl,
            )
        }

        composable(route = AppScreens.Favorites.route) {
            FavoritesScreen(navController)
        }

        composable(route = AppScreens.About.route) {
            AboutScreen(navController)
        }

        composable(route = AppScreens.WatchHistory.route) {
            WatchHistoryScreen(navController = navController)
        }

        // Detail screens — slide in from the right
        composable(
            route = AppScreens.SingleMovie.route,
            arguments = listOf(navArgument("movieId") { type = NavType.IntType; defaultValue = 0 }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
            SingleMovieScreen(movieId = movieId, navController = navController)
        }
        composable(
            route = AppScreens.SingleSeries.route,
            arguments = listOf(navArgument("seriesId") { type = NavType.IntType; defaultValue = 0 }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: 0
            SingleSeriesScreen(seriesId = seriesId, navController = navController)
        }
        composable(
            route = AppScreens.Country.route,
            arguments = listOf(navArgument("countryId") { type = NavType.IntType; defaultValue = 0 }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val countryId = backStackEntry.arguments?.getInt("countryId") ?: 0
            CountryScreen(countryId = countryId, viewModel = countryViewModel, navController = navController)
        }
        composable(
            route = AppScreens.Collection.route,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
            CollectionScreen(collectionId = collectionId, navController = navController)
        }

        // Mood Mode detail
        composable(
            route = AppScreens.Mood.route,
            arguments = listOf(navArgument("moodId") { type = NavType.StringType }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val moodId = backStackEntry.arguments?.getString("moodId").orEmpty()
            MoodScreen(moodId = moodId, navController = navController)
        }

        // Profile / Statistics
        composable(route = AppScreens.Profile.route) {
            ProfileScreen(navController = navController)
        }

        // Discovery hub
        composable(route = AppScreens.DiscoveryHub.route) {
            DiscoveryHubScreen(navController = navController)
        }

        // Person search (Actor / Director / Studio)
        composable(
            route = AppScreens.PersonSearch.route,
            arguments = listOf(navArgument("entityType") { type = NavType.StringType }),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val entityType = backStackEntry.arguments?.getString("entityType") ?: "Actor"
            PersonSearchScreen(entityType = entityType, navController = navController)
        }

        // Year explorer
        composable(
            route = AppScreens.YearExplorer.route,
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) {
            YearExplorerScreen(navController = navController)
        }

        // Discovery (year mode)
        composable(
            route = AppScreens.Discovery.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("query") { type = NavType.StringType },
            ),
            enterTransition = { NavTransitions.slideInFromRight() },
            exitTransition = { NavTransitions.fadeThroughExit() },
            popEnterTransition = { NavTransitions.fadeThroughEnter() },
            popExitTransition = { NavTransitions.slideOutToRight() },
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "year"
            val query = backStackEntry.arguments?.getString("query") ?: ""
            DiscoveryScreen(mode = mode, query = query, navController = navController)
        }
        // Issue 2: Downloads route removed — no Download Manager page.
    }
}
