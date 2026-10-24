package com.compagnon.courses.feature.catalog

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException

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

        val viewModel = CatalogStatusViewModel(api, country = "FR")

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            assertEquals(loadedVersion1, awaitItem())
        }
        assertEquals("FR", engine.requestHistory.single().url.parameters["country"])
    }

    @Test
    fun unavailableCountryIsAFinalError() = runTest {
        val (api, _) = mockCatalogApi { respondProblem(COUNTRY_PROBLEM_JSON, HttpStatusCode.NotFound) }

        CatalogStatusViewModel(api, country = "ZZ").state.test {
            assertEquals(
                CatalogStatusUiState.Error(CatalogStatusMessages.COUNTRY_NOT_AVAILABLE, retryable = false),
                awaitSettled(),
            )
        }
    }

    @Test
    fun serverErrorIsRetryable() = runTest {
        val (api, _) = mockCatalogApi { respond("upstream down", HttpStatusCode.ServiceUnavailable) }

        CatalogStatusViewModel(api, country = "FR").state.test {
            assertEquals(
                CatalogStatusUiState.Error(CatalogStatusMessages.SERVER_UNAVAILABLE, retryable = true),
                awaitSettled(),
            )
        }
    }

    @Test
    fun clientErrorWithoutProblemShowsTheStatus() = runTest {
        val (api, _) = mockCatalogApi { respond("", HttpStatusCode.BadRequest) }

        CatalogStatusViewModel(api, country = "FR").state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.httpError(400), retryable = false), awaitSettled())
        }
    }

    @Test
    fun connectivityFailureIsRetryable() = runTest {
        val (api, _) = mockCatalogApi { throw IOException("Network is unreachable") }

        CatalogStatusViewModel(api, country = "FR").state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.CONNECTIVITY, retryable = true), awaitSettled())
        }
    }

    @Test
    fun malformedResponseIsNotRetryable() = runTest {
        val (api, _) = mockCatalogApi { respondJson("""{ "country": "FR" }""") }

        CatalogStatusViewModel(api, country = "FR").state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.UNEXPECTED_RESPONSE, retryable = false), awaitSettled())
        }
    }

    @Test
    fun refreshAfterAnErrorReloads() = runTest {
        var calls = 0
        val (api, engine) = mockCatalogApi {
            if (calls++ == 0) respond("", HttpStatusCode.ServiceUnavailable) else respondJson(VERSION_JSON)
        }
        val viewModel = CatalogStatusViewModel(api, country = "FR")

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Error(CatalogStatusMessages.SERVER_UNAVAILABLE, retryable = true), awaitSettled())

            viewModel.onEvent(CatalogStatusEvent.Refresh)

            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            assertEquals(loadedVersion1, awaitItem())
        }
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun refreshWhileLoadingIsIgnored() = runTest {
        val release = CompletableDeferred<Unit>()
        val (api, engine) = mockCatalogApi {
            release.await()
            respondJson(VERSION_JSON)
        }
        val viewModel = CatalogStatusViewModel(api, country = "FR")

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            viewModel.onEvent(CatalogStatusEvent.Refresh)
            viewModel.onEvent(CatalogStatusEvent.Refresh)
            release.complete(Unit)
            assertEquals(loadedVersion1, awaitItem())
            expectNoEvents()
        }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun clearingTheViewModelCancelsTheRequestWithoutPublishingAnError() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val requestCancelled = CompletableDeferred<Unit>()
        val (api, _) = mockCatalogApi {
            requestStarted.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                requestCancelled.complete(Unit)
                throw e
            }
        }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { CatalogStatusViewModel(api, country = "FR") } },
        )[CatalogStatusViewModel::class]

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            requestStarted.await()

            store.clear()

            requestCancelled.await()
            expectNoEvents()
        }
        assertEquals(CatalogStatusUiState.Loading, viewModel.state.value)
    }

    @Test
    fun errorMessagesAreUserFacingFrench() {
        assertTrue(CatalogStatusMessages.CONNECTIVITY.startsWith("Impossible de joindre le serveur"))
        assertEquals("Le serveur a renvoyé une erreur (HTTP 418).", CatalogStatusMessages.httpError(418))
    }
}
