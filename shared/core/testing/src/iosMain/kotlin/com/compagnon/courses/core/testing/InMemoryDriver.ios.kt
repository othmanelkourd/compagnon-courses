package com.compagnon.courses.core.testing

import app.cash.sqldelight.db.SqlDriver
import com.compagnon.courses.core.database.createInMemoryNativeDriver

actual fun createInMemoryDriver(): SqlDriver = createInMemoryNativeDriver()
