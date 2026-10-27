package com.compagnon.courses.core.data

import app.cash.turbine.test
import com.compagnon.courses.contract.CatalogDeleteDto
import com.compagnon.courses.contract.CatalogUpsertsDto
import com.compagnon.courses.contract.CategoryDto
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.testing.SeedCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class CatalogRepositoryTest {

    @Test
    fun firstSyncDownloadsTheSnapshot() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        val result = data.syncSeed()

        val updated = assertIs<SyncResult.Updated>(result)
        assertEquals(SyncMethod.SNAPSHOT, updated.method)
        assertNull(updated.fromVersion)
        assertEquals(1, updated.toVersion)
        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals(312, catalog.products.size)
        assertEquals(19, catalog.categories.size)
        assertEquals(9, catalog.storageZones.size)
        assertEquals(33, catalog.units.units.size)
        assertEquals("fr-FR", catalog.locale)
        val milk = catalog.products.first { it.slug == "lait-demi-ecreme" }
        assertEquals("du lait demi-écrémé", milk.needPhrase)
        assertTrue(milk.unitPolicies.isNotEmpty())
        assertEquals(
            listOf("/v1/catalog/version?country=FR", "/v1/catalog/snapshot?country=FR&locale=fr-FR"),
            data.server.requests,
        )
    }

    @Test
    fun unchangedVersionIsUpToDateWithoutDownloading() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        data.clock.advance(2.hours)

        val result = data.syncSeed()

        assertEquals(SyncResult.UpToDate(1, assertNotNull(result.remote)), result)
        assertEquals(3, data.server.requests.size)
        assertEquals(data.clock.now, data.catalog.observeStatusNow()?.syncedAt)
    }

    @Test
    fun deltaAppliesUpsertsAndTombstones() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val renamed = seedProduct("pommes").copy(name = "pomme bio", forms = mapOf("one" to "pomme bio", "other" to "pommes bio"))
        val newCategory = CategoryDto(id = "cat-new", slug = "nouveautes", position = 999, name = "Nouveautés")
        data.server.version = SeedCatalog.version(2)
        data.server.changes[1] = changes(
            from = 1,
            to = 2,
            upserts = CatalogUpsertsDto(categories = listOf(newCategory), products = listOf(renamed)),
            deletes = listOf(CatalogDeleteDto("product", SeedCatalog.productId("bananes"))),
        )

        val result = data.syncSeed()

        assertEquals(SyncResult.Updated(1, 2, SyncMethod.DELTA, assertNotNull(result.remote)), result)
        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals(2, catalog.version)
        assertEquals(311, catalog.products.size)
        assertEquals("pomme bio", catalog.products.first { it.slug == "pommes" }.name.text)
        assertTrue(catalog.products.none { it.slug == "bananes" })
        assertTrue(catalog.categories.any { it.id == CategoryId("cat-new") })
        assertTrue(data.server.requests.none { "snapshot" in it && data.server.requests.indexOf(it) > 1 })
    }

    @Test
    fun deltaFollowsEveryPage() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        data.server.version = SeedCatalog.version(3)
        data.server.changes[1] = changes(1, 2, deletes = listOf(CatalogDeleteDto("product", SeedCatalog.productId("bananes"))), hasMore = true)
        data.server.changes[2] = changes(2, 3, deletes = listOf(CatalogDeleteDto("product", SeedCatalog.productId("pommes"))))

        val result = assertIs<SyncResult.Updated>(data.syncSeed())

        assertEquals(SyncMethod.DELTA, result.method)
        assertEquals(3, result.toVersion)
        assertEquals(310, assertNotNull(data.catalog.catalog()).products.size)
        assertTrue(data.server.requests.any { it.startsWith("/v1/catalog/changes?since=2") })
    }

    @Test
    fun snapshotRequiredFallsBackToTheSnapshot() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val smaller = SeedCatalog.snapshot(5).let { it.copy(products = it.products.drop(5)) }
        data.server.version = SeedCatalog.version(5)
        data.server.snapshot = smaller

        val result = assertIs<SyncResult.Updated>(data.syncSeed())

        assertEquals(SyncMethod.SNAPSHOT, result.method)
        assertEquals(1, result.fromVersion)
        assertEquals(5, result.toVersion)
        assertEquals(307, assertNotNull(data.catalog.catalog()).products.size)
        assertTrue(data.server.requests.any { it.startsWith("/v1/catalog/changes?since=1") })
    }

    @Test
    fun anotherLocaleReplacesTheCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        data.server.snapshot = SeedCatalog.snapshot.copy(locale = "en")

        val result = assertIs<SyncResult.Updated>(data.catalog.sync("FR", "en"))

        assertEquals(SyncMethod.SNAPSHOT, result.method)
        assertEquals("en", assertNotNull(data.catalog.catalog()).locale)
    }

    @Test
    fun offlineSyncFailsAndKeepsTheLocalCatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        data.server.offline = true

        val result = assertIs<SyncResult.Failed>(data.syncSeed())

        assertEquals(SyncFailure.CONNECTIVITY, result.failure)
        assertTrue(result.retryable)
        assertNull(result.remote)
        assertEquals(312, assertNotNull(data.catalog.catalog()).products.size)
    }

    @Test
    fun noCatalogBeforeTheFirstSync() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.server.offline = true

        assertIs<SyncResult.Failed>(data.syncSeed())
        assertNull(data.catalog.catalog())
    }

    @Test
    fun observeCatalogEmitsAfterEachSync() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        data.catalog.observeCatalog().test {
            assertNull(awaitItem())
            data.syncSeed()
            assertEquals(1, assertNotNull(awaitItem()).version)
            data.server.version = SeedCatalog.version(2)
            data.server.changes[1] = changes(1, 2)
            data.syncSeed()
            assertEquals(2, assertNotNull(awaitItem()).version)
        }
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndRanksPrefixesFirst() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()

        val eggs = data.catalog.search("OEUF")
        assertTrue(eggs.isNotEmpty())
        assertTrue(eggs.first().name.text.startsWith("œuf"), eggs.map { it.name.text }.toString())
        assertTrue(data.catalog.search("pomm").first().slug.startsWith("pomme"))
        assertTrue(data.catalog.search("crème").any { it.slug.startsWith("creme") })
        assertEquals(emptyList(), data.catalog.search("  "))
        // Search results carry their tags (diet and allergen filters rely on them).
        assertTrue(com.compagnon.courses.core.model.TagId("contains-lactose") in data.catalog.search("lait entier").first { it.slug == "lait-entier" }.tags)
        assertEquals(emptyList(), data.catalog.search("%"))
        assertEquals(3, data.catalog.search("e", limit = 3).size)
    }
}
