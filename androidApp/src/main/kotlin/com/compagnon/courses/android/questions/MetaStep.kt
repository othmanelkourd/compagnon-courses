package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.BottomLinks
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.questionflow.MetaQuestionKind
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState

/**
 * Large choices per row: up to four on one row, otherwise balanced rows of three (four for seven or eight choices).
 * Same rule on iOS.
 */
internal fun metaColumns(count: Int): Int = when {
    count <= 4 -> count.coerceAtLeast(1)
    count == 7 || count == 8 -> 4
    else -> 3
}

/**
 * « Courses pour combien de jours ? », « Combien de personnes ? », « Des invités ? »: a large choice is one tap;
 * the boxed stepper and « Continuer avec 7 jours » give any other value; « Passer » keeps the pre-filled value.
 */
@Composable
internal fun MetaStep(state: QuestionUiState.Meta, onEvent: (QuestionEvent) -> Unit) {
    val question = state.question
    var value by rememberSaveable(question.kind) { mutableIntStateOf(question.value.coerceIn(question.min, question.max)) }
    val valueText = QuestionTexts.metaValue(question.kind, value).resolve()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CompagnonSpacing.Screen)
            .padding(bottom = CompagnonSpacing.Bottom),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 40.dp, bottom = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline(text = stringResource(R.string.meta_overline), color = CompagnonColors.Basil, heading = false)
                Text(
                    text = stringResource(QuestionTexts.metaTitle(question.kind)),
                    style = CompagnonType.ScreenTitleLarge,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(QuestionTexts.metaSubtitle(question.kind)),
                    style = CompagnonType.Body,
                    color = CompagnonColors.InkSoft,
                )
            }
            BigChoiceQuestion(
                choices = question.choices.map { choice ->
                    val texts = QuestionTexts.metaChoice(question.kind, choice)
                    BigChoice(
                        value = texts.value.resolve(),
                        subline = texts.unit?.resolve(),
                        selected = choice == value,
                        contentDescription = QuestionTexts.metaValue(question.kind, choice).resolve(),
                        onClick = { onEvent(QuestionEvent.AnswerMeta(choice)) },
                    )
                },
                otherLabel = stringResource(QuestionTexts.metaOther(question.kind), valueText),
                canDecrement = value > question.min,
                canIncrement = value < question.max,
                onStep = { increment -> value = (value + if (increment) 1 else -1).coerceIn(question.min, question.max) },
                decreaseDescription = stringResource(R.string.meta_decrease),
                increaseDescription = stringResource(R.string.meta_increase),
            )
        }

        Text(
            text = stringResource(R.string.meta_one_tap),
            style = CompagnonType.Help,
            color = CompagnonColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
        )
        PrimaryButton(
            text = if (question.kind == MetaQuestionKind.GUESTS && value == 0) {
                stringResource(R.string.meta_continue_no_guests)
            } else {
                stringResource(R.string.meta_continue, valueText)
            },
            onClick = { onEvent(QuestionEvent.AnswerMeta(value)) },
            count = value,
            modifier = Modifier.fillMaxWidth(),
        )
        BottomLinks(
            modifier = Modifier.padding(top = 12.dp),
            start = { UndoAnswerButton(canUndo = state.canUndo, onEvent = onEvent) },
            end = {
                TextActionButton(
                    text = stringResource(R.string.meta_skip),
                    onClick = { onEvent(QuestionEvent.AnswerMeta(question.value)) },
                )
            },
        )
    }
}
