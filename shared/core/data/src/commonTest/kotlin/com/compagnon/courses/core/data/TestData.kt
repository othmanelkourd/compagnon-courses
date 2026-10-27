package com.compagnon.courses.core.data

import app.cash.turbine.test
import com.compagnon.courses.contract.CatalogChangesDto
import com.compagnon.courses.contract.CatalogDeleteDto
import com.compagnon.courses.contract.CatalogUpsertsDto
import com.compagnon.courses.contract.ProductDto
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.testing.MockCatalogServer
import com.compagnon.courses.core.testing.SeedCatalog
import com.compagnon.courses.core.testing.TestClock
import com.compagnon.courses.core.testing.createInMemoryDriver
import kotlin.coroutines.CoroutineContext

/** Sequential ids (`id-1`, `id-2`…), so that tests can name rows. */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0

    override fun newId(): String = "$prefix-${++next}"
}

/** A fresh in-memory database with the repositories over it, a [MockCatalogServer] and a [TestClock]. */
class TestData(val context: CoroutineContext) {
    val database: CompagnonDatabase = createDatabase(createInMemoryDriver())
    val server = MockCatalogServer()
    val clock = TestClock()
    val ids = SequentialIds()
    val catalog = CatalogRepository(database, server.api, clock, context)
    val households = HouseholdRepository(database, "FR", clock, context)
    val lists = ShoppingListRepository(database, ids, clock, context)
    val questions = QuestionRepository(database, households, ids, clock, context)

    suspend fun syncSeed(): SyncResult = catalog.sync("FR", "fr-FR")
}

fun seedProduct(slug: String): ProductDto = SeedCatalog.snapshot.products.first { it.slug == slug }

fun seedProductId(slug: String): ProductId = ProductId(SeedCatalog.productId(slug))

fun changes(
    from: Long,
    to: Long,
    products: List<ProductDto> = emptyList(),
    deletes: List<CatalogDeleteDto> = emptyList(),
    hasMore: Boolean = false,
    upserts: CatalogUpsertsDto = CatalogUpsertsDto(products = products),
): CatalogChangesDto = CatalogChangesDto(from, to, upserts, deletes, hasMore)

/** The local catalog status now (the first value of [CatalogRepository.observeStatus]). */
suspend fun CatalogRepository.observeStatusNow(): LocalCatalogStatus? {
    var status: LocalCatalogStatus? = null
    observeStatus().test {
        status = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return status
}
