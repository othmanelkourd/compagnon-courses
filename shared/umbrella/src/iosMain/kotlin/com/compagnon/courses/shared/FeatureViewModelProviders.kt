package com.compagnon.courses.shared

import com.compagnon.courses.feature.lists.ShoppingListViewModel
import com.compagnon.courses.feature.onboarding.AppGateViewModel
import com.compagnon.courses.feature.onboarding.OnboardingMode
import com.compagnon.courses.feature.onboarding.OnboardingViewModel
import com.compagnon.courses.feature.questionflow.QuestionSessionViewModel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/**
 * Resolves [QuestionSessionViewModel] from Koin for Swift. Requires [doInitKoin].
 *
 * Creating it resumes the unfinished session or starts a new one: keep one per questionnaire screen, owned by
 * a [ViewModelOwner] cleared when the screen goes away (the session stays open and is resumed next time).
 */
class QuestionSessionViewModelProvider {

    /** A new ViewModel. Give it to a [ViewModelOwner] so that it gets cleared. */
    fun create(): QuestionSessionViewModel = KoinPlatform.getKoin().get()

    /** A new ViewModel already owned by [owner]: `owner.clear()` clears it. */
    fun create(owner: ViewModelOwner): QuestionSessionViewModel = create().also(owner::own)
}

/** Resolves [ShoppingListViewModel] from Koin for Swift. Requires [doInitKoin]. */
class ShoppingListViewModelProvider {

    /** A new ViewModel (it starts observing the active list). Give it to a [ViewModelOwner]. */
    fun create(): ShoppingListViewModel = KoinPlatform.getKoin().get()

    /** A new ViewModel already owned by [owner]: `owner.clear()` clears it. */
    fun create(owner: ViewModelOwner): ShoppingListViewModel = create().also(owner::own)
}

/**
 * Resolves [AppGateViewModel] from Koin for Swift. Requires [doInitKoin]. One for the app's lifetime: it decides
 * at launch whether the onboarding comes first.
 */
class AppGateViewModelProvider {

    fun create(): AppGateViewModel = KoinPlatform.getKoin().get()

    fun create(owner: ViewModelOwner): AppGateViewModel = create().also(owner::own)
}

/** Resolves [OnboardingViewModel] from Koin for Swift. Requires [doInitKoin]. */
class OnboardingViewModelProvider {

    /** A new ViewModel for [mode]: the first launch, or « Mon foyer ». Give it to a [ViewModelOwner]. */
    fun create(mode: OnboardingMode): OnboardingViewModel = KoinPlatform.getKoin().get { parametersOf(mode) }

    fun create(mode: OnboardingMode, owner: ViewModelOwner): OnboardingViewModel = create(mode).also(owner::own)
}
