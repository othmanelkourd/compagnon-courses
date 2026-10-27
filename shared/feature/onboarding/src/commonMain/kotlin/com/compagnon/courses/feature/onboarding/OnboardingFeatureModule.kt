package com.compagnon.courses.feature.onboarding

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin definitions of the onboarding « Mon foyer ». Requires `dataModule(...)`.
 *
 * `koinViewModel<AppGateViewModel>()` and `koinViewModel<OnboardingViewModel> { parametersOf(mode) }` on
 * Android; `AppGateViewModelProvider` and `OnboardingViewModelProvider` on iOS.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
val onboardingFeatureModule: Module = module {
    viewModel { AppGateViewModel(households = get(), catalog = get()) }
    viewModel { params ->
        OnboardingViewModel(mode = params.get(), households = get(), catalog = get(), scope = get())
    }
}
