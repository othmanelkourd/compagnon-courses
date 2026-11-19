package com.compagnon.courses.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.data.LocalCatalogStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The catalog held by the device ([state]). Creating it starts the [CatalogUpdater] if the app did not, so that the
 * bundled catalog gets installed.
 *
 * Failures are logged and leave the last state; this ViewModel never throws.
 */
class CatalogStatusViewModel(
    private val catalog: CatalogRepository,
    updater: CatalogUpdater,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogStatusUiState())
    val state: StateFlow<CatalogStatusUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                catalog.observeStatus().collect { local -> _state.value = local.toUiState() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Local catalog status could not be read" }
            }
        }
        updater.start()
    }

    private companion object {
        val log = Logger.withTag("CatalogStatusViewModel")
    }
}

private fun LocalCatalogStatus?.toUiState(): CatalogStatusUiState = when (this) {
    null -> CatalogStatusUiState()
    else -> CatalogStatusUiState(
        ready = true,
        version = version,
        productCount = productCount,
        categoryCount = categoryCount,
    )
}
