package com.compagnon.courses.core.testing

import app.cash.sqldelight.db.SqlDriver

/** A fresh, private in-memory database with the current schema of `CompagnonDatabase`. */
expect fun createInMemoryDriver(): SqlDriver
