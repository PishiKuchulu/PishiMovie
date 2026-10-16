package com.pishimovie.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages [ThemeSettings] persistence using DataStore.
 */
private val Context.dataStore by preferencesDataStore(name = "theme_prefs")

class ThemeManager(private val context: Context) {

    val themeSettings: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        val themeTypeStr = prefs[THEME_TYPE_KEY] ?: ThemeType.NETFLIX.name
        val themeType = try { ThemeType.valueOf(themeTypeStr) } catch (e: Exception) { ThemeType.NETFLIX }
        
        val themeModeStr = prefs[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(themeModeStr) } catch (e: Exception) { ThemeMode.SYSTEM }
        
        val amoledMode = prefs[AMOLED_MODE_KEY] ?: false
        val dynamicColors = prefs[DYNAMIC_COLORS_KEY] ?: false
        val primaryColor = prefs[PRIMARY_COLOR_KEY] ?: defaultPrimaryColor.toArgb()
        
        ThemeSettings(
            themeType = themeType,
            themeMode = themeMode,
            amoledMode = amoledMode,
            dynamicColors = dynamicColors,
            primaryColor = Color(primaryColor),
        )
    }

    suspend fun saveThemeSettings(settings: ThemeSettings) {
        context.dataStore.edit { prefs ->
            prefs[THEME_TYPE_KEY] = settings.themeType.name
            prefs[THEME_MODE_KEY] = settings.themeMode.name
            prefs[AMOLED_MODE_KEY] = settings.amoledMode
            prefs[DYNAMIC_COLORS_KEY] = settings.dynamicColors
            prefs[PRIMARY_COLOR_KEY] = settings.primaryColor.toArgb()
        }
    }

    companion object {
        private val THEME_TYPE_KEY = stringPreferencesKey("theme_type")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val AMOLED_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("amoled_mode")
        private val DYNAMIC_COLORS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_colors")
        private val PRIMARY_COLOR_KEY = androidx.datastore.preferences.core.intPreferencesKey("primary_color")
    }
}

@Composable
fun rememberThemeManager(): ThemeManager {
    val context = LocalContext.current
    val appContext = context.applicationContext
    return remember(appContext) { ThemeManager(appContext) }
}

@Composable
fun rememberAppThemeSettings(): MutableState<ThemeSettings> {
    val themeManager = rememberThemeManager()
    val settingsState = themeManager.themeSettings.collectAsState(initial = ThemeSettings())
    val state = remember { mutableStateOf(settingsState.value) }
    LaunchedEffect(settingsState.value) { state.value = settingsState.value }
    return state
}
