package com.pishimovie.platform

import android.content.Context

/**
 * Process-wide application Context for the shared layer's Android
 * actuals whose common API is context-free (NetworkUtils, DeviceUtils).
 * Set once by the app's Application class in onCreate, before any
 * other app code can run.
 */
object PlatformContextHolder {
    lateinit var appContext: Context
}
