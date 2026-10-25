package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.QuantityStepperControl
import com.compagnon.courses.android.ui.components.resolve
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
        confirmText = if (state.checkedCount > 0) {
            pluralStringResource(R.plurals.checklist_add, state.checkedCount, state.checkedCount)
        } else {
            stringResource(R.string.checklist_continue_without)
        },
        onEvent = onEvent,
    )
}

/** The products behind a gate answered yes, all unchecked: the user picks what they need. */
@Composable
internal fun GateItemsStep(state: QuestionUiState.GateItems, onEvent: (QuestionEvent) -> Unit) {
    ChecklistLayout(
        title = state.gate.name,
        subtitle = stringResource(R.string.gate_items_subtitle),
        items = state.items,
        confirmText = if (state.checkedCount > 0) {
            pluralStringResource(R.plurals.checklist_add, state.checkedCount, state.checkedCount)
        } else {
            stringResource(R.string.gate_items_none)
        },
        onEvent = onEvent,
    )
}

@Composable
private fun ChecklistLayout(
    title: String,
    subtitle: String,
    items: List<SuggestedItemUi>,
    confirmText: String,
    onEvent: (QuestionEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "header") {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.productId }) { item ->
                ChecklistRow(item = item, onEvent = onEvent)
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
        BottomAction(text = confirmText, onClick = { onEvent(QuestionEvent.ConfirmItems) })
    }
}

/** One line: the left part (checkbox, name, reason) toggles; the stepper on the right changes the quantity. */
@Composable
private fun ChecklistRow(item: SuggestedItemUi, onEvent: (QuestionEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 72.dp)
                .toggleable(
                    value = item.checked,
                    role = Role.Checkbox,
                    onValueChange = { onEvent(QuestionEvent.ToggleItem(item.productId)) },
                )
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                item.reason?.let { reason ->
                    Text(
                        text = QuestionTexts.reason(reason).first().resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
        QuantityStepperControl(
            label = item.quantity.selected.label,
            itemName = item.name,
            canDecrement = item.quantity.canDecrement,
            canIncrement = item.quantity.canIncrement,
            onStep = { onEvent(QuestionEvent.StepItem(item.productId, it)) },
            labelMinWidth = 64.dp,
            labelStyle = MaterialTheme.typography.bodyMedium,
            buttonSize = 40.dp,
        )
    }
}

/** The main action of a step, full width at the bottom of the screen. */
@Composable
internal fun BottomAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .heightIn(min = 56.dp),
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
