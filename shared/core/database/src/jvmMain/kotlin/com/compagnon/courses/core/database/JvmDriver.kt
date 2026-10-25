package com.compagnon.courses.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * A JDBC SQLite database with the current schema; in memory by default. The JVM target only runs host tests.
 */
fun createJvmDriver(url: String = JdbcSqliteDriver.IN_MEMORY): SqlDriver =
    JdbcSqliteDriver(url = url, properties = Properties(), schema = CompagnonDatabase.Schema)
