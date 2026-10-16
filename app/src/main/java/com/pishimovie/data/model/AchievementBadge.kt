package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * Achievement badge (Phase 5: optional). Awarded automatically by
 * [com.pishimovie.data.repository.StatisticsRepository] when the user
 * crosses defined thresholds.
 */
@Serializable
data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val accentHex: Long,
    val unlocked: Boolean,
    val progress: Float = 1f,    // 0..1 — partial progress for locked badges
) {
    companion object {
        /** Catalog of all badges. The repository evaluates unlock state. */
        val CATALOG: List<AchievementBadge> = listOf(
            AchievementBadge("first_watch", "First Steps", "Watch your first movie", "🎬", 0xFFE11D48, false),
            AchievementBadge("binge_5", "Binge Beginner", "Complete 5 titles", "🔥", 0xFFFFC53D, false),
            AchievementBadge("binge_25", "Binge Master", "Complete 25 titles", "🏆", 0xFFFFB700, false),
            AchievementBadge("binge_100", "Cinema Legend", "Complete 100 titles", "👑", 0xFFA78BFA, false),
            AchievementBadge("night_owl", "Night Owl", "Watch after midnight", "🦉", 0xFF6B21A8, false),
            AchievementBadge("genre_explorer", "Genre Explorer", "Watch 5 different genres", "🎭", 0xFF2DD4BF, false),
            AchievementBadge("critic", "Critic", "Rate 10 titles", "⭐", 0xFFEC4899, false),
            AchievementBadge("marathon", "Marathon Runner", "Watch 3+ hours in one day", "🏃", 0xFF60A5FA, false),
            AchievementBadge("completionist", "Completionist", "Finish 10 movies 100%", "✅", 0xFF34D399, false),
            AchievementBadge("series_binger", "Series Binger", "Complete 5 series", "📺", 0xFFF59E0B, false),
        )
    }
}
