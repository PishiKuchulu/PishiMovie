package com.pishimovie.utils

import com.pishimovie.util.PLog
import android.content.Context
import com.pishimovie.data.model.FavoriteGroup
import com.pishimovie.data.model.FavoriteItem
import com.pishimovie.data.model.Movie
import com.pishimovie.data.model.Series
import com.pishimovie.data.model.SubtitleSettings
import com.pishimovie.data.model.VideoPlayerSettings
import com.pishimovie.data.model.FontSettings
import com.pishimovie.data.model.WatchedEpisode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date

object StorageUtils {
    private const val TAG = "StorageUtils"

    /** Guards read-modify-write cycles on favorites.json. */
    private val favoritesLock = Any()
    /** Guards read-modify-write cycles on favorite_groups.json. */
    private val groupsLock = Any()
    /** Guards read-modify-write cycles on watched_episodes.json. */
    private val watchedEpisodesLock = Any()
    /** Guards read-modify-write cycles on the detail_cache/ directory. */
    private val detailCacheLock = Any()
    /** Max movie/series detail files kept in detail_cache/. */
    private const val DETAIL_CACHE_MAX_FILES = 50

    // Favorites functions - using a single JSON file for all favorites
    fun saveFavorite(context: Context, favorite: FavoriteItem) {
        synchronized(favoritesLock) {
            try {
                val favorites = loadAllFavorites(context).toMutableList()

                // Remove existing favorite with same id and type if it exists
                favorites.removeAll { it.id == favorite.id && it.type == favorite.type }

                // Add the new favorite at the beginning of the list (newest first)
                favorites.add(0, favorite)

                // Save all favorites to a single file
                val jsonString = Json.encodeToString(favorites)
                val file = File(context.filesDir, "favorites.json")
                file.writeText(jsonString)
                PLog.d(TAG, "Favorite saved: ${favorite.title}")
            } catch (e: Exception) {
                PLog.e(TAG, "Error saving favorite", e)
            }
        }
    }

    fun removeFavorite(context: Context, id: Int, type: String) {
        synchronized(favoritesLock) {
            try {
                val favorites = loadAllFavorites(context).toMutableList()

                // Remove the favorite with matching id and type
                favorites.removeAll { it.id == id && it.type == type }

                // Save updated favorites list
                val jsonString = Json.encodeToString(favorites)
                val file = File(context.filesDir, "favorites.json")
                file.writeText(jsonString)
                PLog.d(TAG, "Favorite removed: $id ($type)")
            } catch (e: Exception) {
                PLog.e(TAG, "Error removing favorite", e)
            }
        }
    }
    
    fun clearAllFavorites(context: Context) {
        try {
            val file = File(context.filesDir, "favorites.json")
            if (file.exists()) {
                file.delete()
            }
            PLog.d(TAG, "All favorites cleared")
        } catch (e: Exception) {
            PLog.e(TAG, "Error clearing all favorites", e)
        }
    }
    
    fun isFavorite(context: Context, id: Int, type: String): Boolean {
        return try {
            val favorites = loadAllFavorites(context)
            favorites.any { it.id == id && it.type == type }
        } catch (e: Exception) {
            PLog.e(TAG, "Error checking if favorite exists", e)
            false
        }
    }
    
    fun loadAllFavorites(context: Context): List<FavoriteItem> {
        return try {
            val file = File(context.filesDir, "favorites.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<List<FavoriteItem>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading all favorites", e)
            emptyList()
        }
    }
    
    // Function to save favorite to movie or series database when navigating to it
    fun saveFavoriteToDatabase(context: Context, favorite: FavoriteItem) {
        try {
            when (favorite.type) {
                "movie" -> {
                    // Convert FavoriteItem to Movie
                    val movie = Movie(
                        id = favorite.id,
                        type = favorite.type,
                        title = favorite.title,
                        description = favorite.description,
                        year = favorite.year,
                        imdb = favorite.imdb,
                        rating = favorite.rating,
                        duration = favorite.duration,
                        image = favorite.image,
                        cover = favorite.cover,
                        genres = favorite.genres,
                        sources = favorite.sources, // Include sources from favorites
                        country = favorite.country
                    )
                    saveMovieToFile(context, movie)
                }
                "series" -> {
                    // Convert FavoriteItem to Series
                    val series = Series(
                        id = favorite.id,
                        type = favorite.type,
                        title = favorite.title,
                        description = favorite.description,
                        year = favorite.year,
                        imdb = favorite.imdb,
                        rating = favorite.rating,
                        duration = favorite.duration,
                        image = favorite.image,
                        cover = favorite.cover,
                        genres = favorite.genres,
                        country = favorite.country
                    )
                    saveSeriesToFile(context, series)
                }
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving favorite to database", e)
        }
    }
    
    /**
     * Directory holding per-id movie/series detail files (movie_<id>.json,
     * series_<id>.json). Kept as a dedicated subdirectory so it can be
     * capped and excluded from auto-backup as a unit.
     */
    private fun detailCacheDir(context: Context): File =
        File(context.filesDir, "detail_cache").apply { mkdirs() }

    @Volatile private var detailCacheMigrated = false

    /**
     * One-time migration: older versions stored movie_*.json / series_*.json
     * directly in filesDir. Move them into detail_cache/ so the cap and the
     * backup exclusion apply uniformly.
     */
    private fun ensureDetailCacheMigrated(context: Context) {
        if (detailCacheMigrated) return
        synchronized(detailCacheLock) {
            if (detailCacheMigrated) return
            try {
                val dir = detailCacheDir(context)
                context.filesDir.listFiles { f ->
                    f.isFile && (f.name.startsWith("movie_") || f.name.startsWith("series_")) &&
                        f.name.endsWith(".json")
                }?.forEach { f ->
                    try {
                        f.renameTo(File(dir, f.name))
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
            detailCacheMigrated = true
        }
    }

    /** Deletes the oldest detail files beyond [DETAIL_CACHE_MAX_FILES]. */
    private fun capDetailCache(context: Context) {
        try {
            val files = detailCacheDir(context)
                .listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.sortedBy { it.lastModified() } ?: return
            if (files.size > DETAIL_CACHE_MAX_FILES) {
                files.take(files.size - DETAIL_CACHE_MAX_FILES).forEach { f ->
                    try { f.delete() } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    fun saveMovieToFile(context: Context, movie: Movie) {
        synchronized(detailCacheLock) {
            try {
                ensureDetailCacheMigrated(context)
                val jsonString = Json.encodeToString(movie)
                val file = File(detailCacheDir(context), "movie_${movie.id}.json")
                file.writeText(jsonString)
                capDetailCache(context)
                PLog.d(TAG, "Movie saved to file: ${file.name}")
            } catch (e: Exception) {
                PLog.e(TAG, "Error saving movie to file", e)
            }
        }
    }

    fun loadMovieFromFile(context: Context, movieId: Int): Movie? {
        return try {
            ensureDetailCacheMigrated(context)
            val file = File(detailCacheDir(context), "movie_$movieId.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<Movie>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading movie from file", e)
            null
        }
    }

    /**
     * Delete all movie files from storage
     */
    fun clearAllMovies(context: Context) {
        synchronized(detailCacheLock) {
            try {
                ensureDetailCacheMigrated(context)
                val movieFiles = detailCacheDir(context).listFiles { file ->
                    file.name.startsWith("movie_") && file.name.endsWith(".json")
                }

                movieFiles?.forEach { file ->
                    file.delete()
                    PLog.d(TAG, "Deleted movie file: ${file.name}")
                }
            } catch (e: Exception) {
                PLog.e(TAG, "Error clearing all movies", e)
            }
        }
    }

    // Series functions
    fun saveSeriesToFile(context: Context, series: Series) {
        synchronized(detailCacheLock) {
            try {
                ensureDetailCacheMigrated(context)
                val jsonString = Json.encodeToString(series)
                val file = File(detailCacheDir(context), "series_${series.id}.json")
                file.writeText(jsonString)
                capDetailCache(context)
                PLog.d(TAG, "Series saved to file: ${file.name}")
            } catch (e: Exception) {
                PLog.e(TAG, "Error saving series to file", e)
            }
        }
    }

    fun loadSeriesFromFile(context: Context, seriesId: Int): Series? {
        return try {
            ensureDetailCacheMigrated(context)
            val file = File(detailCacheDir(context), "series_$seriesId.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<Series>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading series from file", e)
            null
        }
    }

    /**
     * Delete all series files from storage
     */
    fun clearAllSeries(context: Context) {
        synchronized(detailCacheLock) {
            try {
                ensureDetailCacheMigrated(context)
                val seriesFiles = detailCacheDir(context).listFiles { file ->
                    file.name.startsWith("series_") && file.name.endsWith(".json")
                }

                seriesFiles?.forEach { file ->
                    file.delete()
                    PLog.d(TAG, "Deleted series file: ${file.name}")
                }
            } catch (e: Exception) {
                PLog.e(TAG, "Error clearing all series", e)
            }
        }
    }
    
    // Group functions
    fun saveFavoriteGroup(context: Context, group: FavoriteGroup) {
        synchronized(groupsLock) {
            try {
                val groups = loadAllFavoriteGroups(context).toMutableList()

                // Remove existing group with same id if it exists
                groups.removeAll { it.id == group.id }

                // Add the new group
                groups.add(group)

                // Save all groups to a single file
                val jsonString = Json.encodeToString(groups)
                val file = File(context.filesDir, "favorite_groups.json")
                file.writeText(jsonString)
                PLog.d(TAG, "Favorite group saved: ${group.name}")
            } catch (e: Exception) {
                PLog.e(TAG, "Error saving favorite group", e)
            }
        }
    }

    fun removeFavoriteGroup(context: Context, groupId: String) {
        synchronized(groupsLock) {
            try {
                val groups = loadAllFavoriteGroups(context).toMutableList()

                // Remove the group with matching id (but don't allow removing default group)
                groups.removeAll { it.id == groupId && !it.isDefault }

                // Save updated groups list
                val jsonString = Json.encodeToString(groups)
                val file = File(context.filesDir, "favorite_groups.json")
                file.writeText(jsonString)
                PLog.d(TAG, "Favorite group removed: $groupId")
            } catch (e: Exception) {
                PLog.e(TAG, "Error removing favorite group", e)
            }
        }
    }
    
    fun loadAllFavoriteGroups(context: Context): List<FavoriteGroup> {
        return try {
            val file = File(context.filesDir, "favorite_groups.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<List<FavoriteGroup>>(jsonString)
            } else {
                // Return a default group if no groups exist
                listOf(FavoriteGroup(id = "default", name = "Favorites", isDefault = true))
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading all favorite groups", e)
            // Return a default group if there's an error
            listOf(FavoriteGroup(id = "default", name = "Favorites", isDefault = true))
        }
    }
    
    fun getDefaultGroup(context: Context): FavoriteGroup {
        val groups = loadAllFavoriteGroups(context)
        return groups.find { it.isDefault } ?: FavoriteGroup(id = "default", name = "Favorites", isDefault = true)
    }
    
    fun addFavoriteToGroup(context: Context, groupId: String, favoriteId: Int, type: String) {
        synchronized(groupsLock) {
            try {
                val groups = loadAllFavoriteGroups(context).toMutableList()
                val group = groups.find { it.id == groupId }

                if (group != null) {
                    // Add to the specified group (allowing multiple groups)
                    if (type == "movie") {
                        group.addMovie(favoriteId)
                    } else if (type == "series") {
                        group.addSeries(favoriteId)
                    }

                    // Save updated groups
                    val jsonString = Json.encodeToString(groups)
                    val file = File(context.filesDir, "favorite_groups.json")
                    file.writeText(jsonString)
                    PLog.d(TAG, "Favorite added to group: $groupId")
                }
            } catch (e: Exception) {
                PLog.e(TAG, "Error adding favorite to group", e)
            }
        }
    }

    fun removeFavoriteFromGroup(context: Context, groupId: String, favoriteId: Int, type: String) {
        synchronized(groupsLock) {
            try {
                val groups = loadAllFavoriteGroups(context).toMutableList()
                val group = groups.find { it.id == groupId }

                if (group != null) {
                    if (type == "movie") {
                        group.removeMovie(favoriteId)
                    } else if (type == "series") {
                        group.removeSeries(favoriteId)
                    }

                    // Save updated groups
                    val jsonString = Json.encodeToString(groups)
                    val file = File(context.filesDir, "favorite_groups.json")
                    file.writeText(jsonString)
                    PLog.d(TAG, "Favorite removed from group: $groupId")
                }
            } catch (e: Exception) {
                PLog.e(TAG, "Error removing favorite from group", e)
            }
        }
    }
    
    // New function to get all groups for a specific favorite
    fun getGroupsForFavorite(context: Context, favoriteId: Int, type: String): List<FavoriteGroup> {
        return try {
            val groups = loadAllFavoriteGroups(context)
            groups.filter { group ->
                if (type == "movie") {
                    group.containsMovie(favoriteId)
                } else if (type == "series") {
                    group.containsSeries(favoriteId)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error getting groups for favorite", e)
            emptyList()
        }
    }
    
    // New function to check if a favorite is in a specific group
    fun isFavoriteInGroup(context: Context, groupId: String, favoriteId: Int, type: String): Boolean {
        return try {
            val groups = loadAllFavoriteGroups(context)
            val group = groups.find { it.id == groupId }
            if (group != null) {
                if (type == "movie") {
                    group.containsMovie(favoriteId)
                } else if (type == "series") {
                    group.containsSeries(favoriteId)
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error checking if favorite is in group", e)
            false
        }
    }
    
    fun getFavoritesInGroup(context: Context, groupId: String): List<FavoriteItem> {
        return try {
            val allFavorites = loadAllFavorites(context)
            val groups = loadAllFavoriteGroups(context)
            val group = groups.find { it.id == groupId }
            
            if (group != null) {
                val movieFavorites = allFavorites.filter { favorite ->
                    favorite.type == "movie" && group.movieIds.contains(favorite.id)
                }
                
                val seriesFavorites = allFavorites.filter { favorite ->
                    favorite.type == "series" && group.seriesIds.contains(favorite.id)
                }
                
                // Combine and sort by insertion order (newest first)
                (movieFavorites + seriesFavorites).sortedByDescending { favorite ->
                    // We don't have timestamps, so we'll just return them as is
                    favorite.id
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error getting favorites in group", e)
            emptyList()
        }
    }
    
    // Subtitle settings functions — delegate to SubtitleSettingsStorage to
    // avoid duplicated file IO. Existing call sites keep working unchanged.
    fun saveSubtitleSettings(context: Context, settings: SubtitleSettings) =
        SubtitleSettingsStorage.saveGlobal(context, settings)

    fun loadSubtitleSettings(context: Context): SubtitleSettings =
        SubtitleSettingsStorage.loadGlobal(context)
    
    // Video player settings functions
    fun saveVideoPlayerSettings(context: Context, settings: VideoPlayerSettings) {
        try {
            val jsonString = Json.encodeToString(settings)
            val file = File(context.filesDir, "video_player_settings.json")
            file.writeText(jsonString)
            PLog.d(TAG, "Video player settings saved to file")
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving video player settings to file", e)
        }
    }
    
    fun loadVideoPlayerSettings(context: Context): VideoPlayerSettings {
        return try {
            val file = File(context.filesDir, "video_player_settings.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<VideoPlayerSettings>(jsonString)
            } else {
                VideoPlayerSettings.DEFAULT
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading video player settings from file", e)
            VideoPlayerSettings.DEFAULT
        }
    }
    
    // Font settings functions
    fun saveFontSettings(context: Context, settings: FontSettings) {
        try {
            val jsonString = Json.encodeToString(settings)
            val file = File(context.filesDir, "font_settings.json")
            file.writeText(jsonString)
            PLog.d(TAG, "Font settings saved to file")
        } catch (e: Exception) {
            PLog.e(TAG, "Error saving font settings to file", e)
        }
    }
    
    fun loadFontSettings(context: Context): FontSettings {
        return try {
            val file = File(context.filesDir, "font_settings.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<FontSettings>(jsonString)
            } else {
                FontSettings.DEFAULT
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading font settings from file", e)
            FontSettings.DEFAULT
        }
    }
    
    // Watched episodes functions
    fun saveWatchedEpisode(context: Context, watchedEpisode: WatchedEpisode) {
        synchronized(watchedEpisodesLock) {
            try {
                val watchedEpisodes = loadAllWatchedEpisodes(context).toMutableList()

                // Remove existing entry for the same episode if it exists
                watchedEpisodes.removeAll {
                    it.seriesId == watchedEpisode.seriesId &&
                    it.seasonId == watchedEpisode.seasonId &&
                    it.episodeId == watchedEpisode.episodeId
                }

                // Add the new watched episode
                watchedEpisodes.add(watchedEpisode)

                // Save all watched episodes to a single file
                val jsonString = Json.encodeToString(watchedEpisodes)
                val file = File(context.filesDir, "watched_episodes.json")
                file.writeText(jsonString)
                PLog.d(TAG, "Watched episode saved: Series ${watchedEpisode.seriesId}, Season ${watchedEpisode.seasonId}, Episode ${watchedEpisode.episodeId}")
            } catch (e: Exception) {
                PLog.e(TAG, "Error saving watched episode", e)
            }
        }
    }

    fun removeWatchedEpisode(context: Context, seriesId: Int, seasonId: Int, episodeId: Int) {
        synchronized(watchedEpisodesLock) {
            try {
                val watchedEpisodes = loadAllWatchedEpisodes(context).toMutableList()

                // Remove the watched episode with matching ids
                watchedEpisodes.removeAll {
                    it.seriesId == seriesId &&
                    it.seasonId == seasonId &&
                    it.episodeId == episodeId
                }

                // Save updated watched episodes list
                val jsonString = Json.encodeToString(watchedEpisodes)
                val file = File(context.filesDir, "watched_episodes.json")
                file.writeText(jsonString)
                PLog.d(TAG, "Watched episode removed: Series $seriesId, Season $seasonId, Episode $episodeId")
            } catch (e: Exception) {
                PLog.e(TAG, "Error removing watched episode", e)
            }
        }
    }
    
    fun isEpisodeWatched(context: Context, seriesId: Int, seasonId: Int, episodeId: Int): Boolean {
        return try {
            val watchedEpisodes = loadAllWatchedEpisodes(context)
            watchedEpisodes.any { 
                it.seriesId == seriesId && 
                it.seasonId == seasonId && 
                it.episodeId == episodeId 
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error checking if episode is watched", e)
            false
        }
    }
    
    fun loadAllWatchedEpisodes(context: Context): List<WatchedEpisode> {
        return try {
            val file = File(context.filesDir, "watched_episodes.json")
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<List<WatchedEpisode>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Error loading all watched episodes", e)
            emptyList()
        }
    }
    
    fun clearAllWatchedEpisodes(context: Context) {
        try {
            val file = File(context.filesDir, "watched_episodes.json")
            if (file.exists()) {
                file.delete()
            }
            PLog.d(TAG, "All watched episodes cleared")
        } catch (e: Exception) {
            PLog.e(TAG, "Error clearing all watched episodes", e)
        }
    }
}