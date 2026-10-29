package com.compagnon.courses.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.Household
import com.compagnon.courses.core.data.HouseholdRepository
import com.compagnon.courses.core.data.ShoppingListRepository
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.ListItem
import com.compagnon.courses.core.model.ListItemId
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ShoppingList
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.format.QuantityFormatter
import com.compagnon.courses.core.model.format.capitalizedFirst
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.engine.QuantityAdvisor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The active shopping list: items grouped by category in the household's store order, check / uncheck,
 * delete with undo, add a product by search (with its default unit policy) or a free text (« Autre chose ? »),
 * and complete the list. Offline: everything reads and writes the local database.
 *
 * Failures are values of [state]; this ViewModel never throws.
 */
class ShoppingListViewModel(
    private val lists: ShoppingListRepository,
    private val catalog: CatalogRepository,
    private val households: HouseholdRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ShoppingListUiState>(ShoppingListUiState.Loading)
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    private data class Pending(val kind: UndoKind, val itemId: ListItemId, val name: String)

    private data class Local(
        val query: String = "",
        val results: List<Product> = emptyList(),
        val undo: Pending? = null,
        val error: ListErrorKind? = null,
    )

    private val local = MutableStateFlow(Local())
    private var latest: Snapshot? = null
    private var observeJob: Job? = null
    private var searchJob: Job? = null

    private class Snapshot(val list: ShoppingList, val catalog: Catalog?, val household: Household)

    init {
        observe()
    }

    fun onEvent(event: ShoppingListEvent) {
        when (event) {
            is ShoppingListEvent.ToggleChecked -> toggle(ListItemId(event.itemId))
            is ShoppingListEvent.Delete -> delete(ListItemId(event.itemId))
            ShoppingListEvent.Undo -> undo()
            ShoppingListEvent.DismissUndo -> local.value = local.value.copy(undo = null)
            is ShoppingListEvent.Search -> search(event.query)
            is ShoppingListEvent.AddProduct -> addProduct(ProductId(event.productId))
            is ShoppingListEvent.AddFreeText -> action { lists.addFreeText(event.text) }
            ShoppingListEvent.CompleteList -> action(clearsUndo = true) { lists.completeList() }
            ShoppingListEvent.Retry -> if (_state.value is ShoppingListUiState.Error) observe()
        }
    }

    private fun observe() {
        observeJob?.cancel()
        _state.value = ShoppingListUiState.Loading
        observeJob = viewModelScope.launch {
            combine(lists.observeActiveList(), catalog.observeCatalog(), households.observe(), local) { list, catalog, household, local ->
                val snapshot = Snapshot(list, catalog, household)
                latest = snapshot
                render(snapshot, local) as ShoppingListUiState
            }
                .catch { e ->
                    log.e(e) { "The shopping list could not be read" }
                    emit(ShoppingListUiState.Error(ListErrorKind.STORAGE_FAILED))
                }
                .collect { _state.value = it }
        }
    }

    private fun toggle(id: ListItemId) {
        val item = latest?.list?.items?.firstOrNull { it.id == id } ?: return
        val checked = !item.isChecked
        val kind = if (checked) UndoKind.CHECKED else UndoKind.UNCHECKED
        action(undo = Pending(kind, id, nameOf(item))) { lists.setChecked(id, checked) }
    }

    private fun delete(id: ListItemId) {
        val item = latest?.list?.items?.firstOrNull { it.id == id } ?: return
        action(undo = Pending(UndoKind.DELETED, id, nameOf(item))) { lists.delete(id) }
    }

    private fun undo() {
        val pending = local.value.undo ?: return
        action(clearsUndo = true) {
            when (pending.kind) {
                UndoKind.DELETED -> lists.restore(pending.itemId)
                UndoKind.CHECKED -> lists.setChecked(pending.itemId, checked = false)
                UndoKind.UNCHECKED -> lists.setChecked(pending.itemId, checked = true)
            }
        }
    }

    private fun addProduct(id: ProductId) {
        val product = local.value.results.firstOrNull { it.id == id }
            ?: latest?.catalog?.products?.firstOrNull { it.id == id }
            ?: return
        val units = latest?.catalog?.units ?: UnitCatalog.EMPTY
        val quantity = defaultQuantity(product, units) ?: return
        action { lists.addProduct(id, quantity) }
    }

    private fun search(query: String) {
        local.value = local.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            local.value = local.value.copy(results = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            val results = try {
                catalog.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Search failed" }
                emptyList()
            }
            if (local.value.query == query) local.value = local.value.copy(results = results)
        }
    }

    /** Runs [block]; on success records [undo] (or clears it), on failure shows [ListErrorKind.STORAGE_FAILED]. */
    private fun action(undo: Pending? = null, clearsUndo: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                local.value = local.value.copy(
                    undo = if (undo != null) {
                        undo
                    } else if (clearsUndo) {
                        null
                    } else {
                        local.value.undo
                    },
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Shopping list update failed" }
                local.value = local.value.copy(error = ListErrorKind.STORAGE_FAILED)
            }
        }
    }

    private fun render(snapshot: Snapshot, local: Local): ShoppingListUiState.Loaded {
        val catalog = snapshot.catalog
        val units = catalog?.units ?: UnitCatalog.EMPTY
        val formatter = QuantityFormatter(units, catalog?.locale ?: DEFAULT_LOCALE)
        val products = catalog?.products?.associateBy { it.id }.orEmpty()
        val categories = catalog?.categories.orEmpty()
        val order =
            storeOrder(categories.sortedWith(compareBy({ it.position }, { it.id.value })).map { it.id }, snapshot.household)
        val byCategory = snapshot.list.items.groupBy { item -> item.productId?.let { products[it]?.categoryId } }
        val names = categories.associate { it.id to it.name }
        val slugs = categories.associate { it.id to it.slug }

        fun imageOf(product: Product): String = Illustrations.forProduct(product.slug, slugs[product.categoryId])

        fun line(item: ListItem): ListItemUi {
            val product = item.productId?.let { products[it] }
            return ListItemUi(
                id = item.id.value,
                name = nameOf(item, product),
                quantityLabel = item.quantity?.let { formatter.format(it, product) },
                checked = item.isChecked,
                productId = item.productId?.value,
                isFreeText = item.freeText != null,
                imageKey = if (item.freeText != null || product == null) Illustrations.Screen.FREE_TEXT else imageOf(product),
            )
        }

        fun sorted(items: List<ListItem>): List<ListItemUi> =
            items.sortedWith(compareBy<ListItem>({ it.isChecked }, { it.position })).map(::line)

        val sections = order.mapNotNull { id ->
            byCategory[id]?.let { ListSectionUi(categoryId = id.value, title = names[id], items = sorted(it)) }
        } + listOfNotNull(byCategory[null]?.let { ListSectionUi(categoryId = null, title = null, items = sorted(it)) })

        return ShoppingListUiState.Loaded(
            sections = sections,
            itemCount = snapshot.list.items.size,
            remainingCount = snapshot.list.items.count { !it.isChecked },
            searchQuery = local.query,
            searchResults = local.results.mapNotNull { product ->
                defaultQuantity(product, units)?.let { quantity ->
                    ProductSuggestionUi(product.id.value, product.name.text.capitalizedFirst(), formatter.format(quantity, product), imageOf(product))
                }
            },
            undo = local.undo?.let { UndoUi(it.kind, it.name) },
            error = local.error,
        )
    }

    private fun nameOf(
        item: ListItem,
        product: Product? = item.productId?.let { id ->
            latest?.catalog?.products?.firstOrNull {
                it.id == id
            }
        },
    ): String =
        item.freeText ?: product?.name?.text?.capitalizedFirst() ?: item.productId?.value.orEmpty()

    private companion object {
        val log = Logger.withTag("ShoppingListViewModel")
        const val DEFAULT_LOCALE = "fr-FR"

        /** The household's store order first, then the other categories by catalog position. */
        fun storeOrder(byPosition: List<CategoryId>, household: Household): List<CategoryId> {
            val known = byPosition.toSet()
            val store = household.storeCategoryOrder.filter { it in known }.distinct()
            return store + byPosition.filterNot { it in store.toSet() }
        }

        /** The default unit policy's first preset (or its step). */
        fun defaultQuantity(
            product: Product,
            units: UnitCatalog,
        ) = QuantityAdvisor(units).propose(product, null)?.quantity
    }
}
