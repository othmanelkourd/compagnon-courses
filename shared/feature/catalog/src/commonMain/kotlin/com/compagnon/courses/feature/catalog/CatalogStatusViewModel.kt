package com.compagnon.courses.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.contract.CatalogVersionDto
import com.compagnon.courses.core.network.CatalogApi
import com.compagnon.courses.core.network.NetworkError
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shows which catalog version the server publishes for [country]. Loads on creation.
 *
 * Failures are values of [state] ([CatalogStatusUiState.Error]); this ViewModel never throws.
 */
class CatalogStatusViewModel(
    private val api: CatalogApi,
    private val country: String,
) : ViewModel() {

    private val _state = MutableStateFlow<CatalogStatusUiState>(CatalogStatusUiState.Loading)
    val state: StateFlow<CatalogStatusUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: CatalogStatusEvent) {
        when (event) {
            CatalogStatusEvent.Refresh -> load()
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        _state.value = CatalogStatusUiState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                api.version(country).toLoadedState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkError) {
                log.w(e) { "Catalog version request failed for $country" }
                e.toErrorState()
            } catch (e: Exception) {
                // CatalogApi only throws NetworkError: anything else is a bug, but must not crash the screen.
                log.e(e) { "Unexpected failure while loading the catalog version for $country" }
                CatalogStatusUiState.Error(message = CatalogStatusMessages.UNEXPECTED, retryable = true)
            }
        }
    }

    private companion object {
        val log = Logger.withTag("CatalogStatusViewModel")
    }
}

internal fun CatalogVersionDto.toLoadedState(): CatalogStatusUiState.Loaded =
    CatalogStatusUiState.Loaded(
        country = country,
        version = version,
        publishedAt = publishedAt,
        categoryCount = categoryCount,
        productCount = productCount,
        locales = locales,
    )
