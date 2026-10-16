package com.pishimovie.data.repository

import com.pishimovie.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Single shared OkHttpClient for all repositories (one connection pool and
 * one dispatcher thread pool per process instead of per repository instance).
 */
private val sharedClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * Base repository for all PishiMovie backend calls.
 *
 * Phase 7: API key + helper servers are now sourced from [BuildConfig] instead
 * of being hardcoded in source — cleaner separation and easier to rotate.
 *
 * CRITICAL: This class is preserved exactly as-is in terms of behavior —
 * the executeRequest fallback logic (primary → helper servers) is the
 * backbone of PishiMovie's streaming compatibility and must not change.
 */
open class BaseRepository {
    /** Shared process-wide client (see [sharedClient]). */
    protected val client: OkHttpClient get() = sharedClient

    /** API key sourced from BuildConfig (Phase 7 security improvement). */
    protected val API_KEY: String get() = BuildConfig.API_KEY

    /** Helper servers sourced from BuildConfig (Phase 7 security improvement). */
    protected val helperServers: Array<String> get() = BuildConfig.HELPER_SERVERS

    protected suspend fun executeRequest(
        primaryUrl: String,
        requestBuilder: (String) -> Request,
    ): String {
        return withContext(Dispatchers.IO) {
            // First, try the primary server
            try {
                val primaryRequest = requestBuilder(primaryUrl)
                client.newCall(primaryRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext response.body?.string()
                            ?: throw Exception("Empty response body from primary server")
                    } else {
                        throw Exception("Primary server returned error: ${response.code}")
                    }
                }
            } catch (primaryException: Exception) {
                // If primary server fails, try helper servers
                for (helperServer in helperServers) {
                    try {
                        val helperUrl = primaryUrl.replace(Regex("^https?://[^/]+"), helperServer)
                        val helperRequest = requestBuilder(helperUrl)
                        client.newCall(helperRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                return@withContext response.body?.string()
                                    ?: throw Exception("Empty response body from helper server")
                            }
                        }
                    } catch (helperException: Exception) {
                        // Keep the helper failure visible: it becomes a suppressed
                        // exception on the primary one instead of being discarded.
                        primaryException.addSuppressed(helperException)
                        continue
                    }
                }
                throw primaryException
            }
        }
    }
}
