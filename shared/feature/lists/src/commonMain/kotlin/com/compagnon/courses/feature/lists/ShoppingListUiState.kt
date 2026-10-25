package com.compagnon.courses.feature.lists

/** UI state of the shopping list screen (« Liste »). */
sealed interface ShoppingListUiState {

    data object Loading : ShoppingListUiState

    /**
     * @property sections categories in the household's store order, then « Autres » (free texts and products
     *   unknown to the local catalog). Empty sections are left out.
     * @property itemCount live items (checked or not); [remainingCount] the unchecked ones.
     * @property searchResults catalog products matching [searchQuery], added with [ShoppingListEvent.AddProduct].
     * @property undo the last action that [ShoppingListEvent.Undo] reverts (snackbar), or null.
     * @property error the last action that failed, or null; cleared by the next successful action.
     */
    data class Loaded(
        val sections: List<ListSectionUi>,
        val itemCount: Int,
        val remainingCount: Int,
        val searchQuery: String,
        val searchResults: List<ProductSuggestionUi>,
        val undo: UndoUi?,
        val error: ListErrorKind?,
    ) : ShoppingListUiState {
        val isEmpty: Boolean get() = itemCount == 0
    }

    /** The list could not be read (storage failure). [ShoppingListEvent.Retry] reloads it. */
    data class Error(val kind: ListErrorKind) : ShoppingListUiState
}

enum class ListErrorKind {
    /** The local database could not be read or written. */
    STORAGE_FAILED,
}

/**
 * A section of the list. [title] is the category name, or null for the « Autres » section (free texts, products
 * missing from the local catalog): the platform shows its own « Autres » / « Other » label.
 */
data class ListSectionUi(
    val categoryId: String?,
    val title: String?,
    val items: List<ListItemUi>,
)

/**
 * One line: unchecked lines first in each section, then the checked ones.
 *
 * @property quantityLabel « 2 bouteilles », « 1,5 kg »; null for a free text without quantity.
 */
data class ListItemUi(
    val id: String,
    val name: String,
    val quantityLabel: String?,
    val checked: Boolean,
    val productId: String?,
    val isFreeText: Boolean,
)

/** A catalog product found by the search, with the quantity it is added with (its default unit policy). */
data class ProductSuggestionUi(
    val productId: String,
    val name: String,
    val quantityLabel: String,
)

enum class UndoKind { DELETED, CHECKED, UNCHECKED }

/** What « Annuler » would revert: the snackbar shows [kind] and [itemName]. */
data class UndoUi(val kind: UndoKind, val itemName: String)

sealed interface ShoppingListEvent {
    data class ToggleChecked(val itemId: String) : ShoppingListEvent

    /** Soft delete; [Undo] restores the line. */
    data class Delete(val itemId: String) : ShoppingListEvent

    /** Reverts the last delete or check ([ShoppingListUiState.Loaded.undo]). */
    data object Undo : ShoppingListEvent

    /** The snackbar was dismissed: the last action can no longer be undone. */
    data object DismissUndo : ShoppingListEvent

    /** Searches the local catalog (accents and case ignored); a blank query clears the results. */
    data class Search(val query: String) : ShoppingListEvent

    /** Adds a product with its default quantity; merged into its line when it is already on the list. */
    data class AddProduct(val productId: String) : ShoppingListEvent

    /** « Autre chose ? »: a free-text line. Blank texts are ignored. */
    data class AddFreeText(val text: String) : ShoppingListEvent

    /** « Courses terminées »: checked products become purchases; unchecked lines move to the new list. */
    data object CompleteList : ShoppingListEvent

    data object Retry : ShoppingListEvent
}
