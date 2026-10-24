package com.compagnon.courses.feature.catalog

import app.cash.turbine.test
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.core.network.createHttpClient
import com.compagnon.courses.core.network.networkModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogFeatureModuleTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModelIsCreatedForTheRequestedCountry() = runTest {
        val config = NetworkConfig(baseUrl = "http://api.test", platform = "android", appVersion = "0.1.0")
        val engine = MockEngine { respondJson(VERSION_JSON) }
        val app = koinApplication {
            modules(
                networkModule(config),
                catalogFeatureModule,
                module { single<HttpClient> { createHttpClient(config.platform, config.appVersion, engine) } },
            )
        }

        val viewModel = app.koin.get<CatalogStatusViewModel> { parametersOf("FR") }

        viewModel.state.test {
            assertEquals(loadedVersion1, awaitSettled())
        }
        assertEquals("FR", engine.requestHistory.single().url.parameters["country"])
        // ViewModel definitions are not singletons: each screen gets its own instance.
        assertNotSame(viewModel, app.koin.get<CatalogStatusViewModel> { parametersOf("FR") })
        app.close()
    }
}
