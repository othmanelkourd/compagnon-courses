package com.compagnon.courses.feature.lists

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.Household
import com.compagnon.courses.core.data.HouseholdRepository
import com.compagnon.courses.core.data.IdGenerator
import com.compagnon.courses.core.data.ShoppingListRepository
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.core.testing.SeedCatalog
import com.compagnon.courses.core.testing.TestClock
import com.compagnon.courses.core.testing.createInMemoryDriver
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {
    private val main = StandardTestDispatcher()
    private val nbsp = ' '

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Ids : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${++next}"
    }

    private inner class Device(scope: TestScope) {
        val context = StandardTestDispatcher(scope.testScheduler)
        val database = createDatabase(createInMemoryDriver())
        val clock = TestClock()
        val catalog = CatalogRepository(database, clock, context)
        val households = HouseholdRepository(database, "FR", clock, context)
        val lists = ShoppingListRepository(database, Ids(), clock, context)
        val store = ViewModelStore()

        fun viewModel(): ShoppingListViewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { ShoppingListViewModel(lists, catalog, households) } },
        )[ShoppingListViewModel::class]
    }

    private fun test(block: suspend TestScope.(Device) -> Unit) = runTest(main) {
        val device = Device(this)
        try {
            device.catalog.installBundled("FR", "fr-FR")
            block(device)
        } finally {
            device.store.clear()
        }
    }

    private fun ShoppingListViewModel.loaded(): ShoppingListUiState.Loaded = assertIs(state.value)

    private fun id(slug: String) = ProductId(SeedCatalog.productId(slug))

    @Test
    fun startsEmptyThenShowsItemsGroupedInStoreOrder() = test { device ->
        val viewModel = device.viewModel()

        viewModel.state.test {
            assertEquals(ShoppingListUiState.Loading, awaitItem())
            val empty = assertIs<ShoppingListUiState.Loaded>(awaitItem())
            assertTrue(empty.isEmpty)

            device.lists.addProduct(id("lait-demi-ecreme"), Quantity.of(2, UnitCode("bottle")))
            device.lists.addProduct(id("pommes"), Quantity(1500, UnitCode.KILOGRAM))
            device.lists.addFreeText("piles AAA")
            var loaded = empty
            while (loaded.itemCount < 3) loaded = assertIs(awaitItem())
            assertEquals(listOf("Fruits et légumes", "Crèmerie", null), loaded.sections.map { it.title })
            assertEquals("Pomme", loaded.sections[0].items.single().name)
            assertEquals("red_apple", loaded.sections[0].items.single().imageKey)
            assertEquals("glass_of_milk", loaded.sections[1].items.single().imageKey)
            assertEquals("1,5${nbsp}kg", loaded.sections[0].items.single().quantityLabel)
            assertEquals("2${nbsp}bouteilles", loaded.sections[1].items.single().quantityLabel)
            val other = loaded.sections[2].items.single()
            assertEquals("piles AAA", other.name)
            assertTrue(other.isFreeText)
            assertNull(other.quantityLabel)
            assertEquals(Illustrations.Screen.FREE_TEXT, other.imageKey)
            assertEquals(3, loaded.remainingCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theHouseholdStoreOrderComesFirst() = test { device ->
        val dairy = CategoryId(SeedCatalog.categoryId("cremerie"))
        device.households.update(Household.default("FR").copy(storeCategoryOrder = listOf(dairy)))
        device.lists.addProduct(id("pommes"), Quantity.of(1, UnitCode.KILOGRAM))
        device.lists.addProduct(id("oeufs"), Quantity.of(6, UnitCode.PIECE))
        val viewModel = device.viewModel()
        advanceUntilIdle()

        val loaded = viewModel.loaded()
        assertEquals(listOf("Crèmerie", "Fruits et légumes"), loaded.sections.map { it.title })
        assertEquals("6${nbsp}œufs", loaded.sections[0].items.single().quantityLabel)
    }

    @Test
    fun checkingMovesTheLineDownAndCanBeUndone() = test { device ->
        val milk = device.lists.addProduct(id("lait-demi-ecreme"), Quantity.of(1, UnitCode("bottle")))
        device.lists.addProduct(id("beurre-doux"), Quantity.of(1, UnitCode("block")))
        val viewModel = device.viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShoppingListEvent.ToggleChecked(milk.value))
        advanceUntilIdle()

        val checked = viewModel.loaded()
        val dairy = checked.sections.single()
        assertEquals(listOf(false, true), dairy.items.map { it.checked })
        assertEquals(milk.value, dairy.items.last().id)
        assertEquals(UndoUi(UndoKind.CHECKED, "Lait demi-écrémé"), checked.undo)
        assertEquals(1, checked.remainingCount)

        viewModel.onEvent(ShoppingListEvent.Undo)
        advanceUntilIdle()
        val undone = viewModel.loaded()
        assertTrue(undone.sections.single().items.none { it.checked })
        assertNull(undone.undo)
    }

    @Test
    fun deleteIsUndoneFromTheSnackbar() = test { device ->
        val milk = device.lists.addProduct(id("lait-demi-ecreme"), Quantity.of(1, UnitCode("bottle")))
        val viewModel = device.viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShoppingListEvent.Delete(milk.value))
        advanceUntilIdle()
        assertTrue(viewModel.loaded().isEmpty)
        assertEquals(UndoKind.DELETED, viewModel.loaded().undo?.kind)

        viewModel.onEvent(ShoppingListEvent.Undo)
        advanceUntilIdle()
        assertEquals(1, viewModel.loaded().itemCount)

        viewModel.onEvent(ShoppingListEvent.Delete(milk.value))
        advanceUntilIdle()
        viewModel.onEvent(ShoppingListEvent.DismissUndo)
        advanceUntilIdle()
        assertNull(viewModel.loaded().undo)
        viewModel.onEvent(ShoppingListEvent.Undo)
        advanceUntilIdle()
        assertTrue(viewModel.loaded().isEmpty)
    }

    @Test
    fun searchThenAddWithTheDefaultUnit() = test { device ->
        val viewModel = device.viewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShoppingListEvent.Search("oeuf"))
        advanceUntilIdle()
        val results = viewModel.loaded().searchResults
        val eggs = results.first { it.productId == id("oeufs").value }
        assertEquals("Œuf", eggs.name)
        assertEquals("6${nbsp}œufs", eggs.quantityLabel)
        assertEquals("egg", eggs.imageKey)

        viewModel.onEvent(ShoppingListEvent.AddProduct(eggs.productId))
        advanceUntilIdle()
        viewModel.onEvent(ShoppingListEvent.AddProduct(eggs.productId))
        advanceUntilIdle()
        val line = viewModel.loaded().sections.single().items.single()
        assertEquals("12${nbsp}œufs", line.quantityLabel)

        viewModel.onEvent(ShoppingListEvent.Search("  "))
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.loaded().searchResults)
    }

    @Test
    fun freeTextAndCompletion() = test { device ->
        val viewModel = device.viewModel()
        advanceUntilIdle()
        viewModel.onEvent(ShoppingListEvent.AddFreeText("  "))
        viewModel.onEvent(ShoppingListEvent.AddFreeText("cadeau pour Léa"))
        val milk = device.lists.addProduct(id("lait-demi-ecreme"), Quantity.of(1, UnitCode("bottle")))
        advanceUntilIdle()
        assertEquals(2, viewModel.loaded().itemCount)

        viewModel.onEvent(ShoppingListEvent.ToggleChecked(milk.value))
        advanceUntilIdle()
        viewModel.onEvent(ShoppingListEvent.CompleteList)
        advanceUntilIdle()

        val next = viewModel.loaded()
        assertEquals(1, next.itemCount)
        assertEquals("cadeau pour Léa", next.sections.single().items.single().name)
        assertEquals(1L, device.database.householdQueries.countPurchases().executeAsOne())
    }
}
