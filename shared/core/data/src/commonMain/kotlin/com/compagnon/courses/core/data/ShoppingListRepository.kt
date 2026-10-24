package com.compagnon.courses.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.core.model.ListItemId
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.PurchaseEvent
import com.compagnon.courses.core.model.PurchaseSource
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.ShoppingList
import com.compagnon.courses.engine.Predictor
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The household's active shopping list, offline only in V0. There is always exactly one active list: it is
 * created on first use and replaced when the list is completed.
 *
 * Every function runs one transaction. Database failures are rethrown as is: callers (ViewModels) turn them
 * into UI state.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class ShoppingListRepository(
    private val database: CompagnonDatabase,
    private val ids: IdGenerator = UuidV7Generator,
    private val clock: Clock = Clock.System,
    private val context: CoroutineContext = Dispatchers.Default,
    private val predictor: Predictor = Predictor(),
) {
    private val lists = ListStore(database, ids)
    private val catalog = CatalogStore(database)
    private val stats = StatsStore(database, ids)
    private val creation = Mutex()

    /** The active list and its live items, updated on every change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveList(): Flow<ShoppingList> = database.shoppingListQueries.selectActiveList().asFlow()
        .mapToOneOrNull(context)
        .flatMapLatest { list ->
            if (list == null) {
                // Creating the list makes this query emit again, with the new list.
                flow<ShoppingList> { activeListId() }
            } else {
                database.shoppingListQueries.selectItems(list.id).asFlow().mapToList(context)
                    .map { lists.shoppingList(list) }
            }
        }
        .flowOn(context)

    suspend fun activeList(): ShoppingList = write { now -> lists.shoppingList(lists.ensureActiveList(now)) }

    suspend fun activeListId(): ListId = creation.withLock {
        write { now -> ListId(lists.ensureActiveList(now).id) }
    }

    /**
     * Adds [quantity] of a product to the active list; a product already on the list (unchecked) is merged:
     * same or convertible unit → sum, otherwise the new quantity replaces the old one.
     */
    suspend fun addProduct(productId: ProductId, quantity: Quantity, addedVia: AddedVia = AddedVia.MANUAL): ListItemId =
        write { now ->
            val list = lists.ensureActiveList(now)
            lists.addProduct(list.id, productId, quantity, catalog.product(productId), catalog.units(), addedVia, now)
        }

    /** « Autre chose ? »: a free-text line. Blank texts are ignored (null). */
    suspend fun addFreeText(text: String): ListItemId? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return write { now -> lists.addFreeText(lists.ensureActiveList(now).id, trimmed, now) }
    }

    suspend fun setChecked(itemId: ListItemId, checked: Boolean) = write { now ->
        val product = lists.item(itemId)?.product_id?.let { catalog.product(ProductId(it)) }
        lists.setChecked(itemId, checked, now, product, catalog.units())
    }

    /** Soft delete: [restore] undoes it. */
    suspend fun delete(itemId: ListItemId) = write { now -> lists.delete(itemId, now) }

    suspend fun restore(itemId: ListItemId) = write { now ->
        val product = lists.item(itemId)?.product_id?.let { catalog.product(ProductId(it)) }
        lists.restore(itemId, product, catalog.units(), now)
    }

    /**
     * « Courses terminées »: every checked product becomes a confirmed purchase (`purchase_event`, statistics
     * updated), the list is completed and a new active list takes the unchecked items. Returns the new list.
     */
    suspend fun completeList(): ListId = write { now ->
        val list = lists.ensureActiveList(now)
        val units = catalog.units()
        val at = Instant.fromEpochMilliseconds(now)
        val items = lists.items(list.id)
        items.filter { it.isChecked }.forEach { item ->
            val productId = item.productId ?: return@forEach
            val quantity = item.quantity ?: return@forEach
            val product = catalog.product(productId)
            val purchase = PurchaseEvent(
                productId = productId,
                quantity = quantity,
                qtyBase = product?.let { predictor.baseQuantity(it, quantity, units) },
                at = item.checkedAt ?: at,
                source = PurchaseSource.LIST_CHECKED,
                listId = ListId(list.id),
            )
            stats.recordPurchase(purchase)
            if (product != null) {
                stats.put(productId, predictor.onPurchase(stats.get(productId), product, quantity, purchase.at, units))
            }
        }
        lists.complete(list.id, now)
        val next = lists.ensureActiveList(now)
        items.filterNot { it.isChecked }.forEach { lists.moveTo(it, next.id) }
        ListId(next.id)
    }

    private suspend fun <T> write(block: (now: Long) -> T): T = withContext(context) {
        val now = clock.now().toEpochMilliseconds()
        database.transactionWithResult { block(now) }
    }
}
