package com.compagnon.courses.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.catalog.currentLocale
import com.compagnon.courses.android.ui.illustration.Illustration
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.android.ui.theme.LocalReduceMotion

/** « Surtitre »: capitals, 13 / 700, +0.8, Encre douce by default. A heading for TalkBack. */
@Composable
internal fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CompagnonColors.InkSoft,
    heading: Boolean = true,
) {
    val locale = currentLocale()
    Text(
        text = text.uppercase(locale),
        style = CompagnonType.Overline,
        color = color,
        modifier = if (heading) modifier.semantics { heading() } else modifier,
    )
}

/** « Bloc de liste »: Surface, Trait border, radius 20 (24 for the onboarding blocks); rows separated by [BlockDivider]. */
@Composable
internal fun ListBlock(
    modifier: Modifier = Modifier,
    shape: Shape = CompagnonShapes.Block,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, shape),
        content = content,
    )
}

/** A block of rows with its section overline above (« FRUITS ET LÉGUMES »). */
@Composable
internal fun SectionBlock(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline(text = title, modifier = Modifier.padding(horizontal = 6.dp))
        ListBlock(content = content)
    }
}

/** Separator between the rows of a block (Trait léger). */
@Composable
internal fun BlockDivider(modifier: Modifier = Modifier, color: Color = CompagnonColors.LineLight) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

/** « Indice »: Surface douce, radius 12, info icon and Aide text; full width, wraps. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier, icon: ImageVector = AppIcons.Info) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.Small)
            .background(CompagnonColors.SurfaceSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CompagnonColors.InkSoft, modifier = Modifier.size(16.dp))
        Text(text = text, style = CompagnonType.Help, color = CompagnonColors.InkSoft)
    }
}

/**
 * « Ligne suggestion »: the product's image as its check (a 48 image button with a tick badge), name (Ligne de
 * liste), reason (Légende) and a compact stepper. The left part toggles the line; unchecked, the name turns Encre
 * discrète and the image greys out.
 */
@Composable
internal fun SuggestionRow(
    name: String,
    reason: String?,
    imageKey: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    stepper: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 68.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckableIllustration(imageKey = imageKey, checked = checked)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = CompagnonType.ListRow,
                    color = if (checked) CompagnonColors.Ink else CompagnonColors.InkMuted,
                )
                if (reason != null) {
                    Text(text = reason, style = CompagnonType.Caption, color = CompagnonColors.InkSoft, maxLines = 2)
                }
            }
        }
        stepper()
    }
}

/**
 * The check of a suggestion line drawn on its image: a 44 pastille with the 32 image (greyed when unchecked) and a
 * 22 badge at the bottom right, Basilic with a white tick when checked, an empty ring otherwise. Drawn only: the row
 * toggles.
 */
@Composable
internal fun CheckableIllustration(imageKey: String, checked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(48.dp)) {
        Illustration(
            key = imageKey,
            size = 32.dp,
            plate = 44.dp,
            dimmed = !checked,
            pop = false,
            modifier = Modifier.align(Alignment.Center),
        )
        CheckBadge(
            checked = checked,
            size = 22.dp,
            border = 2.dp,
            borderColor = Color.White,
            tick = 12.dp,
            tickStroke = 3.4f,
            emptyRing = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp),
        )
    }
}

/**
 * A round Basilic badge with a white tick, on top of an image (suggestion line, pet tile, end screens). It fills
 * and draws its tick when [checked] turns on; [emptyRing] shows a white disc with a Contour case ring when off,
 * otherwise nothing. [animateOnAppear] draws the tick the first time too, after [delayMillis].
 */
@Composable
internal fun CheckBadge(
    checked: Boolean,
    size: Dp,
    border: Dp,
    borderColor: Color,
    tick: Dp,
    tickStroke: Float,
    modifier: Modifier = Modifier,
    emptyRing: Boolean = false,
    animateOnAppear: Boolean = false,
    delayMillis: Int = 0,
) {
    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = CompagnonMotion.standard(CompagnonMotion.QUICK),
        label = "badge fill",
    )
    if (!emptyRing && fill == 0f) return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(lerp(Color.White, CompagnonColors.Basil, fill))
            .border(border, if (checked || !emptyRing) borderColor else CompagnonColors.CheckboxOutline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DrawnCheck(
            color = Color.White,
            strokeWidth = tickStroke,
            visible = checked,
            animateOnAppear = animateOnAppear,
            delayMillis = delayMillis,
            modifier = Modifier.size(tick),
        )
    }
}

/**
 * « Ligne produit » (Ma liste): square checkbox, the product's image (36 pastille, greyed when checked), name
 * (struck through and Encre discrète when checked), quantity (Secondaire). The whole row toggles. Animation 7: the
 * line strikes the name from left to right (standard 250).
 */
@Composable
internal fun ProductRow(
    name: String,
    quantity: String?,
    imageKey: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CompagnonColors.Surface)
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(checked = checked, round = false)
        Illustration(key = imageKey, size = 26.dp, plate = 36.dp, dimmed = checked, pop = false)
        val color by animateColorAsState(
            targetValue = if (checked) CompagnonColors.InkMuted else CompagnonColors.Ink,
            animationSpec = CompagnonMotion.standard(),
            label = "name colour",
        )
        StruckText(
            text = name,
            struck = checked,
            style = CompagnonType.ListRow,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (quantity != null) {
            Text(text = quantity, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
        }
    }
}

/**
 * A text whose strike-through line is drawn from left to right, line after line, when it gets [struck] (standard
 * 250); it fades in instead with « Réduire les animations ».
 */
@Composable
private fun StruckText(text: String, struck: Boolean, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val progress by animateFloatAsState(
        targetValue = if (struck) 1f else 0f,
        animationSpec = CompagnonMotion.standard(),
        label = "strike",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val result = layout ?: return@drawWithContent
            if (progress <= 0f) return@drawWithContent
            val thickness = 1.5.dp.toPx()
            // The line crosses the middle of the lowercase letters, a little above the baseline.
            val lift = style.fontSize.toPx() * 0.3f
            val lines = (0 until result.lineCount).map { line ->
                Triple(result.getLineLeft(line), result.getLineRight(line), result.getLineBaseline(line) - lift)
            }
            var remaining = lines.sumOf { (left, right, _) -> (right - left).toDouble() }.toFloat() *
                (if (reduceMotion) 1f else progress)
            lines.forEach { (left, right, y) ->
                if (remaining <= 0f) return@forEach
                val end = minOf(right, left + remaining)
                remaining -= right - left
                drawLine(
                    color = color.copy(alpha = color.alpha * if (reduceMotion) progress else 1f),
                    start = Offset(left, y),
                    end = Offset(end, y),
                    strokeWidth = thickness,
                )
            }
        },
    )
}

/**
 * « Ligne récapitulatif »: the product's image (40 pastille), name and quantity. The charter's [×] « Retirer »
 * needs a remove event the shared state does not offer yet; [trailing] receives it when it exists.
 */
@Composable
internal fun ReviewRow(
    name: String,
    quantity: String,
    imageKey: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 12.dp, end = if (trailing == null) 16.dp else 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Illustration(key = imageKey, size = 28.dp, plate = 40.dp, pop = false)
        Text(text = name, style = CompagnonType.ListRow, color = CompagnonColors.Ink, modifier = Modifier.weight(1f))
        Text(text = quantity, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
        trailing?.invoke()
    }
}

/**
 * A tappable row of a block that adds something (search result, free text): Basilic « + », name, quantity. With an
 * [imageKey], the product's image ([imageSize] in a pastille) comes first and the « + » goes to the end.
 */
@Composable
internal fun AddRow(
    name: String,
    detail: String?,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    imageKey: String? = null,
    imageSize: Dp = 28.dp,
    plateSize: Dp = 40.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(start = if (imageKey != null) 12.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageKey != null) {
            Illustration(key = imageKey, size = imageSize, plate = plateSize, pop = false)
        } else {
            Icon(AppIcons.Add, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(20.dp))
        }
        Text(text = name, style = CompagnonType.ListRow, color = CompagnonColors.Ink, modifier = Modifier.weight(1f))
        if (detail != null) Text(text = detail, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
        if (imageKey != null) {
            Icon(AppIcons.Add, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(20.dp))
        }
    }
}

/** The three tones of « Option de réponse ». */
internal enum class OptionTone(val tint: Color, val ink: Color) {
    STOCK(CompagnonColors.BasilLight, CompagnonColors.Basil),
    PAUSE(CompagnonColors.PauseTint, CompagnonColors.InkSoft),
    STOP(CompagnonColors.TomatoLight, CompagnonColors.TomatoText),
}

/** « Option de réponse » (options sheet): a 40 tile with its icon, the label (17 / 600) and its hint (Légende). */
@Composable
internal fun ResponseOption(
    label: String,
    hint: String,
    icon: ImageVector,
    tone: OptionTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CompagnonShapes.Small)
                .background(tone.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = CompagnonType.ListRow,
                color = if (tone == OptionTone.STOP) CompagnonColors.TomatoText else CompagnonColors.Ink,
            )
            Text(text = hint, style = CompagnonType.Caption, color = CompagnonColors.InkSoft)
        }
    }
}

/**
 * The bottom of a scrolling screen: the actions on a gradient to Fond (transparent, then Fond from [solidFrom]),
 * above the navigation bar.
 */
@Composable
internal fun GradientBottomBar(
    modifier: Modifier = Modifier,
    topPadding: Dp = 20.dp,
    solidFrom: Float = 0.28f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to CompagnonColors.Background.copy(alpha = 0f),
                    solidFrom to CompagnonColors.Background,
                ),
            )
            .padding(top = topPadding)
            .navigationBarsPadding()
            .padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, bottom = CompagnonSpacing.Bottom),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * Places a column's content as one group in its free height, the free space split [above] : [below] (the hero
 * screens: end of session, empty list). Used with `heightIn(min = …)` in a scrolling column.
 */
internal fun splitSpace(above: Float, below: Float): Arrangement.Vertical = object : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val free = (totalSize - sizes.sum()).coerceAtLeast(0)
        var y = (free * above / (above + below)).toInt()
        sizes.forEachIndexed { index, size ->
            outPositions[index] = y
            y += size
        }
    }
}

/** The bottom row of a question: « Annuler » on the left, an optional action on the right. */
@Composable
internal fun BottomLinks(
    modifier: Modifier = Modifier,
    start: @Composable () -> Unit,
    end: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        start()
        end?.invoke()
    }
}

/** « Snackbar »: Encre, radius 16, white 15 message, « Annuler » in Snackbar action 16 / 700. */
@Composable
internal fun CompagnonSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = CompagnonSpacing.Block, vertical = 8.dp)
            .fillMaxWidth()
            .softShadow(CompagnonShapes.Field, radius = 24.dp, offsetY = 10.dp, alpha = 0.25f)
            .clip(CompagnonShapes.Field)
            .background(CompagnonColors.Ink)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.visuals.message,
            style = CompagnonType.Secondary,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        data.visuals.actionLabel?.let { label ->
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(CompagnonShapes.Small)
                    .clickable(role = Role.Button) { data.performAction() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = CompagnonType.ButtonText.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = CompagnonColors.SnackbarAction,
                )
            }
        }
    }
}
