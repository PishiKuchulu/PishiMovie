package com.pishimovie.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * PishiMovie spacing tokens — strict 8pt grid, premium streaming spacing.
 *
 * Used via [LocalPishiDimens] for consistent rhythm across the entire app.
 */
data class PishiDimens(
    val xxs: androidx.compose.ui.unit.Dp = 2.dp,
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 20.dp,
    val xxl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxxl: androidx.compose.ui.unit.Dp = 32.dp,
    val huge: androidx.compose.ui.unit.Dp = 48.dp,
    val massive: androidx.compose.ui.unit.Dp = 64.dp,

    // Component-specific tokens — optimized for premium streaming
    val cardPadding: androidx.compose.ui.unit.Dp = 12.dp,
    val cardHeight: androidx.compose.ui.unit.Dp = 180.dp,
    val cardFullHeight: androidx.compose.ui.unit.Dp = 310.dp,
    val posterWidth: androidx.compose.ui.unit.Dp = 120.dp,
    val posterHeight: androidx.compose.ui.unit.Dp = 180.dp,
    val heroHeight: androidx.compose.ui.unit.Dp = 560.dp,       // taller hero
    val sectionSpacing: androidx.compose.ui.unit.Dp = 24.dp,    // increased
    val sectionTitleBottom: androidx.compose.ui.unit.Dp = 12.dp, // title-to-content gap
    val bottomNavHeight: androidx.compose.ui.unit.Dp = 72.dp,
    val topBarHeight: androidx.compose.ui.unit.Dp = 64.dp,
    val chipHeight: androidx.compose.ui.unit.Dp = 36.dp,
    val minTouchTarget: androidx.compose.ui.unit.Dp = 48.dp,
    val contentPadding: androidx.compose.ui.unit.Dp = 16.dp,    // screen edge padding
    val interCardGap: androidx.compose.ui.unit.Dp = 10.dp,       // gap between cards in a row
    val sectionHorizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
)

val DefaultPishiDimens = PishiDimens()

/**
 * TV variant — same component tokens, roomier spacing scale for the 10-foot
 * UI. Provided by the theme when [com.pishimovie.utils.DeviceUtils.isTv] is
 * true; every other form factor keeps [DefaultPishiDimens] untouched.
 */
val TvPishiDimens = PishiDimens(
    xxs = 3.dp,
    xs = 6.dp,
    sm = 12.dp,
    md = 16.dp,
    lg = 24.dp,
    xl = 28.dp,
    xxl = 32.dp,
    xxxl = 40.dp,
    huge = 56.dp,
    massive = 72.dp,
    cardPadding = 16.dp,
    sectionSpacing = 32.dp,
    sectionTitleBottom = 16.dp,
    contentPadding = 24.dp,
    interCardGap = 14.dp,
    sectionHorizontalPadding = 24.dp,
)

val LocalPishiDimens = staticCompositionLocalOf { DefaultPishiDimens }
