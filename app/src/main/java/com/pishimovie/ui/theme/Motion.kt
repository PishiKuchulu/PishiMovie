package com.pishimovie.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * PishiMovie motion system.
 *
 * Inspired by Material 3 Expressive motion + the spec (Parts 6 & 13):
 *  - Spring-based, with a touch of overshoot for "premium micro interactions"
 *  - Fade-through transitions between top-level destinations
 *  - Container transform feeling for hero → detail navigation
 *  - Skeleton shimmer loops at 1200ms
 *
 * Token map (duration / easing):
 *   short  = 180ms  — micro interactions (taps, hovers)
 *   medium = 300ms  — component entries (chips, sheets)
 *   long   = 450ms  — screen transitions, fade-through
 *   xl     = 600ms  — hero / shared element transitions
 *
 * Easings follow Material 3 standard curves.
 */
object PishiMotion {

    // ─── Durations ──────────────────────────────────────────────────────
    const val DurationShort = 180
    const val DurationMedium = 300
    const val DurationLong = 450
    const val DurationXl = 600
    const val ShimmerPeriod = 1200

    // ─── Easings (Material 3 standard + emphasized) ─────────────────────
    val EasingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingStandardAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val EasingStandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // ─── Spring specs (used for gesture-driven UI) ──────────────────────
    fun <T> springDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> springLowBounce(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> springMediumBounce(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Snappy spring for press / focus feedback (the "premium micro interaction"). */
    fun <T> springPress(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    // ─── Tween specs (used for state-driven UI) ────────────────────────
    fun <T> tweenShort(): DurationBasedAnimationSpec<T> = tween(
        durationMillis = DurationShort,
        easing = EasingStandard,
    )

    fun <T> tweenMedium(): DurationBasedAnimationSpec<T> = tween(
        durationMillis = DurationMedium,
        easing = EasingEmphasizedDecelerate,
    )

    fun <T> tweenLong(): DurationBasedAnimationSpec<T> = tween(
        durationMillis = DurationLong,
        easing = EasingEmphasizedDecelerate,
    )

    fun <T> tweenXl(): DurationBasedAnimationSpec<T> = tween(
        durationMillis = DurationXl,
        easing = EasingEmphasizedDecelerate,
    )

    // ─── Common IntOffset springs (for slide-in/out) ───────────────────
    fun intOffsetSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ─── Hero carousel animation ──────────────────────────────────────
    const val DurationHero = 800
    const val HeroAutoScrollDelay = 5000L

    /** Smooth tween for hero page transitions. */
    fun <T> tweenHero(): DurationBasedAnimationSpec<T> = tween(
        durationMillis = DurationHero,
        easing = EasingEmphasizedDecelerate,
    )
}
