package com.compagnon.courses.core.data

import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.List_item
import com.compagnon.courses.core.database.Shopping_list
import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.core.model.ListItem
import com.compagnon.courses.core.model.ListItemId
import com.compagnon.courses.core.model.ListStatus
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.ShoppingList
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitCode
import kotlin.time.Instant

/**
 * Blocking access to `shopping_list` and `list_item`, with the merge rules of the list. Run writes inside a
 * transaction (they read before writing).
 *
 * Merge: adding a product that already has a live, unchecked line sums the quantities when the units are the
 * same or convertible (through the product's packages: 1 bottle = 1 L), and otherwise replaces the line's
 * quantity with the new one (the last answer wins, as in the session drafts).
 */
internal class ListStore(private val database: CompagnonDatabase, private val ids: IdGenerator) {
    private val queries get() = database.shoppingListQueries

    fun activeList(): Shopping_list? = queries.selectActiveList().executeAsOneOrNull()

    /** The active list, created when there is none. */
    fun ensureActiveList(now: Long): Shopping_list {
        activeList()?.let { return it }
        val id = ids.newId()
        queries.insertList(id = id, name = null, createdAt = now)
        return checkNotNull(queries.selectList(id).executeAsOneOrNull())
    }

    fun shoppingList(list: Shopping_list): ShoppingList = ShoppingList(
        id = ListId(list.id),
        status = ListStatus.fromWireName(list.status) ?: ListStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(list.created_at),
        items = items(list.id),
    )

    fun items(listId: String): List<ListItem> = queries.selectItems(listId).executeAsList().mapNotNull { it.toDomain() }

    fun item(id: ListItemId): List_item? = queries.selectItem(id.value).executeAsOneOrNull()

    /** Adds [quantity] of [productId] to [listId], merged into its open line if any; returns the line. */
    fun addProduct(
        listId: String,
        productId: ProductId,
        quantity: Quantity,
        product: Product?,
        units: UnitCatalog,
        addedVia: AddedVia,
        now: Long,
    ): ListItemId {
        val existing = queries.selectOpenProductItem(listId, productId.value).executeAsOneOrNull()
        if (existing == null) {
            val id = ids.newId()
            queries.insertItem(
                id = id,
                listId = listId,
                productId = productId.value,
                freeText = null,
                quantityMilli = quantity.milli,
                unit = quantity.unit.value,
                position = queries.nextPosition(listId).executeAsOne(),
                addedVia = addedVia.wireName,
                addedAt = now,
            )
            return ListItemId(id)
        }
        val merged = merge(existing.quantity(), quantity, product, units)
        queries.updateQuantity(merged.milli, merged.unit.value, existing.id)
        return ListItemId(existing.id)
    }

    fun addFreeText(listId: String, text: String, now: Long): ListItemId {
        val id = ids.newId()
        queries.insertItem(
            id = id,
            listId = listId,
            productId = null,
            freeText = text,
            quantityMilli = null,
            unit = null,
            position = queries.nextPosition(listId).executeAsOne(),
            addedVia = AddedVia.MANUAL.wireName,
            addedAt = now,
        )
        return ListItemId(id)
    }

    /**
     * Checks or unchecks a line. Unchecking a product that meanwhile got another open line merges this one into
     * it (one open line per product).
     */
    fun setChecked(id: ListItemId, checked: Boolean, now: Long, product: Product?, units: UnitCatalog) {
        val item = item(id) ?: return
        if (checked) {
            if (item.checked_at == null) queries.setChecked(now, item.id)
            return
        }
        if (item.checked_at == null) return
        if (!mergeIntoOpenLine(item, product, units, now)) queries.setChecked(null, item.id)
    }

    fun delete(id: ListItemId, now: Long) {
        val item = item(id) ?: return
        if (item.deleted_at == null) queries.softDelete(now, item.id)
    }

    /** Undoes [delete]; a product that meanwhile got another open line is merged into it. */
    fun restore(id: ListItemId, product: Product?, units: UnitCatalog, now: Long) {
        val item = item(id) ?: return
        if (item.deleted_at == null) return
        if (item.checked_at != null || !mergeIntoOpenLine(item, product, units, now, alreadyDeleted = true)) {
            queries.restore(item.id)
        }
    }

    fun complete(listId: String, now: Long) = queries.completeList(now, listId)

    fun moveTo(item: ListItem, listId: String) =
        queries.moveItem(listId, queries.nextPosition(listId).executeAsOne(), item.id.value)

    /** Product → quantity of the last completed list (« Reprendre ma dernière liste »). */
    fun lastCompletedQuantities(): Map<ProductId, Quantity> {
        val list = queries.selectLastCompletedList().executeAsOneOrNull() ?: return emptyMap()
        return items(list.id).mapNotNull { item ->
            val productId = item.productId ?: return@mapNotNull null
            item.quantity?.let { productId to it }
        }.toMap()
    }

    fun openCount(listId: String): Long = queries.countOpenItems(listId).executeAsOne()

    /** Merges [item] into the other open line of its product, if any; returns whether it did. */
    private fun mergeIntoOpenLine(
        item: List_item,
        product: Product?,
        units: UnitCatalog,
        now: Long,
        alreadyDeleted: Boolean = false,
    ): Boolean {
        val productId = item.product_id ?: return false
        val other = queries.selectOpenProductItem(item.list_id, productId).executeAsOneOrNull() ?: return false
        if (other.id == item.id) return false
        val merged = item.quantity()?.let { merge(other.quantity(), it, product, units) } ?: return false
        queries.updateQuantity(merged.milli, merged.unit.value, other.id)
        if (!alreadyDeleted) queries.softDelete(now, item.id)
        return true
    }

    private fun merge(existing: Quantity?, added: Quantity, product: Product?, units: UnitCatalog): Quantity {
        if (existing == null) return added
        val converted = if (existing.unit == added.unit) {
            added
        } else {
            units.convertOrNull(added, existing.unit, product?.packages.orEmpty())
        } ?: return added
        return try {
            existing + converted
        } catch (_: ArithmeticException) {
            existing
        }
    }
}

private fun List_item.quantity(): Quantity? {
    val milli = quantity_milli ?: return null
    val unit = unit ?: return null
    return if (milli >= 0) Quantity(milli, UnitCode(unit)) else null
}

private fun List_item.toDomain(): ListItem? = try {
    ListItem(
        id = ListItemId(id),
        listId = ListId(list_id),
        productId = product_id?.let(::ProductId),
        freeText = free_text,
        quantity = quantity(),
        position = position,
        addedVia = AddedVia.fromWireName(added_via) ?: AddedVia.MANUAL,
        addedAt = Instant.fromEpochMilliseconds(added_at),
        checkedAt = checked_at?.let(Instant::fromEpochMilliseconds),
    )
} catch (_: IllegalArgumentException) {
    null
}
