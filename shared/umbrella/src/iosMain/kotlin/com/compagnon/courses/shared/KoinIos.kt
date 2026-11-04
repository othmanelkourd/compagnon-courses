package com.compagnon.courses.shared

import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.network.NetworkConfig
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform
import kotlin.experimental.ExperimentalNativeApi

/** Value of the `X-Platform` header sent by the iOS app. */
internal const val IOS_PLATFORM: String = "ios"

/**
 * Starts Koin for the iOS app with the French catalog (`FR`, `fr-FR`). Call it once, from the `App` initializer,
 * before creating any ViewModel. Calling it again is a no-op (the first configuration wins).
 *
 * HTTP logging is on in Debug frameworks only.
 *
 * @param baseUrl API root, e.g. `http://localhost:8080` in the simulator; nil in a build without server (ADR-013,
 *   Release): the app then never goes online.
 * @param appVersion the app's `CFBundleShortVersionString`, sent as `X-App-Version`.
 */
fun doInitKoin(baseUrl: String?, appVersion: String) {
    doInitKoin(baseUrl = baseUrl, appVersion = appVersion, deviceLanguage = CatalogScope.DEFAULT.locale)
}

/**
 * Same as `doInitKoin(baseUrl:appVersion:)`, with the catalog locale chosen from the device language
 * (`Locale.current.identifier`: `fr_FR`, `en_GB`…): French gets the French catalog, any other language English.
 * The catalog country stays `FR` until other markets launch. Starts the [CatalogUpdater]: the bundled catalog, then
 * the check of the server's latest release (when there is a server).
 */
@OptIn(ExperimentalNativeApi::class)
fun doInitKoin(baseUrl: String?, appVersion: String, deviceLanguage: String) {
    val koin = startSharedKoin(
        NetworkConfig(
            baseUrl = baseUrl,
            platform = IOS_PLATFORM,
            appVersion = appVersion,
            enableLogging = Platform.isDebugBinary,
        ),
        scope = CatalogScope.forDevice(deviceLanguage),
    )
    // The bundled catalog is installed, then the server's latest release is checked, in the background.
    koin.get<CatalogUpdater>().start()
}

/** Starts the global Koin application unless one is already running; [overrides] replace shared definitions (tests). */
internal fun startSharedKoin(
    config: NetworkConfig,
    overrides: List<Module> = emptyList(),
    scope: CatalogScope = CatalogScope.DEFAULT,
): Koin = KoinPlatform.getKoinOrNull() ?: startKoin { modules(sharedModules(config, scope) + overrides) }.koin
