package com.compagnon.courses.feature.onboarding

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.compagnon.courses.contract.CatalogSnapshotDto
import com.compagnon.courses.core.data.BundledCatalogSource
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.HouseholdRepository
import com.compagnon.courses.core.data.HouseholdSetup
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Avoidance
import com.compagnon.courses.core.model.Diet
import com.compagnon.courses.core.model.PetKind
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.core.testing.MockCatalogServer
import com.compagnon.courses.core.testing.SeedCatalog
import com.compagnon.courses.core.testing.TestClock
import com.compagnon.courses.core.testing.createInMemoryDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private inner class Device(scope: TestScope, bundled: BundledCatalogSource = BundledCatalogSource.App) {
        val context = StandardTestDispatcher(scope.testScheduler)
        val database = createDatabase(createInMemoryDriver())
        val server = MockCatalogServer()
        val clock = TestClock()
        val catalog = CatalogRepository(database, server.api, clock, context, bundled)
        val households = HouseholdRepository(database, "FR", clock, context)
        val store = ViewModelStore()

        fun onboarding(mode: OnboardingMode = OnboardingMode.FIRST_RUN): OnboardingViewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { OnboardingViewModel(mode, households, catalog, CatalogScope.DEFAULT) } },
        )["onboarding-$mode", OnboardingViewModel::class]

        fun gate(): AppGateViewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { AppGateViewModel(households, catalog) } },
        )[AppGateViewModel::class]
    }

    private fun test(
        sync: Boolean = true,
        bundled: BundledCatalogSource = BundledCatalogSource.App,
        block: suspend TestScope.(Device) -> Unit,
    ) = runTest(main) {
        val device = Device(this, bundled)
        try {
            if (sync) device.catalog.sync("FR", "fr-FR")
            block(device)
        } finally {
            device.store.clear()
        }
    }

    private fun OnboardingViewModel.ready(): OnboardingUiState.Ready = assertIs(state.value)

    private fun TestScope.send(viewModel: OnboardingViewModel, vararg events: OnboardingEvent) {
        events.forEach {
            viewModel.onEvent(it)
            advanceUntilIdle()
        }
    }

    private fun id(slug: String) = ProductId(SeedCatalog.productId(slug))

    @Test
    fun theFirstRunWalksThroughTheFourStepsAndSavesOnce() = test { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()
        assertEquals(OnboardingStep.WELCOME, viewModel.ready().step)
        assertNull(viewModel.ready().stepNumber)

        send(viewModel, OnboardingEvent.Start)
        assertEquals(OnboardingStep.HOUSEHOLD, viewModel.ready().step)
        assertEquals(1, viewModel.ready().stepNumber)
        assertFalse(viewModel.ready().canSkip)
        send(
            viewModel,
            OnboardingEvent.StepMember(MemberKind.BABIES, increment = true),
            OnboardingEvent.StepMember(MemberKind.CHILDREN, increment = true),
            OnboardingEvent.TogglePet(PetChoice.CAT),
        )
        assertEquals(4, viewModel.ready().household.persons)

        send(viewModel, OnboardingEvent.Next, OnboardingEvent.SelectDiet(DietChoice.HALAL), OnboardingEvent.Next)
        val avoid = viewModel.ready()
        assertEquals(OnboardingStep.AVOID, avoid.step)
        assertEquals(2, avoid.stepNumber)
        assertEquals(AvoidOptionUi(AvoidChoice.ALCOHOL, checked = true, locked = true), avoid.avoid.last())

        send(viewModel, OnboardingEvent.ToggleAvoid(AvoidChoice.LACTOSE), OnboardingEvent.ToggleAvoid(AvoidChoice.ALCOHOL), OnboardingEvent.Next)
        val essentials = viewModel.ready()
        assertEquals(OnboardingStep.ESSENTIALS, essentials.step)
        val chips = essentials.essentials.aisles.flatMap { it.items }
        // Baby and cat starters are pre-checked; nothing with pork, alcohol or lactose is offered.
        assertTrue(chips.first { it.productId == id("couches").value }.checked)
        assertTrue(chips.first { it.productId == id("croquettes-chat").value }.checked)
        assertTrue(chips.none { it.productId == id("jambon-blanc").value })
        assertTrue(chips.none { it.productId == id("croquettes-chien").value && it.checked })
        assertTrue(essentials.essentials.aisles.any { it.title == "Bébé" })
        assertEquals("baby_bottle", essentials.essentials.aisles.first { it.title == "Bébé" }.imageKey)
        assertEquals("baguette_bread", chips.first { it.productId == id("baguette").value }.imageKey)
        chips.forEach { assertTrue(it.imageKey in Illustrations.ALL, it.imageKey) }

        send(viewModel, OnboardingEvent.ToggleEssential(id("baguette").value))
        val withoutBaguette = viewModel.ready().essentials.checkedCount
        send(viewModel, OnboardingEvent.Next)
        val recurring = viewModel.ready()
        assertEquals(OnboardingStep.RECURRING, recurring.step)
        assertEquals(4, recurring.stepNumber)
        assertTrue(recurring.recurring.rows.size in 1..3, "pre-filled: ${recurring.recurring.rows}")
        recurring.recurring.rows.forEach { assertTrue(it.imageKey in Illustrations.ALL, it.imageKey) }

        send(
            viewModel,
            OnboardingEvent.AddRecurring(id("baguette").value),
            OnboardingEvent.SetCadence(id("baguette").value, CadenceChoice.WEEK),
            OnboardingEvent.Next,
            OnboardingEvent.SelectRhythm(RhythmChoice.TWO_WEEKS),
            OnboardingEvent.Next,
        )
        val recap = viewModel.ready()
        assertEquals(OnboardingStep.RECAP, recap.step)
        assertEquals(4, recap.summary.persons)
        assertEquals(DietChoice.HALAL, recap.summary.diet)
        assertEquals(listOf(AvoidChoice.LACTOSE), recap.summary.avoid)
        assertEquals(withoutBaguette, recap.summary.essentials)
        assertEquals(14, recap.summary.horizonDays)

        val saved = device.households.setup()
        assertEquals(listOf(AgeBand.BABY, AgeBand.CHILD), saved.children)
        assertEquals(setOf(PetKind.CAT), saved.pets)
        assertEquals(Diet.HALAL, saved.diet)
        assertEquals(setOf(Avoidance.LACTOSE), saved.avoid)
        assertFalse(id("baguette") in assertNotNull(saved.essentials))
        assertEquals(7, saved.cadences!![id("baguette")])
        assertEquals(14, saved.usualHorizonDays)
        assertTrue(device.households.observeOnboarded().first())

        send(viewModel, OnboardingEvent.Finish(startSession = true))
        assertEquals(OnboardingExit.START_SESSION, viewModel.ready().exit)
        send(viewModel, OnboardingEvent.ExitHandled)
        assertNull(viewModel.ready().exit)
    }

    @Test
    fun everyStepCanBeSkippedAndKeepsTheDefaults() = test { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()
        send(viewModel, OnboardingEvent.Start, OnboardingEvent.Next)
        repeat(5) { send(viewModel, OnboardingEvent.Skip) }

        assertEquals(OnboardingStep.RECAP, viewModel.ready().step)
        val saved = device.households.setup()
        assertEquals(HouseholdSetup(adults = 2, cadences = emptyMap()), saved)
        assertTrue(device.households.observeOnboarded().first())
    }

    @Test
    fun laterKeepsTheDefaultHouseholdAndClosesTheOnboarding() = test { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()

        send(viewModel, OnboardingEvent.Later)

        assertEquals(OnboardingExit.HOME, viewModel.ready().exit)
        assertTrue(device.households.observeOnboarded().first())
        assertNull(device.households.setup().essentials)
    }

    @Test
    fun changingTheDietRefiltersTheEssentials() = test { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()
        send(viewModel, OnboardingEvent.Start, OnboardingEvent.Next, OnboardingEvent.Next, OnboardingEvent.Next)
        val omnivore = viewModel.ready().essentials
        assertTrue(omnivore.aisles.flatMap { it.items }.any { it.productId == id("jambon-blanc").value })

        send(viewModel, OnboardingEvent.Back, OnboardingEvent.Back, OnboardingEvent.SelectDiet(DietChoice.VEGETARIAN), OnboardingEvent.Next, OnboardingEvent.Next)

        val vegetarian = viewModel.ready().essentials
        assertEquals(OnboardingStep.ESSENTIALS, viewModel.ready().step)
        assertTrue(vegetarian.aisles.flatMap { it.items }.none { it.productId == id("jambon-blanc").value })
        assertTrue(vegetarian.checkedCount < omnivore.checkedCount)
    }

    @Test
    fun aislesFoldAndSearchFindsProducts() = test { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()
        send(viewModel, OnboardingEvent.Start, OnboardingEvent.Next, OnboardingEvent.Next, OnboardingEvent.Next)
        val produce = viewModel.ready().essentials.aisles.first()
        assertEquals("Fruits et légumes", produce.title)
        assertTrue(produce.items.size < produce.total)

        send(viewModel, OnboardingEvent.ExpandAisle(produce.categoryId))
        assertEquals(produce.total, viewModel.ready().essentials.aisles.first().items.size)

        send(viewModel, OnboardingEvent.SearchEssentials("pain de mie"))
        val results = viewModel.ready().essentials.results
        assertTrue(results.any { it.productId == id("pain-de-mie").value }, "$results")
        send(viewModel, OnboardingEvent.ToggleEssential(id("pain-de-mie").value))
        assertTrue(viewModel.ready().essentials.results.first { it.productId == id("pain-de-mie").value }.checked)
    }

    @Test
    fun searchNeverOffersAProductTheHouseholdAvoids() = test { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()
        send(viewModel, OnboardingEvent.Start, OnboardingEvent.Next, OnboardingEvent.Next, OnboardingEvent.ToggleAvoid(AvoidChoice.LACTOSE), OnboardingEvent.Next)

        send(viewModel, OnboardingEvent.SearchEssentials("lait"))

        val results = viewModel.ready().essentials.results.map { it.productId }
        assertTrue(id("lait-de-coco").value in results, "$results")
        assertFalse(id("lait-entier").value in results, "$results")
    }

    @Test
    fun monFoyerPreFillsAndSavesOneSectionAtATime() = test { device ->
        device.households.saveSetup(
            HouseholdSetup(adults = 1, diet = Diet.VEGAN, essentials = setOf(id("bananes")), cadences = mapOf(id("baguette") to 14)),
        )
        val viewModel = device.onboarding(OnboardingMode.EDIT)
        advanceUntilIdle()
        val overview = viewModel.ready()
        assertEquals(OnboardingStep.OVERVIEW, overview.step)
        assertEquals(1, overview.summary.persons)
        assertEquals(DietChoice.VEGAN, overview.summary.diet)
        assertEquals(1, overview.summary.essentials)
        assertEquals(1, overview.summary.recurring)

        send(viewModel, OnboardingEvent.Edit(HouseholdSection.HOUSEHOLD))
        assertTrue(viewModel.ready().closeInsteadOfBack)
        assertFalse(viewModel.ready().canSkip)
        send(viewModel, OnboardingEvent.StepMember(MemberKind.ADULTS, increment = true), OnboardingEvent.Next)

        assertEquals(OnboardingStep.OVERVIEW, viewModel.ready().step)
        assertEquals(2, device.households.setup().adults)
        assertEquals(setOf(id("bananes")), device.households.setup().essentials)

        // ✕ closes a section without saving it.
        send(viewModel, OnboardingEvent.Edit(HouseholdSection.FOOD), OnboardingEvent.SelectDiet(DietChoice.OMNIVORE), OnboardingEvent.Back)
        assertEquals(OnboardingStep.OVERVIEW, viewModel.ready().step)
        assertEquals(DietChoice.VEGAN, viewModel.ready().summary.diet)
        assertEquals(Diet.VEGAN, device.households.setup().diet)

        send(viewModel, OnboardingEvent.Edit(HouseholdSection.RHYTHM))
        assertEquals(listOf(CadenceChoice.TWO_WEEKS), viewModel.ready().recurring.rows.map { it.cadence })
        send(viewModel, OnboardingEvent.RemoveRecurring(id("baguette").value), OnboardingEvent.Next, OnboardingEvent.Next)
        assertEquals(OnboardingStep.OVERVIEW, viewModel.ready().step)
        assertEquals(emptyMap(), device.households.setup().cadences)
    }

    @Test
    fun theEssentialsComeFromTheBundledCatalogOffline() = test(sync = false) { device ->
        device.server.offline = true
        val viewModel = device.onboarding()
        advanceUntilIdle()
        send(viewModel, OnboardingEvent.Start, OnboardingEvent.Next, OnboardingEvent.Next, OnboardingEvent.Next)

        val essentials = (viewModel.state.first { (it as? OnboardingUiState.Ready)?.essentials?.checkedCount ?: 0 > 0 } as OnboardingUiState.Ready).essentials
        assertFalse(essentials.catalogMissing)
        assertTrue(essentials.aisles.isNotEmpty())
        assertEquals(emptyList(), device.server.requests)
    }

    @Test
    fun theEssentialsCanBeRetriedWhenNoCatalogCouldBeWritten() = test(sync = false, bundled = NoBundledCatalog) { device ->
        val viewModel = device.onboarding()
        advanceUntilIdle()
        send(viewModel, OnboardingEvent.Start, OnboardingEvent.Next, OnboardingEvent.Next, OnboardingEvent.Next)
        viewModel.state.first { (it as? OnboardingUiState.Ready)?.essentials?.catalogMissing == true }

        device.catalog.sync("FR", "fr-FR")
        send(viewModel, OnboardingEvent.Retry)

        val essentials = (viewModel.state.first { (it as? OnboardingUiState.Ready)?.essentials?.checkedCount ?: 0 > 0 } as OnboardingUiState.Ready).essentials
        assertFalse(essentials.catalogMissing)
        assertFalse(essentials.catalogLoading)
    }

    @Test
    fun theGateShowsTheOnboardingOnceThenTheHouseholdSummary() = test { device ->
        val gate = device.gate()
        gate.state.test {
            assertEquals(AppGateUiState.Loading, awaitItem())
            assertEquals(AppGateUiState.NeedsOnboarding, awaitItem())

            // Saving does not switch by itself: the recap stays until the onboarding closes.
            device.households.saveSetup(HouseholdSetup(adults = 3, diet = Diet.HALAL))
            advanceUntilIdle()
            expectNoEvents()

            gate.onEvent(AppGateEvent.OnboardingClosed)
            var ready = assertIs<AppGateUiState.Ready>(awaitItem())
            while (ready.summary.persons != 3) ready = assertIs(awaitItem())
            assertEquals(DietChoice.HALAL, ready.summary.diet)
            assertTrue(ready.summary.onboarded)
            assertTrue(ready.summary.essentials > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOnboardedHouseholdGoesStraightToTheApp() = test { device ->
        device.households.skipSetup()
        val gate = device.gate()
        advanceUntilIdle()

        assertIs<AppGateUiState.Ready>(gate.state.value)
    }
}

/** An app without a bundled catalog (or a device that cannot store it): only the server can provide one. */
private object NoBundledCatalog : BundledCatalogSource {
    override fun version(country: String, locale: String): Long? = null

    override fun snapshot(country: String, locale: String): CatalogSnapshotDto? = null
}
