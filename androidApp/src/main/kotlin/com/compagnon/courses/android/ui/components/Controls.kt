package com.compagnon.courses.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType

/** The questionnaire progress: [total] segments of 5, 3 apart, filled (Basilic) up to the question [current]. */
@Composable
internal fun ProgressSegments(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index < current) CompagnonColors.Basil else CompagnonColors.SegmentEmpty),
            )
        }
    }
}

/** A thin progress bar (« Ma liste »): Trait track, Basilic fill. */
@Composable
internal fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(CompagnonColors.Line),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(CompagnonColors.Basil),
        )
    }
}

/**
 * « Choix de quantité »: Surface tile, Trait border, radius 16, value 20 / 700; selected: Basilic clair, Basilic
 * border of 2. [large] is the meta question size (88 high, value 28, radius 20) with a [subline] (« jours »).
 */
@Composable
internal fun QuantityChoiceTile(
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subline: String? = null,
    large: Boolean = false,
    contentDescription: String? = null,
) {
    val shape = if (large) CompagnonShapes.Block else CompagnonShapes.Field
    val textColor = if (selected) CompagnonColors.BasilDark else CompagnonColors.Ink
    Column(
        modifier = modifier
            .heightIn(min = if (large) 88.dp else 64.dp)
            .clip(shape)
            .background(if (selected) CompagnonColors.BasilLight else CompagnonColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CompagnonColors.Basil else CompagnonColors.Line,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            // A large tile without a sub-line holds a word (« Aucun »): it keeps the regular value size (same on iOS).
            style = if (large && subline != null) CompagnonType.ValueLarge else CompagnonType.Value,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        if (subline != null) {
            Text(
                text = subline,
                style = if (large) CompagnonType.Help else CompagnonType.Chip.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) CompagnonColors.BasilDark else CompagnonColors.InkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The tones of « Puce ». */
internal enum class ChipTone(val content: Color, val background: Color) {
    /** « Essentiel ». */
    POSITIVE(CompagnonColors.BasilDark, CompagnonColors.BasilLight),

    /** « À surveiller ». */
    WATCH(CompagnonColors.TomatoText, CompagnonColors.TomatoLight),
}

/** « Puce »: radius 999, 12 / 700. */
@Composable
internal fun Chip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = CompagnonType.Chip,
        color = tone.content,
        modifier = modifier
            .clip(CompagnonShapes.Pill)
            .background(tone.background)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/**
 * « Case à cocher », drawn only (the row around it toggles): « ronde » (26, suggestions) or « carrée » (radius 8,
 * Ma liste). Checked: Basilic with a white tick; empty: Contour case border of 2. It sits in a 48 box.
 */
@Composable
internal fun CheckMark(checked: Boolean, round: Boolean, modifier: Modifier = Modifier) {
    val shape: Shape = if (round) CircleShape else CompagnonShapes.Checkbox
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(if (round) 28.dp else 26.dp)
                    .clip(shape)
                    .background(CompagnonColors.Basil),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.dp, CompagnonColors.CheckboxOutline, shape),
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    shape: Shape,
    background: Color,
    iconSize: Dp,
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) CompagnonColors.Ink else CompagnonColors.CheckboxOutline,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * « Pas-à-pas compact » (suggestion row): Surface douce, radius 12, « − value + ». The buttons are 36 × 44 (Compose
 * extends their touch area to 48 dp).
 */
@Composable
internal fun CompactStepper(
    label: String,
    itemName: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onStep: (increment: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CompagnonShapes.Small)
            .background(CompagnonColors.SurfaceSoft)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            AppIcons.Remove, stringResource(R.string.quantity_decrease, itemName), canDecrement, { onStep(false) },
            width = 36.dp, height = 44.dp, shape = CompagnonShapes.Small, background = Color.Transparent, iconSize = 16.dp,
        )
        Text(
            text = label,
            style = CompagnonType.Secondary.copy(fontWeight = FontWeight.Bold),
            color = CompagnonColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 64.dp, max = 96.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(
            AppIcons.Add, stringResource(R.string.quantity_increase, itemName), canIncrement, { onStep(true) },
            width = 36.dp, height = 44.dp, shape = CompagnonShapes.Small, background = Color.Transparent, iconSize = 16.dp,
        )
    }
}

/**
 * « Pas-à-pas encadré » (meta question, « Autre durée : 7 jours »): Surface, Trait border, radius 20, 48 buttons
 * in Surface douce.
 */
@Composable
internal fun BoxedStepper(
    label: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onStep: (increment: Boolean) -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.Block)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, CompagnonShapes.Block)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            AppIcons.Remove, decreaseDescription, canDecrement, { onStep(false) },
            width = 48.dp, height = 48.dp, shape = CompagnonShapes.Control, background = CompagnonColors.SurfaceSoft,
            iconSize = 20.dp,
        )
        Text(
            text = label,
            style = CompagnonType.ListRow,
            color = CompagnonColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(
            AppIcons.Add, increaseDescription, canIncrement, { onStep(true) },
            width = 48.dp, height = 48.dp, shape = CompagnonShapes.Control, background = CompagnonColors.SurfaceSoft,
            iconSize = 20.dp,
        )
    }
}

/** « Pas-à-pas libre » (product card): « Autre quantité : **1 kg** » between 48 × 44 buttons. */
@Composable
internal fun FreeStepper(
    prefix: String,
    value: String,
    itemName: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onStep: (increment: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(
            AppIcons.Remove, stringResource(R.string.quantity_decrease, itemName), canDecrement, { onStep(false) },
            width = 48.dp, height = 44.dp, shape = CompagnonShapes.Control, background = CompagnonColors.SurfaceSoft,
            iconSize = 18.dp,
        )
        Text(
            text = buildAnnotatedString {
                append(prefix)
                withStyle(SpanStyle(color = CompagnonColors.Ink, fontWeight = FontWeight.Bold)) { append(value) }
            },
            style = CompagnonType.Secondary,
            color = CompagnonColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(
            AppIcons.Add, stringResource(R.string.quantity_increase, itemName), canIncrement, { onStep(true) },
            width = 48.dp, height = 44.dp, shape = CompagnonShapes.Control, background = CompagnonColors.SurfaceSoft,
            iconSize = 18.dp,
        )
    }
}
