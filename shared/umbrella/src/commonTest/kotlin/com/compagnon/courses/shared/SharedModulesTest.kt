package com.compagnon.courses.shared

import app.cash.sqldelight.db.SqlDriver
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.testing.createInMemoryDriver
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The platform database replaced with a private in-memory one. */
internal fun inMemoryDatabaseModule(): Module = module { single<SqlDriver> { createInMemoryDriver() } }

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
    fun sharedGraphWorksFromTheBundledCatalog() = runTest {
        val app = koinApplication { modules(sharedModules(CatalogScope.forDevice("fr-FR")) + inMemoryDatabaseModule()) }

        app.koin.get<CatalogUpdater>().awaitInstalled()
        val status = app.koin.get<CatalogStatusViewModel>()
        assertTrue(status.state.first { it.ready }.productCount > 0)
        val questions = app.koin.get<QuestionSessionViewModel>()
        assertIs<QuestionUiState.Meta>(questions.state.first { it != QuestionUiState.Loading })
        app.close()
    }

    @Test
    fun sharedGraphProvidesTheQuestionFlowAndTheListOverTheSameDatabase() = runTest {
        val app = koinApplication { modules(sharedModules(CatalogScope.forDevice("fr-FR")) + inMemoryDatabaseModule()) }

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
        val app = koinApplication { modules(sharedModules(CatalogScope.forDevice("fr-FR")) + inMemoryDatabaseModule()) }

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
