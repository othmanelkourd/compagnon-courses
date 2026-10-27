package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.BottomLinks
import com.compagnon.courses.android.ui.components.CompactStepper
import com.compagnon.courses.android.ui.components.GradientBottomBar
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.SuggestionRow
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState
import com.compagnon.courses.feature.questionflow.SuggestedItemUi

/** T1, « Probablement nécessaire »: pre-checked lines, validated in one tap. */
@Composable
internal fun SuggestedStep(state: QuestionUiState.Suggested, onEvent: (QuestionEvent) -> Unit) {
    ChecklistLayout(
        title = stringResource(R.string.suggested_title),
        subtitle = stringResource(R.string.suggested_subtitle),
        items = state.items,
        checkedCount = state.checkedCount,
        canUndo = state.canUndo,
        onEvent = onEvent,
    )
}

/** The products behind a gate answered yes, all unchecked: the user picks what they need. Same layout as T1. */
@Composable
internal fun GateItemsStep(state: QuestionUiState.GateItems, onEvent: (QuestionEvent) -> Unit) {
    ChecklistLayout(
        title = state.gate.name,
        subtitle = stringResource(R.string.gate_items_subtitle),
        items = state.items,
        checkedCount = state.checkedCount,
        canUndo = state.canUndo,
        onEvent = onEvent,
    )
}

/**
 * The title, a white block of suggestion rows, and the bottom on a gradient: « Ajouter N articles », « Annuler »
 * on the left and « Rien de tout ça » on the right (unchecks every line, then confirms: nothing is added).
 */
@Composable
private fun ChecklistLayout(
    title: String,
    subtitle: String,
    items: List<SuggestedItemUi>,
    checkedCount: Int,
    canUndo: Boolean,
    onEvent: (QuestionEvent) -> Unit,
) {
    var barHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = with(density) { barHeight.toDp() } + 8.dp),
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.padding(
                        start = CompagnonSpacing.Screen,
                        end = CompagnonSpacing.Screen,
                        top = 24.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = CompagnonType.ScreenTitle,
                        color = CompagnonColors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(text = subtitle, style = CompagnonType.BodyCompact, color = CompagnonColors.InkSoft)
                }
            }
            if (items.isNotEmpty()) {
                item(key = "items") {
                    ListBlock(modifier = Modifier.padding(horizontal = CompagnonSpacing.Block)) {
                        items.forEachIndexed { index, item ->
                            key(item.productId) {
                                if (index > 0) BlockDivider()
                                SuggestionRow(
                                    name = item.name,
                                    reason = item.reason?.let { QuestionTexts.reason(it, compact = true).resolve() },
                                    imageKey = item.imageKey,
                                    checked = item.checked,
                                    onToggle = { onEvent(QuestionEvent.ToggleItem(item.productId)) },
                                    // The lines come in one after the other (animation 2).
                                    modifier = Modifier.riseIn(index = index + 1),
                                ) {
                                    CompactStepper(
                                        label = item.quantity.selected.label,
                                        itemName = item.name,
                                        canDecrement = item.quantity.canDecrement,
                                        canIncrement = item.quantity.canIncrement,
                                        onStep = { onEvent(QuestionEvent.StepItem(item.productId, it)) },
                                        // An unchecked line dims its quantity (same on iOS).
                                        modifier = Modifier.alpha(if (item.checked) 1f else 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        GradientBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight = it.height },
        ) {
            // Nothing checked: the button says so and waits; « Rien de tout ça » goes on without adding anything.
            PrimaryButton(
                text = if (checkedCount > 0) {
                    pluralStringResource(R.plurals.checklist_add, checkedCount, checkedCount)
                } else {
                    stringResource(R.string.checklist_none_checked)
                },
                onClick = { onEvent(QuestionEvent.ConfirmItems) },
                enabled = checkedCount > 0,
                count = checkedCount.takeIf { it > 0 },
                modifier = Modifier.fillMaxWidth(),
            )
            BottomLinks(
                start = { UndoAnswerButton(canUndo = canUndo, onEvent = onEvent) },
                end = {
                    TextActionButton(
                        text = stringResource(R.string.gate_items_none),
                        onClick = {
                            items.filter { it.checked }.forEach { onEvent(QuestionEvent.ToggleItem(it.productId)) }
                            onEvent(QuestionEvent.ConfirmItems)
                        },
                    )
                },
            )
        }
    }
}
