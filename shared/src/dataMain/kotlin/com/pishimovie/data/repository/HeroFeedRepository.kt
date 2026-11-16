package com.pishimovie.data.repository

import com.pishimovie.data.model.Country
import com.pishimovie.data.model.Genre
import com.pishimovie.data.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Curated hero feed for the Home screen.
 *
 * The feed is served by the PishiMovie hero Worker (Cloudflare): a
 * scheduled updater mirrors the hero curation of an external index
 * site by TITLE NAME only and resolves every title against the
 * PishiMovie backend, so the items returned here are ordinary
 * catalogue items (own ids, posters and backdrops) in curated order.
 * No media or text comes from the external site.
 *
 * Failure contract: ANY problem (offline, timeout, bad payload, empty
 * list) returns an empty list and [HomeRepository] falls back to its
 * built-in hero — this feed can never break or delay Home: the whole
 * call is capped at ~6 s and the dedicated client below fails fast,
 * deliberately NOT reusing the backend's primary→helper chain (the
 * feed is a nice-to-have, the backend is the source of truth).
 */
class HeroFeedRepository {

    private val feedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    suspend fun getHeroItems(): List<Movie> {
        val body = withTimeoutOrNull(FEED_DEADLINE_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(HERO_FEED_URL)
                        .header("Accept", "application/json")
                        .get()
                        .build()
                    feedClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }.getOrNull()
            }
        } ?: return emptyList()

        return runCatching { parseFeed(body) }.getOrDefault(emptyList())
    }

    /** Feed payload: { items: [ <backend item>, … ], meta: { … } }. */
    private fun parseFeed(body: String): List<Movie> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val movies = mutableListOf<Movie>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            try {
                movies.add(parseItem(obj))
            } catch (e: Exception) {
                // Skip items that fail to parse, like MovieRepository does.
                continue
            }
        }
        return movies
    }

    /** Same field defaults as MovieRepository.parseMovie. */
    private fun parseItem(obj: JSONObject): Movie {
        return Movie(
            id = obj.optInt("id", 0),
            type = obj.optString("type", ""),
            title = obj.optString("title", "Unknown Title"),
            description = obj.optString("description", "No description available"),
            year = obj.optInt("year", 0),
            imdb = obj.optDouble("imdb", 0.0),
            rating = obj.optDouble("rating", 0.0),
            duration = obj.optString("duration", null).takeIf { it != "null" && it != "N/A" },
            image = obj.optString("image", ""),
            cover = obj.optString("cover", ""),
            genres = parseGenres(obj.optJSONArray("genres")),
            sources = emptyList(),
            country = parseCountries(obj.optJSONArray("country"))
        )
    }

    private fun parseGenres(array: JSONArray?): List<Genre> {
        if (array == null) return emptyList()
        val genres = mutableListOf<Genre>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            genres.add(Genre(id = obj.optInt("id", 0), title = obj.optString("title", "Unknown")))
        }
        return genres
    }

    private fun parseCountries(array: JSONArray?): List<Country> {
        if (array == null) return emptyList()
        val countries = mutableListOf<Country>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            countries.add(
                Country(
                    id = obj.optInt("id", 0),
                    title = obj.optString("title", "Unknown"),
                    image = obj.optString("image", "")
                )
            )
        }
        return countries
    }

    private companion object {
        const val HERO_FEED_URL =
            "https://pishimovie-hero.farzad-sholeh.workers.dev/"
        const val FEED_DEADLINE_MS = 6_000L
    }
}
