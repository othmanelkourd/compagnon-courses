package com.compagnon.courses.android.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * The motion tokens of the charter (`design/maquette/Motion.dc.html`), the same on iOS (`Motion.swift`): four
 * durations, three curves and a soft spring. An animation shows what changes; nothing loops.
 */
object CompagnonMotion {
    /** Press, tick, chip. */
    const val QUICK: Int = 150

    /** Steps, rows, selectors, rolling numbers. */
    const val STANDARD: Int = 250

    /** Entrances, the card stack, images, progress fills. */
    const val AMPLE: Int = 400

    /** The rain of leaves, once. */
    const val CELEBRATION: Int = 1200

    /** Between two blocks of a cascade entrance. */
    const val CASCADE_STEP: Int = 40

    /** Steps, selections: `cubic-bezier(0.2, 0, 0, 1)`. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** What appears: `cubic-bezier(0, 0, 0, 1)`. */
    val Enter: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** What leaves (the card, the leaves): `cubic-bezier(0.3, 0, 1, 1)`. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** The soft spring (damping 0.8) of the images and ticks that pop. */
    fun <T> softSpring(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    /** A tween of [durationMillis] on the standard curve. */
    fun <T> standard(durationMillis: Int = STANDARD, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Standard)

    /** A tween of [durationMillis] on the entrance curve. */
    fun <T> enter(durationMillis: Int = AMPLE, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Enter)

    /** A tween of [durationMillis] on the exit curve. */
    fun <T> exit(durationMillis: Int = STANDARD, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Exit)
}

/**
 * « Réduire les animations » (the system animation scale at 0): slides, scales, the card's flight and the
 * celebration become plain fades, everywhere. Provided by [CompagnonTheme].
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Whether the system animation scale (`Settings.Global.ANIMATOR_DURATION_SCALE`) is 0, kept up to date. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    var scale by remember(resolver) { mutableFloatStateOf(resolver.animatorDurationScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = resolver.animatorDurationScale()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        resolver.registerContentObserver(uri, false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale == 0f
}

private fun ContentResolver.animatorDurationScale(): Float =
    Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
