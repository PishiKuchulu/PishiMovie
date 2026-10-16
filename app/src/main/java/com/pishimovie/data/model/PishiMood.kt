package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * A PishiMovie Mood Mode (Part 5: "Mood Mode (Action, Relax, Horror, Family,
 * Romance, Sci-Fi)"; Phase 5 spec expands to 9 moods).
 *
 * Each mood maps to a curated set of genre keywords. The [MoodRepository]
 * uses these keywords to search the existing backend and return dynamic
 * recommendations — no external AI service required.
 */
@Serializable
data class PishiMood(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val accentHex: Long,        // ARGB long for the gradient surface
    val keywords: List<String>, // genre keywords used as backend search queries
) {
    companion object {
        val CATALOG: List<PishiMood> = listOf(
            PishiMood(
                id = "action_night",
                title = "Action Night",
                subtitle = "High-octane thrills & explosions",
                emoji = "💥",
                accentHex = 0xFFE11D48,
                keywords = listOf("Action", "Thriller", "Adventure"),
            ),
            PishiMood(
                id = "family_time",
                title = "Family Time",
                subtitle = "Fun for all ages",
                emoji = "👨‍👩‍👧",
                accentHex = 0xFF34D399,
                keywords = listOf("Family", "Animation", "Kids"),
            ),
            PishiMood(
                id = "comedy",
                title = "Comedy",
                subtitle = "Laugh out loud",
                emoji = "😂",
                accentHex = 0xFFFFC53D,
                keywords = listOf("Comedy", "Romance"),
            ),
            PishiMood(
                id = "romance",
                title = "Romance",
                subtitle = "Love stories & heart flutter",
                emoji = "❤️",
                accentHex = 0xFFEC4899,
                keywords = listOf("Romance", "Drama"),
            ),
            PishiMood(
                id = "horror",
                title = "Horror",
                subtitle = "Scares & suspense",
                emoji = "👻",
                accentHex = 0xFF6B21A8,
                keywords = listOf("Horror", "Thriller", "Mystery"),
            ),
            PishiMood(
                id = "sci_fi",
                title = "Sci-Fi",
                subtitle = "Future worlds & technology",
                emoji = "🚀",
                accentHex = 0xFF60A5FA,
                keywords = listOf("Science Fiction", "Fantasy"),
            ),
            PishiMood(
                id = "relax",
                title = "Relax",
                subtitle = "Chill & unwind",
                emoji = "🧘",
                accentHex = 0xFF2DD4BF,
                keywords = listOf("Drama", "Romance", "Music"),
            ),
            PishiMood(
                id = "mystery",
                title = "Mystery",
                subtitle = "Puzzles & investigations",
                emoji = "🔍",
                accentHex = 0xFF7C3AED,
                keywords = listOf("Mystery", "Crime", "Thriller"),
            ),
            PishiMood(
                id = "adventure",
                title = "Adventure",
                subtitle = "Epic journeys & quests",
                emoji = "🗺️",
                accentHex = 0xFFF59E0B,
                keywords = listOf("Adventure", "Action", "Fantasy"),
            ),
        )

        fun byId(id: String): PishiMood? = CATALOG.find { it.id == id }
    }
}
