package com.compagnon.courses.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import co.touchlab.kermit.Logger
import com.compagnon.courses.contract.CatalogChangesDto
import com.compagnon.courses.contract.CatalogVersionDto
import com.compagnon.courses.contract.ErrorCode
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.network.CatalogApi
import com.compagnon.courses.core.network.NetworkError
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the server publishes for a country (`/v1/catalog/version`). */
data class RemoteCatalogVersion(
    val country: String,
    val version: Long,
    val publishedAt: String,
    val locales: List<String>,
    val categoryCount: Int,
    val productCount: Int,
)

/** The local catalog: which projection it holds and when it was last synced. */
data class LocalCatalogStatus(
    val country: String,
    val locale: String,
    val version: Long,
    val syncedAt: Instant,
    val productCount: Int,
    val categoryCount: Int,
)

enum class SyncMethod {
    /** The whole catalog was downloaded (first run, other projection, 410 `SNAPSHOT_REQUIRED`). */
    SNAPSHOT,

    /** Only the changes since the local version were applied. */
    DELTA,
}

enum class SyncFailure {
    /** No HTTP answer: offline, DNS, timeout… */
    CONNECTIVITY,

    /** 5xx, 408, 429. */
    SERVER,

    COUNTRY_NOT_AVAILABLE,

    APP_UPGRADE_REQUIRED,

    /** Another 4xx, or an answer that is not the expected document. */
    INVALID_RESPONSE,

    /** The local database could not be written. */
    STORAGE,
}

/** Outcome of [CatalogRepository.sync]; a failure is a value, never an exception. */
sealed interface SyncResult {
    /** The server version, when it could be read. */
    val remote: RemoteCatalogVersion?

    data class UpToDate(val version: Long, override val remote: RemoteCatalogVersion) : SyncResult

    data class Updated(
        val fromVersion: Long?,
        val toVersion: Long,
        val method: SyncMethod,
        override val remote: RemoteCatalogVersion,
    ) : SyncResult

    /** [networkError] is the HTTP failure, when the failure comes from the network. */
    data class Failed(
        val failure: SyncFailure,
        val retryable: Boolean,
        override val remote: RemoteCatalogVersion?,
        val networkError: NetworkError? = null,
    ) : SyncResult
}

/**
 * The local copy of the published catalog, offline first: the app always reads the local tables, and [sync]
 * brings them up to date.
 *
 * Sync: `version` → unchanged, nothing to do; otherwise `changes?since=` (every page) applied in one
 * transaction; on 410, on first run or for another (country, locale), the `snapshot` replaces everything.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class CatalogRepository(
    private val database: CompagnonDatabase,
    private val api: CatalogApi,
    private val clock: Clock = Clock.System,
    private val context: CoroutineContext = Dispatchers.Default,
) {
    private val store = CatalogStore(database)
    private val syncMutex = Mutex()

    /** Syncs the local catalog with the published one for ([country], [locale]). Concurrent calls run one by one. */
    suspend fun sync(country: String, locale: String): SyncResult = syncMutex.withLock {
        val remote = try {
            api.version(country).toRemote()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkError) {
            log.w(e) { "Catalog version request failed for $country" }
            return@withLock e.toFailure(remote = null)
        }
        try {
            syncTo(remote, country, locale)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkError) {
            log.w(e) { "Catalog sync failed for $country/$locale" }
            e.toFailure(remote)
        } catch (e: Exception) {
            // SQLite failures (disk full, corrupted file…): the previous catalog is kept (transaction rolled back).
            log.e(e) { "Catalog could not be stored for $country/$locale" }
            SyncResult.Failed(SyncFailure.STORAGE, retryable = true, remote = remote)
        }
    }

    private suspend fun syncTo(remote: RemoteCatalogVersion, country: String, locale: String): SyncResult {
        val local = withContext(context) { store.stateFor(country, locale) }
        val now = clock.now().toEpochMilliseconds()
        if (local != null && local.version == remote.version) {
            withContext(context) { store.touch(country, locale, now) }
            return SyncResult.UpToDate(local.version, remote)
        }
        if (local != null && local.version < remote.version) {
            val pages = deltaPages(since = local.version, country = country, locale = locale)
            if (pages != null) {
                val toVersion = pages.last().toVersion
                withContext(context) {
                    database.transaction {
                        pages.forEach(::apply)
                        store.setState(country, locale, toVersion, etag = local.etag, syncedAt = now)
                    }
                }
                return SyncResult.Updated(local.version, toVersion, SyncMethod.DELTA, remote)
            }
        }
        val snapshot = api.taggedSnapshot(country, locale)
        withContext(context) {
            database.transaction { store.replaceWith(snapshot.value, snapshot.etag, now) }
        }
        return SyncResult.Updated(local?.version, snapshot.value.version, SyncMethod.SNAPSHOT, remote)
    }

    /**
     * Every page of `/changes` from [since], or null when a snapshot is required (410, or pages that do not
     * move forward). A page with `hasMore` is followed by the request `since = page.toVersion`.
     */
    private suspend fun deltaPages(since: Long, country: String, locale: String): List<CatalogChangesDto>? {
        val pages = mutableListOf<CatalogChangesDto>()
        var from = since
        while (pages.size < MAX_DELTA_PAGES) {
            val page = try {
                api.changes(from, country, locale)
            } catch (e: NetworkError.Http) {
                if (e.status == HTTP_GONE || e.problem?.code == ErrorCode.SNAPSHOT_REQUIRED) return null
                throw e
            }
            if (page.toVersion < from || (page.hasMore && page.toVersion == from)) return null
            pages += page
            if (!page.hasMore) return pages
            from = page.toVersion
        }
        return null
    }

    private fun apply(page: CatalogChangesDto) {
        page.upserts.units.forEach(store::upsertUnit)
        page.upserts.categories.forEach(store::upsertCategory)
        page.upserts.storageZones.forEach(store::upsertZone)
        page.upserts.products.forEach(store::upsertProduct)
        page.deletes.forEach { store.delete(it.entity, it.id) }
    }

    /** The local catalog, re-read after every sync; null until the first successful sync. */
    fun observeCatalog(): Flow<Catalog?> = database.catalogStateQueries.selectCurrent().asFlow()
        .mapToOneOrNull(context)
        .map { state -> state?.let { store.load()?.catalog } }
        .flowOn(context)

    /** The local catalog now; null until the first successful sync. */
    suspend fun catalog(): Catalog? = withContext(context) { store.load()?.catalog }

    fun observeStatus(): Flow<LocalCatalogStatus?> = database.catalogStateQueries.selectCurrent().asFlow()
        .mapToOneOrNull(context)
        .map { state ->
            state?.let {
                LocalCatalogStatus(
                    country = it.country,
                    locale = it.locale,
                    version = it.version,
                    syncedAt = Instant.fromEpochMilliseconds(it.synced_at),
                    productCount = store.productCount().toInt(),
                    categoryCount = store.categoryCount().toInt(),
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(context)

    /** Products whose name (any plural form) matches [query], accents and case ignored; prefix matches first. */
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<Product> =
        withContext(context) { store.search(query, limit) }

    private companion object {
        val log = Logger.withTag("CatalogRepository")
        const val HTTP_GONE = 410
        const val MAX_DELTA_PAGES = 100
        const val DEFAULT_SEARCH_LIMIT = 20
    }
}

private fun CatalogVersionDto.toRemote() = RemoteCatalogVersion(
    country = country,
    version = version,
    publishedAt = publishedAt,
    locales = locales,
    categoryCount = categoryCount,
    productCount = productCount,
)

private fun NetworkError.toFailure(remote: RemoteCatalogVersion?): SyncResult.Failed {
    val failure = when (this) {
        is NetworkError.Connectivity -> SyncFailure.CONNECTIVITY
        is NetworkError.Serialization -> SyncFailure.INVALID_RESPONSE
        is NetworkError.Http -> when {
            problem?.code == ErrorCode.COUNTRY_NOT_AVAILABLE -> SyncFailure.COUNTRY_NOT_AVAILABLE
            problem?.code == ErrorCode.APP_UPGRADE_REQUIRED -> SyncFailure.APP_UPGRADE_REQUIRED
            isRetryable -> SyncFailure.SERVER
            else -> SyncFailure.INVALID_RESPONSE
        }
    }
    return SyncResult.Failed(failure, retryable = isRetryable, remote = remote, networkError = this)
}
