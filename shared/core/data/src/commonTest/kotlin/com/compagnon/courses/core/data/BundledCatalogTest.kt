package com.compagnon.courses.core.data

import com.compagnon.courses.contract.CatalogSnapshotDto
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.testing.SeedCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledCatalogTest {

    @Test
    fun theFirstLaunchInstallsTheBundledCatalogWithoutTheNetwork() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.server.offline = true

        assertTrue(data.catalog.installBundled("FR", "fr-FR"))

        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals(312, catalog.products.size)
        assertEquals(19, catalog.categories.size)
        assertEquals("fr-FR", catalog.locale)
        assertEquals("du lait demi-écrémé", catalog.products.first { it.slug == "lait-demi-ecreme" }.needPhrase)
        assertEquals(SeedCatalog.release, data.catalog.observeStatusNow()?.version)
        assertEquals(emptyList(), data.server.requests)
    }

    @Test
    fun anEnglishDeviceGetsTheEnglishCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        data.catalog.installBundled("FR", "en")

        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals("en", catalog.locale)
        assertEquals("semi-skimmed milk", catalog.products.first { it.slug == "lait-demi-ecreme" }.name.text)
        assertEquals(SeedCatalog.english.products.size, catalog.products.size)
        // Same release, same ids: the deltas of the server apply to either language.
        assertEquals(SeedCatalog.snapshot.products.map { it.id }, SeedCatalog.english.products.map { it.id })
    }

    @Test
    fun theBundledCatalogIsInstalledOnce() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.catalog.installBundled("FR", "fr-FR")
        val installedAt = data.catalog.observeStatusNow()?.syncedAt

        assertFalse(data.catalog.installBundled("FR", "fr-FR"))
        assertEquals(installedAt, data.catalog.observeStatusNow()?.syncedAt)
    }

    @Test
    fun aNewerReleaseFromTheServerIsNeverReplacedByTheBundle() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.catalog.installBundled("FR", "fr-FR")
        val renamed = seedProduct("pommes").copy(name = "pomme bio", forms = mapOf("one" to "pomme bio", "other" to "pommes bio"))
        val release = SeedCatalog.release
        data.server.version = SeedCatalog.version(release + 1)
        data.server.changes[release] = changes(from = release, to = release + 1, products = listOf(renamed))

        val result = assertIs<SyncResult.Updated>(data.syncSeed())

        assertEquals(SyncMethod.DELTA, result.method)
        assertFalse(data.catalog.installBundled("FR", "fr-FR"))
        assertEquals(SeedCatalog.release + 1, data.catalog.observeStatusNow()?.version)
        assertEquals("pomme bio", assertNotNull(data.catalog.catalog()).products.first { it.slug == "pommes" }.name.text)
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
        val updated = CatalogRepository(data.database, data.server.api, data.clock, context, FixedBundle(newer))

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
    fun aProjectionThatIsNotBundledIsLeftToTheServer() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        assertFalse(data.catalog.installBundled("FR", "fr-BE"))

        assertNull(data.catalog.catalog())
    }

    @Test
    fun theUpdaterInstallsTheBundleThenChecksTheServer() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val updater = updater(data)

        updater.start()
        updater.awaitInstalled()
        assertEquals(312, assertNotNull(data.catalog.catalog()).products.size)

        val check = updater.check.first { it.result != null }
        assertIs<SyncResult.UpToDate>(check.result)
        assertFalse(check.running)
        assertEquals(listOf("/v1/catalog/version?country=FR"), data.server.requests)
    }

    @Test
    fun theUpdaterAppliesANewerReleaseAtLaunch() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val renamed = seedProduct("pommes").copy(name = "pomme bio", forms = mapOf("one" to "pomme bio", "other" to "pommes bio"))
        val release = SeedCatalog.release
        data.server.version = SeedCatalog.version(release + 1)
        data.server.changes[release] = changes(from = release, to = release + 1, products = listOf(renamed))

        val updater = updater(data)
        updater.start()

        val updated = assertIs<SyncResult.Updated>(updater.check.first { it.result != null }.result)
        assertEquals(SeedCatalog.release, updated.fromVersion)
        assertEquals(SyncMethod.DELTA, updated.method)
        assertEquals("pomme bio", assertNotNull(data.catalog.catalog()).products.first { it.slug == "pommes" }.name.text)
    }

    @Test
    fun anUnreachableServerKeepsTheLocalCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.server.offline = true
        val updater = updater(data)

        updater.start()
        updater.start()

        val failed = assertIs<SyncResult.Failed>(updater.check.first { it.result != null }.result)
        assertEquals(SyncFailure.CONNECTIVITY, failed.failure)
        assertEquals(312, assertNotNull(data.catalog.catalog()).products.size)

        data.server.offline = false
        updater.checkNow()
        advanceUntilIdle()
        assertIs<SyncResult.UpToDate>(updater.check.first { it.result is SyncResult.UpToDate }.result)
    }

    @Test
    fun aBuildWithoutServerOnlyInstallsTheBundledCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val context = StandardTestDispatcher(testScheduler)
        val catalog = CatalogRepository(data.database, api = null, clock = data.clock, context = context)
        val updater = CatalogUpdater(catalog, CatalogScope.DEFAULT, backgroundScope)

        assertFalse(updater.checksServer)
        updater.start()
        updater.awaitInstalled()
        updater.checkNow()
        advanceUntilIdle()

        assertEquals(312, assertNotNull(catalog.catalog()).products.size)
        assertEquals(CatalogCheck(), updater.check.value)
        assertIs<SyncResult.Failed>(catalog.sync("FR", "fr-FR"))
        assertEquals(emptyList(), data.server.requests)
    }

    @Test
    fun aLanguageChangeInstallsTheCatalogOfTheNewLanguageWhileTheAppRuns() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val context = StandardTestDispatcher(testScheduler)
        val catalog = CatalogRepository(data.database, api = null, clock = data.clock, context = context)
        val updater = CatalogUpdater(catalog, CatalogScope.forDevice("en-US"), backgroundScope)
        updater.awaitInstalled()
        assertEquals("semi-skimmed milk", assertNotNull(catalog.catalog()).milk.name.text)

        updater.switchTo(CatalogScope.forDevice("fr-FR"))
        catalog.observeStatus().first { it?.locale == "fr-FR" }

        assertEquals("fr-FR", updater.currentScope.locale)
        assertEquals("lait demi-écrémé", assertNotNull(catalog.catalog()).milk.name.text)
    }

    @Test
    fun aLanguageChangeChecksTheServerForTheNewLanguage() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.server.offline = true
        val updater = updater(data)
        updater.start()
        updater.check.first { it.result != null }

        data.server.offline = false
        updater.switchTo(CatalogScope.forDevice("en-US"))

        assertIs<SyncResult.UpToDate>(updater.check.first { it.result is SyncResult.UpToDate }.result)
        assertEquals("en", assertNotNull(data.catalog.catalog()).locale)
    }

    @Test
    fun aConfigurationChangeInTheSameLanguageDoesNothing() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.server.offline = true
        val updater = updater(data)
        updater.start()
        updater.check.first { it.result != null }

        data.server.offline = false
        updater.switchTo(CatalogScope.DEFAULT)
        runCurrent()

        assertFalse(updater.check.value.running)
        assertIs<SyncResult.Failed>(updater.check.value.result)
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
