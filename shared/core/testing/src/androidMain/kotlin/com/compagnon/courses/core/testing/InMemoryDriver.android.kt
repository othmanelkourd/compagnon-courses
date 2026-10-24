package com.compagnon.courses.core.testing

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.compagnon.courses.core.database.CompagnonDatabase
import java.util.Properties

actual fun createInMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(url = JdbcSqliteDriver.IN_MEMORY, properties = Properties(), schema = CompagnonDatabase.Schema)
