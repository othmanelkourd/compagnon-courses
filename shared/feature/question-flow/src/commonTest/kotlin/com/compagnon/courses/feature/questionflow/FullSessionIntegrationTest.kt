package com.compagnon.courses.feature.questionflow

import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * A whole quick session on the real seed (312 products, FR), from the meta questions to the list:
 * the budget holds, every proposed quantity is valid for its product, and the list receives exactly the
 * reviewed items with their quantities, the answers and the statistics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FullSessionIntegrationTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aQuickSessionOnTheSeedFillsTheList() = runTest(main) {
        val device = TestDevice(StandardTestDispatcher(testScheduler))
        device.sync()
        // « Il en reste un peu » last time: these are always cards, asked before the T2 cards.
        device.watch("lait-demi-ecreme", "pommes", "cafe-moulu")
        val viewModel = device.newViewModel(seed = 2026)
        advanceUntilIdle()

        val seen = mutableListOf<QuestionUiState>()
        var answered = 0
        var guard = 0
        while (guard++ < 100) {
            val current = viewModel.state.value
            seen += current
            when (current) {
                is QuestionUiState.Meta -> viewModel.onEvent(QuestionEvent.AnswerMeta(current.question.value))
                is QuestionUiState.Suggested -> {
                    current.items.forEach { device.assertValid(it.productId, it.quantity.selected) }
                    viewModel.onEvent(QuestionEvent.ConfirmItems)
                }
                is QuestionUiState.Card -> {
                    val card = current.card
                    card.presets.forEach { device.assertValid(card.productId, it) }
                    device.assertValid(card.productId, card.quantity.selected)
                    when (answered++ % 3) {
                        0 -> viewModel.onEvent(QuestionEvent.Yes(card.presets.last().value))
                        1 -> viewModel.onEvent(QuestionEvent.No)
                        else -> viewModel.onEvent(QuestionEvent.Nuanced(NuancedAnswer.ENOUGH_FOR_A_WEEK))
                    }
                }
                is QuestionUiState.Gate -> viewModel.onEvent(QuestionEvent.AnswerGate(yes = answered++ % 2 == 0))
                is QuestionUiState.GateItems -> {
                    current.items.firstOrNull()?.let { viewModel.onEvent(QuestionEvent.ToggleItem(it.productId)) }
                    viewModel.onEvent(QuestionEvent.ConfirmItems)
                }
                is QuestionUiState.Review -> break
                else -> error("Unexpected state $current")
            }
            advanceUntilIdle()
        }
        val review = assertIs<QuestionUiState.Review>(viewModel.state.value)

        // Budget: quick sessions cost at most 12 questions (3 meta + T1 screen + cards and gates).
        val totals = seen.mapNotNull { it.progressOrNull()?.total }.toSet()
        assertTrue(totals.all { it <= 12 }, "totals $totals")
        assertEquals(review.progress.total, review.progress.answered)
        assertEquals(3, seen.count { it is QuestionUiState.Meta })
        assertTrue(review.items.isNotEmpty())
        assertTrue(review.items.any { it.origin == ItemOrigin.SUGGESTED })
        // 12 − 3 meta − T1 screen = 8 slots: cards first (the 3 watched products, then the likeliest T2
        // products), 2 slots reserved for the gates. (Before cards came first, gates took every slot.)
        val cards = seen.filterIsInstance<QuestionUiState.Card>()
        assertEquals(6, cards.size)
        assertEquals(3, cards.count { it.card.kind == ProductCardKind.WATCH })
        assertEquals(3, cards.count { it.card.kind == ProductCardKind.STANDARD })
        assertEquals(2, seen.count { it is QuestionUiState.Gate })

        viewModel.onEvent(QuestionEvent.Confirm)
        advanceUntilIdle()

        assertEquals(QuestionUiState.Done(itemsAdded = review.items.size, abandoned = false), viewModel.state.value)
        val items = device.lists.activeList().items
        assertEquals(review.items.map { it.productId }.toSet(), items.mapNotNull { it.productId?.value }.toSet())
        review.items.forEach { line ->
            val item = items.single { it.productId?.value == line.productId }
            assertEquals(Quantity(line.quantity.value.milli, UnitCode(line.quantity.value.unit)), item.quantity)
            assertEquals(AddedVia.QUESTION_FLOW, item.addedVia)
        }
        val session = device.database.questionQueries.selectSession("id-2").executeAsOne()
        assertNotNull(session.completed_at)
        assertEquals(review.items.size.toLong(), session.items_added)
        val answers = device.database.questionQueries.selectAnswers("id-2").executeAsList()
        assertTrue(answers.count { it.subject_kind == "meta" } == 3)
        assertTrue(answers.any { it.answer == "no" } || answers.any { it.answer == "have_enough" })
        val snoozed = answers.firstOrNull { it.answer == "have_enough" }
        if (snoozed != null) {
            val pref = device.database.householdQueries.selectPref(snoozed.subject_id).executeAsOne()
            assertNotNull(pref.snooze_until)
        }

        // The next session does not ask again about the snoozed products.
        device.clearViewModels()
        val next = device.newViewModel(seed = 2027)
        advanceUntilIdle()
        assertIs<QuestionUiState.Meta>(next.state.value)
        device.clearViewModels()
    }

    private suspend fun TestDevice.assertValid(productId: String, choice: QuantityChoice) {
        val product = product(productId)
        val quantity = Quantity(choice.value.milli, UnitCode(choice.value.unit))
        val policy = assertNotNull(product.policyFor(quantity.unit), "${product.slug}: no policy for ${quantity.unit}")
        assertTrue(policy.isAllowed(quantity), "${product.slug}: $quantity not allowed")
        assertTrue(choice.label.isNotBlank())
    }
}

private fun QuestionUiState.progressOrNull(): QuestionProgress? = when (this) {
    is QuestionUiState.Meta -> progress
    is QuestionUiState.Suggested -> progress
    is QuestionUiState.Card -> progress
    is QuestionUiState.Gate -> progress
    is QuestionUiState.GateItems -> progress
    is QuestionUiState.Review -> progress
    else -> null
}
