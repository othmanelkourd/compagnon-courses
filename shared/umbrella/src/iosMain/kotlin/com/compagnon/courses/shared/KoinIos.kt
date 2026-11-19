package com.compagnon.courses.shared

import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.CatalogUpdater
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform

/**
 * Starts Koin for the iOS app, with the catalog locale chosen from the device language (`Locale.current.identifier`:
 * `fr_FR`, `en_GB`…): French gets the French catalog, any other language English. The catalog country stays `FR`
 * until other markets launch. Call it once, from the `App` initializer, before creating any ViewModel. Calling it
 * again is a no-op (the first configuration wins).
 *
 * Starts the [CatalogUpdater]: the catalog bundled in the app is installed in the background.
 */
fun doInitKoin(deviceLanguage: String) {
    val koin = startSharedKoin(scope = CatalogScope.forDevice(deviceLanguage))
    koin.get<CatalogUpdater>().start()
}

/** Starts the global Koin application unless one is already running; [overrides] replace shared definitions (tests). */
internal fun startSharedKoin(
    overrides: List<Module> = emptyList(),
    scope: CatalogScope = CatalogScope.DEFAULT,
): Koin = KoinPlatform.getKoinOrNull() ?: startKoin { modules(sharedModules(scope) + overrides) }.koin
