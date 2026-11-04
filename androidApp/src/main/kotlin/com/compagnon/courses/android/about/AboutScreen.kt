package com.compagnon.courses.android.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.BuildConfig
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.currentLocale
import com.compagnon.courses.android.catalog.formatCount
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.CompagnonIconButton
import com.compagnon.courses.android.ui.components.SectionBlock
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.feature.about.AboutLinks
import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** « À propos »: the app and its version, the catalog of the device, what happens to the data, links and licenses. */
@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    scope: CatalogScope = koinInject(),
    catalogViewModel: CatalogStatusViewModel = koinViewModel { parametersOf(scope.country) },
) {
    val sync by catalogViewModel.sync.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AboutContent(
        sync = sync,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        privacyPolicyUrl = AboutLinks.PRIVACY_POLICY_URL,
        supportEmail = AboutLinks.SUPPORT_EMAIL,
        onBack = onBack,
        onOpenLicenses = onOpenLicenses,
        onOpenUrl = { context.open(Intent(Intent.ACTION_VIEW, it.toUri())) },
        onWrite = { context.open(Intent(Intent.ACTION_SENDTO, "mailto:$it".toUri())) },
    )
}

/** Stateless « À propos »: a link row is hidden while its address is empty ([AboutLinks]). */
@Composable
internal fun AboutContent(
    sync: CatalogSyncUiState,
    versionName: String,
    versionCode: Int,
    privacyPolicyUrl: String,
    supportEmail: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onWrite: (String) -> Unit,
) {
    AboutScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(painterResource(R.drawable.ocimio_logo), contentDescription = null, modifier = Modifier.size(72.dp))
            Text(stringResource(R.string.app_name), style = CompagnonType.SheetTitle, color = CompagnonColors.Ink)
            Text(
                text = stringResource(R.string.about_version, versionName, versionCode.toString()),
                style = CompagnonType.Secondary,
                color = CompagnonColors.InkSoft,
            )
        }
        SectionBlock(title = stringResource(R.string.about_data_title)) {
            Text(
                text = stringResource(R.string.about_data_body),
                style = CompagnonType.BodyCompact,
                color = CompagnonColors.Ink,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (sync.localVersion != null) {
            SectionBlock(title = stringResource(R.string.about_catalog_title)) {
                val products = pluralStringResource(
                    R.plurals.catalog_product_count,
                    sync.productCount,
                    formatCount(sync.productCount, currentLocale()),
                )
                Text(
                    text = stringResource(R.string.about_catalog_body, products, sync.localVersion.toString()),
                    style = CompagnonType.BodyCompact,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        SectionBlock(title = stringResource(R.string.about_more_title)) {
            if (privacyPolicyUrl.isNotEmpty()) {
                LinkRow(stringResource(R.string.about_privacy)) { onOpenUrl(privacyPolicyUrl) }
                BlockDivider()
            }
            if (supportEmail.isNotEmpty()) {
                LinkRow(stringResource(R.string.about_contact)) { onWrite(supportEmail) }
                BlockDivider()
            }
            LinkRow(stringResource(R.string.about_licenses), onClick = onOpenLicenses)
        }
    }
}

/** The frame of « À propos » and « Licences »: back button, title, scrolling content. */
@Composable
internal fun AboutScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CompagnonColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = CompagnonSpacing.Top),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompagnonIconButton(
                    icon = AppIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.onboarding_back),
                    onClick = onBack,
                )
                Text(
                    text = title,
                    style = CompagnonType.SheetTitle,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CompagnonSpacing.Screen)
                    .padding(top = 8.dp, bottom = CompagnonSpacing.Bottom + 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = CompagnonType.ListRow,
            color = CompagnonColors.Ink,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = CompagnonColors.InkSoft,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Opens [intent] in another app; nothing happens when no app can handle it (no browser, no mail app). */
private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing to open it with.
    }
}
