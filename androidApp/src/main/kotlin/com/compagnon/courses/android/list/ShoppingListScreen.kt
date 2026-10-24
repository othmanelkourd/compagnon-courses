package com.compagnon.courses.android.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.SearchField
import com.compagnon.courses.feature.lists.ListItemUi
import com.compagnon.courses.feature.lists.ListSectionUi
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
 * Stateless list screen: sections in store order, tap to check, swipe either way to delete (with « Annuler » in
 * a snackbar), and the add field at the top (catalog search, or « Autre chose ? » as free text).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmComplete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(text = stringResource(R.string.list_title), modifier = Modifier.semantics { heading() })
                        if (loaded != null && !loaded.isEmpty) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.list_remaining,
                                    loaded.remainingCount,
                                    loaded.remainingCount,
                                    loaded.itemCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (loaded != null && !loaded.isEmpty) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.list_complete)) },
                                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmComplete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
        when (state) {
            ShoppingListUiState.Loading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                val description = stringResource(R.string.list_loading)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
            }
            is ShoppingListUiState.Error -> ListErrorContent(onRetry = { onEvent(ShoppingListEvent.Retry) }, modifier = contentModifier)
            is ShoppingListUiState.Loaded -> LoadedList(state, onEvent, onStartSession, contentModifier)
        }
    }

    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text(stringResource(R.string.list_complete_title)) },
            text = { Text(stringResource(R.string.list_complete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmComplete = false
                        onEvent(ShoppingListEvent.CompleteList)
                    },
                ) { Text(stringResource(R.string.list_complete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * « Lait supprimé · Annuler » after a delete. Checks are undone by tapping again, so they get no snackbar; a newer
 * action replaces the pending undo, so a stale snackbar is dismissed. Storage errors are shown too.
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
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        currentOnEvent(if (result == SnackbarResult.ActionPerformed) ShoppingListEvent.Undo else ShoppingListEvent.DismissUndo)
    }
    val error = loaded?.error
    val errorMessage = stringResource(R.string.list_error_action)
    LaunchedEffect(error) {
        if (error != null) snackbarHostState.showSnackbar(errorMessage)
    }
}

@Composable
private fun LoadedList(
    state: ShoppingListUiState.Loaded,
    onEvent: (ShoppingListEvent) -> Unit,
    onStartSession: () -> Unit,
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

    LazyColumn(modifier = modifier) {
        item(key = "add") {
            SearchField(
                state = query,
                onQueryChange = { onEvent(ShoppingListEvent.Search(it)) },
                placeholder = stringResource(R.string.list_add_placeholder),
                onSubmit = { if (state.searchResults.isEmpty()) addFreeText() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (typed.isNotEmpty()) {
            items(state.searchResults, key = { "result:${it.productId}" }) { result ->
                val addLabel = stringResource(R.string.list_add_product, result.name)
                ListItem(
                    headlineContent = { Text(result.name) },
                    supportingContent = { Text(result.quantityLabel) },
                    trailingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = addLabel) {
                        onEvent(ShoppingListEvent.AddProduct(result.productId))
                        query.clearText()
                    },
                )
            }
            item(key = "free-text") {
                val label = stringResource(R.string.list_add_free_text, typed)
                ListItem(
                    overlineContent = { Text(stringResource(R.string.list_other_title)) },
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button, onClick = addFreeText),
                )
                HorizontalDivider()
            }
        }

        if (state.isEmpty && typed.isEmpty()) {
            item(key = "empty") { EmptyList(onStartSession = onStartSession) }
        }

        state.sections.forEach { section ->
            item(key = "section:${section.categoryId}") { SectionHeader(section) }
            items(section.items, key = { "item:${it.id}" }) { item ->
                SwipeToDeleteRow(item = item, onEvent = onEvent, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SectionHeader(section: ListSectionUi) {
    Text(
        text = section.title ?: stringResource(R.string.list_section_other),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
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
    val deleteLabel = stringResource(R.string.list_delete_item, item.name)
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
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = { Checkbox(checked = item.checked, onCheckedChange = null) },
            headlineContent = {
                Text(
                    text = item.name,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            },
            trailingContent = item.quantityLabel?.let { label ->
                {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    )
                }
            },
            modifier = Modifier
                .heightIn(min = 56.dp)
                .toggleable(
                    value = item.checked,
                    role = Role.Checkbox,
                    onValueChange = { onEvent(ShoppingListEvent.ToggleChecked(item.id)) },
                )
                .semantics {
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

@Composable
private fun EmptyList(onStartSession: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.list_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.list_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onStartSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.home_start_session)) }
        }
    }
}

@Composable
private fun ListErrorContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.list_error_storage),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
