package com.compagnon.courses.feature.catalog

import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.testing.createInMemoryDriver
import kotlinx.coroutines.test.TestScope

/** A [CatalogRepository] over an in-memory database, with the catalog bundled in the app. */
internal fun repository(): CatalogRepository = CatalogRepository(createDatabase(createInMemoryDriver()))

/**
 * The status ViewModel over [repository], with its own [CatalogUpdater] running in the test's background scope (the
 * app's one lives as long as the app).
 */
internal fun TestScope.statusViewModel(repository: CatalogRepository): CatalogStatusViewModel =
    CatalogStatusViewModel(repository, CatalogUpdater(repository, CatalogScope.DEFAULT, backgroundScope))
