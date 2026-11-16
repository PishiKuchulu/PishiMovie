package com.pishimovie

import android.app.Application
import com.pishimovie.data.BackendConfig
import com.pishimovie.platform.PlatformContextHolder

/**
 * App process entry point. Its one job today: inject the backend
 * connection values from this module's BuildConfig (unchanged) into
 * the shared data layer, which lives in :shared and cannot reference
 * the app module's BuildConfig. Application.onCreate runs before any
 * repository can be constructed, so the shared layer sees the same
 * values at the same time as when it read BuildConfig directly.
 */
class PishiMovieApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlatformContextHolder.appContext = applicationContext
        BackendConfig.init(
            BuildConfig.API_BASE_URL,
            BuildConfig.API_KEY,
            BuildConfig.HELPER_SERVERS,
        )
    }
}
