package com.compagnon.courses.shared

import app.cash.turbine.test
import com.compagnon.courses.core.network.CatalogApi
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.core.network.createHttpClient
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal const val VERSION_JSON: String =
    """{"country":"FR","version":7,"publishedAt":"2026-10-24T08:30:00Z","locales":["fr-FR","fr","en"],"categoryCount":18,"productCount":120}"""

/** Replaces the platform HTTP client with one backed by [engine] (same production configuration). */
internal fun mockHttpModule(config: NetworkConfig, engine: MockEngine): Module = module {
    single<HttpClient> { createHttpClient(config.platform, config.appVersion, engine) }
}

internal fun versionEngine(): MockEngine = MockEngine {
    respond(VERSION_JSON, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharedModulesTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sharedGraphProvidesAWorkingCatalogStatusViewModel() = runTest {
        val config = NetworkConfig(baseUrl = "http://api.test", platform = "android", appVersion = "0.1.0")
        val engine = versionEngine()
        val app = koinApplication { modules(sharedModules(config) + mockHttpModule(config, engine)) }

        app.koin.get<CatalogApi>()
        val viewModel = app.koin.get<CatalogStatusViewModel> { parametersOf("FR") }

        viewModel.state.test {
            var state = awaitItem()
            while (state == CatalogStatusUiState.Loading) state = awaitItem()
            assertEquals(7L, assertIs<CatalogStatusUiState.Loaded>(state).version)
        }
        assertEquals("http://api.test/v1/catalog/version?country=FR", engine.requestHistory.single().url.toString())
        app.close()
    }
}
