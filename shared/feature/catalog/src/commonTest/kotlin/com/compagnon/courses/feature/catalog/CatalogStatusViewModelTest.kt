package com.compagnon.courses.feature.catalog

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.compagnon.courses.core.data.SyncResult
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogStatusViewModelTest {

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main; runTest shares this dispatcher's scheduler.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheCatalogVersionOnCreation() = runTest {
        val (api, engine) = mockCatalogApi { respondJson(VERSION_JSON) }

        val viewModel = statusViewModel(api, country = "FR")

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            assertEquals(loadedVersion1, awaitItem())
        }
        assertEquals("FR", engine.requestHistory.first().url.parameters["country"])
    }

    @Test
    fun unavailableCountryIsAFinalError() = runTest {
        val (api, _) = mockCatalogApi { respondProblem(COUNTRY_PROBLEM_JSON, HttpStatusCode.NotFound) }

        statusViewModel(api, country = "ZZ").state.test {
            assertEquals(
                CatalogStatusUiState.Error(CatalogStatusMessages.COUNTRY_NOT_AVAILABLE, retryable = false),
                awaitSettled(),
            )
        }
    }

    @Test
    fun serverErrorIsRetryable() = runTest {
        val (api, _) = mockCatalogApi { respond("upstream down", HttpStatusCode.ServiceUnavailable) }

        statusViewModel(api, country = "FR").state.test {
            assertEquals(
                CatalogStatusUiState.Error(CatalogStatusMessages.SERVER_UNAVAILABLE, retryable = true),
                awaitSettled(),
            )
        }
    }

    @Test
    fun clientErrorWithoutProblemShowsTheStatus() = runTest {
        val (api, _) = mockCatalogApi { respond("", HttpStatusCode.BadRequest) }

        statusViewModel(api, country = "FR").state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.httpError(400), retryable = false), awaitSettled())
        }
    }

    @Test
    fun connectivityFailureIsRetryable() = runTest {
        val (api, _) = mockCatalogApi { throw IOException("Network is unreachable") }

        statusViewModel(api, country = "FR").state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.CONNECTIVITY, retryable = true), awaitSettled())
        }
    }

    @Test
    fun malformedResponseIsNotRetryable() = runTest {
        val (api, _) = mockCatalogApi { respondJson("""{ "country": "FR" }""") }

        statusViewModel(api, country = "FR").state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.UNEXPECTED_RESPONSE, retryable = false), awaitSettled())
        }
    }

    @Test
    fun refreshAfterAnErrorReloads() = runTest {
        var calls = 0
        val (api, engine) = mockCatalogApi {
            if (calls++ == 0) respond("", HttpStatusCode.ServiceUnavailable) else respondJson(VERSION_JSON)
        }
        val viewModel = statusViewModel(api, country = "FR")

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.SERVER_UNAVAILABLE, retryable = true), awaitSettled())

            viewModel.onEvent(CatalogStatusEvent.Refresh)

            // The last outcome stays until the new check ends.
            assertEquals(loadedVersion1, awaitItem())
        }
        assertEquals(2, engine.versionRequests)
    }

    @Test
    fun refreshWhileLoadingIsIgnored() = runTest {
        val release = CompletableDeferred<Unit>()
        val (api, engine) = mockCatalogApi {
            release.await()
            respondJson(VERSION_JSON)
        }
        val viewModel = statusViewModel(api, country = "FR")

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            viewModel.onEvent(CatalogStatusEvent.Refresh)
            viewModel.onEvent(CatalogStatusEvent.Refresh)
            release.complete(Unit)
            assertEquals(loadedVersion1, awaitItem())
            expectNoEvents()
        }
        assertEquals(1, engine.versionRequests)
    }

    @Test
    fun theCheckOutlivesTheScreen() = runTest {
        val release = CompletableDeferred<Unit>()
        val (api, _) = mockCatalogApi {
            release.await()
            respondJson(VERSION_JSON)
        }
        val updater = updater(api)
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { CatalogStatusViewModel(api, updater, country = "FR", locale = "fr-FR") } },
        )[CatalogStatusViewModel::class]

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            store.clear()
            release.complete(Unit)
            expectNoEvents()
        }
        // The check belongs to the app, not to the home screen: it ends even when the screen is gone.
        assertIs<SyncResult.UpToDate>(updater.check.first { it.result != null }.result)
    }

    @Test
    fun errorMessagesAreUserFacingFrench() {
        assertTrue(CatalogStatusMessages.CONNECTIVITY.startsWith("Impossible de joindre le serveur"))
        assertEquals("Le serveur a renvoyé une erreur (HTTP 418).", CatalogStatusMessages.httpError(418))
    }
}
