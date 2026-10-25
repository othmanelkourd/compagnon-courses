package com.compagnon.courses.core.database

import app.cash.sqldelight.db.SqlDriver

/** File name of the app database (Android `databases/`, iOS Application Support). */
const val DATABASE_NAME: String = "compagnon.db"

/**
 * Opens [CompagnonDatabase] over a driver created by the platform factory (`createAndroidDriver`,
 * `createNativeDriver`, `createJvmDriver`). Every column uses a plain SQLite type, so no adapter is needed.
 */
fun createDatabase(driver: SqlDriver): CompagnonDatabase = CompagnonDatabase(driver)
