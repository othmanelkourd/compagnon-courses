package com.compagnon.courses.shared

import app.cash.sqldelight.db.SqlDriver
import com.compagnon.courses.core.database.createNativeDriver
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

internal actual fun platformDatabaseModule(): Module = module {
    single<SqlDriver> { createNativeDriver() } withOptions { onClose { it?.close() } }
}
