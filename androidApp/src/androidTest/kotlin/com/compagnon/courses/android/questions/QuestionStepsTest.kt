package com.compagnon.courses.android.questions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.R
import com.compagnon.courses.android.questions.QuestionFixtures.cleaningGate
import com.compagnon.courses.android.questions.QuestionFixtures.pastaResult
import com.compagnon.courses.android.questions.QuestionFixtures.reviewItems
import com.compagnon.courses.android.questions.QuestionFixtures.suggested
import com.compagnon.courses.android.testing.overline
import com.compagnon.courses.android.testing.plural
import com.compagnon.courses.android.testing.string
import com.compagnon.courses.android.ui.components.SEARCH_FIELD_TAG
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.feature.questionflow.MetaQuestionKind
import com.compagnon.courses.feature.questionflow.MetaQuestionUi
import com.compagnon.courses.feature.questionflow.QuestionErrorKind
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionProgress
import com.compagnon.courses.feature.questionflow.QuestionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gates, the T1 list, the review, meta questions and the end of a session, rendered from fake states. */
@RunWith(AndroidJUnit4::class)
class QuestionStepsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<QuestionEvent>()
    private var openedList = 0

    private val progress = QuestionProgress(answered = 4, total = 10)

    private fun setState(state: QuestionUiState) {
        composeRule.setContent {
            CompagnonTheme {
                QuestionnaireContent(state = state, onEvent = { events += it }, onClose = {}, onOpenList = { openedList++ })
            }
        }
    }

    // --- Category gate ---

    @Test
    fun aGateWithoutCatalogQuestionUsesTheFallbackWording() {
        setState(QuestionUiState.Gate(cleaningGate, progress, canUndo = true))

        composeRule.onNodeWithText(composeRule.string(R.string.gate_question_fallback, "Entretien")).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.gate_product_count, 12)).assertIsDisplayed()
    }

    @Test
    fun aGateShowsTheCatalogQuestion() {
        setState(QuestionUiState.Gate(cleaningGate.copy(question = "Des produits ménagers ?"), progress, canUndo = true))

        composeRule.onNodeWithText("Des produits ménagers ?").assertIsDisplayed()
    }

    @Test
    fun aGateIsAnsweredWithTheButtonsOrASwipe() {
        setState(QuestionUiState.Gate(cleaningGate, progress, canUndo = true))

        composeRule.onNodeWithText(composeRule.string(R.string.gate_yes)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.gate_no)).performClick()
        composeRule.onNodeWithTag(SWIPE_CARD_TAG).performTouchInput { swipeLeft() }

        composeRule.runOnIdle {
            assertEquals(
                listOf(QuestionEvent.AnswerGate(yes = true), QuestionEvent.AnswerGate(yes = false), QuestionEvent.AnswerGate(yes = false)),
                events,
            )
        }
    }

    @Test
    fun theProductsOfAGateArePickedThenConfirmed() {
        setState(QuestionUiState.GateItems(cleaningGate, suggested.map { it.copy(checked = false) }, 0, progress, true))

        composeRule.onNodeWithText("Entretien").assertIsDisplayed()
        // Nothing checked yet: the main button says so and waits.
        composeRule.onNodeWithText(composeRule.string(R.string.checklist_none_checked)).assertIsNotEnabled()
        composeRule.onNode(isToggleable() and hasText("Œufs", substring = true)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.ToggleItem("oeufs")), events) }
    }

    @Test
    fun noneOfTheseUnchecksEveryLineThenConfirms() {
        setState(QuestionUiState.Suggested(suggested, checkedCount = 1, progress = progress, canUndo = true))

        composeRule.onNodeWithText(composeRule.string(R.string.gate_items_none)).performClick()

        // Only « Baguette » was checked: it is unchecked, then nothing is added.
        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.ToggleItem("pain"), QuestionEvent.ConfirmItems), events) }
    }

    // --- T1 « Probablement nécessaire » ---

    @Test
    fun theSuggestedListShowsCheckedLinesAndConfirmsInOneTap() {
        setState(QuestionUiState.Suggested(suggested, checkedCount = 1, progress = progress, canUndo = false))

        composeRule.onNodeWithText(composeRule.string(R.string.suggested_title)).assertIsDisplayed()
        composeRule.onNode(isToggleable() and hasText("Baguette", substring = true)).assertIsOn()
        composeRule.onNode(isToggleable() and hasText("Œufs", substring = true)).assertIsOff()
        composeRule.onNodeWithText("2 baguettes").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.checklist_add, 1)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.ConfirmItems), events) }
    }

    @Test
    fun aSuggestedQuantityIsStepped() {
        setState(QuestionUiState.Suggested(suggested, checkedCount = 1, progress = progress, canUndo = false))

        composeRule.onNode(
            hasContentDescription(composeRule.string(R.string.quantity_increase, "Œufs")),
        ).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.StepItem("oeufs", increment = true)), events) }
    }

    // --- Meta questions ---

    @Test
    fun aMetaQuickAnswerIsOneTap() {
        val question = MetaQuestionUi(MetaQuestionKind.HORIZON_DAYS, value = 7, min = 1, max = 31, choices = listOf(3, 7, 14))
        setState(QuestionUiState.Meta(question, QuestionProgress(0, 10), canUndo = false))

        composeRule.onNodeWithText(composeRule.string(R.string.meta_horizon_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.plural(R.plurals.meta_days, 14)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.AnswerMeta(14)), events) }
    }

    @Test
    fun aMetaValueCanBeAdjustedThenValidated() {
        val question = MetaQuestionUi(MetaQuestionKind.GUESTS, value = 0, min = 0, max = 20, choices = listOf(0, 2, 4))
        setState(QuestionUiState.Meta(question, QuestionProgress(2, 10), canUndo = true))

        composeRule.onNode(hasContentDescription(composeRule.string(R.string.meta_increase)))
            .performClick()
            .performClick()
            .performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.meta_continue, composeRule.plural(R.plurals.meta_guests, 3))).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.AnswerMeta(3)), events) }
    }

    @Test
    fun skippingAMetaQuestionKeepsItsPrefilledValue() {
        val question = MetaQuestionUi(MetaQuestionKind.PERSONS, value = 2, min = 1, max = 12, choices = listOf(1, 2, 3, 4, 5, 6))
        setState(QuestionUiState.Meta(question, QuestionProgress(1, 10), canUndo = true))

        composeRule.onNode(hasContentDescription(composeRule.string(R.string.meta_increase))).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.meta_skip)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.AnswerMeta(2)), events) }
    }

    // --- Review ---

    @Test
    fun theReviewListsTheItemsAndWritesTheList() {
        setState(QuestionUiState.Review(reviewItems, "", emptyList(), QuestionProgress(10, 10), canUndo = true))

        composeRule.onNodeWithText(composeRule.plural(R.plurals.review_items_title, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.review_questions, 10)).assertIsDisplayed()
        composeRule.onNodeWithText("Lait demi-écrémé").assertIsDisplayed()
        composeRule.onNodeWithText("2 bouteilles").assertIsDisplayed()
        // Blocks by origin, with their overline.
        composeRule.onNodeWithText(composeRule.overline(composeRule.string(R.string.origin_card))).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.overline(composeRule.string(R.string.origin_suggested))).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.review_add_to_list)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Confirm), events) }
    }

    @Test
    fun theReviewMenuStartsOverOrAbandonsButDoesNotSkip() {
        setState(QuestionUiState.Review(reviewItems, "", emptyList(), QuestionProgress(10, 10), canUndo = true))

        // Same top bar as iOS: [Fermer] and [⋯], no counter on the review.
        composeRule.onNodeWithText(composeRule.string(R.string.question_progress, 10, 10)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.action_more)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.question_end_questions)).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.string(R.string.question_abandon)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.question_start_over)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.StartOver), events) }
    }

    @Test
    fun theReviewGoesBackToTheQuestions() {
        setState(QuestionUiState.Review(reviewItems, "", emptyList(), QuestionProgress(10, 10), canUndo = true))

        composeRule.onNodeWithText(composeRule.string(R.string.review_back)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Undo), events) }
    }

    @Test
    fun anythingElseSearchesTheCatalog() {
        setState(QuestionUiState.Review(reviewItems, "", emptyList(), QuestionProgress(10, 10), canUndo = true))

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performScrollTo().performTextInput("pât")

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Search("pât")), events) }
    }

    @Test
    fun aSearchResultIsAddedInOneTap() {
        setState(QuestionUiState.Review(reviewItems, "pât", listOf(pastaResult), QuestionProgress(10, 10), canUndo = true))

        composeRule.onNodeWithText("Pâtes").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(QuestionEvent.AddOther("pates"), events.first())
            // The field is cleared for the next search.
            assertEquals(QuestionEvent.Search(""), events.last())
        }
    }

    // --- End of the session ---

    @Test
    fun doneShowsHowManyItemsWereAddedAndOpensTheList() {
        setState(QuestionUiState.Done(itemsAdded = 5, abandoned = false))

        composeRule.onNodeWithText(composeRule.plural(R.plurals.done_items_added, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.done_open_list)).performClick()

        composeRule.runOnIdle { assertEquals(1, openedList) }
    }

    @Test
    fun anAbandonedSessionSaysSo() {
        setState(QuestionUiState.Done(itemsAdded = 0, abandoned = true))

        composeRule.onNodeWithText(composeRule.string(R.string.done_abandoned_title)).assertIsDisplayed()
    }

    @Test
    fun aRetryableErrorOffersRetryAndStartOver() {
        setState(QuestionUiState.Error(QuestionErrorKind.NO_CATALOG, retryable = true))

        composeRule.onNodeWithText(composeRule.string(R.string.question_error_no_catalog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.question_error_no_catalog)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.action_retry)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.question_start_over)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(QuestionEvent.Retry, QuestionEvent.StartOver), events) }
    }
}
