package com.compagnon.courses.feature.questionflow

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.compagnon.courses.contract.CatalogSnapshotDto
import com.compagnon.courses.core.data.BundledCatalogSource
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.HouseholdRepository
import com.compagnon.courses.core.data.IdGenerator
import com.compagnon.courses.core.data.QuestionRepository
import com.compagnon.courses.core.data.ShoppingListRepository
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.createDatabase
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.testing.MockCatalogServer
import com.compagnon.courses.core.testing.TestClock
import com.compagnon.courses.core.testing.createInMemoryDriver
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.flow.first

internal class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

/** One device: an in-memory database, the repositories, a fake server and a clock. */
internal class TestDevice(context: CoroutineContext, bundled: BundledCatalogSource = BundledCatalogSource.App) {
    val database: CompagnonDatabase = createDatabase(createInMemoryDriver())
    val server = MockCatalogServer()
    val clock = TestClock()
    private val ids = SequentialIds()
    val catalog = CatalogRepository(database, server.api, clock, context, bundled)
    val households = HouseholdRepository(database, "FR", clock, context)
    val lists = ShoppingListRepository(database, ids, clock, context)
    val questions = QuestionRepository(database, households, ids, clock, context)
    private val store = ViewModelStore()
    private var created = 0

    suspend fun sync() = catalog.sync("FR", "fr-FR")

    /** A new ViewModel, as a new screen (or a new process) would create it. */
    fun newViewModel(seed: Long = 7): QuestionSessionViewModel =
        ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    QuestionSessionViewModel(questions, catalog, lists, CatalogScope.DEFAULT, clock, Random(seed))
                }
            },
        )["vm-${created++}", QuestionSessionViewModel::class]

    /** Clears every ViewModel created so far (the screen is gone, or the process died). */
    fun clearViewModels() = store.clear()
}

internal suspend fun TestDevice.product(id: String): Product {
    val catalog: Catalog = checkNotNull(catalog.catalog())
    return catalog.products.first { it.id.value == id }
}

/**
 * Marks products « il en reste un peu »: watched products are always asked as cards, before the T2 cards, so
 * tests that need a given product as a card use this.
 */
internal fun TestDevice.watch(vararg slugs: String) {
    slugs.forEach { slug ->
        val id = com.compagnon.courses.core.testing.SeedCatalog.productId(slug)
        database.householdQueries.upsertPref(id, null, null, 0, null, 1, null, 0)
    }
}

/** The first state that is neither loading nor committing (waits for real-thread work such as HTTP). */
internal suspend fun QuestionSessionViewModel.awaitSettled(): QuestionUiState =
    state.first { it != QuestionUiState.Loading && it != QuestionUiState.Committing }

/** An app without a bundled catalog: only the server can provide one. */
internal object NoBundledCatalog : BundledCatalogSource {
    override fun version(country: String, locale: String): Long? = null

    override fun snapshot(country: String, locale: String): CatalogSnapshotDto? = null
}
