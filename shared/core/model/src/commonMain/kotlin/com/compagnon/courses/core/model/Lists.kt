package com.compagnon.courses.core.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline
value class ListItemId(val value: String)

/** Lifecycle of a shopping list (`shopping_list.status`). */
enum class ListStatus(val wireName: String) {
    ACTIVE("active"),
    COMPLETED("completed"),
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromWireName(value: String): ListStatus? = entries.firstOrNull { it.wireName == value }
    }
}

/** How an item reached the list (`list_item.added_via`). */
enum class AddedVia(val wireName: String) {
    QUESTION_FLOW("question_flow"),
    MANUAL("manual"),
    ;

    companion object {
        fun fromWireName(value: String): AddedVia? = entries.firstOrNull { it.wireName == value }
    }
}

/**
 * One line of a shopping list: a catalog product with a quantity in one of its policy units, or a free text
 * (« Autre chose ? »), whose quantity is optional.
 */
data class ListItem(
    val id: ListItemId,
    val listId: ListId,
    val productId: ProductId?,
    val freeText: String?,
    val quantity: Quantity?,
    val position: Long,
    val addedVia: AddedVia,
    val addedAt: Instant,
    val checkedAt: Instant? = null,
) {
    init {
        require((productId == null) != (freeText == null)) { "A list item is either a product or a free text" }
        require(productId == null || quantity != null) { "A product item needs a quantity" }
    }

    val isChecked: Boolean get() = checkedAt != null
}

/** A shopping list with its live (not deleted) items, in insertion order. */
data class ShoppingList(
    val id: ListId,
    val status: ListStatus,
    val createdAt: Instant,
    val items: List<ListItem>,
)

/** Where a purchase observation comes from (`purchase_event.source`). */
enum class PurchaseSource(val wireName: String) {
    /** The item was checked, then the list was completed. */
    LIST_CHECKED("list_checked"),

    /** A « yes » never checked: counted with a reduced weight. */
    IMPLICIT("implicit"),
    MANUAL("manual"),
    ;

    companion object {
        fun fromWireName(value: String): PurchaseSource? = entries.firstOrNull { it.wireName == value }
    }
}

/** @property qtyBase [quantity] in the base unit of the product's dimension (g, mL, piece), when convertible. */
data class PurchaseEvent(
    val productId: ProductId,
    val quantity: Quantity,
    val qtyBase: Double?,
    val at: Instant,
    val source: PurchaseSource,
    val listId: ListId? = null,
)
