package com.compagnon.courses.android.questions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BottomLinks
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.illustration.Illustration
import com.compagnon.courses.android.ui.illustration.LeafPattern
import com.compagnon.courses.android.ui.illustration.PatternLeaf
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.questionflow.CategoryGateUi
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState

/**
 * « Besoin de quelque chose en Entretien ? »: one dark swipeable card with the aisle's image and leaves as a
 * watermark; yes opens the aisle's products, and the buttons make the card fly off as a swipe does. The charter's
 * product examples are left out: the state does not provide them.
 */
@Composable
internal fun GateStep(state: QuestionUiState.Gate, onEvent: (QuestionEvent) -> Unit) {
    val gate = state.gate
    val yesLabel = stringResource(R.string.answer_yes)
    val noLabel = stringResource(R.string.answer_no)
    val cardState = key(state.stepKey()) { rememberAnswerCardState() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CompagnonSpacing.Screen)
            .padding(top = 28.dp, bottom = CompagnonSpacing.Bottom),
    ) {
        // The card wraps its content at the top; the free space stays above the buttons.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            key(state.stepKey()) {
                SwipeableAnswerCard(
                    yesLabel = yesLabel,
                    noLabel = noLabel,
                    onYes = { onEvent(QuestionEvent.AnswerGate(yes = true)) },
                    onNo = { onEvent(QuestionEvent.AnswerGate(yes = false)) },
                    state = cardState,
                    style = AnswerCardStyle.AISLE,
                    fillHeight = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.TopEnd) {
                            LeafPattern(
                                leaves = GateLeaves,
                                width = 350.dp,
                                height = 200.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.wrapContentSize(Alignment.TopEnd, unbounded = true),
                            )
                        }
                        GateCardBody(gate = gate)
                    }
                }
            }
        }
        AnswerButtons(
            noText = stringResource(R.string.gate_no),
            yesText = stringResource(R.string.gate_yes),
            onYes = { cardState.answer(yes = true) { onEvent(QuestionEvent.AnswerGate(yes = true)) } },
            onNo = { cardState.answer(yes = false) { onEvent(QuestionEvent.AnswerGate(yes = false)) } },
            yesTrailingIcon = AppIcons.ArrowForward,
            modifier = Modifier.padding(top = 16.dp),
        )
        BottomLinks(
            modifier = Modifier.padding(top = 10.dp),
            start = { UndoAnswerButton(canUndo = state.canUndo, onEvent = onEvent) },
            end = {
                TextActionButton(
                    text = stringResource(R.string.question_end_questions),
                    onClick = { onEvent(QuestionEvent.EndQuestions) },
                )
            },
        )
    }
}

/** The leaves of the dark card (`Gate.dc.html`), white at 8 %, from its top right corner. */
private val GateLeaves = listOf(
    PatternLeaf(300f, 40f, 30f),
    PatternLeaf(240f, 120f, -20f, 0.75f),
    PatternLeaf(330f, 150f, 70f, 0.6f),
    PatternLeaf(200f, 30f, -50f, 0.5f),
)

/** The aisle's image in a soft light disc, « TOUT UN RAYON », the question and the number of products. */
@Composable
private fun GateCardBody(gate: CategoryGateUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Illustration(key = gate.imageKey, size = 60.dp, rotation = -8f, popDelayMillis = 120)
            }
            Overline(
                text = stringResource(R.string.gate_overline),
                color = Color.White.copy(alpha = 0.8f),
                heading = false,
            )
        }
        Text(
            text = QuestionTexts.gateQuestion(gate).resolve(),
            style = CompagnonType.ScreenTitleLarge,
            color = Color.White,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (gate.productCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.gate_product_count, gate.productCount, gate.productCount),
                style = CompagnonType.BodyCompact,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
    }
}
