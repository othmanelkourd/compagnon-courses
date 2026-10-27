package com.compagnon.courses.android.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AddRow
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.GradientBottomBar
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.SearchField
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.components.softShadow
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.onboarding.CadenceChoice
import com.compagnon.courses.feature.onboarding.EssentialsAisleUi
import com.compagnon.courses.feature.onboarding.OnboardingEvent
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.ProductChipUi
import com.compagnon.courses.feature.onboarding.RecurringRowUi

/**
 * A long step: [content] scrolls under the top bar, the [bottom] actions sit on a gradient to Fond above the
 * navigation bar (and above the keyboard).
 */
@Composable
private fun LongStepLayout(bottom: @Composable () -> Unit, content: LazyListScope.() -> Unit) {
    var barHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = with(density) { barHeight.toDp() } + 8.dp),
            content = content,
        )
        GradientBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight = it.height },
            topPadding = 28.dp,
            solidFrom = 0.34f,
        ) {
            bottom()
        }
    }
}

/**
 * `Onboarding-Essentials`: a search field, then the aisles as blocks of chips (checked: Basilic clair, Basilic
 * border of 2, tick) with « Voir tout (n) »; « Continuer · 14 essentiels » at the bottom. Without a catalog: its
 * download, or a sentence and « Réessayer » (the step can be skipped).
 */
@Composable
internal fun EssentialsStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val essentials = state.essentials
    val hasCatalog = !essentials.catalogLoading && !essentials.catalogMissing
    LongStepLayout(
        bottom = {
            StepPrimaryButton(
                text = if (hasCatalog) {
                    pluralStringResource(R.plurals.onboarding_essentials_continue, essentials.checkedCount, essentials.checkedCount)
                } else {
                    stringResource(R.string.onboarding_continue)
                },
                saving = state.saving,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        item(key = "header") {
            StepHeader(
                overline = null,
                title = stringResource(R.string.onboarding_section_essentials),
                text = stringResource(R.string.onboarding_essentials_text),
                modifier = Modifier.padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 24.dp),
            )
        }
        when {
            essentials.catalogLoading -> item(key = "loading") { CatalogLoading() }
            essentials.catalogMissing -> item(key = "missing") { CatalogMissing(onRetry = { onEvent(OnboardingEvent.Retry) }) }
            else -> {
                item(key = "search") {
                    val field = rememberTextFieldState(essentials.query)
                    SearchField(
                        state = field,
                        onQueryChange = { onEvent(OnboardingEvent.SearchEssentials(it)) },
                        placeholder = stringResource(R.string.onboarding_search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 16.dp),
                    )
                }
                if (essentials.query.isNotBlank()) {
                    item(key = "results") {
                        Box(modifier = Modifier.padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 18.dp)) {
                            if (essentials.results.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_no_result, essentials.query.trim()),
                                    style = CompagnonType.Secondary,
                                    color = CompagnonColors.InkSoft,
                                )
                            } else {
                                ChipFlow(essentials.results, onToggle = { onEvent(OnboardingEvent.ToggleEssential(it)) })
                            }
                        }
                    }
                } else {
                    items(essentials.aisles, key = { "aisle-${it.categoryId}" }) { aisle ->
                        AisleChips(aisle = aisle, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun AisleChips(aisle: EssentialsAisleUi, onEvent: (OnboardingEvent) -> Unit) {
    Column(
        modifier = Modifier.padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Overline(text = aisle.title, modifier = Modifier.weight(1f))
            if (aisle.expanded || aisle.total > aisle.items.size) {
                // The text button's own padding is taken back so that its text ends at the screen margin.
                TextActionButton(
                    text = if (aisle.expanded) {
                        stringResource(R.string.onboarding_aisle_less)
                    } else {
                        stringResource(R.string.onboarding_aisle_more, aisle.total)
                    },
                    onClick = { onEvent(OnboardingEvent.ExpandAisle(aisle.categoryId)) },
                    textStyle = CompagnonType.Help.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .offset(x = 12.dp),
                )
            }
        }
        ChipFlow(aisle.items, onToggle = { onEvent(OnboardingEvent.ToggleEssential(it)) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(chips: List<ProductChipUi>, onToggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip -> ProductChip(chip, onToggle = { onToggle(chip.productId) }) }
    }
}

/** A product of the essentials grid: a 44 pill that toggles. */
@Composable
private fun ProductChip(chip: ProductChipUi, onToggle: () -> Unit) {
    val ink = if (chip.checked) CompagnonColors.BasilDark else CompagnonColors.Ink
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CompagnonShapes.Pill)
            .background(if (chip.checked) CompagnonColors.BasilLight else CompagnonColors.Surface)
            .border(
                width = if (chip.checked) 2.dp else 1.dp,
                color = if (chip.checked) CompagnonColors.Basil else CompagnonColors.Line,
                shape = CompagnonShapes.Pill,
            )
            .toggleable(value = chip.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.checked) Icon(AppIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
        Text(text = chip.name, style = CompagnonType.Secondary.copy(fontWeight = FontWeight.SemiBold), color = ink)
    }
}

@Composable
private fun CatalogLoading() {
    val text = stringResource(R.string.onboarding_catalog_loading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CompagnonSpacing.Screen, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            color = CompagnonColors.Basil,
            trackColor = CompagnonColors.SegmentEmpty,
            modifier = Modifier.semantics { contentDescription = text },
        )
        Text(text = text, style = CompagnonType.Body, color = CompagnonColors.InkSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CatalogMissing(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CompagnonSpacing.Screen, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_catalog_missing),
            style = CompagnonType.Body,
            color = CompagnonColors.InkSoft,
            textAlign = TextAlign.Center,
        )
        TextActionButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

/**
 * `Onboarding-Recurring`: one row per product with a 3-segment selector (Semaine, 2 semaines, Mois) and a small
 * [✕]; « + Ajouter un produit » opens a search field whose results add a product. « Continuer » at the bottom.
 */
@Composable
internal fun RecurringStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val recurring = state.recurring
    LongStepLayout(
        bottom = {
            StepPrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                saving = state.saving,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        item(key = "header") {
            StepHeader(
                overline = stringResource(R.string.onboarding_section_rhythm),
                title = stringResource(R.string.onboarding_recurring_title),
                text = stringResource(R.string.onboarding_recurring_text),
                modifier = Modifier.padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 24.dp),
            )
        }
        item(key = "rows") {
            ListBlock(
                shape = CompagnonShapes.BlockLarge,
                modifier = Modifier.padding(start = CompagnonSpacing.Block, end = CompagnonSpacing.Block, top = 18.dp),
            ) {
                if (recurring.rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.onboarding_recurring_empty),
                        style = CompagnonType.Secondary,
                        color = CompagnonColors.InkSoft,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
                recurring.rows.forEachIndexed { index, row ->
                    if (index > 0) BlockDivider()
                    RecurringRow(row = row, onEvent = onEvent)
                }
                BlockDivider()
                if (recurring.adding) {
                    AddRecurringSearch(state = state, onEvent = onEvent)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onEvent(OnboardingEvent.AddingRecurring(open = true)) }
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.onboarding_recurring_add),
                            style = CompagnonType.ButtonText,
                            color = CompagnonColors.Basil,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(row: RecurringRowUi, onEvent: (OnboardingEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = row.name, style = CompagnonType.ListRow, color = CompagnonColors.Ink, modifier = Modifier.weight(1f))
            // The small [✕] « Retirer »: a 44 target, the icon in Encre discrète.
            val removeLabel = stringResource(R.string.onboarding_recurring_remove, row.name)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onEvent(OnboardingEvent.RemoveRecurring(row.productId)) }
                    .semantics { contentDescription = removeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Close, contentDescription = null, tint = CompagnonColors.InkMuted, modifier = Modifier.size(18.dp))
            }
        }
        CadenceSelector(row = row, onSelect = { onEvent(OnboardingEvent.SetCadence(row.productId, it)) })
    }
}

/** The 3-segment selector: Surface douce track, the chosen segment in Surface with a soft shadow. */
@Composable
private fun CadenceSelector(row: RecurringRowUi, onSelect: (CadenceChoice) -> Unit) {
    val groupLabel = stringResource(R.string.onboarding_cadence_group, row.name)
    val segment = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.Control)
            .background(CompagnonColors.SurfaceSoft)
            .padding(4.dp)
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CadenceChoice.entries.forEach { cadence ->
            val selected = cadence == row.cadence
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .let { if (selected) it.softShadow(segment, radius = 3.dp, offsetY = 1.dp, alpha = 0.12f) else it }
                    .clip(segment)
                    .background(if (selected) CompagnonColors.Surface else CompagnonColors.SurfaceSoft)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(cadence) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(OnboardingTexts.cadenceLabel(cadence)),
                    style = CompagnonType.Help.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (selected) CompagnonColors.Ink else CompagnonColors.InkSoft,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** « Ajouter un produit » opened: the search field (focused), its results, and « Annuler ». */
@Composable
private fun AddRecurringSearch(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val recurring = state.recurring
    val field = rememberTextFieldState(recurring.query)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier = Modifier.fillMaxWidth()) {
        SearchField(
            state = field,
            onQueryChange = { onEvent(OnboardingEvent.SearchRecurring(it)) },
            placeholder = stringResource(R.string.onboarding_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                .focusRequester(focus),
        )
        if (recurring.query.isNotBlank() && recurring.results.isEmpty()) {
            Text(
                text = stringResource(R.string.search_no_result, recurring.query.trim()),
                style = CompagnonType.Secondary,
                color = CompagnonColors.InkSoft,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        val addLabel = stringResource(R.string.onboarding_recurring_add)
        recurring.results.forEach { result ->
            AddRow(
                name = result.name,
                detail = null,
                onClick = { onEvent(OnboardingEvent.AddRecurring(result.productId)) },
                onClickLabel = addLabel,
            )
        }
        TextActionButton(
            text = stringResource(R.string.action_cancel),
            onClick = { onEvent(OnboardingEvent.AddingRecurring(open = false)) },
            color = CompagnonColors.Ink,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}
