package com.compagnon.courses.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.catalogbundle.BundledCatalog
import com.compagnon.courses.core.catalogformat.CatalogJson
import com.compagnon.courses.core.catalogformat.CatalogSnapshotDto
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Instant

/** The local catalog: which projection it holds and when it was installed. */
data class LocalCatalogStatus(
    val country: String,
    val locale: String,
    val version: Long,
    val installedAt: Instant,
    val productCount: Int,
    val categoryCount: Int,
)

/** The catalog shipped in the app, per (country, locale); null when that projection is not bundled. */
interface BundledCatalogSource {
    /** The release it holds, without reading it. */
    fun version(country: String, locale: String): Long?

    /** The whole projection. */
    fun snapshot(country: String, locale: String): CatalogSnapshotDto?

    /** The catalog of `:shared:core:catalog-bundle`. */
    object App : BundledCatalogSource {
        override fun version(country: String, locale: String): Long? =
            if (BundledCatalog.contains(country, locale)) BundledCatalog.VERSION else null

        override fun snapshot(country: String, locale: String): CatalogSnapshotDto? =
            BundledCatalog.json(country, locale)?.let { json ->
                CatalogJson.decodeFromString(CatalogSnapshotDto.serializer(), json)
            }
    }
}

/**
 * The local copy of the catalog shipped in the app (ADR-012): the app always reads the local tables, and
 * [installBundled] writes the bundled catalog into them.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class CatalogRepository(
    private val database: CompagnonDatabase,
    private val clock: Clock = Clock.System,
    private val context: CoroutineContext = Dispatchers.Default,
    private val bundled: BundledCatalogSource = BundledCatalogSource.App,
) {
    private val store = CatalogStore(database)
    private val installMutex = Mutex()

    /**
     * Writes the catalog bundled in the app for ([country], [locale]) when the device has none for that projection,
     * or an older release (an app update ships a newer one). True when it was written. Never throws: a storage failure
     * keeps the previous catalog and returns false.
     */
    suspend fun installBundled(country: String, locale: String): Boolean = installMutex.withLock {
        try {
            withContext(context) {
                val version = bundled.version(country, locale) ?: return@withContext false
                val local = store.stateFor(country, locale)
                if (local != null && local.version >= version) return@withContext false
                val snapshot = bundled.snapshot(country, locale) ?: return@withContext false
                database.transaction { store.replaceWith(snapshot, installedAt = clock.now().toEpochMilliseconds()) }
                log.i { "Bundled catalog release ${snapshot.version} installed for $country/$locale" }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "The bundled catalog could not be stored for $country/$locale" }
            false
        }
    }

    /** The local catalog, re-read after every install; null until the first one. */
    fun observeCatalog(): Flow<Catalog?> = database.catalogStateQueries.selectCurrent().asFlow()
        .mapToOneOrNull(context)
        .map { state -> state?.let { store.load()?.catalog } }
        .flowOn(context)

    /** The local catalog now; null until the first install. */
    suspend fun catalog(): Catalog? = withContext(context) { store.load()?.catalog }

    fun observeStatus(): Flow<LocalCatalogStatus?> = database.catalogStateQueries.selectCurrent().asFlow()
        .mapToOneOrNull(context)
        .map { state ->
            state?.let {
                LocalCatalogStatus(
                    country = it.country,
                    locale = it.locale,
                    version = it.version,
                    installedAt = Instant.fromEpochMilliseconds(it.synced_at),
                    productCount = store.productCount().toInt(),
                    categoryCount = store.categoryCount().toInt(),
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(context)

    /** The locale of the local catalog, then each new one: another language replaced the tables. */
    fun observeLocale(): Flow<String> = observeStatus().mapNotNull { it?.locale }.distinctUntilChanged()

    /** Products whose name (any plural form) matches [query], accents and case ignored; prefix matches first. */
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<Product> =
        withContext(context) { store.search(query, limit) }

    private companion object {
        val log = Logger.withTag("CatalogRepository")
        const val DEFAULT_SEARCH_LIMIT = 20
    }
}
