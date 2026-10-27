package com.compagnon.courses.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.android.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A cascade stops growing after this many blocks: the later ones (below the screen) come in with the last. */
private const val MAX_CASCADE_INDEX: Int = 8

/** Above this font scale, [AnimatedCounter] shows its text as one wrapping line (the rolling number would split it). */
private const val COUNTER_MAX_FONT_SCALE: Float = 1.3f

/**
 * Animation 8, « Appui »: the element shrinks to 0.98 while [interaction] is pressed (quick, standard curve). Home
 * cards and main buttons. Nothing with « Réduire les animations ».
 */
internal fun Modifier.pressScale(interaction: InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.98f else 1f,
        animationSpec = CompagnonMotion.standard(CompagnonMotion.QUICK),
        label = "press scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Animation 2, « Entrée en cascade »: the element rises 12 and fades in (entrance curve, 400), [index] × 40 ms after
 * the first one, once per appearance. A fade only with « Réduire les animations ».
 */
internal fun Modifier.riseIn(index: Int = 0, delayMillis: Int = 0): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val step = index.coerceAtMost(MAX_CASCADE_INDEX) * CompagnonMotion.CASCADE_STEP
        progress.animateTo(1f, CompagnonMotion.enter(delayMillis = delayMillis + step))
    }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    graphicsLayer {
        alpha = progress.value
        if (!reduceMotion) translationY = (1f - progress.value) * rise
    }
}

/**
 * Animation 10, « Images 3D »: the element pops in from 0.9 with the soft spring and fades in, after [delayMillis];
 * never loops. A fade only with « Réduire les animations ». [enabled] false shows it at once (an image already on
 * screen, e.g. on the card behind).
 */
internal fun Modifier.popIn(delayMillis: Int = 0, enabled: Boolean = true): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember { Animatable(if (enabled) 0f else 1f) }
    val scale = remember { Animatable(if (enabled && !reduceMotion) 0.9f else 1f) }
    LaunchedEffect(Unit) {
        if (alpha.value == 1f) return@LaunchedEffect
        delay(delayMillis.toLong())
        launch { alpha.animateTo(1f, CompagnonMotion.standard()) }
        scale.animateTo(1f, CompagnonMotion.softSpring())
    }
    graphicsLayer {
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Animation 4, « Puce qui pop »: the element dips to 0.96 and springs back when [value] changes (a chip or tile
 * toggled), not when it first appears. Nothing with « Réduire les animations ».
 */
internal fun Modifier.bounceOnChange(value: Boolean): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    val shown = remember { booleanArrayOf(value) }
    LaunchedEffect(value) {
        if (shown[0] == value) return@LaunchedEffect
        shown[0] = value
        if (reduceMotion) return@LaunchedEffect
        scale.animateTo(0.96f, CompagnonMotion.standard(CompagnonMotion.QUICK / 2))
        scale.animateTo(1f, CompagnonMotion.softSpring())
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Animation 5, « Chiffres qui roulent »: [text] with its number [count] rolling when it changes (up when it grows,
 * down when it shrinks; standard 250). The text around the number changes at once (« article » → « articles »).
 * Read as one text by TalkBack. Plain text when [count] is not in [text] or with a large font.
 */
@Composable
internal fun AnimatedCounter(
    text: String,
    count: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val digits = count.toString()
    val at = text.indexOf(digits)
    val fontScale = LocalDensity.current.fontScale
    if (at < 0 || fontScale > COUNTER_MAX_FONT_SCALE) {
        Text(text = text, style = style, color = color, textAlign = textAlign, modifier = modifier)
        return
    }
    val reduceMotion = LocalReduceMotion.current
    Row(
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The spaces around the number stay measured (a trailing space of a line may be dropped).
        if (at > 0) Text(text = text.substring(0, at).nonBreakingEnd(), style = style, color = color, maxLines = 1)
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                val spec = CompagnonMotion.standard<Float>()
                if (reduceMotion) {
                    fadeIn(spec) togetherWith fadeOut(spec)
                } else {
                    val slide = CompagnonMotion.standard<IntOffset>()
                    (slideInVertically(slide) { if (up) it else -it } + fadeIn(spec)) togetherWith
                        (slideOutVertically(slide) { if (up) -it else it } + fadeOut(spec))
                }
            },
            label = "rolling number",
            // The digits roll inside the line, as through a window.
            modifier = Modifier.clipToBounds(),
        ) { value ->
            Text(text = value.toString(), style = style, color = color, maxLines = 1)
        }
        val end = at + digits.length
        if (end < text.length) {
            Text(text = text.substring(end).nonBreakingStart(), style = style, color = color, maxLines = 1)
        }
    }
}

private fun String.nonBreakingEnd(): String = if (endsWith(' ')) dropLast(1) + '\u00A0' else this

private fun String.nonBreakingStart(): String = if (startsWith(' ')) '\u00A0' + drop(1) else this

/** The tick of the charter (`M20 6 9 17l-5-5`), in a 24 box. */
private val CheckPoints = listOf(Offset(20f, 6f), Offset(9f, 17f), Offset(4f, 12f))

/**
 * Animation 4, « Coche qui se dessine »: the tick is drawn from its start to its end (standard 250, after
 * [delayMillis]) when it becomes [visible]; [animateOnAppear] also draws it the first time, otherwise a tick
 * already there is shown at once. [strokeWidth] is in the 24 box of the mockup's SVG. With « Réduire les
 * animations », it fades in instead.
 */
@Composable
internal fun DrawnCheck(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 3f,
    visible: Boolean = true,
    animateOnAppear: Boolean = false,
    delayMillis: Int = 0,
) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(if (visible && !animateOnAppear) 1f else 0f) }
    LaunchedEffect(visible) {
        if (!visible) {
            progress.snapTo(0f)
        } else {
            progress.animateTo(1f, CompagnonMotion.standard(delayMillis = delayMillis))
        }
    }
    Spacer(
        modifier = modifier.drawWithCache {
            val unit = size.minDimension / 24f
            val origin = Offset((size.width - 24f * unit) / 2f, (size.height - 24f * unit) / 2f)
            val path = Path().apply {
                CheckPoints.forEachIndexed { index, point ->
                    val x = origin.x + point.x * unit
                    val y = origin.y + point.y * unit
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
            val stroke = Stroke(width = strokeWidth * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val segment = Path()
            onDrawBehind {
                val shown = progress.value
                if (shown <= 0f) return@onDrawBehind
                if (reduceMotion) {
                    drawPath(path, color.copy(alpha = color.alpha * shown), style = stroke)
                } else {
                    segment.reset()
                    measure.getSegment(0f, measure.length * shown, segment, startWithMoveTo = true)
                    drawPath(segment, color, style = stroke)
                }
            }
        },
    )
}
