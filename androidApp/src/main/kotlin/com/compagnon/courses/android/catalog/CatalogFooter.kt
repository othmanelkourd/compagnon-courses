package com.compagnon.courses.android.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import java.util.Locale

/**
 * The catalog at the foot of the home screen: « ✓ Catalogue à jour · 312 produits », or the sync / offline state.
 * Tapping it syncs the catalog again (not while a sync runs).
 */
@Composable
internal fun CatalogFooter(sync: CatalogSyncUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val footer = catalogFooter(sync)
    val locale = currentLocale()
    val text = if (footer.withProducts) {
        val products = pluralStringResource(R.plurals.catalog_product_count, sync.productCount, formatCount(sync.productCount, locale))
        stringResource(footer.text, products)
    } else {
        stringResource(footer.text)
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CompagnonShapes.Small)
            .clickable(
                enabled = !sync.syncing,
                role = Role.Button,
                onClickLabel = stringResource(R.string.catalog_sync_now),
                onClick = onRefresh,
            )
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (footer.tone) {
            CatalogFooterTone.SYNCING -> CircularProgressIndicator(
                color = CompagnonColors.Basil,
                trackColor = CompagnonColors.SegmentEmpty,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            CatalogFooterTone.READY ->
                Icon(AppIcons.Check, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(16.dp))
            CatalogFooterTone.OFFLINE ->
                Icon(AppIcons.Info, contentDescription = null, tint = CompagnonColors.InkSoft, modifier = Modifier.size(16.dp))
            CatalogFooterTone.PROBLEM ->
                Icon(AppIcons.Info, contentDescription = null, tint = CompagnonColors.TomatoText, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            style = CompagnonType.Caption,
            color = if (footer.tone == CatalogFooterTone.PROBLEM) CompagnonColors.TomatoText else CompagnonColors.InkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
