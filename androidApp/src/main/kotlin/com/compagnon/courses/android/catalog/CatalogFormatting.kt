package com.compagnon.courses.android.catalog

import androidx.annotation.StringRes
import com.compagnon.courses.android.R
import com.compagnon.courses.feature.catalog.CatalogSyncOutcome
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import java.text.NumberFormat
import java.util.Locale

/** Formats a count with the locale's digit grouping (`1 234` in French, `1,234` in English). */
internal fun formatCount(count: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(count)

/** The look of the catalog footer of the home screen. */
internal enum class CatalogFooterTone {
    /** ✓ « Catalogue à jour · 312 produits ». */
    READY,

    /** A sync is running. */
    SYNCING,

    /** Offline, the catalog of the device is used. */
    OFFLINE,

    /** No usable catalog, or it could not be saved. */
    PROBLEM,
}

/**
 * The footer line: [text], followed by the product count when [withProducts] (`%1$s` of the string).
 */
internal data class CatalogFooterText(
    val tone: CatalogFooterTone,
    @param:StringRes val text: Int,
    val withProducts: Boolean,
)

/** What the home footer says about the local catalog and its last sync. */
internal fun catalogFooter(sync: CatalogSyncUiState): CatalogFooterText = when {
    sync.syncing -> CatalogFooterText(CatalogFooterTone.SYNCING, R.string.catalog_syncing, withProducts = false)
    sync.lastOutcome == CatalogSyncOutcome.FAILED_STORAGE ->
        CatalogFooterText(CatalogFooterTone.PROBLEM, R.string.catalog_outcome_storage_failed, withProducts = false)
    sync.localVersion == null ->
        if (sync.lastOutcome == CatalogSyncOutcome.FAILED_NETWORK) {
            CatalogFooterText(CatalogFooterTone.PROBLEM, R.string.catalog_outcome_offline_none, withProducts = false)
        } else {
            CatalogFooterText(CatalogFooterTone.PROBLEM, R.string.catalog_local_none, withProducts = false)
        }
    sync.lastOutcome == CatalogSyncOutcome.FAILED_NETWORK ->
        CatalogFooterText(CatalogFooterTone.OFFLINE, R.string.catalog_footer_offline, withProducts = true)
    else -> CatalogFooterText(CatalogFooterTone.READY, R.string.catalog_footer_ready, withProducts = true)
}
