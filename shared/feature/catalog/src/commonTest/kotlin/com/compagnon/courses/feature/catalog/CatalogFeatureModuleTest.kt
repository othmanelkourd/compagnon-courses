package com.compagnon.courses.feature.catalog

import app.cash.sqldelight.db.SqlDriver
import com.compagnon.courses.core.data.dataModule
import com.compagnon.courses.core.testing.createInMemoryDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogFeatureModuleTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theViewModelShowsTheBundledCatalog() = runTest {
        val app = koinApplication {
            modules(dataModule(), catalogFeatureModule, module { single<SqlDriver> { createInMemoryDriver() } })
        }

        val viewModel = app.koin.get<CatalogStatusViewModel>()

        assertTrue(viewModel.state.first { it.ready }.productCount > 0)
        // ViewModel definitions are not singletons: each screen gets its own instance.
        assertNotSame(viewModel, app.koin.get<CatalogStatusViewModel>())
        app.close()
    }
}
