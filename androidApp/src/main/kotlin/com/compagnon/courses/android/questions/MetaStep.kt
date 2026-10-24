package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.QuantityStepperControl
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState

/**
 * « Courses pour combien de jours ? », « Combien de personnes ? », « Des invités ? »: a quick answer is one tap;
 * the stepper and « Valider » give any other value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetaStep(state: QuestionUiState.Meta, onEvent: (QuestionEvent) -> Unit) {
    val question = state.question
    var value by rememberSaveable(question.kind) { mutableIntStateOf(question.value.coerceIn(question.min, question.max)) }
    val title = stringResource(QuestionTexts.metaTitle(question.kind))

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (question.choices.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    question.choices.forEach { choice ->
                        FilledTonalButton(
                            onClick = { onEvent(QuestionEvent.AnswerMeta(choice)) },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text(
                                text = QuestionTexts.metaValue(question.kind, choice).resolve(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            QuantityStepperControl(
                label = QuestionTexts.metaValue(question.kind, value).resolve(),
                itemName = title,
                canDecrement = value > question.min,
                canIncrement = value < question.max,
                onStep = { increment -> value = (value + if (increment) 1 else -1).coerceIn(question.min, question.max) },
                buttonSize = 56.dp,
                labelMinWidth = 140.dp,
                labelStyle = MaterialTheme.typography.headlineSmall,
                decreaseDescription = stringResource(R.string.meta_decrease),
                increaseDescription = stringResource(R.string.meta_increase),
            )
        }
        BottomAction(text = stringResource(R.string.action_validate), onClick = { onEvent(QuestionEvent.AnswerMeta(value)) })
    }
}
