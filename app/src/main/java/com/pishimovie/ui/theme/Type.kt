package com.pishimovie.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PishiMovie premium typography — refined hierarchy for streaming UI.
 *
 * Follows Material 3 Expressive with streaming-specific tuning:
 *   Display   — hero numbers, splash wordmark
 *   Headline  — section titles, screen titles
 *   Title     — card titles, row headers
 *   Body      — descriptions, metadata, list items
 *   Label     — chips, buttons, captions
 *
 * Key improvements:
 *  - Larger section titles (headlineSmall → 26sp)
 *  - Tighter letter-spacing on large text for premium feel
 *  - Slightly more open tracking on body for readability
 *  - Better line-height ratio (~1.35) across all sizes
 *  - Consistent font weights hierarchy
 *
 * Vazirmatn (open-source IranSans-equivalent) is applied globally via
 * the theme composable, ensuring Persian text renders beautifully at
 * every weight.
 */
fun pishiTypography(fontFamily: FontFamily? = null): Typography {
    val fam = fontFamily ?: FontFamily.Default
    return Typography(
        // Display — hero carousel, splash wordmark
        displayLarge = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Black,
            fontSize = 57.sp,
            lineHeight = 68.sp,
            letterSpacing = (-1.0).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Bold,
            fontSize = 45.sp,
            lineHeight = 54.sp,
            letterSpacing = (-0.5).sp,
        ),
        displaySmall = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        ),

        // Headline — screen titles, section headers
        headlineLarge = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),

        // Title — card titles, row headers
        titleLarge = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),

        // Body — descriptions, list items, metadata
        bodyLarge = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.4.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),

        // Label — chips, buttons, captions
        labelLarge = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = fam,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}

// Backward-compatible alias
val Typography = pishiTypography(null)
