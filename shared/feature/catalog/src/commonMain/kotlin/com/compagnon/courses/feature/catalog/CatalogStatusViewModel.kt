package com.compagnon.courses.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.LocalCatalogStatus
import com.compagnon.courses.core.data.RemoteCatalogVersion
import com.compagnon.courses.core.data.SyncFailure
import com.compagnon.courses.core.data.SyncMethod
import com.compagnon.courses.core.data.SyncResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Catalog status: what the server publishes for [country] ([state]) and the local, offline copy ([sync]).
 * Creating it syncs the local catalog for ([country], [locale]); [CatalogStatusEvent.Refresh] syncs again.
 *
 * Failures are values ([CatalogStatusUiState.Error], [CatalogSyncOutcome]); this ViewModel never throws.
 */
class CatalogStatusViewModel(
    private val catalog: CatalogRepository,
    private val country: String,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow<CatalogStatusUiState>(CatalogStatusUiState.Loading)
    val state: StateFlow<CatalogStatusUiState> = _state.asStateFlow()

    private val _sync = MutableStateFlow(CatalogSyncUiState())

    /** The local catalog and its last sync. */
    val sync: StateFlow<CatalogSyncUiState> = _sync.asStateFlow()

    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                catalog.observeStatus().collect { local -> _sync.update { it.withLocal(local) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Local catalog status could not be read" }
            }
        }
        load()
    }

    fun onEvent(event: CatalogStatusEvent) {
        when (event) {
            CatalogStatusEvent.Refresh -> load()
        }
    }

    private fun load() {
        if (syncJob?.isActive == true) return
        _state.value = CatalogStatusUiState.Loading
        _sync.update { it.copy(syncing = true) }
        syncJob = viewModelScope.launch {
            val result = try {
                catalog.sync(country, locale)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // CatalogRepository.sync returns failures as values: anything else is a bug, but must not crash.
                log.e(e) { "Unexpected failure while syncing the catalog for $country/$locale" }
                null
            }
            _state.value = when {
                result == null -> CatalogStatusUiState.Error(message = CatalogStatusMessages.UNEXPECTED, retryable = true)
                result.remote != null -> checkNotNull(result.remote).toLoadedState()
                result is SyncResult.Failed && result.networkError != null -> checkNotNull(result.networkError).toErrorState()
                else -> CatalogStatusUiState.Error(message = CatalogStatusMessages.UNEXPECTED, retryable = true)
            }
            _sync.update { it.copy(syncing = false, lastOutcome = result?.toOutcome() ?: CatalogSyncOutcome.FAILED_NETWORK) }
        }
    }

    private companion object {
        val log = Logger.withTag("CatalogStatusViewModel")
    }
}

internal fun RemoteCatalogVersion.toLoadedState(): CatalogStatusUiState.Loaded =
    CatalogStatusUiState.Loaded(
        country = country,
        version = version,
        publishedAt = publishedAt,
        categoryCount = categoryCount,
        productCount = productCount,
        locales = locales,
    )

private fun CatalogSyncUiState.withLocal(local: LocalCatalogStatus?): CatalogSyncUiState = copy(
    localVersion = local?.version,
    lastSyncedAt = local?.syncedAt?.toString(),
    productCount = local?.productCount ?: 0,
    categoryCount = local?.categoryCount ?: 0,
)

private fun SyncResult.toOutcome(): CatalogSyncOutcome = when (this) {
    is SyncResult.UpToDate -> CatalogSyncOutcome.UP_TO_DATE
    is SyncResult.Updated -> when (method) {
        SyncMethod.DELTA -> CatalogSyncOutcome.UPDATED_WITH_CHANGES
        SyncMethod.SNAPSHOT -> CatalogSyncOutcome.DOWNLOADED
    }
    is SyncResult.Failed -> if (failure == SyncFailure.STORAGE) CatalogSyncOutcome.FAILED_STORAGE else CatalogSyncOutcome.FAILED_NETWORK
}
