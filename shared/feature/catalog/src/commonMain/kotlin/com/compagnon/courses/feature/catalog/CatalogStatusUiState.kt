package com.compagnon.courses.feature.catalog

/** State of the catalog status screen (walking skeleton of Sprint 0). */
sealed interface CatalogStatusUiState {

    data object Loading : CatalogStatusUiState

    /**
     * @property publishedAt RFC 3339 UTC timestamp as sent by the server (e.g. `2026-10-24T08:30:00Z`);
     *   formatting it for display is up to the platform UI.
     * @property locales locales that have translations in this catalog, e.g. `["fr-FR", "fr", "en"]`.
     */
    data class Loaded(
        val country: String,
        val version: Long,
        val publishedAt: String,
        val categoryCount: Int,
        val productCount: Int,
        val locales: List<String>,
    ) : CatalogStatusUiState

    /**
     * @property message user-facing French message, ready to display.
     * @property retryable whether [CatalogStatusEvent.Refresh] may succeed (network or server outage).
     */
    data class Error(
        val message: String,
        val retryable: Boolean,
    ) : CatalogStatusUiState
}

sealed interface CatalogStatusEvent {
    /** Reloads the catalog version. Ignored while a load is already in progress. */
    data object Refresh : CatalogStatusEvent
}
