package com.pishimovie.navigation

import androidx.navigation.NavController

/**
 * Navigates to [route] unless the current back-stack entry already shows that
 * exact destination. Protects against double-tap duplicate destinations: the
 * current entry's route pattern (e.g. "single_movie/{movieId}") is expanded
 * with its own arguments and compared against the concrete target route.
 */
fun NavController.navigateSafe(route: String) {
    val entry = currentBackStackEntry
    val pattern = entry?.destination?.route
    if (pattern != null) {
        var current = pattern
        val args = entry.arguments
        if (args != null) {
            current = Regex("\\{([^}]+)\\}").replace(pattern) { match ->
                val name = match.groupValues[1]
                if (args.containsKey(name)) args.get(name)?.toString() ?: match.value else match.value
            }
        }
        if (current == route) return
    }
    navigate(route)
}
