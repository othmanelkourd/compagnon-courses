package com.compagnon.courses.android.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.R
import com.compagnon.courses.android.testing.overline
import com.compagnon.courses.android.testing.plural
import com.compagnon.courses.android.testing.string
import com.compagnon.courses.android.ui.components.SEARCH_FIELD_TAG
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.feature.lists.ListErrorKind
import com.compagnon.courses.feature.lists.ListItemUi
import com.compagnon.courses.feature.lists.ListSectionUi
import com.compagnon.courses.feature.lists.ProductSuggestionUi
import com.compagnon.courses.feature.lists.ShoppingListEvent
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.lists.UndoKind
import com.compagnon.courses.feature.lists.UndoUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The « Ma liste » screen, rendered from fake states. */
@RunWith(AndroidJUnit4::class)
class ShoppingListContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ShoppingListEvent>()
    private var sessionsStarted = 0

    private val milk = ListItemUi("1", "Lait demi-écrémé", "2 bouteilles", checked = false, productId = "lait", isFreeText = false)
    private val butter = ListItemUi("2", "Beurre doux", "250 g", checked = true, productId = "beurre", isFreeText = false)
    private val candles = ListItemUi("3", "Bougies d'anniversaire", null, checked = false, productId = null, isFreeText = true)

    private val loaded = ShoppingListUiState.Loaded(
        sections = listOf(
            ListSectionUi(categoryId = "cremerie", title = "Crèmerie", items = listOf(milk, butter)),
            ListSectionUi(categoryId = null, title = null, items = listOf(candles)),
        ),
        itemCount = 3,
        remainingCount = 2,
        searchQuery = "",
        searchResults = emptyList(),
        undo = null,
        error = null,
    )

    private val empty = loaded.copy(sections = emptyList(), itemCount = 0, remainingCount = 0)

    private fun setState(state: ShoppingListUiState) {
        composeRule.setContent {
            CompagnonTheme {
                ShoppingListContent(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    onStartSession = { sessionsStarted++ },
                )
            }
        }
    }

    @Test
    fun showsSectionsInOrderWithOthersLast() {
        setState(loaded)

        val dairyTitle = composeRule.overline("Crèmerie")
        val othersTitle = composeRule.overline(composeRule.string(R.string.list_section_other))
        composeRule.onNodeWithText(dairyTitle).assertIsDisplayed()
        composeRule.onNodeWithText(othersTitle).assertIsDisplayed()
        composeRule.onNodeWithText("2 bouteilles").assertIsDisplayed()
        composeRule.onNodeWithText("Bougies d'anniversaire").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.list_remaining, 2, 2, 3)).assertIsDisplayed()

        val dairy = composeRule.onNodeWithText(dairyTitle).fetchSemanticsNode().boundsInRoot.top
        val others = composeRule.onNodeWithText(othersTitle).fetchSemanticsNode().boundsInRoot.top
        assertTrue(dairy < others)
    }

    @Test
    fun tappingALineChecksIt() {
        setState(loaded)

        composeRule.onNode(isToggleable() and hasText("Lait demi-écrémé")).assertIsOff().performClick()
        composeRule.onNode(isToggleable() and hasText("Beurre doux")).assertIsOn()

        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.ToggleChecked("1")), events) }
    }

    @Test
    fun swipingALineDeletesIt() {
        setState(loaded)

        composeRule.onNode(isToggleable() and hasText("Lait demi-écrémé")).performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.Delete("1")), events) }
    }

    @Test
    fun talkBackCanDeleteALine() {
        setState(loaded)

        val label = composeRule.string(R.string.list_delete_item)
        val actions = composeRule.onNode(isToggleable() and hasText("Beurre doux"))
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        composeRule.runOnUiThread { actions.single { it.label == label }.action() }

        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.Delete("2")), events) }
    }

    @Test
    fun aDeletionCanBeUndoneFromTheSnackbar() {
        setState(loaded.copy(undo = UndoUi(UndoKind.DELETED, "Lait demi-écrémé")))

        val message = composeRule.string(R.string.list_item_deleted, "Lait demi-écrémé")
        composeRule.waitUntil { composeRule.onAllNodes(hasText(message)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(composeRule.string(R.string.action_undo)).performClick()

        composeRule.waitUntil { events.isNotEmpty() }
        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.Undo), events) }
    }

    @Test
    fun aCheckGetsNoSnackbar() {
        setState(loaded.copy(undo = UndoUi(UndoKind.CHECKED, "Beurre doux")))

        composeRule.onNodeWithText(composeRule.string(R.string.action_undo)).assertDoesNotExist()
    }

    @Test
    fun typingSearchesTheCatalog() {
        setState(loaded)

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("pât")

        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.Search("pât")), events) }
    }

    @Test
    fun aSearchResultIsAddedWithItsDefaultQuantity() {
        setState(
            loaded.copy(
                searchQuery = "pât",
                searchResults = listOf(ProductSuggestionUi(productId = "pates", name = "Pâtes", quantityLabel = "500 g")),
            ),
        )

        composeRule.onNodeWithText("500 g").assertIsDisplayed()
        composeRule.onNodeWithText("Pâtes").performClick()

        composeRule.runOnIdle {
            assertEquals(ShoppingListEvent.AddProduct("pates"), events.first())
            assertEquals(ShoppingListEvent.Search(""), events.last())
        }
    }

    @Test
    fun anythingElseAddsTheTypedTextAsAFreeLine() {
        setState(loaded.copy(searchQuery = "Bougies", searchResults = emptyList()))

        composeRule.onNodeWithText(composeRule.string(R.string.list_add_free_text, "Bougies")).performClick()

        composeRule.runOnIdle { assertEquals(ShoppingListEvent.AddFreeText("Bougies"), events.first()) }
    }

    @Test
    fun aFailedActionIsShownWithoutASnackbar() {
        setState(loaded.copy(error = ListErrorKind.STORAGE_FAILED))

        composeRule.onNodeWithText(composeRule.string(R.string.list_error_action)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.action_undo)).assertDoesNotExist()
    }

    @Test
    fun anEmptyListHasNothingToComplete() {
        setState(empty)

        composeRule.onNodeWithText(composeRule.string(R.string.list_complete)).assertDoesNotExist()
        composeRule.onNode(hasContentDescription(composeRule.string(R.string.list_share))).assertDoesNotExist()
    }

    @Test
    fun aFullyCheckedListSaysSoAndHasNothingToShare() {
        setState(loaded.copy(remainingCount = 0))

        composeRule.onNodeWithText(composeRule.string(R.string.list_all_checked)).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(composeRule.string(R.string.list_share))).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.string(R.string.list_complete)).assertIsDisplayed()
    }

    @Test
    fun anEmptyListOffersASession() {
        setState(empty)

        composeRule.onNodeWithText(composeRule.string(R.string.list_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.home_start_session)).performClick()

        composeRule.runOnIdle { assertEquals(1, sessionsStarted) }
    }

    @Test
    fun completingTheListAsksForConfirmation() {
        setState(loaded)

        composeRule.onNodeWithText(composeRule.string(R.string.list_complete)).performClick()
        composeRule.runOnIdle { assertEquals(emptyList<ShoppingListEvent>(), events) }
        composeRule.onNodeWithText(composeRule.string(R.string.list_complete_confirm)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.CompleteList), events) }
    }

    @Test
    fun aStorageErrorOffersRetry() {
        setState(ShoppingListUiState.Error(ListErrorKind.STORAGE_FAILED))

        composeRule.onNodeWithText(composeRule.string(R.string.list_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.list_error_storage)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.action_retry)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(ShoppingListEvent.Retry), events) }
    }
}
