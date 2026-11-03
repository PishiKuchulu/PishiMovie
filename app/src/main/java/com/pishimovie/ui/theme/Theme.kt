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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pishimovie.utils.DeviceUtils

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

/**
 * Designed light accents per theme — darkened variants of the brand colors.
 *
 * The previous implementation mechanically inverted the dark palette (kept
 * the bright accent, forced white on-colors), which broke WCAG contrast in
 * 4 of 5 themes (ratios as low as 1.84:1). Every pair used below was verified
 * with the WCAG relative-luminance formula (white on accent / dark text on
 * container tints, all ≥ 4.5:1 — see the E1 fix-wave report).
 */
private class LightAccents(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val surfaceVariant: Color,
)

private fun lightAccentsFor(themeType: ThemeType): LightAccents = when (themeType) {
    ThemeType.NETFLIX -> LightAccents(
        primary = Color(0xFFB20710),              // white on it: 7.17:1
        primaryContainer = Color(0xFFFFDAD6),
        onPrimaryContainer = Color(0xFF5F0A0E),   // on container: 10.58:1
        secondary = Color(0xFF595959),            // white on it: 7.00:1
        secondaryContainer = Color(0xFFE8E8E8),
        onSecondaryContainer = Color(0xFF1C1C1C), // 13.91:1
        tertiary = Color(0xFFB20710),
        tertiaryContainer = Color(0xFFFFDAD6),
        onTertiaryContainer = Color(0xFF5F0A0E),
        background = Color(0xFFF7F5F2),
        surfaceVariant = Color(0xFFE9E6E1),
    )
    ThemeType.PISHI -> LightAccents(
        primary = Color(0xFF9E5B00),              // white on it: 5.32:1
        primaryContainer = Color(0xFFFFE0BD),
        onPrimaryContainer = Color(0xFF3A1E00),   // 12.19:1
        secondary = Color(0xFF00708C),            // white on it: 5.68:1
        secondaryContainer = Color(0xFFC2EDFB),
        onSecondaryContainer = Color(0xFF00363F), // 10.50:1
        tertiary = Color(0xFFB01A20),             // white on it: 6.97:1
        tertiaryContainer = Color(0xFFFFDAD5),
        onTertiaryContainer = Color(0xFF5F1410),  // 10.19:1
        background = Color(0xFFF6F4EF),
        surfaceVariant = Color(0xFFEAE4DA),
    )
    ThemeType.MIDNIGHT_BLUE -> LightAccents(
        primary = Color(0xFF00707F),              // white on it: 5.79:1
        primaryContainer = Color(0xFFC7F0F5),
        onPrimaryContainer = Color(0xFF00363D),   // 10.79:1
        secondary = Color(0xFF007885),            // white on it: 5.22:1
        secondaryContainer = Color(0xFFBDEDF2),
        onSecondaryContainer = Color(0xFF003A40), // 9.86:1
        tertiary = Color(0xFF5E35B1),             // white on it: 8.02:1
        tertiaryContainer = Color(0xFFE6DEFF),
        onTertiaryContainer = Color(0xFF2C1065),  // 11.87:1
        background = Color(0xFFF2F6F8),
        surfaceVariant = Color(0xFFDFE7EC),
    )
    ThemeType.CINEMA_GOLD -> LightAccents(
        primary = Color(0xFF7A5A00),              // white on it: 6.38:1
        primaryContainer = Color(0xFFFFE9B0),
        onPrimaryContainer = Color(0xFF3A2A00),   // 11.60:1
        secondary = Color(0xFF6E5940),            // white on it: 6.63:1
        secondaryContainer = Color(0xFFF0E2C4),
        onSecondaryContainer = Color(0xFF2A1D08), // 12.82:1
        tertiary = Color(0xFF7A5A00),
        tertiaryContainer = Color(0xFFFFE9B0),
        onTertiaryContainer = Color(0xFF3A2A00),
        background = Color(0xFFF7F3EC),
        surfaceVariant = Color(0xFFEAE3D5),
    )
    ThemeType.OLED_BLACK -> LightAccents(
        primary = Color(0xFF2962FF),              // white on it: 4.90:1
        primaryContainer = Color(0xFFD6E4FF),
        onPrimaryContainer = Color(0xFF0B2A6B),   // 10.53:1
        secondary = Color(0xFF3D5A80),            // white on it: 7.06:1
        secondaryContainer = Color(0xFFD9E6FA),
        onSecondaryContainer = Color(0xFF10243E), // 12.39:1
        tertiary = Color(0xFF3D5AFE),             // white on it: 5.13:1
        tertiaryContainer = Color(0xFFDEE2FF),
        onTertiaryContainer = Color(0xFF0B2470),  // 10.95:1
        background = Color(0xFFF5F6F8),
        surfaceVariant = Color(0xFFE2E6EC),
    )
}

fun colorSchemeForTheme(themeType: ThemeType, isDark: Boolean): ColorScheme {
    val c: BrandPalette = when (themeType) {
        ThemeType.NETFLIX -> NetflixColors
        ThemeType.PISHI -> PishiColors
        ThemeType.MIDNIGHT_BLUE -> MidnightBlueColors
        ThemeType.CINEMA_GOLD -> CinemaGoldColors
        ThemeType.OLED_BLACK -> OledColors
    }
    val la = lightAccentsFor(themeType)
    if (isDark) {
        // ALL ColorScheme roles are populated from the palette (container
        // tiers / error & tertiary containers are derived by blending palette
        // colors) so no baseline M3 color can leak into the UI.
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
            tertiaryContainer = lerp(c.tertiary, Color.Black, 0.45f),
            onTertiaryContainer = lerp(c.tertiary, Color.White, 0.6f),
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariant,
            surfaceTint = c.surface,
            error = c.error,
            onError = c.onError,
            errorContainer = lerp(c.error, Color.Black, 0.55f),
            onErrorContainer = lerp(c.error, Color.White, 0.6f),
            outline = c.outline,
            outlineVariant = lerp(c.outline, c.background, 0.5f),
            inverseSurface = c.inverseSurface,
            inverseOnSurface = c.inverseOnSurface,
            inversePrimary = la.primary,
            surfaceDim = c.background,
            surfaceBright = c.surfaceVariant,
            surfaceContainerLowest = lerp(c.background, c.surface, 0.5f),
            surfaceContainerLow = c.surface,
            surfaceContainer = lerp(c.surface, c.surfaceVariant, 0.4f),
            surfaceContainerHigh = lerp(c.surface, c.surfaceVariant, 0.7f),
            surfaceContainerHighest = c.surfaceVariant,
            scrim = c.scrim,
        )
    } else {
        // Designed light palette (see LightAccents) — all roles populated.
        return lightColorScheme(
            primary = la.primary,
            onPrimary = Color.White,
            primaryContainer = la.primaryContainer,
            onPrimaryContainer = la.onPrimaryContainer,
            secondary = la.secondary,
            onSecondary = Color.White,
            secondaryContainer = la.secondaryContainer,
            onSecondaryContainer = la.onSecondaryContainer,
            tertiary = la.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = la.tertiaryContainer,
            onTertiaryContainer = la.onTertiaryContainer,
            background = la.background,
            onBackground = Color(0xFF1A1A1A),
            surface = Color.White,
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = la.surfaceVariant,
            onSurfaceVariant = Color(0xFF494949),
            surfaceTint = Color.White,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF93000A),
            outline = Color(0xFF767676),
            outlineVariant = Color(0xFFCAC5BD),
            inverseSurface = Color(0xFF303030),
            inverseOnSurface = Color(0xFFF2F2F2),
            inversePrimary = c.primary,
            surfaceDim = lerp(la.background, Color.Black, 0.05f),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(Color.White, la.background, 0.5f),
            surfaceContainer = la.background,
            surfaceContainerHigh = lerp(la.background, la.surfaceVariant, 0.5f),
            surfaceContainerHighest = la.surfaceVariant,
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

    val context = LocalContext.current

    // Resolve dynamic colors (Android 12+ Material You)
    val dynOn = themeSettings.dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var colorScheme = if (dynOn) {
        if (isDark) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        colorSchemeForTheme(themeSettings.themeType, isDark)
    }
    // AMOLED override applies to EVERY surface/container role and is applied
    // AFTER the dynamic-color selection, so it also wins when Material You is
    // on (previously it was silently ignored in the dynamic branch).
    if (amoledOverride && isDark) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color(0xFF050505),
            surfaceVariant = Color(0xFF111111),
            surfaceTint = Color(0xFF050505),
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF2A2A2A),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF111111),
            surfaceContainerHighest = Color(0xFF1C1C1C),
        )
    }

    // Spacing scale: roomier tokens on TV (10-foot UI), defaults elsewhere.
    val dimens = remember(context) {
        if (DeviceUtils.isTv(context)) TvPishiDimens else DefaultPishiDimens
    }

    // Typography with selected font
    val fontType = fontSettings?.fontType ?: com.pishimovie.data.model.FontType.DEFAULT
    val fontFamily = com.pishimovie.ui.theme.FontManager.loadFontFamily(context, fontType)
    val typography = pishiTypography(fontFamily)

    CompositionLocalProvider(
        LocalThemeSettings provides themeSettings,
        LocalPishiDimens provides dimens
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
