package com.compagnon.courses.shared

import com.compagnon.courses.core.network.NetworkConfig
import kotlin.experimental.ExperimentalNativeApi
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform

/** Value of the `X-Platform` header sent by the iOS app. */
internal const val IOS_PLATFORM: String = "ios"

/**
 * Starts Koin for the iOS app. Call it once, from the `App` initializer, before creating any ViewModel.
 * Calling it again is a no-op (the first configuration wins).
 *
 * HTTP logging is on in Debug frameworks only.
 *
 * @param baseUrl API root, e.g. `http://localhost:8080` in the simulator.
 * @param appVersion the app's `CFBundleShortVersionString`, sent as `X-App-Version`.
 */
@OptIn(ExperimentalNativeApi::class)
fun doInitKoin(baseUrl: String, appVersion: String) {
    startSharedKoin(
        NetworkConfig(
            baseUrl = baseUrl,
            platform = IOS_PLATFORM,
            appVersion = appVersion,
            enableLogging = Platform.isDebugBinary,
        ),
    )
}

/** Starts the global Koin application unless one is already running; [overrides] replace shared definitions (tests). */
internal fun startSharedKoin(config: NetworkConfig, overrides: List<Module> = emptyList()): Koin =
    KoinPlatform.getKoinOrNull() ?: startKoin { modules(sharedModules(config) + overrides) }.koin
