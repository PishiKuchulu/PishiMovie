package com.pishimovie.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.pishimovie.ui.theme.PishiMotion

/**
 * PishiMovie navigation transitions.
 *
 * Two flavours:
 *  - Top-level tab switches (Home ↔ Movies ↔ Series ↔ Search ↔ Favorites)
 *    use a "fade-through" pattern: outgoing content fades + scales down
 *    slightly, incoming content fades + scales up from a slightly larger
 *    size. The Material 3 Expressive "fade through" pattern.
 *
 *  - Detail-page navigation (SingleMovie / SingleSeries / Collection / Country)
 *    uses a slide-in-from-right + fade, matching the standard Android
 *    detail-page pattern but with a smoother emphasized easing.
 */
object NavTransitions {

    /** Tab-switch fade-through enter transition. */
    fun fadeThroughEnter(): EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = PishiMotion.DurationMedium,
            delayMillis = PishiMotion.DurationShort / 2,
            easing = PishiMotion.EasingEmphasizedDecelerate,
        ),
    )

    /** Tab-switch fade-through exit transition. */
    fun fadeThroughExit(): ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = PishiMotion.DurationShort,
            easing = PishiMotion.EasingEmphasizedAccelerate,
        ),
    )

    /** Detail-page slide-in from the right edge. */
    fun slideInFromRight(): EnterTransition = slideInHorizontally(
        animationSpec = tween(
            durationMillis = PishiMotion.DurationLong,
            easing = PishiMotion.EasingEmphasizedDecelerate,
        ),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = PishiMotion.DurationLong,
            easing = PishiMotion.EasingEmphasizedDecelerate,
        ),
    )

    /** Detail-page slide-out to the right edge (when popping). */
    fun slideOutToRight(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = PishiMotion.DurationMedium,
            easing = PishiMotion.EasingEmphasizedAccelerate,
        ),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = PishiMotion.DurationMedium,
            easing = PishiMotion.EasingEmphasizedAccelerate,
        ),
    )
}
