package com.compagnon.courses.android.catalog

import androidx.annotation.StringRes
import com.compagnon.courses.android.R
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import java.text.NumberFormat
import java.util.Locale

/** Formats a count with the locale's digit grouping (`1 234` in French, `1,234` in English). */
internal fun formatCount(count: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(count)

/** The look of the catalog footer of the home screen. */
internal enum class CatalogFooterTone {
    /** ✓ « 312 produits sur l'appareil ». */
    READY,

    /** The bundled catalog is being installed (first launch). */
    PREPARING,
}

/**
 * The footer line: [text], followed by the product count when [withProducts] (`%1$s` of the string).
 */
internal data class CatalogFooterText(
    val tone: CatalogFooterTone,
    @param:StringRes val text: Int,
    val withProducts: Boolean,
)

/** What the home footer says about the catalog of the device. */
internal fun catalogFooter(catalog: CatalogStatusUiState): CatalogFooterText = when {
    // The bundled catalog is being written (first launch): a moment, never the network.
    !catalog.ready -> CatalogFooterText(CatalogFooterTone.PREPARING, R.string.catalog_local_none, withProducts = false)
    else -> CatalogFooterText(CatalogFooterTone.READY, R.string.catalog_footer_offline, withProducts = true)
}
