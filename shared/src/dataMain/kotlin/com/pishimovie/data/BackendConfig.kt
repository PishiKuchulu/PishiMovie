package com.pishimovie.data

/**
 * Backend connection config for the shared data layer.
 *
 * The values themselves live in the Android app module's BuildConfig
 * (unchanged — see app/build.gradle.kts) and are injected once at
 * process start by the app's Application class; the desktop app will
 * inject its own values the same way. Shared code cannot reference the
 * app module's BuildConfig, so it reads them from here instead. Every
 * repository resolves these lazily (instance getters / construction
 * time), and injection happens in Application.onCreate before any
 * repository can be created — the same values, at the same time, as
 * when the data layer read BuildConfig directly.
 */
object BackendConfig {
    @Volatile
    var apiBaseUrl: String = ""

    @Volatile
    var apiKey: String = ""

    @Volatile
    var helperServers: Array<String> = emptyArray()

    fun init(apiBaseUrl: String, apiKey: String, helperServers: Array<String>) {
        this.apiBaseUrl = apiBaseUrl
        this.apiKey = apiKey
        this.helperServers = helperServers
    }
}
