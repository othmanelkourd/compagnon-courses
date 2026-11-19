package com.compagnon.courses.android.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.formatCount
import com.compagnon.courses.android.onboarding.OnboardingFixtures
import com.compagnon.courses.android.testing.plural
import com.compagnon.courses.android.testing.string
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen with fake catalog and list states. */
@RunWith(AndroidJUnit4::class)
class HomeContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var sessions = 0
    private var listOpened = 0

    private val installed = CatalogStatusUiState(ready = true, version = 3, productCount = 312, categoryCount = 18)
    private val list = ShoppingListUiState.Loaded(
        sections = emptyList(),
        itemCount = 5,
        remainingCount = 3,
        searchQuery = "",
        searchResults = emptyList(),
        undo = null,
        error = null,
    )

    private var householdOpened = 0

    private fun setContent(
        catalog: CatalogStatusUiState = installed,
        shoppingList: ShoppingListUiState = list,
        household: HouseholdSummaryUi? = OnboardingFixtures.summary,
    ) {
        composeRule.setContent {
            CompagnonTheme {
                HomeContent(
                    catalog = catalog,
                    list = shoppingList,
                    household = household,
                    onStartSession = { sessions++ },
                    onOpenList = { listOpened++ },
                    onOpenHousehold = { householdOpened++ },
                )
            }
        }
    }

    @Test
    fun startsASessionFromTheMainButton() {
        setContent()

        composeRule.onNodeWithText(composeRule.string(R.string.home_start_session)).assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, sessions) }
    }

    @Test
    fun showsTheListCountAndOpensIt() {
        setContent()

        val summary = composeRule.string(
            R.string.home_list_summary,
            composeRule.plural(R.plurals.home_list_items, 5),
            composeRule.plural(R.plurals.home_list_checked, 2),
        )
        composeRule.onNodeWithText(summary).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.home_my_list)).performClick()

        composeRule.runOnIdle { assertEquals(1, listOpened) }
    }

    @Test
    fun theHomeHeadlineAsksIfReadyToShop() {
        setContent()

        composeRule.onNodeWithText(composeRule.string(R.string.home_headline)).assertIsDisplayed()
    }

    @Test
    fun myHouseholdSummarisesTheHouseholdAndOpensIt() {
        setContent()

        // « 4 personnes · Végétarien · 14 essentiels » (mockup `Main`).
        val summary = listOf(
            composeRule.plural(R.plurals.home_household_persons, 4),
            composeRule.string(R.string.diet_vegetarian),
            composeRule.plural(R.plurals.home_household_essentials, 14),
        ).joinToString(" · ")
        composeRule.onNodeWithText(summary).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.household_title)).performClick()

        composeRule.runOnIdle { assertEquals(1, householdOpened) }
    }

    @Test
    fun withoutAHouseholdThereIsNoCard() {
        setContent(household = null)

        composeRule.onNodeWithText(composeRule.string(R.string.household_title)).assertDoesNotExist()
    }

    @Test
    fun anEmptyListSaysSo() {
        setContent(shoppingList = list.copy(itemCount = 0, remainingCount = 0))

        composeRule.onNodeWithText(composeRule.string(R.string.home_list_empty)).assertIsDisplayed()
    }

    @Test
    fun theFooterShowsTheLocalCatalog() {
        setContent()

        composeRule.onNodeWithText(footerReady(), substring = true).assertIsDisplayed()
    }

    @Test
    fun theFirstLaunchPreparesTheBundledCatalog() {
        setContent(catalog = CatalogStatusUiState())

        composeRule.onNodeWithText(composeRule.string(R.string.catalog_local_none), substring = true).assertExists()
    }

    private fun products(): String {
        val locale = composeRule.activity.resources.configuration.locales[0]
        return composeRule.activity.resources.getQuantityString(R.plurals.catalog_product_count, 312, formatCount(312, locale))
    }

    private fun footerReady(): String = composeRule.string(R.string.catalog_footer_offline, products())
}
