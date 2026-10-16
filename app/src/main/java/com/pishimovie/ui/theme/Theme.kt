package com.pishimovie.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * PishiMovie theme — wraps Material 3 [MaterialTheme] with custom color
 * schemes across 5 premium streaming themes.
 *
 * Themes:
 *   NETFLIX      — classic dark red / near-black (default)
 *   PISHI        — Cat Orange #E88224 + Cyan #00B5E2 (from logo)
 *   MIDNIGHT_BLUE — dark navy + cyan accents
 *   CINEMA_GOLD   — dark graphite + gold highlights
 *   OLED_BLACK    — true #000000 AMOLED + cool blue
 */

val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

fun colorSchemeForTheme(themeType: ThemeType, isDark: Boolean): ColorScheme {
    val c: BrandPalette = when (themeType) {
        ThemeType.NETFLIX -> NetflixColors
        ThemeType.PISHI -> PishiColors
        ThemeType.MIDNIGHT_BLUE -> MidnightBlueColors
        ThemeType.CINEMA_GOLD -> CinemaGoldColors
        ThemeType.OLED_BLACK -> OledColors
    }
    if (isDark) {
        return darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline,
            error = c.error,
            onError = c.onError,
            inverseSurface = c.inverseSurface,
            inverseOnSurface = c.inverseOnSurface,
            scrim = c.scrim,
        )
    } else {
        // Light variants — automatically inverted from dark scheme colors
        return lightColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = Color(0xFF1A1A1A),
            secondary = c.secondary,
            onSecondary = Color.White,
            secondaryContainer = c.secondaryContainer.copy(alpha = 0.3f),
            onSecondaryContainer = Color(0xFF1A1A1A),
            tertiary = c.tertiary,
            onTertiary = Color.White,
            background = Color(0xFFF5F5F0),
            onBackground = Color(0xFF1A1A1A),
            surface = Color.White,
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFE8E8E4),
            onSurfaceVariant = Color(0xFF494949),
            outline = Color(0xFF7A7A7A),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            inverseSurface = Color(0xFF303030),
            inverseOnSurface = Color(0xFFF2F2F2),
            scrim = Color.Black,
        )
    }
}

@Composable
fun PishiMovieTheme(
    themeSettings: ThemeSettings = ThemeSettings(),
    fontSettings: com.pishimovie.data.model.FontSettings? = null,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // OxygenUnder = amoledMode ON overrides surfaces to true black
    val amoledOverride = themeSettings.amoledMode

    // Resolve dynamic colors (Android 12+ Material You)
    val dynOn = themeSettings.dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (dynOn) {
        if (isDark) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else {
        var scheme = colorSchemeForTheme(themeSettings.themeType, isDark)
        if (amoledOverride && isDark) {
            scheme = scheme.copy(
                background = Color.Black,
                surface = Color(0xFF050505),
                surfaceVariant = Color(0xFF111111),
            )
        }
        scheme
    }

    // Typography with selected font
    val context = LocalContext.current
    val fontType = fontSettings?.fontType ?: com.pishimovie.data.model.FontType.DEFAULT
    val fontFamily = com.pishimovie.ui.theme.FontManager.loadFontFamily(context, fontType)
    val typography = pishiTypography(fontFamily)

    CompositionLocalProvider(
        LocalThemeSettings provides themeSettings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = PishiShapes,
            content = content,
        )
    }

    // Status bar / nav bar coloring
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context.findActivity()
        if (activity != null) {
            SideEffect {
                val window = activity.window
                @Suppress("DEPRECATION")
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
                // One consistent color for both bars (no two-tone). Harmless on
                // API 35+ where window colors are ignored under enforced
                // edge-to-edge.
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
            }
        }
    }
}

/** Walks the [Context] wrapper chain to find the hosting [Activity], or null. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
