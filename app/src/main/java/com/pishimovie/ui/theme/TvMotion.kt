package com.pishimovie.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pishimovie.utils.DeviceUtils

/**
 * TV motion policy — the single source of truth for reduced-motion gating.
 *
 * Android TV boxes and sticks ship weak GPUs; perpetual animations (shimmer
 * sweeps, spinning progress rings, spring bounces, 3D tilts, auto-advancing
 * carousels) cause visible stutter and waste power on a 10-foot UI that the
 * user navigates with a D-pad. This file is the ONLY place that decides
 * whether motion is reduced — every composable gates its animations through
 * these helpers instead of calling [DeviceUtils.isTv] directly.
 *
 * Reduced motion is active when:
 *  - the device is a TV ([DeviceUtils.isTv]), or
 *  - the system animator duration scale is 0 (accessibility / developer
 *    option "Remove animations", [Settings.Global.ANIMATOR_DURATION_SCALE]).
 *
 * Policy:
 *  - Decorative infinite animations (shimmer, spinners) are NOT composed at
 *    all — callers render a static placeholder instead.
 *  - Functional motion (focus changes, visibility, press feedback) is kept
 *    but cheapened: springs become [snap], tweens become 0ms.
 */
@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { DeviceUtils.isTv(context) }
}

/**
 * True when animations should be skipped or snapped: on TV, or when the
 * system animator duration scale is 0.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (DeviceUtils.isTv(context)) {
            true
        } else {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            scale == 0f
        }
    }
}

/**
 * Duration in ms for an animation whose normal duration is [baseMs].
 * Returns 0 when reduced motion is active.
 */
@Composable
fun tvDuration(baseMs: Int): Int {
    val reducedMotion = rememberReducedMotion()
    return if (reducedMotion) 0 else baseMs
}

/**
 * A [TweenSpec] honoring the TV motion policy: a normal [tween] of [baseMs],
 * or a 0ms tween (effectively an instant snap) when reduced motion is active.
 */
@Composable
fun <T> tvTween(baseMs: Int): TweenSpec<T> {
    val duration = tvDuration(baseMs)
    return remember(duration) { tween(durationMillis = duration) }
}

/**
 * [PishiMotion.springDefault] normally, [snap] when reduced motion is active.
 * Use for press / focus / selection feedback that must stay instant on TV.
 */
@Composable
fun <T> tvSpringOrSnap(): FiniteAnimationSpec<T> {
    val reducedMotion = rememberReducedMotion()
    return remember(reducedMotion) {
        if (reducedMotion) snap() else PishiMotion.springDefault()
    }
}

/**
 * Infinite shimmer sweep offset — or 0f when reduced motion is active.
 *
 * On TV / reduced motion NO infinite transition is composed at all;
 * callers must render a static placeholder with the returned offset.
 */
@Composable
fun rememberShimmerSweepOrStatic(
    initialValue: Float = -1000f,
    targetValue: Float = 1000f,
    periodMs: Int = PishiMotion.ShimmerPeriod,
): Float {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) return 0f
    val transition = rememberInfiniteTransition(label = "tv_shimmer")
    val offset by transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_sweep",
    )
    return offset
}
