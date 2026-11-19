package com.compagnon.courses.core.data

import com.compagnon.courses.core.catalogformat.CatalogSnapshotDto
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.testing.SeedCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledCatalogTest {

    @Test
    fun theFirstLaunchInstallsTheBundledCatalogWithoutTheNetwork() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        assertTrue(data.catalog.installBundled("FR", "fr-FR"))

        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals(312, catalog.products.size)
        assertEquals(19, catalog.categories.size)
        assertEquals("fr-FR", catalog.locale)
        assertEquals("du lait demi-écrémé", catalog.products.first { it.slug == "lait-demi-ecreme" }.needPhrase)
        assertEquals(SeedCatalog.release, data.catalog.observeStatusNow()?.version)
    }

    @Test
    fun anEnglishDeviceGetsTheEnglishCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        data.catalog.installBundled("FR", "en")

        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals("en", catalog.locale)
        assertEquals("semi-skimmed milk", catalog.products.first { it.slug == "lait-demi-ecreme" }.name.text)
        assertEquals(SeedCatalog.english.products.size, catalog.products.size)
        // Same release, same ids: lists and history stay valid in either language.
        assertEquals(SeedCatalog.snapshot.products.map { it.id }, SeedCatalog.english.products.map { it.id })
    }

    @Test
    fun theBundledCatalogIsInstalledOnce() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.catalog.installBundled("FR", "fr-FR")
        val installedAt = data.catalog.observeStatusNow()?.installedAt

        assertFalse(data.catalog.installBundled("FR", "fr-FR"))
        assertEquals(installedAt, data.catalog.observeStatusNow()?.installedAt)
    }

    @Test
    fun anAppUpdateWithANewerBundleReplacesTheCatalogAndKeepsTheList() = runTest {
        val context = StandardTestDispatcher(testScheduler)
        val data = TestData(context)
        data.catalog.installBundled("FR", "fr-FR")
        val milk = seedProductId("lait-demi-ecreme")
        data.lists.addProduct(milk, Quantity.of(2, UnitCode("bottle")))
        val newer = SeedCatalog.snapshot.copy(
            version = SeedCatalog.release + 2,
            products = SeedCatalog.snapshot.products.map { if (it.slug == "pommes") it.copy(name = "pomme reinette") else it },
        )
        val updated = CatalogRepository(data.database, data.clock, context, FixedBundle(newer))

        assertTrue(updated.installBundled("FR", "fr-FR"))

        assertEquals(SeedCatalog.release + 2, data.catalog.observeStatusNow()?.version)
        assertEquals("pomme reinette", assertNotNull(updated.catalog()).products.first { it.slug == "pommes" }.name.text)
        assertEquals(listOf(milk), data.lists.activeList().items.map { it.productId })
    }

    @Test
    fun anotherLanguageInstallsItsOwnProjection() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.catalog.installBundled("FR", "fr-FR")

        assertTrue(data.catalog.installBundled("FR", "en"))

        assertEquals("en", assertNotNull(data.catalog.catalog()).locale)
    }

    @Test
    fun aProjectionThatIsNotBundledIsNotInstalled() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        assertFalse(data.catalog.installBundled("FR", "fr-BE"))

        assertNull(data.catalog.catalog())
    }

    @Test
    fun aLanguageChangeInstallsTheCatalogOfTheNewLanguageWhileTheAppRuns() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val context = StandardTestDispatcher(testScheduler)
        val catalog = CatalogRepository(data.database, clock = data.clock, context = context)
        val updater = CatalogUpdater(catalog, CatalogScope.forDevice("en-US"), backgroundScope)
        updater.awaitInstalled()
        assertEquals("semi-skimmed milk", assertNotNull(catalog.catalog()).milk.name.text)

        updater.switchTo(CatalogScope.forDevice("fr-FR"))
        catalog.observeStatus().first { it?.locale == "fr-FR" }

        assertEquals("fr-FR", updater.currentScope.locale)
        assertEquals("lait demi-écrémé", assertNotNull(catalog.catalog()).milk.name.text)
    }

    @Test
    fun anOlderBundleNeverReplacesTheLocalCatalog() = runTest {
        val context = StandardTestDispatcher(testScheduler)
        val data = TestData(context)
        data.catalog.installBundled("FR", "fr-FR")
        val older =
            CatalogRepository(
                data.database,
                data.clock,
                context,
                FixedBundle(SeedCatalog.snapshot(SeedCatalog.release - 1)),
            )

        assertFalse(older.installBundled("FR", "fr-FR"))

        assertEquals(SeedCatalog.release, data.catalog.observeStatusNow()?.version)
    }

    @Test
    fun theUpdaterInstallsTheBundledCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val updater = updater(data)

        updater.awaitInstalled()

        assertEquals(312, assertNotNull(data.catalog.catalog()).products.size)
    }

    @Test
    fun aConfigurationChangeInTheSameLanguageKeepsTheCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val updater = updater(data)
        updater.awaitInstalled()
        val installedAt = data.catalog.observeStatusNow()?.installedAt

        updater.switchTo(CatalogScope.DEFAULT)
        runCurrent()

        assertEquals(installedAt, data.catalog.observeStatusNow()?.installedAt)
    }

    private val Catalog.milk get() = products.first { it.slug == "lait-demi-ecreme" }

    private fun TestScope.updater(data: TestData) = CatalogUpdater(data.catalog, CatalogScope.DEFAULT, backgroundScope)

    private class FixedBundle(private val snapshot: CatalogSnapshotDto) : BundledCatalogSource {
        override fun version(
            country: String,
            locale: String,
        ): Long? = snapshot.version.takeIf { locale == snapshot.locale }

        override fun snapshot(country: String, locale: String): CatalogSnapshotDto? = snapshot.takeIf { locale == it.locale }
    }
}
