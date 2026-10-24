package com.compagnon.courses.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver

/**
 * The app database file [name], created or migrated to [CompagnonDatabase.Schema] on first use.
 *
 * The `Shared` framework is static: the app target must link SQLite itself (`-lsqlite3` in Other Linker Flags).
 */
fun createNativeDriver(name: String = DATABASE_NAME): SqlDriver =
    NativeSqliteDriver(schema = CompagnonDatabase.Schema, name = name)

/** A private in-memory database with the current schema (tests, previews). */
fun createInMemoryNativeDriver(): SqlDriver = inMemoryDriver(CompagnonDatabase.Schema)
