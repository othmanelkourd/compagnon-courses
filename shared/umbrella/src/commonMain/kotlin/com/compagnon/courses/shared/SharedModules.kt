package com.compagnon.courses.shared

import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.dataModule
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.core.network.networkModule
import com.compagnon.courses.feature.catalog.catalogFeatureModule
import com.compagnon.courses.feature.lists.listsFeatureModule
import com.compagnon.courses.feature.questionflow.questionFlowModule
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import org.koin.core.module.Module

/**
 * Every Koin module of the shared code, for `startKoin { androidContext(app); modules(sharedModules(config, scope)) }`
 * on Android (the database driver needs the Android `Context`). Hidden from Swift: iOS calls `doInitKoin` instead.
 *
 * @param scope the catalog projection: `CatalogScope.forDevice(Locale.getDefault().toLanguageTag())`.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun sharedModules(config: NetworkConfig, scope: CatalogScope = CatalogScope.DEFAULT): List<Module> = listOf(
    networkModule(config),
    platformDatabaseModule(),
    dataModule(scope),
    catalogFeatureModule,
    questionFlowModule,
    listsFeatureModule,
)

/** The [app.cash.sqldelight.db.SqlDriver] of the platform (closed with the Koin application). */
internal expect fun platformDatabaseModule(): Module
