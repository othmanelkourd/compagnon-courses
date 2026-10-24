package com.compagnon.courses.android.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.formatCount
import com.compagnon.courses.android.testing.plural
import com.compagnon.courses.android.testing.string
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import com.compagnon.courses.feature.catalog.CatalogSyncOutcome
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import com.compagnon.courses.feature.lists.ShoppingListUiState
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
    private var refreshes = 0

    private val published = CatalogStatusUiState.Loaded(
        country = "FR",
        version = 3,
        publishedAt = "2026-10-24T08:30:00Z",
        categoryCount = 18,
        productCount = 312,
        locales = listOf("fr-FR", "fr", "en"),
    )
    private val synced = CatalogSyncUiState(
        syncing = false,
        localVersion = 3,
        lastSyncedAt = "2026-10-24T09:00:00Z",
        productCount = 312,
        categoryCount = 18,
        lastOutcome = CatalogSyncOutcome.UP_TO_DATE,
    )
    private val list = ShoppingListUiState.Loaded(
        sections = emptyList(),
        itemCount = 5,
        remainingCount = 3,
        searchQuery = "",
        searchResults = emptyList(),
        undo = null,
        error = null,
    )

    private fun setContent(
        status: CatalogStatusUiState = published,
        sync: CatalogSyncUiState = synced,
        shoppingList: ShoppingListUiState = list,
    ) {
        composeRule.setContent {
            CompagnonTheme(dynamicColor = false) {
                HomeContent(
                    status = status,
                    sync = sync,
                    list = shoppingList,
                    onStartSession = { sessions++ },
                    onOpenList = { listOpened++ },
                    onRefresh = { refreshes++ },
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

        composeRule.onNodeWithText(composeRule.plural(R.plurals.home_list_count, 5, 5, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.home_my_list)).performClick()

        composeRule.runOnIdle { assertEquals(1, listOpened) }
    }

    @Test
    fun anEmptyListSaysSo() {
        setContent(shoppingList = list.copy(itemCount = 0, remainingCount = 0))

        composeRule.onNodeWithText(composeRule.string(R.string.home_list_empty)).assertIsDisplayed()
    }

    @Test
    fun showsTheLocalCatalogAndItsLastSync() {
        setContent()

        val locale = composeRule.activity.resources.configuration.locales[0]
        val products = composeRule.activity.resources.getQuantityString(
            R.plurals.catalog_product_count,
            312,
            formatCount(312, locale),
        )
        composeRule.onNodeWithText(composeRule.string(R.string.catalog_local_summary, products, 3L), substring = true)
            .assertExists()
        composeRule.onNodeWithText(composeRule.string(R.string.catalog_outcome_up_to_date), substring = true).assertExists()
    }

    @Test
    fun refreshSyncsTheCatalogUnlessASyncIsRunning() {
        setContent()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.catalog_sync_now)).assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun theRefreshIsDisabledWhileSyncing() {
        setContent(status = CatalogStatusUiState.Loading, sync = synced.copy(syncing = true))

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.catalog_sync_now)).assertIsNotEnabled()
        composeRule.onNodeWithText(composeRule.string(R.string.catalog_syncing), substring = true).assertExists()
    }

    @Test
    fun offlineWithoutCatalogTellsTheUserToConnect() {
        setContent(
            status = CatalogStatusUiState.Error(message = "Impossible de joindre le serveur.", retryable = true),
            sync = CatalogSyncUiState(lastOutcome = CatalogSyncOutcome.FAILED_NETWORK),
        )

        composeRule.onNodeWithText(composeRule.string(R.string.catalog_local_none), substring = true).assertExists()
        composeRule.onNodeWithText(composeRule.string(R.string.catalog_outcome_offline_none), substring = true).assertExists()
        composeRule.onNodeWithText("Impossible de joindre le serveur.", substring = true).assertExists()
    }
}
