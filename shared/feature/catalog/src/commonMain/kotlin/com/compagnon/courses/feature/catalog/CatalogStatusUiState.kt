package com.compagnon.courses.feature.catalog

/**
 * The catalog held by the device (ADR-012), shown in the home footer and in « À propos ».
 *
 * @property ready false until the catalog bundled in the app is installed (first launch): « Préparation… ».
 * @property version the release held by the device, null before the first install.
 */
data class CatalogStatusUiState(
    val ready: Boolean = false,
    val version: Long? = null,
    val productCount: Int = 0,
    val categoryCount: Int = 0,
)
