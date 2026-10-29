package com.compagnon.courses.core.data

import app.cash.turbine.test
import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.core.model.ListStatus
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ShoppingListRepositoryTest {
    private val bottle = UnitCode("bottle")
    private val apples = seedProductId("pommes")
    private val milk = seedProductId("lait-demi-ecreme")

    @Test
    fun theActiveListIsCreatedOnFirstUseAndKept() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        val first = data.lists.activeList()
        val second = data.lists.activeList()

        assertEquals(first.id, second.id)
        assertEquals(ListStatus.ACTIVE, first.status)
        assertEquals(emptyList(), first.items)
    }

    @Test
    fun theSameProductInTheSameUnitIsSummed() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()

        val first = data.lists.addProduct(milk, Quantity.of(2, bottle))
        val second = data.lists.addProduct(milk, Quantity.of(1, bottle), AddedVia.QUESTION_FLOW)

        assertEquals(first, second)
        val item = data.lists.activeList().items.single()
        assertEquals(Quantity.of(3, bottle), item.quantity)
        assertEquals(AddedVia.MANUAL, item.addedVia)
    }

    @Test
    fun aConvertibleUnitIsConvertedThenSummed() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()

        data.lists.addProduct(milk, Quantity.of(2, bottle))
        data.lists.addProduct(milk, Quantity.of(1, UnitCode.LITER))
        data.lists.addProduct(apples, Quantity.of(1, UnitCode.KILOGRAM))
        data.lists.addProduct(apples, Quantity.of(500, UnitCode.GRAM))

        val items = data.lists.activeList().items.associateBy { it.productId }
        assertEquals(Quantity.of(3, bottle), items[milk]?.quantity)
        assertEquals(Quantity(1500, UnitCode.KILOGRAM), items[apples]?.quantity)
    }

    @Test
    fun anIncompatibleUnitReplacesTheQuantity() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()

        data.lists.addProduct(apples, Quantity.of(1, UnitCode.KILOGRAM))
        data.lists.addProduct(apples, Quantity.of(6, UnitCode.PIECE))

        assertEquals(Quantity.of(6, UnitCode.PIECE), data.lists.activeList().items.single().quantity)
    }

    @Test
    fun aCheckedProductIsNotMergedIntoAgain() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val first = data.lists.addProduct(milk, Quantity.of(1, bottle))
        data.lists.setChecked(first, checked = true)

        val second = data.lists.addProduct(milk, Quantity.of(2, bottle))

        assertNotEquals(first, second)
        assertEquals(2, data.lists.activeList().items.size)

        // Unchecking the first one merges it into the open line.
        data.lists.setChecked(first, checked = false)
        val item = data.lists.activeList().items.single()
        assertEquals(second, item.id)
        assertEquals(Quantity.of(3, bottle), item.quantity)
    }

    @Test
    fun freeTextLinesIgnoreBlankTexts() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        assertNull(data.lists.addFreeText("   "))
        val id = assertNotNull(data.lists.addFreeText("  piles AAA "))

        val item = data.lists.activeList().items.single()
        assertEquals(id, item.id)
        assertEquals("piles AAA", item.freeText)
        assertNull(item.quantity)
    }

    @Test
    fun checkAndUncheck() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val id = data.lists.addProduct(milk, Quantity.of(1, bottle))

        data.lists.setChecked(id, checked = true)
        assertEquals(data.clock.now, data.lists.activeList().items.single().checkedAt)
        data.lists.setChecked(id, checked = false)
        assertNull(data.lists.activeList().items.single().checkedAt)
    }

    @Test
    fun deleteIsSoftAndCanBeUndone() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val id = data.lists.addProduct(milk, Quantity.of(2, bottle))

        data.lists.delete(id)
        assertEquals(emptyList(), data.lists.activeList().items)
        assertNotNull(data.database.shoppingListQueries.selectItem(id.value).executeAsOne().deleted_at)

        data.lists.restore(id)
        assertEquals(Quantity.of(2, bottle), data.lists.activeList().items.single().quantity)
    }

    @Test
    fun undoingADeleteMergesIntoALineAddedMeanwhile() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val deleted = data.lists.addProduct(milk, Quantity.of(2, bottle))
        data.lists.delete(deleted)
        val added = data.lists.addProduct(milk, Quantity.of(1, bottle))

        data.lists.restore(deleted)

        val item = data.lists.activeList().items.single()
        assertEquals(added, item.id)
        assertEquals(Quantity.of(3, bottle), item.quantity)
    }

    @Test
    fun completingTheListRecordsPurchasesAndCarriesUncheckedItemsOver() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val list = data.lists.activeList()
        val milkLine = data.lists.addProduct(milk, Quantity.of(2, bottle))
        data.lists.addProduct(apples, Quantity.of(1, UnitCode.KILOGRAM))
        data.lists.addFreeText("piles")
        data.clock.advance(30.minutes)
        data.lists.setChecked(milkLine, checked = true)

        val next = data.lists.completeList()

        assertNotEquals(list.id, next)
        val completed = assertNotNull(data.database.shoppingListQueries.selectList(list.id.value).executeAsOneOrNull())
        assertEquals("completed", completed.status)
        val active = data.lists.activeList()
        assertEquals(next, active.id)
        assertEquals(setOf(apples, null), active.items.map { it.productId }.toSet())
        val purchase = data.database.householdQueries.selectPurchases(milk.value).executeAsList().single()
        assertEquals(2000L, purchase.quantity_milli)
        assertEquals(2000.0, purchase.qty_base)
        assertEquals("list_checked", purchase.source)
        val stats = data.database.householdQueries.selectStats(milk.value).executeAsOne()
        assertEquals(1L, stats.n_purchases)
        assertEquals(2000.0, stats.ewma_qty_base)

        // A second trip ten days later observes the interval.
        data.clock.advance(10.days)
        data.lists.setChecked(data.lists.addProduct(milk, Quantity.of(1, bottle)), checked = true)
        data.lists.completeList()
        val again = data.database.householdQueries.selectStats(milk.value).executeAsOne()
        assertEquals(2L, again.n_purchases)
        assertEquals(10.0, assertNotNull(again.ewma_interval_days), 1e-6)
    }

    @Test
    fun observeTheActiveList() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()

        data.lists.observeActiveList().test {
            assertEquals(emptyList(), awaitItem().items)
            data.lists.addProduct(milk, Quantity.of(1, bottle))
            assertEquals(1, awaitItem().items.size)
            data.lists.addFreeText("piles")
            assertEquals(2, awaitItem().items.size)
            val next = data.lists.completeList()
            var list = awaitItem()
            while (list.id != next || list.items.size != 2) list = awaitItem()
            assertTrue(list.items.none { it.isChecked })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
