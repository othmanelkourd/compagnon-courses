package com.compagnon.courses.feature.lists

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin definitions of the lists feature. Requires `dataModule(...)`.
 *
 * `koinViewModel<ShoppingListViewModel>()` on Android, `ShoppingListViewModelProvider` on iOS.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
val listsFeatureModule: Module = module {
    viewModel { ShoppingListViewModel(lists = get(), catalog = get(), households = get()) }
}
