package com.compagnon.courses.feature.catalog

import com.compagnon.courses.core.data.CatalogScope
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Koin definitions of the catalog feature. Requires `dataModule(...)` (CatalogRepository, CatalogUpdater, CatalogScope).
 *
 * The country is a parameter: `koinViewModel<CatalogStatusViewModel> { parametersOf("FR") }` on Android,
 * `CatalogStatusViewModelProvider` on iOS; the locale is the one of the [CatalogScope]. Hidden from Swift,
 * which never deals with Koin modules.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
val catalogFeatureModule: Module = module {
    viewModel { (country: String) ->
        CatalogStatusViewModel(catalog = get(), updater = get(), country = country, locale = get<CatalogScope>().locale)
    }
}
