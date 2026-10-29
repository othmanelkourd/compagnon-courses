package com.compagnon.courses.android.list

import android.content.Intent
import android.content.res.Resources
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AddRow
import com.compagnon.courses.android.ui.components.AnimatedCounter
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.CompagnonIconButton
import com.compagnon.courses.android.ui.components.CompagnonSnackbar
import com.compagnon.courses.android.ui.components.FieldIcon
import com.compagnon.courses.android.ui.components.GradientBottomBar
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.ProductRow
import com.compagnon.courses.android.ui.components.ProgressBar
import com.compagnon.courses.android.ui.components.SearchField
import com.compagnon.courses.android.ui.components.SecondaryButton
import com.compagnon.courses.android.ui.components.SectionBlock
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.components.splitSpace
import com.compagnon.courses.android.ui.illustration.HaloAnchor
import com.compagnon.courses.android.ui.illustration.HeroIllustration
import com.compagnon.courses.android.ui.illustration.anchoredHalo
import com.compagnon.courses.android.ui.illustration.haloSource
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.android.ui.theme.LocalReduceMotion
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.feature.lists.ListItemUi
import com.compagnon.courses.feature.lists.ShoppingListEvent
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.lists.ShoppingListViewModel
import com.compagnon.courses.feature.lists.UndoKind
import org.koin.compose.viewmodel.koinViewModel

/** Share of the line width to swipe past for a slow release to delete it. */
private const val DELETE_THRESHOLD_FRACTION: Float = 0.4f

/** « Ma liste », backed by the shared [ShoppingListViewModel]. */
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShoppingListContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onStartSession = onStartSession,
        modifier = modifier,
    )
}

/**
 * Stateless list screen: [‹] and [partager] at the top, « Ma liste » and what is left, the add field (catalog
 * search, or « Autre chose ? » as free text), sections in store order (tap to check, swipe either way to delete,
 * with « Annuler » in a snackbar), and « Courses terminées » at the bottom.
 */
@Composable
internal fun ShoppingListContent(
    state: ShoppingListUiState,
    onEvent: (ShoppingListEvent) -> Unit,
    onBack: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val loaded = state as? ShoppingListUiState.Loaded
    UndoSnackbarEffect(loaded = loaded, snackbarHostState = snackbarHostState, onEvent = onEvent)

    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    var barHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val showComplete = loaded != null && !loaded.isEmpty

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CompagnonColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            TopButtons(loaded = loaded, onBack = onBack)
            val contentModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            when (state) {
                ShoppingListUiState.Loading -> Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator(color = CompagnonColors.Basil, trackColor = CompagnonColors.SegmentEmpty)
                    Text(
                        text = stringResource(R.string.list_loading),
                        style = CompagnonType.Body,
                        color = CompagnonColors.InkSoft,
                        textAlign = TextAlign.Center,
                    )
                }
                is ShoppingListUiState.Error ->
                    ListErrorContent(onRetry = { onEvent(ShoppingListEvent.Retry) }, modifier = contentModifier)
                is ShoppingListUiState.Loaded -> LoadedList(
                    state = state,
                    onEvent = onEvent,
                    onStartSession = onStartSession,
                    bottomPadding = if (showComplete) with(density) { barHeight.toDp() } else 0.dp,
                    modifier = contentModifier,
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            SnackbarHost(snackbarHostState) { data -> CompagnonSnackbar(data) }
            if (showComplete) {
                GradientBottomBar(
                    modifier = Modifier.onSizeChanged { barHeight = it.height },
                    topPadding = 16.dp,
                    solidFrom = 0.3f,
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.list_complete),
                        onClick = { confirmComplete = true },
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            containerColor = CompagnonColors.Surface,
            title = { Text(stringResource(R.string.list_complete_title), style = CompagnonType.SheetTitle) },
            text = { Text(stringResource(R.string.list_complete_text), style = CompagnonType.BodyCompact) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmComplete = false
                        onEvent(ShoppingListEvent.CompleteList)
                    },
                ) {
                    Text(stringResource(R.string.list_complete_confirm), style = CompagnonType.ButtonText, color = CompagnonColors.Basil)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) {
                    Text(stringResource(R.string.action_cancel), style = CompagnonType.ButtonText, color = CompagnonColors.Ink)
                }
            },
        )
    }
}

/** [‹] back to the home screen, and [partager] (the items still to buy, as text) when there are some. */
@Composable
private fun TopButtons(loaded: ShoppingListUiState.Loaded?, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CompagnonSpacing.Screen - 2.dp, end = CompagnonSpacing.Screen - 2.dp, top = CompagnonSpacing.Top),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CompagnonIconButton(icon = AppIcons.ChevronLeft, contentDescription = stringResource(R.string.done_back_home), onClick = onBack)
        if (loaded != null && loaded.remainingCount > 0) {
            CompagnonIconButton(
                icon = AppIcons.Share,
                contentDescription = stringResource(R.string.list_share),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.list_title))
                        .putExtra(Intent.EXTRA_TEXT, shareText(loaded, resources))
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
        }
    }
}

/** « Ma liste », then each section and its unchecked lines (« • Lait (2 bouteilles) »), for the share sheet. */
internal fun shareText(list: ShoppingListUiState.Loaded, resources: Resources): String = buildString {
    append(resources.getString(R.string.list_title))
    list.sections.forEach { section ->
        val remaining = section.items.filterNot { it.checked }
        if (remaining.isEmpty()) return@forEach
        append("\n\n").append(section.title ?: resources.getString(R.string.list_section_other))
        remaining.forEach { item ->
            append('\n')
            val quantity = item.quantityLabel
            append(
                if (quantity != null) {
                    resources.getString(R.string.list_share_line, item.name, quantity)
                } else {
                    resources.getString(R.string.list_share_line_plain, item.name)
                },
            )
        }
    }
}

/**
 * « « Lait » supprimé · Annuler » after a delete, and nothing else: checks are undone by tapping again, and a newer
 * action replaces the pending undo, so a stale snackbar is dismissed.
 */
@Composable
private fun UndoSnackbarEffect(
    loaded: ShoppingListUiState.Loaded?,
    snackbarHostState: SnackbarHostState,
    onEvent: (ShoppingListEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val undo = loaded?.undo
    val deletedMessage = undo?.let { stringResource(R.string.list_item_deleted, it.itemName) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undo) {
        if (undo?.kind != UndoKind.DELETED || deletedMessage == null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long,
        )
        currentOnEvent(if (result == SnackbarResult.ActionPerformed) ShoppingListEvent.Undo else ShoppingListEvent.DismissUndo)
    }
}

@Composable
private fun LoadedList(
    state: ShoppingListUiState.Loaded,
    onEvent: (ShoppingListEvent) -> Unit,
    onStartSession: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val query = rememberTextFieldState(state.searchQuery)
    val typed = query.text.toString().trim()
    val addFreeText = {
        if (typed.isNotEmpty()) {
            onEvent(ShoppingListEvent.AddFreeText(typed))
            query.clearText()
        }
    }
    val reduceMotion = LocalReduceMotion.current
    val rowMove = remember(reduceMotion) {
        BoundsTransform { _, _ -> if (reduceMotion) snap() else CompagnonMotion.standard() }
    }
    val blockPadding = Modifier.padding(start = CompagnonSpacing.Block, end = CompagnonSpacing.Block, top = 14.dp)
    val density = LocalDensity.current
    // The heights of the title and of the field: the empty list takes the rest of the screen.
    var titleHeight by remember { mutableIntStateOf(0) }
    var fieldHeight by remember { mutableIntStateOf(0) }
    val halo = remember { HaloAnchor() }
    val emptyList = state.isEmpty && typed.isEmpty()

    BoxWithConstraints(modifier = modifier) {
        val viewport = maxHeight
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (emptyList) Modifier.anchoredHalo(halo, size = 520.dp, offsetY = 90.dp) else Modifier),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
        ) {
            item(key = "title") {
                Column(
                    modifier = Modifier
                        .onSizeChanged { titleHeight = it.height }
                        .padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = stringResource(R.string.list_title),
                            style = CompagnonType.ScreenTitleLarge,
                            color = CompagnonColors.Ink,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        if (!state.isEmpty) {
                            AnimatedCounter(
                                text = if (state.remainingCount == 0) {
                                    stringResource(R.string.list_all_checked)
                                } else {
                                    pluralStringResource(
                                        R.plurals.list_remaining,
                                        state.remainingCount,
                                        state.remainingCount,
                                        state.itemCount,
                                    )
                                },
                                count = state.remainingCount,
                                style = CompagnonType.Secondary.copy(fontWeight = FontWeight.SemiBold),
                                color = CompagnonColors.InkSoft,
                                modifier = Modifier.padding(bottom = 4.dp, start = 12.dp),
                            )
                        }
                    }
                    if (!state.isEmpty) {
                        ProgressBar(
                            fraction = (state.itemCount - state.remainingCount) / state.itemCount.toFloat(),
                            fillOnAppear = true,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            item(key = "add") {
                SearchField(
                    state = query,
                    onQueryChange = { onEvent(ShoppingListEvent.Search(it)) },
                    placeholder = stringResource(R.string.list_add_placeholder),
                    icon = FieldIcon.ADD,
                    // Return adds the first product found, otherwise the text itself (same on iOS).
                    onSubmit = {
                        val first = state.searchResults.firstOrNull()
                        if (first != null && typed.isNotEmpty()) {
                            onEvent(ShoppingListEvent.AddProduct(first.productId))
                            query.clearText()
                        } else {
                            addFreeText()
                        }
                    },
                    modifier = Modifier
                        .onSizeChanged { fieldHeight = it.height }
                        .fillMaxWidth()
                        .padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 16.dp),
                )
            }
            state.error?.let {
                item(key = "error") {
                    Text(
                        text = stringResource(R.string.list_error_action),
                        style = CompagnonType.Help,
                        color = CompagnonColors.TomatoText,
                        modifier = blockPadding
                            .fillMaxWidth()
                            .clip(CompagnonShapes.Small)
                            .background(CompagnonColors.TomatoLight)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // While typing: one block with the products found, then the typed text as a free line (same on iOS).
            if (typed.isNotEmpty()) {
                item(key = "results") {
                    ListBlock(modifier = Modifier.padding(start = CompagnonSpacing.Block, end = CompagnonSpacing.Block, top = 16.dp)) {
                        state.searchResults.forEach { result ->
                            key(result.productId) {
                                AddRow(
                                    name = result.name,
                                    detail = result.quantityLabel,
                                    imageKey = result.imageKey,
                                    imageSize = 26.dp,
                                    plateSize = 36.dp,
                                    onClickLabel = stringResource(R.string.list_add_product, result.name),
                                    onClick = {
                                        onEvent(ShoppingListEvent.AddProduct(result.productId))
                                        query.clearText()
                                    },
                                )
                                BlockDivider()
                            }
                        }
                        AddRow(
                            name = stringResource(R.string.list_add_free_text, typed),
                            detail = null,
                            imageKey = Illustrations.Screen.FREE_TEXT,
                            imageSize = 26.dp,
                            plateSize = 36.dp,
                            onClickLabel = stringResource(R.string.list_add_free_text, typed),
                            onClick = addFreeText,
                        )
                    }
                }
            } else if (state.isEmpty) {
                item(key = "empty") {
                    val available = viewport - with(density) { (titleHeight + fieldHeight).toDp() } - 16.dp
                    EmptyList(
                        halo = halo,
                        onStartSession = onStartSession,
                        modifier = Modifier.heightIn(min = available),
                    )
                }
            }

            if (typed.isEmpty()) {
                items(state.sections, key = { "section:${it.categoryId}" }) { section ->
                    SectionBlock(
                        title = section.title ?: stringResource(R.string.list_section_other),
                        modifier = blockPadding.then(Modifier.animateItem()),
                    ) {
                        // A checked line goes down to the end of its section (animation 7), the others close up.
                        LookaheadScope {
                            Column {
                                section.items.forEachIndexed { index, item ->
                                    key(item.id) {
                                        Column(modifier = Modifier.animateBounds(this@LookaheadScope, boundsTransform = rowMove)) {
                                            if (index > 0) BlockDivider()
                                            SwipeToDeleteRow(item = item, onEvent = onEvent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One line: tap toggles « acheté »; swipe either way (or the TalkBack action) deletes it. */
@Composable
private fun SwipeToDeleteRow(item: ListItemUi, onEvent: (ShoppingListEvent) -> Unit, modifier: Modifier = Modifier) {
    // Not saveable on purpose: a line restored by « Annuler » must come back in place, not swiped away.
    val dismissState = remember(item.id) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * DELETE_THRESHOLD_FRACTION })
    }
    val currentOnEvent by rememberUpdatedState(onEvent)
    // Stable per line: SwipeToDismissBox relaunches its dismiss effect when the callback changes.
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(item.id) {
        { _ -> currentOnEvent(ShoppingListEvent.Delete(item.id)) }
    }
    val deleteLabel = stringResource(R.string.list_delete_item)
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        onDismiss = onDismiss,
        backgroundContent = {
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CompagnonColors.TomatoLight)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                Icon(AppIcons.Close, contentDescription = null, tint = CompagnonColors.TomatoText)
            }
        },
    ) {
        ProductRow(
            name = item.name,
            quantity = item.quantityLabel,
            imageKey = item.imageKey,
            checked = item.checked,
            onToggle = { onEvent(ShoppingListEvent.ToggleChecked(item.id)) },
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        onEvent(ShoppingListEvent.Delete(item.id))
                        true
                    },
                )
            },
        )
    }
}

/**
 * `List-Empty.dc.html`: the cart on its disc, « Votre liste est vide » and its sentence in the free height (on a
 * halo), « Faire le point » at the bottom.
 */
@Composable
private fun EmptyList(halo: HaloAnchor, onStartSession: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CompagnonSpacing.Screen),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = splitSpace(above = 1f, below = 1.2f),
        ) {
            HeroIllustration(
                key = Illustrations.Screen.LIST_EMPTY,
                disc = 136.dp,
                image = 92.dp,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .haloSource(halo),
            )
            Text(
                text = stringResource(R.string.list_empty_title),
                style = CompagnonType.ScreenTitle.copy(fontSize = 28.sp, lineHeight = 31.sp),
                color = CompagnonColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 26.dp)
                    .riseIn(delayMillis = 120)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.list_empty_text),
                style = CompagnonType.Body,
                color = CompagnonColors.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 24.dp)
                    .riseIn(delayMillis = 160),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.home_start_session),
            onClick = onStartSession,
            modifier = Modifier
                .fillMaxWidth()
                .riseIn(delayMillis = 240),
        )
    }
}

/** An unreadable list: a round Tomate tile with the info icon, a Bricolage title, a sentence and « Réessayer ». */
@Composable
private fun ListErrorContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CompagnonSpacing.Screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CompagnonColors.TomatoLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Info, contentDescription = null, tint = CompagnonColors.TomatoText, modifier = Modifier.size(36.dp))
            }
            Text(
                text = stringResource(R.string.list_error_title),
                style = CompagnonType.ScreenTitle,
                color = CompagnonColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.list_error_storage),
                style = CompagnonType.Body,
                color = CompagnonColors.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            PrimaryButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
            )
        }
    }
}
