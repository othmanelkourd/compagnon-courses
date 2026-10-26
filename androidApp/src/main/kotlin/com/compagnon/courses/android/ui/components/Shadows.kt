package com.compagnon.courses.android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.theme.CompagnonColors

/**
 * The soft shadows of the charter (§ 3, « Ombres »), e.g. `0 8 24 rgba(23,33,27,0.08)`: a blur [radius], a vertical
 * offset and the [color] (Encre by default) at [alpha].
 */
internal fun Modifier.softShadow(
    shape: Shape,
    radius: Dp,
    offsetY: Dp,
    alpha: Float,
    color: Color = CompagnonColors.Ink,
): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(radius = radius, color = color.copy(alpha = alpha), offset = DpOffset(0.dp, offsetY)),
)
