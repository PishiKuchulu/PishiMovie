package com.pishimovie.util

/**
 * Network connectivity helper (Phase 6: "Offline Mode", "Sync when online again").
 *
 * The common API is context-free: connectivity is a device-global
 * property, so the Android actual consults the application Context
 * (registered at process start) and the desktop actual lets the real
 * request be the judge. Android call sites in the app module that
 * pass a Context keep working through the actual's overloads.
 */
expect object NetworkUtils {

    /** Returns true if the device currently has any active network connection. */
    fun isOnline(): Boolean

    /** Returns true if connected to an unmetered connection (Wi-Fi). */
    fun isUnmetered(): Boolean
}
