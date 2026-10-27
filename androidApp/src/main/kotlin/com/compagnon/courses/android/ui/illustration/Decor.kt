package com.compagnon.courses.android.ui.illustration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.theme.CompagnonColors

/**
 * « Halo »: a Basilic clair glow, from [alpha] to transparent, behind a hero screen (home, welcome, recaps, end of
 * session). It fills its parent and draws a [size] circle placed like the mockup's `position: absolute`: [top] or
 * [bottom], [left] or [right] (negative to overflow the screen); centred on the axis given neither.
 */
@Composable
internal fun Halo(
    size: Dp,
    modifier: Modifier = Modifier,
    top: Dp? = null,
    right: Dp? = null,
    bottom: Dp? = null,
    left: Dp? = null,
    alpha: Float = 0.95f,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = size.toPx() / 2f
        val x = when {
            left != null -> left.toPx() + radius
            right != null -> this.size.width - right.toPx() - radius
            else -> this.size.width / 2f
        }
        val y = when {
            top != null -> top.toPx() + radius
            bottom != null -> this.size.height - bottom.toPx() - radius
            else -> this.size.height / 2f
        }
        drawHalo(Offset(x, y), radius, alpha)
    }
}

/**
 * Where a [Halo] follows an element rather than a corner (the end of a session, the empty list): the element
 * reports its centre with [haloSource], the screen draws the halo behind everything with [anchoredHalo].
 */
@Stable
internal class HaloAnchor {
    internal var screen: LayoutCoordinates? = null
    internal var center: Offset? by mutableStateOf(null)
}

/** Draws a [size] halo behind this screen, [offsetY] below the centre of the [anchor]'s element. */
internal fun Modifier.anchoredHalo(anchor: HaloAnchor, size: Dp, offsetY: Dp = 0.dp, alpha: Float = 1f): Modifier =
    onPlaced { anchor.screen = it }
        .drawBehind {
            val center = anchor.center ?: return@drawBehind
            drawHalo(Offset(center.x, center.y + offsetY.toPx()), size.toPx() / 2f, alpha)
        }

/** The element a [HaloAnchor]'s halo follows. */
internal fun Modifier.haloSource(anchor: HaloAnchor): Modifier = onGloballyPositioned { coordinates ->
    val screen = anchor.screen?.takeIf { it.isAttached } ?: return@onGloballyPositioned
    anchor.center = screen.localPositionOf(coordinates, Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
}

private fun DrawScope.drawHalo(center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CompagnonColors.BasilLight.copy(alpha = alpha), CompagnonColors.BasilLight.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** One leaf of a [LeafPattern]: its centre ([x], [y]) in the pattern box, its [rotation] in degrees, its [scale]. */
internal data class PatternLeaf(val x: Float, val y: Float, val rotation: Float, val scale: Float = 1f)

/**
 * « Motif feuilles »: basil leaves drawn with a line, as a watermark ([color] carries the opacity: white at 8 to
 * 14 % on Basilic or the dark card, Basilic at 10 % on a light background). [leaves] are placed in a [width] ×
 * [height] box, in dp, as in the mockup's SVG.
 */
@Composable
internal fun LeafPattern(
    leaves: List<PatternLeaf>,
    width: Dp,
    height: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width, height)) {
        val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round)
        val px = density
        leaves.forEach { leaf ->
            withTransform({
                translate(leaf.x * px, leaf.y * px)
                rotate(leaf.rotation, pivot = Offset.Zero)
                scale(leaf.scale * px, leaf.scale * px, pivot = Offset.Zero)
            }) {
                drawPath(LeafOutline, color, style = stroke)
                drawPath(LeafVein, color, style = stroke)
            }
        }
    }
}

/** The basil leaf of the charter, `M0-22C13-13 15 9 0 22C-15 9-13-13 0-22Z`, centred on 0 (44 high). */
internal val LeafOutline: Path = Path().apply {
    moveTo(0f, -22f)
    cubicTo(13f, -13f, 15f, 9f, 0f, 22f)
    cubicTo(-15f, 9f, -13f, -13f, 0f, -22f)
    close()
}

/** Its midrib, `M0-18V20`. */
internal val LeafVein: Path = Path().apply {
    moveTo(0f, -18f)
    lineTo(0f, 20f)
}
