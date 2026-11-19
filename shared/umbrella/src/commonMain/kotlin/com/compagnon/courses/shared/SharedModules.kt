package com.compagnon.courses.shared

import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.dataModule
import com.compagnon.courses.feature.catalog.catalogFeatureModule
import com.compagnon.courses.feature.lists.listsFeatureModule
import com.compagnon.courses.feature.onboarding.onboardingFeatureModule
import com.compagnon.courses.feature.questionflow.questionFlowModule
import org.koin.core.module.Module
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Every Koin module of the shared code, for `startKoin { androidContext(app); modules(sharedModules(scope)) }`
 * on Android (the database driver needs the Android `Context`). Hidden from Swift: iOS calls `doInitKoin` instead.
 *
 * @param scope the catalog projection: `CatalogScope.forDevice(Locale.getDefault().toLanguageTag())`.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun sharedModules(scope: CatalogScope = CatalogScope.DEFAULT): List<Module> = listOf(
    platformDatabaseModule(),
    dataModule(scope),
    catalogFeatureModule,
    questionFlowModule,
    listsFeatureModule,
    onboardingFeatureModule,
)

/** The [app.cash.sqldelight.db.SqlDriver] of the platform (closed with the Koin application). */
internal expect fun platformDatabaseModule(): Module
