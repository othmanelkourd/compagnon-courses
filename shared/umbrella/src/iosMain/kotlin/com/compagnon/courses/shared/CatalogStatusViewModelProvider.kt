package com.compagnon.courses.shared

import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/**
 * Resolves [CatalogStatusViewModel] from Koin for Swift. Requires [doInitKoin].
 *
 * It reads the global Koin instance instead of implementing `KoinComponent`, so that Koin's API stays
 * out of the Swift framework interface.
 */
class CatalogStatusViewModelProvider {

    /** A new ViewModel (it starts loading immediately). Give it to a [ViewModelOwner] so that it gets cleared. */
    fun create(country: String): CatalogStatusViewModel =
        KoinPlatform.getKoin().get { parametersOf(country) }

    /** A new ViewModel already owned by [owner]: `owner.clear()` clears it. */
    fun create(country: String, owner: ViewModelOwner): CatalogStatusViewModel =
        create(country).also(owner::own)
}
