package com.compagnon.courses.feature.questionflow

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Koin definitions of the question flow. Requires `dataModule(...)` (repositories, scope, clock).
 *
 * `koinViewModel<QuestionSessionViewModel>()` on Android, `QuestionSessionViewModelProvider` on iOS.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
val questionFlowModule: Module = module {
    viewModel {
        QuestionSessionViewModel(questions = get(), catalog = get(), lists = get(), scope = get(), clock = get())
    }
}
