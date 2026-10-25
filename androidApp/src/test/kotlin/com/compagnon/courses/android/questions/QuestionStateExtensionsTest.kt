package com.compagnon.courses.android.questions

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.compagnon.courses.feature.questionflow.CategoryGateUi
import com.compagnon.courses.feature.questionflow.MetaQuestionKind
import com.compagnon.courses.feature.questionflow.MetaQuestionUi
import com.compagnon.courses.feature.questionflow.ProductCardKind
import com.compagnon.courses.feature.questionflow.ProductCardUi
import com.compagnon.courses.feature.questionflow.QuantityChoice
import com.compagnon.courses.feature.questionflow.QuantityStepper
import com.compagnon.courses.feature.questionflow.QuantityValue
import com.compagnon.courses.feature.questionflow.QuestionErrorKind
import com.compagnon.courses.feature.questionflow.QuestionHaptic
import com.compagnon.courses.feature.questionflow.QuestionProgress
import com.compagnon.courses.feature.questionflow.QuestionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionStateExtensionsTest {

    private val twoBottles = QuantityChoice(QuantityValue(2_000, "bottle"), "2 bouteilles")
    private val milk = ProductCardUi(
        productId = "lait",
        name = "Lait",
        needPhrase = "du lait",
        categoryName = "Crèmerie",
        kind = ProductCardKind.STANDARD,
        presets = listOf(twoBottles),
        quantity = QuantityStepper(twoBottles, canDecrement = true, canIncrement = true),
        units = emptyList(),
        isSameAsLastTime = false,
        reason = null,
    )

    @Test
    fun positionCountsTheQuestionOnScreenFromOne() {
        assertEquals(ProgressPosition(current = 1, total = 12), QuestionProgress(answered = 0, total = 12).position())
        assertEquals(ProgressPosition(current = 8, total = 12), QuestionProgress(answered = 7, total = 12).position())
        // Every question answered (review): never past the total.
        assertEquals(ProgressPosition(current = 12, total = 12), QuestionProgress(answered = 12, total = 12).position())
        assertNull(QuestionProgress(answered = 0, total = 0).position())
    }

    @Test
    fun fractionIsTheShareAnswered() {
        assertEquals(0f, ProgressPosition(current = 1, total = 4).fraction)
        assertEquals(0.5f, ProgressPosition(current = 3, total = 4).fraction)
    }

    @Test
    fun undoAndProgressOnlyExistWhileQuestionsAreAsked() {
        val card = QuestionUiState.Card(milk, QuestionProgress(3, 10), canUndo = true)

        assertTrue(card.canUndo)
        assertEquals(QuestionProgress(3, 10), card.progress)
        assertTrue(card.isAsking)

        listOf(
            QuestionUiState.Loading,
            QuestionUiState.Committing,
            QuestionUiState.Done(itemsAdded = 3, abandoned = false),
            QuestionUiState.Error(QuestionErrorKind.NO_CATALOG, retryable = true),
        ).forEach { state ->
            assertFalse(state.toString(), state.canUndo)
            assertNull(state.toString(), state.progress)
            assertFalse(state.toString(), state.isAsking)
        }
    }

    @Test
    fun theReviewHasProgressButIsNoLongerAsking() {
        val review = QuestionUiState.Review(emptyList(), "", emptyList(), QuestionProgress(10, 10), canUndo = true)

        assertTrue(review.canUndo)
        assertFalse(review.isAsking)
    }

    @Test
    fun aQuantityChangeKeepsTheStepButANewQuestionDoesNot() {
        val card = QuestionUiState.Card(milk, QuestionProgress(3, 10), canUndo = true)
        val stepped = card.copy(card = milk.copy(quantity = milk.quantity.copy(canDecrement = false)))
        val next = QuestionUiState.Card(milk.copy(productId = "oeufs"), QuestionProgress(4, 10), canUndo = true)
        // « Plus tard » asks the same product again later in the session.
        val askedAgain = card.copy(progress = QuestionProgress(9, 10))

        assertEquals(card.stepKey(), stepped.stepKey())
        assertNotEquals(card.stepKey(), next.stepKey())
        assertNotEquals(card.stepKey(), askedAgain.stepKey())
    }

    @Test
    fun stepKeysDifferAcrossKinds() {
        val progress = QuestionProgress(0, 5)
        val keys = listOf(
            QuestionUiState.Meta(MetaQuestionUi(MetaQuestionKind.PERSONS, 2, 1, 12, listOf(1, 2, 4)), progress, false),
            QuestionUiState.Meta(MetaQuestionUi(MetaQuestionKind.GUESTS, 0, 0, 20, listOf(0, 2)), progress, false),
            QuestionUiState.Suggested(emptyList(), 0, progress, false),
            QuestionUiState.Gate(CategoryGateUi("menage", "Entretien", null, 4), progress, false),
            QuestionUiState.GateItems(CategoryGateUi("menage", "Entretien", null, 4), emptyList(), 0, progress, false),
        ).map { it.stepKey() }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun everyHapticHasItsOwnFeedback() {
        assertEquals(HapticFeedbackType.Confirm, QuestionHaptic.ITEM_ADDED.feedbackType())
        assertEquals(QuestionHaptic.entries.size, QuestionHaptic.entries.map { it.feedbackType() }.toSet().size)
    }
}
