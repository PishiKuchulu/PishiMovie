package com.pishimovie

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pishimovie.data.model.FontSettings
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.pishimovie.navigation.AppNavigation
import com.pishimovie.navigation.AppScreens
import com.pishimovie.navigation.PishiTopAppBar
import com.pishimovie.navigation.SidebarNavigation
import com.pishimovie.ui.components.AppUpdateController
import com.pishimovie.ui.components.AppUpdateDialog
import com.pishimovie.ui.components.LocalAppUpdateController
import com.pishimovie.ui.theme.PishiContextHolder
import com.pishimovie.ui.theme.PishiImageLoader
import com.pishimovie.ui.theme.PishiMovieTheme
import com.pishimovie.ui.theme.rememberAppThemeSettings
import com.pishimovie.ui.theme.ThemeMode
import com.pishimovie.ui.theme.LocalThemeSettings
import com.pishimovie.utils.StorageUtils
import com.pishimovie.utils.DeviceUtils
import com.pishimovie.util.PerformanceOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize the Coil context holder before any Compose code runs so
        // image loading requests can find the loader.
        PishiContextHolder.init(applicationContext)
        super.onCreate(savedInstanceState)

        // Set default orientation to portrait for mobile/tablet.
        if (!DeviceUtils.isTv(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // Edge-to-edge on supported versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            enableEdgeToEdge()
        }

        coil.Coil.setImageLoader(PishiImageLoader)

        // Warm up caches + auto-resume interrupted downloads
        PerformanceOptimizer.onAppStart(applicationContext)

        setContent {
            MainApp()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        PerformanceOptimizer.onTrimMemory(applicationContext, level)
    }
}

@Composable
fun MainApp() {
    val themeSettingsState = rememberAppThemeSettings()
    val context = androidx.compose.ui.platform.LocalContext.current
    val fontSettings by produceState(initialValue = FontSettings(), context) {
        value = withContext(Dispatchers.IO) { StorageUtils.loadFontSettings(context.applicationContext) }
    }

    PishiMovieTheme(themeSettingsState.value, fontSettings) {
        MainScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val themeSettings = LocalThemeSettings.current
    val isTv = DeviceUtils.isTv(androidx.compose.ui.platform.LocalContext.current)
    val configuration = LocalConfiguration.current

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // System UI controller for edge-to-edge support
    val systemUiController = rememberSystemUiController()
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (themeSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
        else -> true // Exhaustive fallback
    }
    val useDarkIcons = !isDarkTheme

    LaunchedEffect(isDarkTheme) {
        systemUiController.setStatusBarColor(
            color = androidx.compose.ui.graphics.Color.Transparent,
            darkIcons = useDarkIcons
        )
        systemUiController.setNavigationBarColor(
            color = androidx.compose.ui.graphics.Color.Transparent,
            darkIcons = useDarkIcons
        )
    }

    // In-app update: one controller at the app root, shared by the launch
    // auto-check popup and the Settings card.
    val context = LocalContext.current
    val updateController = remember { AppUpdateController(context) }

    LaunchedEffect(Unit) {
        updateController.check()
    }
    LaunchedEffect(updateController.state) {
        if (updateController.state is AppUpdateController.State.Available) {
            updateController.dialogRequested = true
        }
    }

    // Top-level pages show the shared PishiTopAppBar.
    val showTopBar = when {
        currentRoute == null -> true
        currentRoute == AppScreens.Home.route -> true
        currentRoute.startsWith("movies") -> true
        currentRoute == AppScreens.Series.route -> true
        currentRoute == AppScreens.Search.route -> true
        currentRoute == AppScreens.Favorites.route -> true
        currentRoute == AppScreens.Profile.route -> true
        currentRoute == AppScreens.DiscoveryHub.route -> true
        currentRoute == AppScreens.WatchHistory.route -> true
        currentRoute == AppScreens.Settings.route -> true
        currentRoute == AppScreens.About.route -> true
        currentRoute.startsWith("subtitle_settings") -> false
        else -> false
    }

    CompositionLocalProvider(LocalAppUpdateController provides updateController) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isTv && showTopBar) {
                    PishiTopAppBar(
                        navController = navController,
                        currentRoute = currentRoute,
                    )
                }
            },
        ) { innerPadding ->
            // Force layout direction app-wide based on locale if needed,
            // but here we follow system/app locale.
            if (isTv) {
                // TV layout with sidebar navigation
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    SidebarNavigation(navController)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
                    ) {
                        AppNavigation(navController)
                    }
                }
            } else {
                // Mobile/tablet layout — content fills the area below the top bar
                Box(modifier = Modifier.padding(innerPadding)) {
                    AppNavigation(navController)
                }
            }
        }

        if (updateController.dialogRequested) {
            AppUpdateDialog(
                controller = updateController,
                onDismiss = { updateController.dialogRequested = false },
            )
        }
    }
}
