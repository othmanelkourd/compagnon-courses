package com.compagnon.courses.shared

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.compagnon.courses.core.database.createAndroidDriver
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/** Requires `androidContext(...)` in `startKoin`. */
internal actual fun platformDatabaseModule(): Module = module {
    single<SqlDriver> { createAndroidDriver(get<Context>()) } withOptions { onClose { it?.close() } }
}
