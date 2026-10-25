package com.compagnon.courses.android.questions

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.R
import com.compagnon.courses.android.questions.QuestionFixtures.bottles
import com.compagnon.courses.android.questions.QuestionFixtures.milkCard
import com.compagnon.courses.android.testing.string
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.feature.questionflow.NuancedAnswer
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionProgress
import com.compagnon.courses.feature.questionflow.QuestionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The product card of the questionnaire, rendered from fake states. */
@RunWith(AndroidJUnit4::class)
class CardStepTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<QuestionEvent>()

    private fun setCard(state: QuestionUiState.Card = QuestionUiState.Card(milkCard, QuestionProgress(7, 12), canUndo = true)) {
        composeRule.setContent {
            CompagnonTheme(dynamicColor = false) {
                QuestionnaireContent(state = state, onEvent = { events += it }, onClose = {}, onOpenList = {})
            }
        }
    }

    @Test
    fun showsTheQuestionTheProgressAndTheContext() {
        setCard()

        composeRule.onNodeWithText(composeRule.string(R.string.card_question, "du lait demi-écrémé")).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.question_progress, 8, 12)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.question_progress_spoken, 8, 12)).assertExists()
        composeRule.onNodeWithText("Crèmerie").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.card_same_as_last_time)).assertIsDisplayed()
    }

    @Test
    fun aPresetAnswersYesWithItsQuantity() {
        setCard()

        composeRule.onNodeWithText("6 bouteilles").performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Yes(bottles(6).value)), events) }
    }

    @Test
    fun theYesButtonAnswersTheStepperQuantity() {
        setCard()

        composeRule.onNodeWithText(composeRule.string(R.string.answer_yes_with_quantity, "2 bouteilles")).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Yes()), events) }
    }

    @Test
    fun theNoButtonAnswersNo() {
        setCard()

        composeRule.onNodeWithText(composeRule.string(R.string.answer_no)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.No), events) }
    }

    @Test
    fun swipingRightAnswersYes() {
        setCard()

        composeRule.onNodeWithTag(SWIPE_CARD_TAG).performTouchInput { swipeRight() }

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Yes()), events) }
    }

    @Test
    fun swipingLeftAnswersNo() {
        setCard()

        composeRule.onNodeWithTag(SWIPE_CARD_TAG).performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.No), events) }
    }

    @Test
    fun aShortDragIsNotAnAnswer() {
        setCard()

        composeRule.onNodeWithTag(SWIPE_CARD_TAG).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + width * 0.1f, durationMillis = 1_000)
        }

        composeRule.runOnIdle { assertEquals(emptyList<QuestionEvent>(), events) }
    }

    @Test
    fun talkBackGetsYesAndNoAsCustomActions() {
        setCard()

        val yes = composeRule.string(R.string.answer_yes)
        val no = composeRule.string(R.string.answer_no)
        composeRule.onNodeWithTag(SWIPE_CARD_TAG).assert(
            SemanticsMatcher("has custom actions Yes and No") { node ->
                val labels = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
                labels == listOf(yes, no)
            },
        )
    }

    @Test
    fun theStepperMovesTheQuantityWithinItsBounds() {
        setCard()

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.quantity_decrease, "Lait demi-écrémé"))
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.quantity_increase, "Lait demi-écrémé"))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.StepQuantity(increment = true)), events) }
    }

    @Test
    fun anotherUnitCanBeSelected() {
        setCard()

        composeRule.onNodeWithText("Pack").performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.SelectUnit("pack")), events) }
    }

    @Test
    fun whyThisQuestionRevealsTheReason() {
        setCard()
        val reason = composeRule.activity.resources.getQuantityString(R.plurals.reason_due_last, 9, 9)
        composeRule.onNodeWithText(reason, substring = true).assertDoesNotExist()

        composeRule.onNodeWithText(composeRule.string(R.string.card_why)).performClick()

        composeRule.onNodeWithText(reason, substring = true).assertIsDisplayed()
    }

    @Test
    fun moreOptionsOffersTheNuancedAnswers() {
        setCard()

        composeRule.onNodeWithText(composeRule.string(R.string.card_more_options)).performClick()
        QuestionTexts.nuancedAnswers.forEach { answer ->
            composeRule.onNodeWithText(composeRule.string(QuestionTexts.nuancedLabel(answer))).assertExists()
        }
        composeRule.onNodeWithText(composeRule.string(R.string.nuanced_later)).performClick()

        composeRule.waitUntil { events.isNotEmpty() }
        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Nuanced(NuancedAnswer.LATER)), events) }
        composeRule.onNodeWithText(composeRule.string(R.string.nuanced_never)).assertDoesNotExist()
    }

    @Test
    fun undoIsAlwaysInTheTopBar() {
        setCard()

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.question_undo)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Undo), events) }
    }

    @Test
    fun undoIsDisabledOnTheFirstQuestion() {
        setCard(QuestionUiState.Card(milkCard, QuestionProgress(0, 12), canUndo = false))

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.question_undo)).assertIsNotEnabled()
        composeRule.onNodeWithText(composeRule.string(R.string.question_progress, 1, 12)).assertIsDisplayed()
    }

    @Test
    fun theMenuSkipsToTheSummary() {
        setCard()

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.action_more)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.question_end_questions)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.EndQuestions), events) }
    }

    @Test
    fun abandoningAsksForConfirmation() {
        setCard()

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.action_more)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.question_abandon)).performClick()
        composeRule.runOnIdle { assertEquals(emptyList<QuestionEvent>(), events) }
        composeRule.onNodeWithText(composeRule.string(R.string.question_abandon_confirm)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Abandon), events) }
    }
}
