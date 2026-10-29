package com.compagnon.courses.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.HouseholdRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface AppGateUiState {
    /** Reading the household: a plain background. */
    data object Loading : AppGateUiState

    /** First launch: the onboarding is shown instead of the app, until [AppGateEvent.OnboardingClosed]. */
    data object NeedsOnboarding : AppGateUiState

    /** The app, with the household summary of the « Mon foyer » card on the home screen. */
    data class Ready(val summary: HouseholdSummaryUi) : AppGateUiState
}

sealed interface AppGateEvent {
    /** The first-launch onboarding closed (saved, or put off with « Plus tard »). */
    data object OnboardingClosed : AppGateEvent
}

/**
 * Decides at launch whether the onboarding comes first (it is shown once: until it is saved or put off), and
 * keeps the household summary up to date. The decision is taken once: saving the onboarding does not switch to
 * the app by itself, so that its recap screen stays until the person leaves it. A storage failure never blocks
 * the app: it opens with the default household.
 */
class AppGateViewModel(
    private val households: HouseholdRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AppGateUiState>(AppGateUiState.Loading)
    val state: StateFlow<AppGateUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val onboarded = try {
                households.observeOnboarded().first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "The household could not be read" }
                true
            }
            if (onboarded) observe() else _state.value = AppGateUiState.NeedsOnboarding
        }
    }

    fun onEvent(event: AppGateEvent) {
        when (event) {
            AppGateEvent.OnboardingClosed -> if (_state.value !is AppGateUiState.Ready) observe()
        }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                households.observe(),
                households.observeEssentials(),
                households.observeCadences(),
                catalog.observeCatalog(),
            ) { household, essentials, cadences, catalog ->
                AppGateUiState.Ready(
                    summaryOf(household.toSetup(essentials, cadences), catalog, onboarded = household.onboardedAt != null),
                ) as AppGateUiState
            }
                .catch { e ->
                    log.e(e) { "The household summary could not be read" }
                    emit(AppGateUiState.Ready(summaryOf(com.compagnon.courses.core.data.HouseholdSetup(adults = 2), null, onboarded = false)))
                }
                .collect { _state.value = it }
        }
    }

    private companion object {
        val log = Logger.withTag("AppGateViewModel")
    }
}
