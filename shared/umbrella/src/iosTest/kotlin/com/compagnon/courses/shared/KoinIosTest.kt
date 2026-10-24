package com.compagnon.courses.shared

import app.cash.turbine.test
import com.compagnon.courses.core.network.HEADER_PLATFORM
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.core.network.createHttpClient
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalCoroutinesApi::class)
class KoinIosTest {

    private val config = NetworkConfig(baseUrl = "http://api.test", platform = IOS_PLATFORM, appVersion = "0.1.0")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun doInitKoinConfiguresTheIosClientAndIsIdempotent() {
        doInitKoin(baseUrl = "http://localhost:8080", appVersion = "0.1.0")
        doInitKoin(baseUrl = "http://other:9999", appVersion = "9.9.9")

        val koin = KoinPlatform.getKoin()
        val config = koin.get<NetworkConfig>()
        assertEquals("http://localhost:8080", config.baseUrl)
        assertEquals("ios", config.platform)
        assertEquals("0.1.0", config.appVersion)
        koin.get<HttpClient>() // the Darwin engine can be created
    }

    @Test
    fun providerCreatesALoadingViewModelForTheCountry() = runTest {
        val engine = versionEngine()
        startSharedKoin(config, overrides = listOf(mockHttpModule(config, engine)))
        val owner = ViewModelOwner()

        val viewModel = CatalogStatusViewModelProvider().create(country = "FR", owner = owner)

        viewModel.state.test {
            var state = awaitItem()
            while (state == CatalogStatusUiState.Loading) state = awaitItem()
            assertEquals(7L, assertIs<CatalogStatusUiState.Loaded>(state).version)
        }
        val request = engine.requestHistory.single()
        assertEquals("FR", request.url.parameters["country"])
        assertEquals("ios", request.headers[HEADER_PLATFORM])
        assertNotSame(viewModel, CatalogStatusViewModelProvider().create(country = "FR", owner = owner))
        owner.clear()
    }

    @Test
    fun clearingTheOwnerCancelsTheViewModelWork() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val requestCancelled = CompletableDeferred<Unit>()
        val engine = MockEngine {
            requestStarted.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                requestCancelled.complete(Unit)
                throw e
            }
        }
        startSharedKoin(
            config,
            overrides = listOf(module { single<HttpClient> { createHttpClient(config.platform, config.appVersion, engine) } }),
        )
        val owner = ViewModelOwner()
        val viewModel = CatalogStatusViewModelProvider().create(country = "FR")
        owner.own(viewModel)

        viewModel.state.test {
            assertEquals(CatalogStatusUiState.Loading, awaitItem())
            requestStarted.await()
            owner.clear()
            requestCancelled.await()
            expectNoEvents()
        }
    }
}
