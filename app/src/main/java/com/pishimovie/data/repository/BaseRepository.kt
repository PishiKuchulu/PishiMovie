package com.pishimovie.data.repository

import com.pishimovie.BuildConfig
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
 *  - GETs are NOT routed through the response cache: API payloads embed
 *    signed stream URLs with rotating tokens, and a cached body strands
 *    them (the v1.4.0 total-playback regression, fixed in v1.4.1).
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
        // NO response cache in front of this chain — deliberately. API
        // responses embed signed stream URLs whose tokens rotate; serving a
        // cached body (the ApiResponseCache disk TTL was a fixed 1 hour)
        // strands those URLs and every playback then fails with an HTTP
        // error at the CDN. That is exactly the v1.4.0 regression this
        // hotfix (v1.4.1) removes: every call fetches fresh, exactly like
        // v1.3.1. ApiResponseCache stays in the tree, unwired, until a
        // future change can cache only non-source payloads safely.

        // Primary → helper fallback chain, under one deadline.
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
}
