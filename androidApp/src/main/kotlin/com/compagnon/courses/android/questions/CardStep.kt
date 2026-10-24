package com.compagnon.courses.android.questions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.QuantityStepperControl
import com.compagnon.courses.android.ui.components.resolveAll
import com.compagnon.courses.feature.questionflow.NuancedAnswer
import com.compagnon.courses.feature.questionflow.ProductCardUi
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState
import com.compagnon.courses.feature.questionflow.ReasonUi
import kotlinx.coroutines.launch

/**
 * A full-screen product card: « Il vous faut du lait ? ». Swipe right or « Oui · 2 bouteilles » = yes with the
 * stepper's quantity, a preset = yes with that quantity, swipe left or « Non » = no, « Plus d'options » = the
 * nuanced answers. The answer buttons sit at the bottom, under the thumb.
 */
@Composable
internal fun CardStep(state: QuestionUiState.Card, onEvent: (QuestionEvent) -> Unit) {
    val card = state.card
    var showOptions by rememberSaveable(card.productId) { mutableStateOf(false) }
    val yesLabel = stringResource(R.string.answer_yes)
    val noLabel = stringResource(R.string.answer_no)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
    ) {
        key(state.stepKey()) {
            SwipeableAnswerCard(
                yesLabel = yesLabel,
                noLabel = noLabel,
                onYes = { onEvent(QuestionEvent.Yes()) },
                onNo = { onEvent(QuestionEvent.No) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                ProductCardBody(card = card, onEvent = onEvent)
            }
        }
        AnswerButtons(
            yesText = stringResource(R.string.answer_yes_with_quantity, card.quantity.selected.label),
            onYes = { onEvent(QuestionEvent.Yes()) },
            onNo = { onEvent(QuestionEvent.No) },
            modifier = Modifier.padding(top = 16.dp),
        )
        TextButton(
            onClick = { showOptions = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.card_more_options))
        }
    }

    if (showOptions) {
        NuancedAnswersSheet(
            productName = card.name,
            onAnswer = { answer ->
                showOptions = false
                onEvent(QuestionEvent.Nuanced(answer))
            },
            onDismiss = { showOptions = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProductCardBody(card: ProductCardUi, onEvent: (QuestionEvent) -> Unit) {
    var showReason by rememberSaveable(card.productId) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // The question and its context scroll when long; the quantity controls stay at the bottom of the card.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                QuestionTexts.cardBadge(card.kind)?.let { badge ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(badge),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                card.categoryName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.card_question, card.needPhrase),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )

            val reason = card.reason
            if (card.isSameAsLastTime || reason != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.isSameAsLastTime) {
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(stringResource(R.string.card_same_as_last_time)) },
                        )
                    }
                    if (reason != null) {
                        AssistChip(
                            onClick = { showReason = !showReason },
                            label = { Text(stringResource(R.string.card_why)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }
            if (showReason && reason != null) {
                ReasonText(reason)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (card.units.size > 1) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    card.units.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option.selected,
                            onClick = { onEvent(QuestionEvent.SelectUnit(option.unit)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = card.units.size),
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            if (card.presets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    card.presets.forEach { preset ->
                        val selected = preset.value == card.quantity.selected.value
                        val description = stringResource(R.string.answer_yes_with_quantity, preset.label)
                        val buttonModifier = Modifier
                            .heightIn(min = 56.dp)
                            .semantics { contentDescription = description }
                        val onClick = { onEvent(QuestionEvent.Yes(preset.value)) }
                        if (selected) {
                            Button(onClick = onClick, modifier = buttonModifier) { PresetLabel(preset.label) }
                        } else {
                            FilledTonalButton(onClick = onClick, modifier = buttonModifier) { PresetLabel(preset.label) }
                        }
                    }
                }
            }

            QuantityStepperControl(
                label = card.quantity.selected.label,
                itemName = card.name,
                canDecrement = card.quantity.canDecrement,
                canIncrement = card.quantity.canIncrement,
                onStep = { onEvent(QuestionEvent.StepQuantity(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PresetLabel(label: String) {
    Text(text = label, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun ReasonText(reason: ReasonUi, modifier: Modifier = Modifier) {
    Text(
        text = resolveAll(QuestionTexts.reason(reason)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** « Non » and « Oui » side by side, 64 dp high: the primary answer is on the right, under the thumb. */
@Composable
internal fun AnswerButtons(
    yesText: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onNo,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.answer_no), style = MaterialTheme.typography.titleMedium)
        }
        Button(
            onClick = onYes,
            modifier = Modifier
                .weight(1.6f)
                .heightIn(min = 64.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(yesText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** « Plus d'options »: the nuanced answers, each with a one-line explanation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuancedAnswersSheet(
    productName: String,
    onAnswer: (NuancedAnswer) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = productName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
                .semantics { heading() },
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            QuestionTexts.nuancedAnswers.forEach { answer ->
                ListItem(
                    headlineContent = { Text(stringResource(QuestionTexts.nuancedLabel(answer))) },
                    supportingContent = { Text(stringResource(QuestionTexts.nuancedHint(answer))) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Button) {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onAnswer(answer) }
                        },
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
