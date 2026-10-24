package com.compagnon.courses.shared

import com.compagnon.courses.feature.lists.ShoppingListViewModel
import com.compagnon.courses.feature.questionflow.QuestionSessionViewModel
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
