package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState

/** « Besoin de quelque chose en Entretien ? »: one swipeable card, yes opens the aisle's products. */
@Composable
internal fun GateStep(state: QuestionUiState.Gate, onEvent: (QuestionEvent) -> Unit) {
    val gate = state.gate
    val yesLabel = stringResource(R.string.answer_yes)
    val noLabel = stringResource(R.string.answer_no)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .navigationBarsPadding(),
    ) {
        key(state.stepKey()) {
            SwipeableAnswerCard(
                yesLabel = yesLabel,
                noLabel = noLabel,
                onYes = { onEvent(QuestionEvent.AnswerGate(yes = true)) },
                onNo = { onEvent(QuestionEvent.AnswerGate(yes = false)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.gate_overline),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = QuestionTexts.gateQuestion(gate).resolve(),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                    if (gate.productCount > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.gate_product_count, gate.productCount, gate.productCount),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        AnswerButtons(
            yesText = yesLabel,
            onYes = { onEvent(QuestionEvent.AnswerGate(yes = true)) },
            onNo = { onEvent(QuestionEvent.AnswerGate(yes = false)) },
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
