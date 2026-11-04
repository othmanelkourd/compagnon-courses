package com.compagnon.courses.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.CatalogCheck
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.data.LocalCatalogStatus
import com.compagnon.courses.core.data.RemoteCatalogVersion
import com.compagnon.courses.core.data.SyncFailure
import com.compagnon.courses.core.data.SyncMethod
import com.compagnon.courses.core.data.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Catalog status: what the server publishes for [country] ([state]) and the local, offline copy ([sync]).
 *
 * It does not sync by itself: the [CatalogUpdater] installs the bundled catalog and checks the server at app launch
 * (creating this ViewModel starts it, if the app did not); this ViewModel shows the outcome of the last check, and
 * [CatalogStatusEvent.Refresh] checks again. Without server ([CatalogSyncUiState.canCheck] false), [state] stays
 * [CatalogStatusUiState.Loading] and only [sync] is meaningful.
 *
 * Failures are values ([CatalogStatusUiState.Error], [CatalogSyncOutcome]); this ViewModel never throws.
 */
class CatalogStatusViewModel(
    private val catalog: CatalogRepository,
    private val updater: CatalogUpdater,
    private val country: String,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow<CatalogStatusUiState>(CatalogStatusUiState.Loading)
    val state: StateFlow<CatalogStatusUiState> = _state.asStateFlow()

    private val _sync = MutableStateFlow(CatalogSyncUiState(canCheck = updater.checksServer))

    /** The local catalog and the last check of the server. */
    val sync: StateFlow<CatalogSyncUiState> = _sync.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                catalog.observeStatus().collect { local -> _sync.update { it.withLocal(local).withCheck(updater.check.value) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Local catalog status could not be read for $country/$locale" }
            }
        }
        viewModelScope.launch {
            updater.check.collect { check ->
                _sync.update { it.withCheck(check) }
                val result = check.result
                // Loading until the first check ends; a later check keeps showing the last outcome until it ends.
                if (!check.running && result != null) _state.value = result.toState()
            }
        }
        updater.start()
    }

    fun onEvent(event: CatalogStatusEvent) {
        when (event) {
            CatalogStatusEvent.Refresh -> updater.checkNow()
        }
    }

    private companion object {
        val log = Logger.withTag("CatalogStatusViewModel")
    }
}

/**
 * « Mise à jour du catalogue… » only while the device has no catalog yet: with a local catalog, a check runs
 * quietly and the line keeps saying what the device holds.
 */
private fun CatalogSyncUiState.withCheck(check: CatalogCheck): CatalogSyncUiState = copy(
    syncing = check.running && localVersion == null,
    lastOutcome = check.result?.toOutcome(),
)

private fun SyncResult.toState(): CatalogStatusUiState = when {
    remote != null -> checkNotNull(remote).toLoadedState()
    this is SyncResult.Failed && networkError != null -> checkNotNull(networkError).toErrorState()
    else -> CatalogStatusUiState.Error(message = CatalogStatusMessages.UNEXPECTED, retryable = true)
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
