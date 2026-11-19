package com.compagnon.courses.shared

import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.onboarding.AppGateUiState
import com.compagnon.courses.feature.onboarding.OnboardingMode
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.questionflow.QuestionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KoinIosTest {

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
    fun doInitKoinPicksTheCatalogLocaleFromTheDeviceLanguageAndIsIdempotent() {
        doInitKoin(deviceLanguage = "en_GB")
        doInitKoin(deviceLanguage = "fr_FR")

        assertEquals("en", KoinPlatform.getKoin().get<CatalogScope>().locale)
    }

    @Test
    fun providerCreatesACatalogStatusViewModel() = runTest {
        startSharedKoin(overrides = listOf(inMemoryDatabaseModule()))
        val owner = ViewModelOwner()

        val viewModel = CatalogStatusViewModelProvider().create(owner = owner)

        assertTrue(viewModel.state.first { it.ready }.productCount > 0)
        assertNotSame(viewModel, CatalogStatusViewModelProvider().create(owner = owner))
        owner.clear()
    }

    @Test
    fun providersCreateOwnedQuestionAndListViewModels() = runTest {
        startSharedKoin(overrides = listOf(inMemoryDatabaseModule()))
        val owner = ViewModelOwner()

        val questions = QuestionSessionViewModelProvider().create(owner = owner)
        val list = ShoppingListViewModelProvider().create(owner = owner)

        assertIs<QuestionUiState.Meta>(questions.state.first { it != QuestionUiState.Loading })
        assertIs<ShoppingListUiState.Loaded>(list.state.first { it != ShoppingListUiState.Loading })
        owner.clear()
    }

    @Test
    fun providersCreateOwnedGateAndOnboardingViewModels() = runTest {
        startSharedKoin(overrides = listOf(inMemoryDatabaseModule()))
        val owner = ViewModelOwner()

        val gate = AppGateViewModelProvider().create(owner = owner)
        val onboarding = OnboardingViewModelProvider().create(mode = OnboardingMode.EDIT, owner = owner)

        assertEquals(AppGateUiState.NeedsOnboarding, gate.state.first { it != AppGateUiState.Loading })
        val ready = assertIs<OnboardingUiState.Ready>(onboarding.state.first { it is OnboardingUiState.Ready })
        assertEquals(OnboardingStep.OVERVIEW, ready.step)
        owner.clear()
    }
}
