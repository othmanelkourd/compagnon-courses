package com.compagnon.courses.shared

import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.core.network.networkModule
import com.compagnon.courses.feature.catalog.catalogFeatureModule
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import org.koin.core.module.Module

/**
 * Every Koin module of the shared code, for `startKoin { modules(sharedModules(config)) }` on Android.
 * Hidden from Swift: iOS calls `doInitKoin` instead.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun sharedModules(config: NetworkConfig): List<Module> = listOf(
    networkModule(config),
    catalogFeatureModule,
)
