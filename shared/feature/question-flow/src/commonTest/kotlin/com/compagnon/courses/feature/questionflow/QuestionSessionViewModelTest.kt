package com.compagnon.courses.feature.questionflow

import app.cash.turbine.test
import com.compagnon.courses.core.data.BundledCatalogSource
import com.compagnon.courses.core.data.HouseholdSetup
import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.core.testing.SeedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionSessionViewModelTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(
        bundled: BundledCatalogSource = BundledCatalogSource.App,
        block: suspend TestScope.(TestDevice) -> Unit,
    ) = runTest(main) {
        val device = TestDevice(StandardTestDispatcher(testScheduler), bundled)
        try {
            block(device)
        } finally {
            device.clearViewModels()
        }
    }

    @Test
    fun startsWithTheMetaQuestionsPrefilledFromTheHousehold() = test { device ->
        device.installSeed()
        val viewModel = device.newViewModel()

        viewModel.state.test {
            assertEquals(QuestionUiState.Loading, awaitItem())
            val meta = assertIs<QuestionUiState.Meta>(awaitItem())
            assertEquals(MetaQuestionKind.HORIZON_DAYS, meta.question.kind)
            assertEquals(7, meta.question.value)
            assertEquals(listOf(3, 7, 14), meta.question.choices)
            assertEquals(0, meta.progress.answered)
            assertTrue(meta.progress.total in 4..12, "total ${meta.progress.total}")
            assertFalse(meta.canUndo)

            viewModel.onEvent(QuestionEvent.AnswerMeta(14))
            val persons = assertIs<QuestionUiState.Meta>(awaitItem())
            assertEquals(MetaQuestionKind.PERSONS, persons.question.kind)
            assertEquals(2, persons.question.value)
            assertEquals(1, persons.progress.answered)
            assertTrue(persons.canUndo)
        }
    }

    @Test
    fun theFirstSessionStartsFromTheBundledCatalog() = test { device ->
        val viewModel = device.newViewModel()

        assertIs<QuestionUiState.Meta>(viewModel.awaitSettled())
    }

    @Test
    fun noCatalogAtAllIsARetryableError() = test(bundled = NoBundledCatalog) { device ->
        val viewModel = device.newViewModel()

        assertEquals(QuestionUiState.Error(QuestionErrorKind.NO_CATALOG, retryable = true), viewModel.awaitSettled())

        device.installSeed()
        viewModel.onEvent(QuestionEvent.Retry)
        assertIs<QuestionUiState.Meta>(viewModel.awaitSettled())
    }

    @Test
    fun theSuggestedScreenTogglesAndStepsItsLines() = test { device ->
        device.installSeed()
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas()

        val suggested = assertIs<QuestionUiState.Suggested>(viewModel.state.value)
        assertTrue(suggested.items.isNotEmpty())
        assertEquals(suggested.items.count { it.checked }, suggested.checkedCount)
        val first = suggested.items.first()
        val label = first.quantity.selected.label
        assertTrue(label.isNotBlank() && label.contains(Typography.nbsp), label)

        viewModel.onEvent(QuestionEvent.ToggleItem(first.productId))
        val toggled = assertIs<QuestionUiState.Suggested>(viewModel.state.value)
        assertEquals(!first.checked, toggled.items.first().checked)

        viewModel.onEvent(QuestionEvent.StepItem(first.productId, increment = true))
        val stepped = assertIs<QuestionUiState.Suggested>(viewModel.state.value).items.first()
        assertTrue(stepped.checked)
        assertNotEquals(first.quantity.selected, stepped.quantity.selected)

        viewModel.onEvent(QuestionEvent.ConfirmItems)
        advanceUntilIdle()
        val next = viewModel.state.value
        assertTrue(next is QuestionUiState.Card || next is QuestionUiState.Gate || next is QuestionUiState.Review, "$next")
    }

    @Test
    fun aCardOffersPresetsAStepperAndOtherUnits() = test { device ->
        device.installSeed()
        device.watch("pommes", "lait-demi-ecreme")
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas()
        viewModel.onEvent(QuestionEvent.ConfirmItems)
        advanceUntilIdle()
        val card = viewModel.skipToCard { it.units.size > 1 }

        val product = device.product(card.productId)
        assertEquals(product.name.text.replaceFirstChar { it.uppercase() }, card.name)
        assertTrue(card.needPhrase.isNotBlank())
        assertTrue(card.presets.isNotEmpty())
        card.presets.forEach { preset ->
            val quantity = Quantity(preset.value.milli, UnitCode(preset.value.unit))
            assertTrue(assertNotNull(product.policyFor(quantity.unit)).isAllowed(quantity), "$quantity for ${product.slug}")
        }
        assertEquals(1, card.units.count { it.selected })
        // « Au poids », « À la pièce », « En packs »: the way of buying, never the product name.
        card.units.forEach { option ->
            assertTrue(option.label.startsWith("Au ") || option.label.startsWith("À ") || option.label.startsWith("En "), option.label)
        }
        assertEquals(card.units.size, card.units.map { it.label }.toSet().size)

        viewModel.onEvent(QuestionEvent.StepQuantity(increment = true))
        val stepped = assertIs<QuestionUiState.Card>(viewModel.state.value).card
        if (card.quantity.canIncrement) assertNotEquals(card.quantity.selected, stepped.quantity.selected)

        val other = card.units.first { !it.selected }
        viewModel.onEvent(QuestionEvent.SelectUnit(other.unit))
        val switched = assertIs<QuestionUiState.Card>(viewModel.state.value).card
        assertEquals(other.unit, switched.quantity.selected.value.unit)
        assertTrue(switched.units.first { it.unit == other.unit }.selected)
        assertTrue(switched.presets.all { it.value.unit == other.unit })

        viewModel.onEvent(QuestionEvent.Yes())
        advanceUntilIdle()
        viewModel.onEvent(QuestionEvent.EndQuestions)
        advanceUntilIdle()
        val review = assertIs<QuestionUiState.Review>(viewModel.state.value)
        val line = review.items.first { it.productId == card.productId }
        assertEquals(switched.quantity.selected, line.quantity)
        assertEquals(ItemOrigin.CARD, line.origin)
    }

    @Test
    fun linesAndCardsCarryTheirIllustrationAndTheNextOne() = test { device ->
        device.installSeed()
        device.watch("pommes", "lait-demi-ecreme")
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas()

        val suggested = assertIs<QuestionUiState.Suggested>(viewModel.state.value)
        suggested.items.forEach { assertTrue(it.imageKey in Illustrations.ALL, it.imageKey) }
        viewModel.onEvent(QuestionEvent.ConfirmItems)
        advanceUntilIdle()

        // Each card shows its product; the card behind shows the next question (a product or an aisle).
        // A plain loop: Kotlin/Native misreads variables captured by `repeat` across suspension points.
        val cardImages = mutableMapOf<String, String>()
        var previous: QuestionUiState.Card? = null
        var steps = 0
        while (steps++ < 30) {
            val current = viewModel.state.value
            val shown: String? = when (current) {
                is QuestionUiState.Card -> current.card.imageKey
                is QuestionUiState.Gate -> current.gate.imageKey
                else -> null
            }
            previous?.let { assertEquals(it.nextImageKey, shown, "after ${it.card.name}: $current") }
            previous = current as? QuestionUiState.Card
            when (current) {
                is QuestionUiState.Card -> {
                    cardImages[current.card.productId] = current.card.imageKey
                    viewModel.onEvent(QuestionEvent.No)
                }
                is QuestionUiState.Gate -> viewModel.onEvent(QuestionEvent.AnswerGate(yes = false))
                else -> break
            }
            advanceUntilIdle()
        }
        assertEquals("glass_of_milk", cardImages[SeedCatalog.productId("lait-demi-ecreme")])
        assertEquals("red_apple", cardImages[SeedCatalog.productId("pommes")])
    }

    @Test
    fun undoGoesBackToThePreviousQuestion() = test { device ->
        device.installSeed()
        device.watch("pommes", "lait-demi-ecreme")
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas()
        viewModel.onEvent(QuestionEvent.ConfirmItems)
        advanceUntilIdle()
        val card = viewModel.skipToCard { true }
        val before = viewModel.state.value

        viewModel.haptics.test {
            viewModel.onEvent(QuestionEvent.No)
            assertEquals(QuestionHaptic.ANSWERED, awaitItem())
            assertNotEquals(card.productId, (viewModel.state.value as? QuestionUiState.Card)?.card?.productId)
            viewModel.onEvent(QuestionEvent.Undo)
            assertEquals(QuestionHaptic.UNDONE, awaitItem())
        }
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun aSessionIsResumedAfterProcessDeath() = test { device ->
        device.installSeed()
        device.watch("pommes", "lait-demi-ecreme", "yaourts-nature")
        val first = device.newViewModel()
        advanceUntilIdle()
        first.answerMetas(values = listOf(10, 3, 1))
        first.onEvent(QuestionEvent.ConfirmItems)
        advanceUntilIdle()
        first.skipToCard { true }
        first.onEvent(QuestionEvent.Yes())
        advanceUntilIdle()
        val before = first.state.value

        device.clearViewModels()
        val second = device.newViewModel(seed = 99)
        advanceUntilIdle()

        assertEquals(before, second.state.value)
    }

    @Test
    fun aLanguageChangeKeepsTheSessionAndShowsTheNewLanguage() = test { device ->
        device.installSeed()
        device.watch("pommes", "lait-demi-ecreme", "yaourts-nature")
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas(values = listOf(10, 3, 1))
        viewModel.onEvent(QuestionEvent.ConfirmItems)
        advanceUntilIdle()
        viewModel.skipToCard { true }
        viewModel.onEvent(QuestionEvent.Yes())
        advanceUntilIdle()
        val french = assertIs<QuestionUiState.Card>(viewModel.state.value)

        device.catalog.installBundled("FR", "en")
        advanceUntilIdle()

        val english = assertIs<QuestionUiState.Card>(viewModel.state.value)
        assertEquals(french.progress, english.progress)
        assertEquals(french.card.productId, english.card.productId)
        assertNotEquals(french.card.name, english.card.name)
        assertEquals("en", assertNotNull(device.catalog.catalog()).locale)
    }

    @Test
    fun abandoningAddsNothingAndTheNextSessionStartsAfresh() = test { device ->
        device.installSeed()
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas()

        viewModel.onEvent(QuestionEvent.Abandon)
        advanceUntilIdle()

        assertEquals(QuestionUiState.Done(itemsAdded = 0, abandoned = true), viewModel.state.value)
        assertEquals(emptyList(), device.lists.activeList().items)
        val next = device.newViewModel()
        advanceUntilIdle()
        assertEquals(0, assertIs<QuestionUiState.Meta>(next.state.value).progress.answered)
    }

    @Test
    fun savingMonFoyerStartsTheNextSessionAfreshForTheNewHousehold() = test { device ->
        device.installSeed()
        val first = device.newViewModel()
        advanceUntilIdle()
        first.answerMetas(values = listOf(10, 3, 1))
        device.clearViewModels()

        device.households.saveSetup(HouseholdSetup(adults = 3, children = listOf(AgeBand.BABY), usualHorizonDays = 14))
        val next = device.newViewModel()
        advanceUntilIdle()

        // Not resumed (the open session was closed), and pre-filled from the new household.
        val meta = assertIs<QuestionUiState.Meta>(next.state.value)
        assertEquals(0, meta.progress.answered)
        assertEquals(14, meta.question.value)
        next.onEvent(QuestionEvent.AnswerMeta(14))
        advanceUntilIdle()
        assertEquals(4, assertIs<QuestionUiState.Meta>(next.state.value).question.value)
    }

    @Test
    fun startOverAbandonsTheCurrentSession() = test { device ->
        device.installSeed()
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(QuestionEvent.AnswerMeta(7))
        advanceUntilIdle()

        viewModel.onEvent(QuestionEvent.StartOver)
        advanceUntilIdle()

        val meta = assertIs<QuestionUiState.Meta>(viewModel.state.value)
        assertEquals(MetaQuestionKind.HORIZON_DAYS, meta.question.kind)
        assertEquals(0, meta.progress.answered)
        // id-1 is the list, id-2 the first session: abandoned with its one answer.
        assertNotNull(device.database.questionQueries.selectSession("id-2").executeAsOne().abandoned_at)
        assertEquals(1, device.database.questionQueries.selectAnswers("id-2").executeAsList().size)
    }

    @Test
    fun theReviewSearchesAndAddsOtherProducts() = test { device ->
        device.installSeed()
        val viewModel = device.newViewModel()
        advanceUntilIdle()
        viewModel.answerMetas()
        viewModel.onEvent(QuestionEvent.EndQuestions)
        advanceUntilIdle()
        assertIs<QuestionUiState.Review>(viewModel.state.value)

        viewModel.onEvent(QuestionEvent.Search("sel"))
        advanceUntilIdle()
        val results = assertIs<QuestionUiState.Review>(viewModel.state.value).searchResults
        assertTrue(results.isNotEmpty())
        val salt = results.first()
        viewModel.onEvent(QuestionEvent.AddOther(salt.productId))
        advanceUntilIdle()

        val review = assertIs<QuestionUiState.Review>(viewModel.state.value)
        val line = review.items.single { it.productId == salt.productId }
        assertEquals(ItemOrigin.OTHER, line.origin)
        assertEquals(salt.quantity, line.quantity)

        viewModel.onEvent(QuestionEvent.Confirm)
        advanceUntilIdle()
        assertEquals(QuestionUiState.Done(itemsAdded = 1, abandoned = false), viewModel.state.value)
        val item = device.lists.activeList().items.single()
        assertEquals(salt.productId, item.productId?.value)
        assertEquals(AddedVia.MANUAL, item.addedVia)
    }
}

/** Answers every meta question with [values] (or the pre-filled value). */
internal fun QuestionSessionViewModel.answerMetas(values: List<Int> = emptyList()) {
    var index = 0
    while (true) {
        val meta = state.value as? QuestionUiState.Meta ?: return
        onEvent(QuestionEvent.AnswerMeta(values.getOrNull(index++) ?: meta.question.value))
    }
}

/** Answers gates no and cards no until a card matching [predicate] shows up. */
internal fun QuestionSessionViewModel.skipToCard(predicate: (ProductCardUi) -> Boolean): ProductCardUi {
    repeat(50) {
        when (val current = state.value) {
            is QuestionUiState.Card -> if (predicate(current.card)) return current.card else onEvent(QuestionEvent.No)
            is QuestionUiState.Gate -> onEvent(QuestionEvent.AnswerGate(yes = false))
            is QuestionUiState.GateItems -> onEvent(QuestionEvent.ConfirmItems)
            is QuestionUiState.Suggested -> onEvent(QuestionEvent.ConfirmItems)
            else -> error("No matching card before $current")
        }
    }
    error("No matching card")
}
