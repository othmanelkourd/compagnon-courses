package com.compagnon.courses.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.turbine.test
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.network.CatalogApi
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.core.network.createHttpClient
import com.compagnon.courses.core.testing.MockCatalogServer
import com.compagnon.courses.core.testing.createInMemoryDriver
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.lists.ShoppingListViewModel
import com.compagnon.courses.feature.onboarding.AppGateUiState
import com.compagnon.courses.feature.onboarding.AppGateViewModel
import com.compagnon.courses.feature.onboarding.OnboardingMode
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.OnboardingViewModel
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionSessionViewModel
import com.compagnon.courses.feature.questionflow.QuestionUiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

internal const val VERSION_JSON: String =
    """{"country":"FR","version":7,"publishedAt":"2026-10-24T08:30:00Z","locales":["fr-FR","fr","en"],"categoryCount":18,"productCount":120}"""

/**
 * Replaces the platform HTTP client with one backed by [engine] (same production configuration), and the
 * platform database with a private in-memory one.
 */
internal fun mockHttpModule(config: NetworkConfig, engine: MockEngine): Module = module {
    single<HttpClient> { createHttpClient(config.platform, config.appVersion, engine) }
    single<SqlDriver> { createInMemoryDriver() }
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
        assertEquals("http://api.test/v1/catalog/version?country=FR", engine.requestHistory.first().url.toString())
        app.close()
    }

    @Test
    fun aBuildWithoutServerWorksFromTheBundledCatalog() = runTest {
        val config = NetworkConfig(baseUrl = null, platform = "android", appVersion = "1.0.0")
        val inMemory = module { single<SqlDriver> { createInMemoryDriver() } }
        val app = koinApplication { modules(sharedModules(config, CatalogScope.forDevice("fr-FR")) + inMemory) }

        assertNull(app.koin.getOrNull<CatalogApi>())
        assertNull(app.koin.getOrNull<HttpClient>())
        val updater = app.koin.get<CatalogUpdater>()
        assertFalse(updater.checksServer)
        updater.awaitInstalled()
        val questions = app.koin.get<QuestionSessionViewModel>()
        assertIs<QuestionUiState.Meta>(questions.state.first { it != QuestionUiState.Loading })
        app.close()
    }

    @Test
    fun sharedGraphProvidesTheQuestionFlowAndTheListOverTheSameDatabase() = runTest {
        val config = NetworkConfig(baseUrl = "http://api.test", platform = "android", appVersion = "0.1.0")
        val server = MockCatalogServer()
        val app = koinApplication {
            modules(sharedModules(config, CatalogScope.forDevice("fr-FR")) + mockHttpModule(config, server.engine))
        }

        val questions = app.koin.get<QuestionSessionViewModel>()
        val meta = questions.state.first { it != QuestionUiState.Loading }
        assertIs<QuestionUiState.Meta>(meta)
        questions.onEvent(QuestionEvent.AnswerMeta(7))
        questions.onEvent(QuestionEvent.AnswerMeta(2))
        questions.onEvent(QuestionEvent.AnswerMeta(0))
        questions.onEvent(QuestionEvent.EndQuestions)
        assertIs<QuestionUiState.Review>(questions.state.value)
        questions.onEvent(QuestionEvent.Confirm)
        val done = assertIs<QuestionUiState.Done>(questions.state.first { it is QuestionUiState.Done })
        assertEquals(0, done.itemsAdded)

        val list = app.koin.get<ShoppingListViewModel>()
        val loaded = assertIs<ShoppingListUiState.Loaded>(list.state.first { it != ShoppingListUiState.Loading })
        assertEquals(0, loaded.itemCount)
        assertEquals("fr-FR", app.koin.get<CatalogScope>().locale)
        app.koin.get<CatalogUpdater>().switchTo(CatalogScope.forDevice("en-US"))
        assertEquals("en", app.koin.get<CatalogScope>().locale)
        app.close()
    }

    @Test
    fun sharedGraphProvidesTheGateAndTheOnboarding() = runTest {
        val config = NetworkConfig(baseUrl = "http://api.test", platform = "android", appVersion = "0.1.0")
        val server = MockCatalogServer()
        val app = koinApplication {
            modules(sharedModules(config, CatalogScope.forDevice("fr-FR")) + mockHttpModule(config, server.engine))
        }

        val gate = app.koin.get<AppGateViewModel>()
        assertEquals(AppGateUiState.NeedsOnboarding, gate.state.first { it != AppGateUiState.Loading })
        val onboarding = app.koin.get<OnboardingViewModel> { parametersOf(OnboardingMode.FIRST_RUN) }
        val ready = assertIs<OnboardingUiState.Ready>(onboarding.state.first { it is OnboardingUiState.Ready })
        assertEquals(OnboardingStep.WELCOME, ready.step)
        val edit = app.koin.get<OnboardingViewModel> { parametersOf(OnboardingMode.EDIT) }
        assertEquals(
            OnboardingStep.OVERVIEW,
            assertIs<OnboardingUiState.Ready>(
                edit.state.first {
                    it is OnboardingUiState.Ready
                },
            ).step,
        )
        app.close()
    }
}
