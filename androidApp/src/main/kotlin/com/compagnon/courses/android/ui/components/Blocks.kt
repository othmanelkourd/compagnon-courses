package com.compagnon.courses.android.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.catalog.currentLocale
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType

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

/** « Bloc de liste »: Surface, Trait border, radius 20; rows separated by [BlockDivider]. */
@Composable
internal fun ListBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.Block)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, CompagnonShapes.Block),
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
 * « Ligne suggestion »: round checkbox, name (Ligne de liste), reason (Légende) and a compact stepper. The left
 * part toggles the line; the name turns Encre discrète when unchecked.
 */
@Composable
internal fun SuggestionRow(
    name: String,
    reason: String?,
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
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckMark(checked = checked, round = true)
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
 * « Ligne produit » (Ma liste): square checkbox, name (struck through and Encre discrète when checked), quantity
 * (Secondaire). The whole row toggles.
 */
@Composable
internal fun ProductRow(
    name: String,
    quantity: String?,
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
        Text(
            text = name,
            style = CompagnonType.ListRow,
            color = if (checked) CompagnonColors.InkMuted else CompagnonColors.Ink,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (quantity != null) {
            Text(text = quantity, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
        }
    }
}

/**
 * « Ligne récapitulatif »: name and quantity. The charter's [×] « Retirer » needs a remove event the shared state
 * does not offer yet; [trailing] receives it when it exists.
 */
@Composable
internal fun ReviewRow(
    name: String,
    quantity: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = if (trailing == null) 16.dp else 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, style = CompagnonType.ListRow, color = CompagnonColors.Ink, modifier = Modifier.weight(1f))
        Text(text = quantity, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
        trailing?.invoke()
    }
}

/** A tappable row of a block that adds something (search result, free text): Basilic « + », name, quantity. */
@Composable
internal fun AddRow(
    name: String,
    detail: String?,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Add, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(20.dp))
        Text(text = name, style = CompagnonType.ListRow, color = CompagnonColors.Ink, modifier = Modifier.weight(1f))
        if (detail != null) Text(text = detail, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
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
