package com.pishimovie.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * PishiMovie theme settings — 5 premium streaming themes.
 *
 * Persisted via [ThemeManager] (DataStore).
 */
data class ThemeSettings(
    val themeType: ThemeType = ThemeType.NETFLIX,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledMode: Boolean = false,
    val dynamicColors: Boolean = false,
    val primaryColor: Color = NetflixColors.primary,
)

enum class ThemeType {
    NETFLIX, PISHI, MIDNIGHT_BLUE, CINEMA_GOLD, OLED_BLACK
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

val defaultPrimaryColor = NetflixColors.primary

/**
 * Accent palette for the settings screen.
 */
val colorOptions = listOf(
    NetflixColors.primary,        // Netflix red
    PishiColors.primary,          // Cat Orange
    Color(0xFF00BCD4),            // Cyan
    Color(0xFFD4A843),            // Gold
    Color(0xFF448AFF),            // Blue
    Color(0xFF6650a4),            // Purple
    Color(0xFF7D5260),            // Pink
    Color(0xFF006D32),            // Green
    Color(0xFFBA1A1A),            // Red
    Color(0xFF625B71),            // Purple Grey
)

@Composable
fun rememberThemeSettings(): ThemeSettings {
    return remember { ThemeSettings() }
}
