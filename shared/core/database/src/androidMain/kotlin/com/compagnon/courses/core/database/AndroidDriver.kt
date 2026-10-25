package com.compagnon.courses.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** The app database file [name], created or migrated to [CompagnonDatabase.Schema] on first use. */
fun createAndroidDriver(context: Context, name: String = DATABASE_NAME): SqlDriver =
    AndroidSqliteDriver(schema = CompagnonDatabase.Schema, context = context, name = name)
