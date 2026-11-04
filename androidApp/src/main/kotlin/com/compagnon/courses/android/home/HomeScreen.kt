package com.compagnon.courses.android.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.compagnon.courses.android.onboarding.OnboardingTexts
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.CompagnonIconButton
import com.compagnon.courses.android.ui.components.IconButtonStyle
import com.compagnon.courses.android.ui.components.ProgressBar
import com.compagnon.courses.android.ui.components.pressScale
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.components.softShadow
import com.compagnon.courses.android.ui.illustration.Halo
import com.compagnon.courses.android.ui.illustration.Illustration
import com.compagnon.courses.android.ui.illustration.LeafPattern
import com.compagnon.courses.android.ui.illustration.PatternLeaf
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.feature.catalog.CatalogStatusEvent
import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.lists.ShoppingListViewModel
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

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
    onOpenAbout: () -> Unit = {},
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
        onOpenAbout = onOpenAbout,
        modifier = modifier,
    )
}

/**
 * Stateless home screen: today's date and the headline, the big « Faire le point » card, « Ma liste », « Mon foyer »
 * (when the [household] is known), and the catalog at the foot, on two halos. Only data that exists is shown (no
 * first name, no planned day, no statistics). The blocks come in one after the other (animation 2).
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
    onOpenAbout: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CompagnonColors.Background),
    ) {
        Halo(size = 440.dp, top = (-170).dp, right = (-150).dp)
        Halo(size = 420.dp, bottom = (-200).dp, left = (-180).dp, alpha = 0.7f)
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                Row(modifier = Modifier.riseIn(index = 0), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    CompagnonIconButton(
                        icon = AppIcons.Info,
                        contentDescription = stringResource(R.string.about_open),
                        onClick = onOpenAbout,
                        style = IconButtonStyle.SOFT,
                    )
                }
                StartSessionCard(onClick = onStartSession, modifier = Modifier.riseIn(index = 1))
                ListCard(list = list, onOpenList = onOpenList, modifier = Modifier.riseIn(index = 2))
                if (household != null) {
                    HouseholdCard(household = household, onClick = onOpenHousehold, modifier = Modifier.riseIn(index = 3))
                }
            }
            CatalogFooter(
                sync = sync,
                onRefresh = onRefresh,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .riseIn(index = 4)
                    .padding(horizontal = CompagnonSpacing.Screen)
                    .padding(bottom = CompagnonSpacing.Bottom),
            )
        }
    }
}

/** The leaves drawn in the top right corner of « Faire le point » (`Main.dc.html`). */
private val StartCardLeaves = listOf(
    PatternLeaf(180f, 34f, 28f),
    PatternLeaf(118f, 18f, -24f, 0.7f),
    PatternLeaf(214f, 108f, 64f, 0.8f),
    PatternLeaf(150f, 90f, -8f, 0.55f),
)

/**
 * The big Basilic card: « Faire le point », the promise, « ≈ 2 min · 12 questions », with its decor: leaves as a
 * watermark, a light disc, and a basket of groceries spilling out of the bottom right corner.
 */
@Composable
private fun StartSessionCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .softShadow(CompagnonShapes.Card, radius = 24.dp, offsetY = 10.dp, alpha = 0.22f, color = CompagnonColors.Basil)
            .clip(CompagnonShapes.Card)
            .background(CompagnonColors.Basil)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        LeafPattern(
            leaves = StartCardLeaves,
            width = 230.dp,
            height = 212.dp,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 18.dp, y = 14.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        // The groceries: positions of the mockup from the bottom right corner (right, bottom), in dp.
        Illustration(
            key = Illustrations.Screen.HOME_SESSION,
            size = 126.dp,
            rotation = -6f,
            popDelayMillis = 240,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp),
        )
        Illustration(
            key = Illustrations.Screen.DECOR_BAGUETTE,
            size = 62.dp,
            rotation = -24f,
            popDelayMillis = 320,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-70).dp, y = (-74).dp),
        )
        Illustration(
            key = Illustrations.Screen.DECOR_APPLE,
            size = 50.dp,
            popDelayMillis = 320,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = (-92).dp),
        )
        Illustration(
            key = Illustrations.Screen.DECOR_LEAFY,
            size = 46.dp,
            rotation = -12f,
            popDelayMillis = 320,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-104).dp, y = (-44).dp),
        )
        Icon(
            AppIcons.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 24.dp)
                .size(26.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 212.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.widthIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.home_start_session),
                    style = CompagnonType.ScreenTitle.copy(lineHeight = 34.sp),
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.home_intro),
                    style = CompagnonType.Body,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
            // Kept left of the groceries: the second fact goes below the first with a large font.
            FlowRow(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .widthIn(max = 230.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CardFact(icon = AppIcons.Clock, text = stringResource(R.string.home_duration))
                CardFact(icon = AppIcons.ListBullets, text = stringResource(R.string.home_questions))
            }
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

/** « Ma liste »: notepad tile, « 16 articles · 4 cochés », the progress of the checks (filling in), chevron. */
@Composable
private fun ListCard(list: ShoppingListUiState, onOpenList: () -> Unit, modifier: Modifier = Modifier) {
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
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .clip(CompagnonShapes.BlockLarge)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, CompagnonShapes.BlockLarge)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onOpenList,
            )
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTile(key = Illustrations.Screen.HOME_LIST, popDelayMillis = 240)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.home_my_list),
                style = CompagnonType.ListRow.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                color = CompagnonColors.Ink,
            )
            Text(text = summary, style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
            if (loaded != null && !loaded.isEmpty) {
                ProgressBar(
                    fraction = (loaded.itemCount - loaded.remainingCount) / loaded.itemCount.toFloat(),
                    fillOnAppear = true,
                )
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = CompagnonColors.InkMuted, modifier = Modifier.size(20.dp))
    }
}

/** « Mon foyer »: house tile, « 4 personnes · Halal · 14 essentiels », chevron. */
@Composable
private fun HouseholdCard(household: HouseholdSummaryUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .clip(CompagnonShapes.BlockLarge)
            .background(CompagnonColors.Surface)
            .border(1.dp, CompagnonColors.Line, CompagnonShapes.BlockLarge)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTile(key = Illustrations.Screen.HOME_HOUSEHOLD, popDelayMillis = 320)
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

/** The 52 Basilic clair tile of a home card, with its 40 image. */
@Composable
private fun HomeTile(key: String, popDelayMillis: Int) {
    Illustration(
        key = key,
        size = 40.dp,
        plate = 52.dp,
        plateShape = CompagnonShapes.Field,
        popDelayMillis = popDelayMillis,
    )
}
