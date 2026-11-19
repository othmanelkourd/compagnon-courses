package com.compagnon.courses.core.data

import app.cash.turbine.test
import com.compagnon.courses.core.catalogformat.ProductDto
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.testing.SeedCatalog
import com.compagnon.courses.core.testing.TestClock
import com.compagnon.courses.core.testing.createInMemoryDriver
import kotlin.coroutines.CoroutineContext

/** Sequential ids (`id-1`, `id-2`…), so that tests can name rows. */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0

    override fun newId(): String = "$prefix-${++next}"
}

/** A fresh in-memory database with the repositories over it and a [TestClock]. */
class TestData(val context: CoroutineContext) {
    val database: CompagnonDatabase = createDatabase(createInMemoryDriver())
    val clock = TestClock()
    val ids = SequentialIds()
    val catalog = CatalogRepository(database, clock, context)
    val households = HouseholdRepository(database, "FR", clock, context)
    val lists = ShoppingListRepository(database, ids, clock, context)
    val questions = QuestionRepository(database, households, ids, clock, context)

    /** Installs the bundled seed (FR, fr-FR). */
    suspend fun installSeed(): Boolean = catalog.installBundled("FR", "fr-FR")
}

fun seedProduct(slug: String): ProductDto = SeedCatalog.snapshot.products.first { it.slug == slug }

fun seedProductId(slug: String): ProductId = ProductId(SeedCatalog.productId(slug))

/** The local catalog status now (the first value of [CatalogRepository.observeStatus]). */
suspend fun CatalogRepository.observeStatusNow(): LocalCatalogStatus? {
    var status: LocalCatalogStatus? = null
    observeStatus().test {
        status = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return status
}
