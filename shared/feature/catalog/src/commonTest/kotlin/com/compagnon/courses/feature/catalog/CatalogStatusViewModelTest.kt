package com.compagnon.courses.feature.catalog

import com.compagnon.courses.core.testing.SeedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogStatusViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theBundledCatalogIsInstalledAndShown() = runTest {
        val viewModel = statusViewModel(repository())

        val ready = viewModel.state.first { it.ready }

        assertEquals(SeedCatalog.release, ready.version)
        assertEquals(SeedCatalog.snapshot.products.size, ready.productCount)
        assertEquals(SeedCatalog.snapshot.categories.size, ready.categoryCount)
    }

    @Test
    fun theCatalogIsNotReadyBeforeTheFirstInstall() {
        val initial = CatalogStatusUiState()

        assertFalse(initial.ready)
        assertEquals(null, initial.version)
    }
}
