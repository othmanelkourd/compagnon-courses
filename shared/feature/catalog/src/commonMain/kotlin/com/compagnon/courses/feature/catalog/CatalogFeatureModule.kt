package com.compagnon.courses.feature.catalog

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin definitions of the catalog feature. Requires `networkModule(...)` for [com.compagnon.courses.core.network.CatalogApi].
 *
 * The country is a parameter: `koinViewModel<CatalogStatusViewModel> { parametersOf("FR") }` on Android,
 * `CatalogStatusViewModelProvider` on iOS. Hidden from Swift, which never deals with Koin modules.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
val catalogFeatureModule: Module = module {
    viewModel { (country: String) -> CatalogStatusViewModel(api = get(), country = country) }
}
