package com.pishimovie.desktop

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.pishimovie.data.BackendConfig
import java.io.File

/**
 * Desktop entry point (Windows + Linux).
 *
 * Startup mirrors the Android Application.onCreate: build the
 * platform Context (here the desktop stand-in from :shared, pointing
 * at the app's data/cache folders), inject the backend values into
 * the shared BackendConfig, then open the window. The backend values
 * come from DesktopBackendConfig, generated at build time from the
 * Android module's BuildConfig fields — one source of truth.
 */
fun main() {
    val osName = System.getProperty("os.name").lowercase()
    val dataRoot = if (osName.contains("win")) {
        File(System.getenv("APPDATA") ?: (System.getProperty("user.home") + "\\AppData\\Roaming"), "PishiMovie")
    } else {
        File(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "pishimovie")
    }
    val filesDir = File(dataRoot, "files").apply { mkdirs() }
    val cacheDir = File(dataRoot, "cache").apply { mkdirs() }
    val context = Context(filesDir, cacheDir)

    BackendConfig.init(
        DesktopBackendConfig.API_BASE_URL,
        DesktopBackendConfig.API_KEY,
        DesktopBackendConfig.HELPER_SERVERS,
    )

    SingletonImageLoader.setSafe { platformContext ->
        ImageLoader.Builder(platformContext)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PishiMovie",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        App(context)
                    }
                }
            }
        }
    }
}
