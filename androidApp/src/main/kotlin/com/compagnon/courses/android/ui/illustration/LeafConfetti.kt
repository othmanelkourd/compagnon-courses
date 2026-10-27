package com.compagnon.courses.android.ui.illustration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.android.ui.theme.LocalReduceMotion

/** How many leaves fall. */
private const val LEAF_COUNT: Int = 18

/** The longest wait before a leaf starts falling, in ms; the others spread over it. */
private const val MAX_LEAF_DELAY: Int = 300

/** The fall of a leaf: `cubic-bezier(.3, 0, .8, 1)`, as in the mockup. */
private val FallEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 1f)

/** The leaf colours of the mockup: Basilic, its lighter greens, a touch of Tomate. */
private val LeafColors = listOf(
    CompagnonColors.Basil,
    Color(0xFF2F8A5B),
    CompagnonColors.SnackbarAction,
    CompagnonColors.BasilLight,
    CompagnonColors.Tomato,
    CompagnonColors.Basil,
)

/** One falling leaf, from the mockup's formulas (`Done.dc.html`), in a 390 × 844 frame. */
private class FallingLeaf(index: Int) {
    val width = 12f + (index * 7) % 12
    val height = width * 1.5f
    val x = ((index * 53 + 17) % 370) / 390f
    val drift = ((index * 37) % 80) - 40f
    val turn = 300f + (index * 61) % 360
    val color = LeafColors[index % LeafColors.size]

    /** Start and length of its fall, as fractions of the whole rain. */
    val start = ((index * 70) % 900) / 900f * MAX_LEAF_DELAY / CompagnonMotion.CELEBRATION
    val length = (CompagnonMotion.CELEBRATION - MAX_LEAF_DELAY) * (3.6f + (index % 5) * 0.25f) / 4.6f /
        CompagnonMotion.CELEBRATION
}

private val FallingLeaves = List(LEAF_COUNT) { FallingLeaf(it) }

/**
 * Animation 9, « Fête »: about 18 leaves fall across the [modifier]'s area while turning, once (1200 ms), then
 * disappear; [onFinished] follows. Decorative; nothing at all with « Réduire les animations ». The success haptic
 * is the caller's (same moment).
 */
@Composable
internal fun LeafConfetti(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val reduceMotion = LocalReduceMotion.current
    val currentOnFinished = rememberUpdatedState(onFinished)
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) time.animateTo(1f, tween(CompagnonMotion.CELEBRATION, easing = LinearEasing))
        time.snapTo(1f)
        currentOnFinished.value()
    }
    if (reduceMotion) return
    Canvas(modifier = modifier) {
        val now = time.value
        if (now <= 0f || now >= 1f) return@Canvas
        val fall = size.height * (760f / 844f)
        val lift = 40f * density
        FallingLeaves.forEach { leaf ->
            val t = ((now - leaf.start) / leaf.length).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val eased = FallEasing.transform(t)
            // Opacity: in over the first 9 %, out over the last 21 % (the mockup's 0–6 % and 55–70 % keyframes).
            val alpha = when {
                t < 0.086f -> t / 0.086f
                t < 0.786f -> 1f
                else -> (1f - t) / 0.214f
            }
            val scale = leaf.width / 32f * density
            val center = Offset(
                x = leaf.x * size.width + leaf.width * density / 2f + leaf.drift * density * eased,
                y = -lift + leaf.height * density / 2f + (fall + lift) * eased,
            )
            withTransform({
                translate(center.x, center.y)
                rotate(leaf.turn * eased, pivot = Offset.Zero)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawPath(LeafOutline, leaf.color, alpha = alpha)
                drawPath(
                    LeafVein,
                    Color.White,
                    alpha = alpha * 0.5f,
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                )
            }
        }
    }
}
