package com.pishimovie.ui.theme

import androidx.compose.ui.unit.dp

/**
 * PishiMovie elevation tokens.
 *
 * On AMOLED dark surfaces, traditional drop-shadows are invisible. Instead we
 * rely on surface tonal lifts + thin edge lighting for hierarchy. The dp values
 * below are only used as tonalElevation parameters (which Compose maps to a
 * surface color overlay), not as shadow elevations.
 */
object PishiElevation {
    val None = 0.dp
    val Level1 = 1.dp     // resting cards
    val Level2 = 3.dp     // hovered / focused cards
    val Level3 = 6.dp     // sheets, bottom sheets
    val Level4 = 8.dp     // dialogs
    val Level5 = 12.dp    // FABs, popovers
}
