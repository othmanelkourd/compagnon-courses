package com.compagnon.courses.shared

import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import org.koin.mp.KoinPlatform

/**
 * Resolves [CatalogStatusViewModel] from Koin for Swift. Requires [doInitKoin].
 *
 * It reads the global Koin instance instead of implementing `KoinComponent`, so that Koin's API stays
 * out of the Swift framework interface.
 */
class CatalogStatusViewModelProvider {

    /** A new ViewModel (it starts loading immediately). Give it to a [ViewModelOwner] so that it gets cleared. */
    fun create(): CatalogStatusViewModel = KoinPlatform.getKoin().get()

    /** A new ViewModel already owned by [owner]: `owner.clear()` clears it. */
    fun create(owner: ViewModelOwner): CatalogStatusViewModel = create().also(owner::own)
}
