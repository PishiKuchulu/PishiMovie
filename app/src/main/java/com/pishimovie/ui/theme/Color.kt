package com.pishimovie.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PishiMovie premium brand palettes (5 themes).
 *
 * Theme 1 — NETFLIX Dark  (iconic deep red + near-black)
 * Theme 2 — PISHI Movie   (extracted from the official logo: Orange Cat + Cyan/Red)
 * Theme 3 — MIDNIGHT Blue (dark navy + cyan accents)
 * Theme 4 — CINEMA Gold   (dark graphite + warm gold highlights)
 * Theme 5 — OLED Black    (true #000000 AMOLED black + cool-blue accents)
 */

// ═══════════════════════════════════════════════════════════════════════════════
// BRAND COLORS
// ═══════════════════════════════════════════════════════════════════════════════
val BrandRed = Color(0xFFE50914)
val BrandRedDark = Color(0xFFB20710)
val BrandRedLight = Color(0xFFFFDAD6)

// ═══════════════════════════════════════════════════════════════════════════════
// BRAND PALETTE INTERFACE
// ═══════════════════════════════════════════════════════════════════════════════
interface BrandPalette {
    val primary: Color
    val onPrimary: Color
    val primaryContainer: Color
    val onPrimaryContainer: Color
    val secondary: Color
    val onSecondary: Color
    val secondaryContainer: Color
    val onSecondaryContainer: Color
    val tertiary: Color
    val onTertiary: Color
    val background: Color
    val onBackground: Color
    val surface: Color
    val onSurface: Color
    val surfaceVariant: Color
    val onSurfaceVariant: Color
    val outline: Color
    val error: Color
    val onError: Color
    val inverseSurface: Color
    val inverseOnSurface: Color
    val scrim: Color
    // Semantic status roles (WCAG-verified: on-color pairs ≥ 4.5:1 and each
    // tone ≥ 4.5:1 on every palette background — see fix-wave E1 report).
    val success: Color
    val onSuccess: Color
    val warning: Color
    val onWarning: Color
    val info: Color
    val onInfo: Color
}

// ═══════════════════════════════════════════════════════════════════════════════
// THEME 1 — Netflix Dark  (default)
// ═══════════════════════════════════════════════════════════════════════════════
object NetflixColors : BrandPalette {
    override val primary          = Color(0xFFE50914)  // Netflix red
    override val onPrimary        = Color(0xFFFFFFFF)
    override val primaryContainer = Color(0xFFB20710)
    override val onPrimaryContainer = Color(0xFFFFDAD6)
    override val secondary        = Color(0xFFB3B3B3)  // silver-gray
    override val onSecondary      = Color(0xFF1C1C1C)
    override val secondaryContainer = Color(0xFF333333)
    override val onSecondaryContainer = Color(0xFFE0E0E0)
    override val tertiary         = Color(0xFFE50914)
    override val onTertiary       = Color(0xFFFFFFFF)
    override val background       = Color(0xFF141414)  // near-black
    override val onBackground     = Color(0xFFF5F5F1)
    override val surface          = Color(0xFF1C1C1C)
    override val onSurface        = Color(0xFFF5F5F1)
    override val surfaceVariant   = Color(0xFF2A2A2A)
    override val onSurfaceVariant = Color(0xFFB3B3B3)
    override val outline          = Color(0xFF6E6E6E)  // WCAG: 3.61:1 on background (was #404040 = 1.78:1)
    override val error            = Color(0xFFCF6679)
    override val onError          = Color(0xFF1C1C1C)
    override val inverseSurface   = Color(0xFFF5F5F1)
    override val inverseOnSurface = Color(0xFF141414)
    override val scrim            = Color(0xFF000000)
    override val success          = Color(0xFF81C784)
    override val onSuccess        = Color(0xFF0B2E13)
    override val warning          = Color(0xFFFFB74D)
    override val onWarning        = Color(0xFF2E1C00)
    override val info             = Color(0xFF64B5F6)
    override val onInfo           = Color(0xFF0A2A4A)
}

// ═══════════════════════════════════════════════════════════════════════════════
// THEME 2 — PishiMovie  (from logo: Cat Orange #E88224, Cyan #00B5E2, Red #D62027)
// ═══════════════════════════════════════════════════════════════════════════════
object PishiColors : BrandPalette {
    override val primary          = Color(0xFFE88224)  // Cat Orange
    override val onPrimary        = Color(0xFF2A1500)  // WCAG: 6.34:1 on primary (was white = 2.75:1)
    override val primaryContainer = Color(0xFFFF9E40)
    override val onPrimaryContainer = Color(0xFF3A1E00)
    override val secondary        = Color(0xFF00B5E2)  // Cyan Lens
    override val onSecondary      = Color(0xFF2A1500)  // WCAG: 7.22:1 on secondary (was white = 2.41:1)
    override val secondaryContainer = Color(0xFF006680)
    override val onSecondaryContainer = Color(0xFFB3EBFF)
    override val tertiary         = Color(0xFFD62027)  // Red Lens
    override val onTertiary       = Color(0xFFFFFFFF)
    override val background       = Color(0xFF142125)  // dark slate
    override val onBackground     = Color(0xFFE8EDF0)
    override val surface          = Color(0xFF1C2E34)
    override val onSurface        = Color(0xFFE8EDF0)
    override val surfaceVariant   = Color(0xFF2E4750)
    override val onSurfaceVariant = Color(0xFFB0C4C9)
    override val outline          = Color(0xFF5E8291)  // WCAG: 3.99:1 on background (was #4A6A73 = 2.83:1)
    override val error            = Color(0xFFF06BA8)  // Pink-magenta: 5.79:1 on background; hue 332° vs brand red #D62027 hue 358° (was #CF6679, hue 349°, only 4.58:1)
    override val onError          = Color(0xFF1C1C1C)
    override val inverseSurface   = Color(0xFFE8EDF0)
    override val inverseOnSurface = Color(0xFF142125)
    override val scrim            = Color(0xFF000000)
    override val success          = Color(0xFF81C784)
    override val onSuccess        = Color(0xFF0B2E13)
    override val warning          = Color(0xFFFFB74D)
    override val onWarning        = Color(0xFF2E1C00)
    override val info             = Color(0xFF64B5F6)
    override val onInfo           = Color(0xFF0A2A4A)
}

// ═══════════════════════════════════════════════════════════════════════════════
// THEME 3 — Midnight Blue  (dark navy + cyan accents)
// ═══════════════════════════════════════════════════════════════════════════════
object MidnightBlueColors : BrandPalette {
    override val primary          = Color(0xFF00BCD4)  // vibrant cyan
    override val onPrimary        = Color(0xFF003544)
    override val primaryContainer = Color(0xFF00E5FF)
    override val onPrimaryContainer = Color(0xFF001F29)
    override val secondary        = Color(0xFF4DD0E1)  // soft cyan
    override val onSecondary      = Color(0xFF00323B)
    override val secondaryContainer = Color(0xFF006875)
    override val onSecondaryContainer = Color(0xFFB5EBF5)
    override val tertiary         = Color(0xFF7C4DFF)  // deep purple accent
    override val onTertiary       = Color(0xFFFFFFFF)
    override val background       = Color(0xFF0D1B2A)  // dark navy
    override val onBackground     = Color(0xFFE0EAF5)
    override val surface          = Color(0xFF1B2838)
    override val onSurface        = Color(0xFFE0EAF5)
    override val surfaceVariant   = Color(0xFF2A3A4E)
    override val onSurfaceVariant = Color(0xFFB0C4DE)
    override val outline          = Color(0xFF4A6B82)
    override val error            = Color(0xFFCF6679)
    override val onError          = Color(0xFF1C1C1C)
    override val inverseSurface   = Color(0xFFE0EAF5)
    override val inverseOnSurface = Color(0xFF0D1B2A)
    override val scrim            = Color(0xFF000000)
    override val success          = Color(0xFF81C784)
    override val onSuccess        = Color(0xFF0B2E13)
    override val warning          = Color(0xFFFFB74D)
    override val onWarning        = Color(0xFF2E1C00)
    override val info             = Color(0xFF64B5F6)
    override val onInfo           = Color(0xFF0A2A4A)
}

// ═══════════════════════════════════════════════════════════════════════════════
// THEME 4 — Cinema Gold  (dark graphite + warm gold highlights)
// ═══════════════════════════════════════════════════════════════════════════════
object CinemaGoldColors : BrandPalette {
    override val primary          = Color(0xFFD4A843)  // rich gold
    override val onPrimary        = Color(0xFF2D1F00)
    override val primaryContainer = Color(0xFFF5C95E)
    override val onPrimaryContainer = Color(0xFF1F1400)
    override val secondary        = Color(0xFF8D7961)  // warm bronze
    override val onSecondary      = Color(0xFF140B02)  // WCAG: 4.67:1 on secondary (was white = 4.17:1)
    override val secondaryContainer = Color(0xFF4A3B2A)
    override val onSecondaryContainer = Color(0xFFEAD5BA)
    override val tertiary         = Color(0xFFD4A843)  // gold
    override val onTertiary       = Color(0xFF2D1F00)
    override val background       = Color(0xFF1A1A1A)  // dark graphite
    override val onBackground     = Color(0xFFE8E0D5)
    override val surface          = Color(0xFF242424)
    override val onSurface        = Color(0xFFE8E0D5)
    override val surfaceVariant   = Color(0xFF33302B)
    override val onSurfaceVariant = Color(0xFFC4B8A8)
    override val outline          = Color(0xFF7A6E5C)  // WCAG: 3.49:1 on background (was #5A5044 = 2.21:1)
    override val error            = Color(0xFFCF6679)
    override val onError          = Color(0xFF1C1C1C)
    override val inverseSurface   = Color(0xFFE8E0D5)
    override val inverseOnSurface = Color(0xFF1A1A1A)
    override val scrim            = Color(0xFF000000)
    override val success          = Color(0xFF81C784)
    override val onSuccess        = Color(0xFF0B2E13)
    override val warning          = Color(0xFFFFB74D)
    override val onWarning        = Color(0xFF2E1C00)
    override val info             = Color(0xFF64B5F6)
    override val onInfo           = Color(0xFF0A2A4A)
}

// ═══════════════════════════════════════════════════════════════════════════════
// THEME 5 — OLED Black  (true #000000 + cool-blue accents)
// ═══════════════════════════════════════════════════════════════════════════════
object OledColors : BrandPalette {
    override val primary          = Color(0xFF2962FF)  // darkened blue — WCAG: white on it = 4.90:1 (was #448AFF = 3.32:1)
    override val onPrimary        = Color(0xFFFFFFFF)
    override val primaryContainer = Color(0xFF2962FF)
    override val onPrimaryContainer = Color(0xFFFFFFFF)
    override val secondary        = Color(0xFF82B1FF)  // light blue
    override val onSecondary      = Color(0xFF001D3D)
    override val secondaryContainer = Color(0xFF00499B)
    override val onSecondaryContainer = Color(0xFFD6E4FF)
    override val tertiary         = Color(0xFF3D5AFE)  // indigo — WCAG: white on it = 5.13:1 (was #536DFE = 4.21:1)
    override val onTertiary       = Color(0xFFFFFFFF)
    override val background       = Color(0xFF000000)  // TRUE black
    override val onBackground     = Color(0xFFF0F0F0)
    override val surface          = Color(0xFF0A0A0A)  // off-black for subtle layer
    override val onSurface        = Color(0xFFF0F0F0)
    override val surfaceVariant   = Color(0xFF1A1A1A)
    override val onSurfaceVariant = Color(0xFFB0BEC5)
    override val outline          = Color(0xFF546E7A)  // WCAG: 3.89:1 on background (was #37474F = 2.18:1)
    override val error            = Color(0xFFCF6679)
    override val onError          = Color(0xFF1C1C1C)
    override val inverseSurface   = Color(0xFFF0F0F0)
    override val inverseOnSurface = Color(0xFF000000)
    override val scrim            = Color(0xFF000000)
    override val success          = Color(0xFF81C784)
    override val onSuccess        = Color(0xFF0B2E13)
    override val warning          = Color(0xFFFFB74D)
    override val onWarning        = Color(0xFF2E1C00)
    override val info             = Color(0xFF64B5F6)
    override val onInfo           = Color(0xFF0A2A4A)
}
