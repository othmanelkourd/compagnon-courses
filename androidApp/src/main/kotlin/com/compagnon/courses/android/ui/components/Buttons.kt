package com.compagnon.courses.android.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType

/**
 * « Bouton principal »: Basilic, white 18 / 700 text, radius 18, at least 56 high; Basilic foncé while pressed.
 * [leadingIcon] / [trailingIcon] are optional.
 */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    minHeight: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when {
        !enabled -> CompagnonColors.Basil.copy(alpha = 0.4f)
        pressed -> CompagnonColors.BasilDark
        else -> CompagnonColors.Basil
    }
    ButtonFrame(
        onClick = onClick,
        enabled = enabled,
        interaction = interaction,
        background = background,
        border = null,
        minHeight = minHeight,
        modifier = modifier,
    ) {
        ButtonContent(text, Color.White, leadingIcon, trailingIcon, iconStroke = 20.dp)
    }
}

/**
 * « Bouton secondaire »: Surface, Trait fort border of 1.5, Encre 18 / 700 text. [accent] is the « Courses
 * terminées » variant (Basilic border and text).
 */
@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    leadingIcon: ImageVector? = null,
    minHeight: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val content = when {
        !enabled -> CompagnonColors.InkMuted
        accent -> CompagnonColors.Basil
        else -> CompagnonColors.Ink
    }
    ButtonFrame(
        onClick = onClick,
        enabled = enabled,
        interaction = interaction,
        background = if (pressed) CompagnonColors.SurfaceSoft else CompagnonColors.Surface,
        border = if (accent) CompagnonColors.Basil else CompagnonColors.LineStrong,
        minHeight = minHeight,
        modifier = modifier,
    ) {
        ButtonContent(text, content, leadingIcon, trailingIcon = null, iconStroke = 20.dp)
    }
}

@Composable
private fun ButtonFrame(
    onClick: () -> Unit,
    enabled: Boolean,
    interaction: MutableInteractionSource,
    background: Color,
    border: Color?,
    minHeight: Dp,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(CompagnonShapes.Button)
            .background(background)
            .let { if (border != null) it.border(1.5.dp, border, CompagnonShapes.Button) else it }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ButtonContent(
    text: String,
    color: Color,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    iconStroke: Dp,
) {
    if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = color, modifier = Modifier.size(iconStroke))
    Text(text = text, style = CompagnonType.ButtonPrimary, color = color, textAlign = TextAlign.Center)
    if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = color, modifier = Modifier.size(iconStroke))
}

/**
 * « Bouton texte »: no background, Basilic 16 / 600, 48 high (44 in the mockup, 48 dp on Android). [color] is
 * Encre for « Annuler »; [textStyle] is 14 / 600 for « Voir tout (44) ».
 */
@Composable
internal fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = CompagnonColors.Basil,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    textStyle: TextStyle = CompagnonType.ButtonText,
) {
    val tint = if (enabled) color else CompagnonColors.InkMuted
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CompagnonShapes.Small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = text, style = textStyle, color = tint)
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** « Annuler »: Encre, with the undo arrow. Always at the bottom left. */
@Composable
internal fun UndoTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TextActionButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        color = CompagnonColors.Ink,
        leadingIcon = AppIcons.Undo,
        modifier = modifier,
    )
}

/** The two looks of « Bouton icône ». */
internal enum class IconButtonStyle {
    /** Surface with a Trait border. */
    OUTLINED,

    /** Surface douce, no border. */
    SOFT,
}

/** « Bouton icône »: a 44 circle, in a 48 dp touch area. */
@Composable
internal fun CompagnonIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.OUTLINED,
    enabled: Boolean = true,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (style == IconButtonStyle.SOFT) CompagnonColors.SurfaceSoft else CompagnonColors.Surface)
            .let { if (style == IconButtonStyle.OUTLINED) it.border(1.dp, CompagnonColors.Line, CircleShape) else it }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) CompagnonColors.Ink else CompagnonColors.InkMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}
