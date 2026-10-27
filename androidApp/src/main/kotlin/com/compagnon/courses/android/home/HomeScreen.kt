package com.compagnon.courses.android.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.CatalogFooter
import com.compagnon.courses.android.catalog.currentLocale
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.onboarding.OnboardingTexts
import com.compagnon.courses.android.ui.components.ProgressBar
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.components.softShadow
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.feature.catalog.CatalogStatusEvent
import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.lists.ShoppingListViewModel
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import java.time.LocalDate
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * « Accueil »: « Faire le point » (a question session), « Ma liste » with its count, « Mon foyer » with the
 * [household] summary, and the catalog status. Creating the catalog ViewModel syncs the local catalog.
 */
@Composable
fun HomeScreen(
    household: HouseholdSummaryUi?,
    onStartSession: () -> Unit,
    onOpenList: () -> Unit,
    onOpenHousehold: () -> Unit,
    modifier: Modifier = Modifier,
    scope: CatalogScope = koinInject(),
    catalogViewModel: CatalogStatusViewModel = koinViewModel { parametersOf(scope.country) },
    listViewModel: ShoppingListViewModel = koinViewModel(),
) {
    val sync by catalogViewModel.sync.collectAsStateWithLifecycle()
    val list by listViewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        sync = sync,
        list = list,
        household = household,
        onStartSession = onStartSession,
        onOpenList = onOpenList,
        onOpenHousehold = onOpenHousehold,
        onRefresh = { catalogViewModel.onEvent(CatalogStatusEvent.Refresh) },
        modifier = modifier,
    )
}

/**
 * Stateless home screen: today's date and the headline, the big « Faire le point » card, « Ma liste », « Mon foyer »
 * (when the [household] is known), and the catalog at the foot. Only data that exists is shown (no first name, no
 * planned day, no statistics).
 */
@Composable
internal fun HomeContent(
    sync: CatalogSyncUiState,
    list: ShoppingListUiState,
    onStartSession: () -> Unit,
    onOpenList: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    household: HouseholdSummaryUi? = null,
    onOpenHousehold: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompagnonColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompagnonSpacing.Screen)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val locale = currentLocale()
            val pattern = stringResource(R.string.home_date_pattern)
            val date = remember(today, pattern, locale) { formatHomeDate(today, pattern, locale) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = date,
                    style = CompagnonType.Secondary.copy(fontWeight = FontWeight.Medium),
                    color = CompagnonColors.InkSoft,
                )
                Text(
                    text = stringResource(R.string.home_headline),
                    style = CompagnonType.ScreenTitleLarge,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            StartSessionCard(onClick = onStartSession)
            ListCard(list = list, onOpenList = onOpenList)
            if (household != null) HouseholdCard(household = household, onClick = onOpenHousehold)
        }
        CatalogFooter(
            sync = sync,
            onRefresh = onRefresh,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = CompagnonSpacing.Screen)
                .padding(bottom = CompagnonSpacing.Bottom),
        )
    }
}

/** The big Basilic card: « Faire le point », the promise, « ≈ 2 min · 12 questions ». */
@Composable
private fun StartSessionCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(CompagnonShapes.Card, radius = 24.dp, offsetY = 10.dp, alpha = 0.25f, color = CompagnonColors.Basil)
            .clip(CompagnonShapes.Card)
            .background(CompagnonColors.Basil)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_start_session),
                    style = CompagnonType.ScreenTitle.copy(lineHeight = 34.sp),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Text(
                text = stringResource(R.string.home_intro),
                style = CompagnonType.Body,
                color = Color.White.copy(alpha = 0.88f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CardFact(icon = AppIcons.Clock, text = stringResource(R.string.home_duration))
            CardFact(icon = AppIcons.ListBullets, text = stringResource(R.string.home_questions))
        }
    }
}

@Composable
private fun CardFact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text = text, style = CompagnonType.Secondary.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

/** « Ma liste »: basket tile, « 16 articles · 4 cochés », the progress of the checks, chevron. */
@Composable
private fun ListCard(list: ShoppingListUiState, onOpenList: () -> Unit) {
    val loaded = list as? ShoppingListUiState.Loaded
    val summary = when (list) {
        is ShoppingListUiState.Loaded -> if (list.isEmpty) {
            stringResource(R.string.home_list_empty)
        } else {
            val checked = list.itemCount - list.remainingCount
            stringResource(
                R.string.home_list_summary,
                pluralStringResource(R.plurals.home_list_items, list.itemCount, list.itemCount),
                pluralStringResource(R.plurals.home_list_checked, checked, checked),
            )
        }
        ShoppingListUiState.Loading -> stringResource(R.string.home_list_loading)
        is ShoppingListUiState.Error -> stringResource(R.string.home_list_error)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.BlockLarge)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, CompagnonShapes.BlockLarge)
            .clickable(role = Role.Button, onClick = onOpenList)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CompagnonShapes.Field)
                .background(CompagnonColors.BasilLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Basket, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.home_my_list),
                style = CompagnonType.ListRow.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                color = CompagnonColors.Ink,
            )
            Text(text = summary, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
            if (loaded != null && !loaded.isEmpty) {
                ProgressBar(fraction = (loaded.itemCount - loaded.remainingCount) / loaded.itemCount.toFloat())
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = CompagnonColors.InkMuted, modifier = Modifier.size(20.dp))
    }
}

/** « Mon foyer »: house tile, « 4 personnes · Halal · 14 essentiels », chevron. */
@Composable
private fun HouseholdCard(household: HouseholdSummaryUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.BlockLarge)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, CompagnonShapes.BlockLarge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CompagnonShapes.Field)
                .background(CompagnonColors.BasilLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Home, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.household_title),
                style = CompagnonType.ListRow.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                color = CompagnonColors.Ink,
            )
            Text(
                text = OnboardingTexts.homeSummary(household).resolve(),
                style = CompagnonType.Secondary,
                color = CompagnonColors.InkSoft,
            )
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = CompagnonColors.InkMuted, modifier = Modifier.size(20.dp))
    }
}
