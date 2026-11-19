package com.compagnon.courses.android.catalog

import com.compagnon.courses.android.R
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFooterTextTest {

    @Test
    fun anInstalledCatalogSaysWhatTheDeviceHolds() {
        assertEquals(
            CatalogFooterText(CatalogFooterTone.READY, R.string.catalog_footer_offline, withProducts = true),
            catalogFooter(CatalogStatusUiState(ready = true, version = 1, productCount = 312)),
        )
    }

    @Test
    fun theFirstLaunchPreparesTheBundledCatalog() {
        assertEquals(
            CatalogFooterText(CatalogFooterTone.PREPARING, R.string.catalog_local_none, withProducts = false),
            catalogFooter(CatalogStatusUiState()),
        )
    }
}
