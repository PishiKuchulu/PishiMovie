package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * A curated PishiMovie collection (Part 5: "Collections (Marvel, DC, Harry
 * Potter, John Wick, etc.)").
 *
 * Collections are realised as saved search queries against the existing
 * backend search API — no API changes required. Each collection has a display
 * name, an accent color (for the card surface), and a representative emoji
 * used as a fallback when no poster has loaded yet.
 */
@Serializable
data class PishiCollection(
    val id: String,
    val title: String,
    val query: String,
    val accentHex: Long,        // ARGB long
    val emoji: String,
    val subtitle: String,
) {
    companion object {
        /**
         * Curated catalog of collections. Queries are chosen to map cleanly to
         * the backend's search endpoint.
         */
        val CATALOG: List<PishiCollection> = listOf(
            PishiCollection("marvel", "Marvel Universe", "marvel", 0xFFB11310, "🦸", "Superheroes & Avengers"),
            PishiCollection("dc", "DC Extended Universe", "dc comics", 0xFF0476F2, "🦇", "Batman, Superman & Justice League"),
            PishiCollection("harry_potter", "Harry Potter", "harry potter", 0xFF6B3FA0, "⚡", "The Wizarding World"),
            PishiCollection("john_wick", "John Wick", "john wick", 0xFF1A1A1A, "🔫", "The Baba Yaga Saga"),
            PishiCollection("lotr", "Lord of the Rings", "lord of the rings", 0xFF1F6B3B, "💍", "Middle-earth Adventures"),
            PishiCollection("fast_furious", "Fast & Furious", "fast and furious", 0xFFD64550, "🏎️", "Family & Fast Cars"),
            PishiCollection("james_bond", "James Bond", "james bond", 0xFF0A3D62, "🕶️", "007 Missions"),
            PishiCollection("star_wars", "Star Wars", "star wars", 0xFFE1B41B, "⭐", "A Galaxy Far, Far Away"),
            PishiCollection("spider_man", "Spider-Man", "spider man", 0xFFC53030, "🕷️", "Web-slinging Heroes"),
            PishiCollection("mission_impossible", "Mission Impossible", "mission impossible", 0xFF2D3748, "直升机", "Ethan Hunt Thrillers"),
        )
    }
}
