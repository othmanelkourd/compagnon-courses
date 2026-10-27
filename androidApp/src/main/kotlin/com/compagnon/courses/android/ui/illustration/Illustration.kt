package com.compagnon.courses.android.ui.illustration

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.components.CheckBadge
import com.compagnon.courses.android.ui.components.popIn
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.core.model.illustration.Illustrations

/** Opacity of the image of an unchecked line (« grisé »). */
internal const val DIMMED_ALPHA: Float = 0.45f

/**
 * A 3D illustration (Fluent Emoji, MIT) from its `Illustrations` key, [size] wide. Decorative: TalkBack skips it,
 * the text next to it says what it is. An unknown key shows the fallback image.
 *
 * - [plate]: the « pastille » under it, a Basilic clair disc (or [plateShape]) of that size; Surface douce when
 *   [dimmed].
 * - [dimmed]: an unchecked line, the image at 45 %.
 * - [groundShadow]: the soft blurred shadow under the object (large images: card banner, empty list, end of session).
 * - [pop]: animation 10, the image pops in (0.9 → 1, soft spring) after [popDelayMillis]; false when the image is
 *   already on screen.
 */
@Composable
internal fun Illustration(
    key: String,
    size: Dp,
    modifier: Modifier = Modifier,
    plate: Dp? = null,
    plateShape: Shape = CircleShape,
    dimmed: Boolean = false,
    rotation: Float = 0f,
    groundShadow: Boolean = false,
    pop: Boolean = true,
    popDelayMillis: Int = 0,
) {
    val res = illustrationRes(key) ?: illustrationRes(Illustrations.Screen.FALLBACK) ?: return
    if (plate == null) {
        IllustrationImage(res, size, dimmed, rotation, groundShadow, pop, popDelayMillis, modifier)
        return
    }
    val plateColor by animateColorAsState(
        targetValue = if (dimmed) CompagnonColors.SurfaceSoft else CompagnonColors.BasilLight,
        animationSpec = CompagnonMotion.standard(CompagnonMotion.QUICK),
        label = "plate colour",
    )
    Box(
        modifier = modifier
            .size(plate)
            .clip(plateShape)
            .background(plateColor),
        contentAlignment = Alignment.Center,
    ) {
        IllustrationImage(res, size, dimmed, rotation, groundShadow, pop, popDelayMillis)
    }
}

@Composable
private fun IllustrationImage(
    @DrawableRes res: Int,
    size: Dp,
    dimmed: Boolean,
    rotation: Float,
    groundShadow: Boolean,
    pop: Boolean,
    popDelayMillis: Int,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) DIMMED_ALPHA else 1f,
        animationSpec = CompagnonMotion.standard(CompagnonMotion.QUICK),
        label = "illustration alpha",
    )
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier
            .popIn(delayMillis = popDelayMillis, enabled = pop)
            .size(size)
            .then(if (groundShadow) Modifier.drawBehind { drawGroundShadow() } else Modifier)
            .graphicsLayer {
                this.alpha = alpha
                rotationZ = rotation
            },
    )
}

/**
 * The blurred shadow under a 3D object (Encre at 10 %): an ellipse 70 % as wide as the image and 12 % as high, its
 * bottom just above the image's.
 */
private fun DrawScope.drawGroundShadow() {
    val width = size.width * 0.7f
    val height = size.height * 0.12f
    val center = Offset(size.width / 2f, size.height * 0.97f - height / 2f)
    scale(scaleX = 1f, scaleY = height / width, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to CompagnonColors.Ink.copy(alpha = 0.10f),
                0.55f to CompagnonColors.Ink.copy(alpha = 0.08f),
                1f to Color.Transparent,
                center = center,
                radius = width / 2f,
            ),
            radius = width / 2f,
            center = center,
        )
    }
}

/** The tick badge of a [HeroIllustration]: [size] wide with a Fond [border], its [tick], [offset] from the corner. */
internal data class HeroBadge(val size: Dp, val border: Dp, val tick: Dp, val offset: DpOffset)

/**
 * A large image on its Basilic clair [disc] (end of session, onboarding recap, empty list): the [image] on its
 * ground shadow, an optional halo [ring] around the disc and a tick [badge] at the bottom right. The whole pops in;
 * the badge pops a little later and draws its tick.
 */
@Composable
internal fun HeroIllustration(
    key: String,
    disc: Dp,
    image: Dp,
    modifier: Modifier = Modifier,
    ring: Dp = 0.dp,
    ringAlpha: Float = 0.5f,
    badge: HeroBadge? = null,
) {
    Box(
        modifier = modifier
            .popIn()
            .size(disc)
            .drawBehind {
                if (ring > 0.dp) {
                    val radius = size.minDimension / 2f + ring.toPx()
                    drawCircle(CompagnonColors.BasilLight.copy(alpha = ringAlpha), radius = radius)
                }
                drawCircle(CompagnonColors.BasilLight)
            },
        contentAlignment = Alignment.Center,
    ) {
        Illustration(key = key, size = image, groundShadow = true, pop = false)
        if (badge != null) {
            CheckBadge(
                checked = true,
                size = badge.size,
                border = badge.border,
                borderColor = CompagnonColors.Background,
                tick = badge.tick,
                tickStroke = 3.2f,
                animateOnAppear = true,
                delayMillis = 300,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(badge.offset.x, badge.offset.y)
                    .popIn(delayMillis = 240),
            )
        }
    }
}
