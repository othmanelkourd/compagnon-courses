package com.compagnon.courses.core.data

import app.cash.turbine.test
import com.compagnon.courses.core.model.TagId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogRepositoryTest {

    @Test
    fun theInstalledCatalogIsTheWholeSeed() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        assertTrue(data.installSeed())

        val catalog = assertNotNull(data.catalog.catalog())
        assertEquals(312, catalog.products.size)
        assertEquals(19, catalog.categories.size)
        assertEquals(9, catalog.storageZones.size)
        assertEquals(33, catalog.units.units.size)
        assertEquals("fr-FR", catalog.locale)
        val milk = catalog.products.first { it.slug == "lait-demi-ecreme" }
        assertEquals("du lait demi-écrémé", milk.needPhrase)
        assertTrue(milk.unitPolicies.isNotEmpty())
    }

    @Test
    fun noCatalogBeforeTheFirstInstall() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        assertNull(data.catalog.catalog())
        assertNull(data.catalog.observeStatusNow())
    }

    @Test
    fun observeCatalogEmitsAfterEachInstall() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        data.catalog.observeCatalog().test {
            assertNull(awaitItem())
            data.installSeed()
            assertEquals("fr-FR", assertNotNull(awaitItem()).locale)
            data.catalog.installBundled("FR", "en")
            assertEquals("en", assertNotNull(awaitItem()).locale)
        }
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndRanksPrefixesFirst() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()

        val eggs = data.catalog.search("OEUF")
        assertTrue(eggs.isNotEmpty())
        assertTrue(eggs.first().name.text.startsWith("œuf"), eggs.map { it.name.text }.toString())
        assertTrue(data.catalog.search("pomm").first().slug.startsWith("pomme"))
        assertTrue(data.catalog.search("crème").any { it.slug.startsWith("creme") })
        assertEquals(emptyList(), data.catalog.search("  "))
        // Search results carry their tags (diet and allergen filters rely on them).
        val wholeMilk = data.catalog.search("lait entier").first { it.slug == "lait-entier" }
        assertTrue(TagId("contains-lactose") in wholeMilk.tags)
        assertEquals(emptyList(), data.catalog.search("%"))
        assertEquals(3, data.catalog.search("e", limit = 3).size)
    }
}
