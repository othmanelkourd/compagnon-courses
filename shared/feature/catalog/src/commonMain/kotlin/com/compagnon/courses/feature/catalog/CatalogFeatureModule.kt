package com.compagnon.courses.feature.catalog

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Koin definitions of the catalog feature. Requires `dataModule(...)` (CatalogRepository, CatalogUpdater).
 *
 * `koinViewModel<CatalogStatusViewModel>()` on Android, `CatalogStatusViewModelProvider` on iOS. Hidden from Swift,
 * which never deals with Koin modules.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
val catalogFeatureModule: Module = module {
    viewModel { CatalogStatusViewModel(catalog = get(), updater = get()) }
}
