package com.pishimovie.data.repository

import com.pishimovie.BuildConfig
import com.pishimovie.data.cache.ApiResponseCache
import com.pishimovie.ui.theme.PishiContextHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Matches the scheme + host of a URL so the fallback chain can swap in a
 * helper server's origin. Compiled ONCE: the previous code rebuilt this
 * Regex on every helper attempt of every request.
 */
private val HOST_ORIGIN_REGEX = Regex("^https?://[^/]+")

/**
 * Per-attempt clients for the fallback chain. The primary gets the most
 * patience; helpers are mirrors and fail faster so the chain as a whole
 * stays responsive. Both share one base configuration via newBuilder()
 * semantics (same pool/timeout family, different timeouts).
 */
private val primaryClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private val helperClient: OkHttpClient by lazy {
    primaryClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/** Hard ceiling for one executeRequest call across primary + all helpers. */
private const val OVERALL_DEADLINE_MS = 45_000L

/**
 * Base repository for all PishiMovie backend calls.
 *
 * Phase 7: API key + helper servers are now sourced from [BuildConfig] instead
 * of being hardcoded in source — cleaner separation and easier to rotate.
 *
 * The fallback shape (primary → helper servers) is the backbone of
 * PishiMovie's streaming compatibility and is preserved. What changed in
 * the hardening pass:
 *  - [CancellationException] is rethrown immediately everywhere — a
 *    cancelled call no longer burns time walking the helper chain and no
 *    longer surfaces as a fake network error in the ViewModels.
 *  - The whole chain has an overall 45 s deadline ([withTimeout]) and
 *    per-attempt timeouts are tuned (primary 10/20 s, helpers 8/15 s)
 *    instead of an unbounded 30/30 s per host.
 *  - GETs are routed through [ApiResponseCache.cachedGet] first (TTL
 *    memory+disk cache, previously wired to zero callers); only when the
 *    cache cannot produce a body does the primary→helper chain run, so
 *    error semantics stay exactly the chain's.
 */
open class BaseRepository {
    /** Shared process-wide client (primary-attempt tuning). */
    protected val client: OkHttpClient get() = primaryClient

    /** API key sourced from BuildConfig (Phase 7 security improvement). */
    protected val API_KEY: String get() = BuildConfig.API_KEY

    /** Helper servers sourced from BuildConfig (Phase 7 security improvement). */
    protected val helperServers: Array<String> get() = BuildConfig.HELPER_SERVERS

    protected suspend fun executeRequest(
        primaryUrl: String,
        requestBuilder: (String) -> Request,
    ): String {
        // Phase 1 — response cache. All current callers issue plain GETs
        // (no custom headers), which is exactly what cachedGet performs.
        // A non-null body ends the call; any failure falls through to the
        // authoritative chain below.
        cacheInstance()?.let { cache ->
            try {
                cache.cachedGet(primaryUrl)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Cache-layer failure (HTTP status, transport) — the chain
                // below decides the outcome and produces the real error.
            }
        }

        // Phase 2 — primary → helper fallback chain, under one deadline.
        return try {
            withTimeout(OVERALL_DEADLINE_MS) {
                withContext(Dispatchers.IO) {
                    // First, try the primary server
                    try {
                        val primaryRequest = requestBuilder(primaryUrl)
                        primaryClient.newCall(primaryRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                return@withContext response.body?.string()
                                    ?: throw Exception("Empty response body from primary server")
                            } else {
                                throw Exception("Primary server returned error: ${response.code}")
                            }
                        }
                    } catch (e: CancellationException) {
                        // Never let cancellation masquerade as a primary
                        // failure or trigger the helper chain.
                        throw e
                    } catch (primaryException: Exception) {
                        // If primary server fails, try helper servers
                        for (helperServer in helperServers) {
                            try {
                                val helperUrl = primaryUrl.replace(HOST_ORIGIN_REGEX, helperServer)
                                val helperRequest = requestBuilder(helperUrl)
                                helperClient.newCall(helperRequest).execute().use { response ->
                                    if (response.isSuccessful) {
                                        return@withContext response.body?.string()
                                            ?: throw Exception("Empty response body from helper server")
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
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
        } catch (e: TimeoutCancellationException) {
            // The overall deadline fired — surface it as a normal request
            // failure (callers only treat true CancellationException as
            // "job abandoned").
            throw Exception("Request timed out after ${OVERALL_DEADLINE_MS / 1000}s for $primaryUrl", e)
        }
    }

    /**
     * The shared [ApiResponseCache], or null when the app context holder
     * has not been initialized yet (very early calls simply skip caching
     * instead of crashing on the lateinit).
     */
    private fun cacheInstance(): ApiResponseCache? {
        val ctx = runCatching { PishiContextHolder.context }.getOrNull() ?: return null
        return ApiResponseCache.get(ctx)
    }
}
