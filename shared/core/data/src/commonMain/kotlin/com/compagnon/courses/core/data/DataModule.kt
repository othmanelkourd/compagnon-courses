package com.compagnon.courses.core.data

import app.cash.sqldelight.db.SqlDriver
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.createDatabase
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin definitions of the local data: [CatalogScope], [Clock], [IdGenerator], [CompagnonDatabase] and the
 * repositories. Requires a [SqlDriver] (one per platform, see `:shared:umbrella`) and `networkModule(...)`.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun dataModule(scope: CatalogScope = CatalogScope.DEFAULT): Module = module {
    single { scope }
    single<Clock> { Clock.System }
    single<IdGenerator> { UuidV7Generator }
    single { createDatabase(get<SqlDriver>()) }
    single { CatalogRepository(database = get(), api = get(), clock = get()) }
    single { HouseholdRepository(database = get(), country = get<CatalogScope>().country, clock = get()) }
    single { ShoppingListRepository(database = get(), ids = get(), clock = get()) }
    single { QuestionRepository(database = get(), households = get(), ids = get(), clock = get()) }
}
