package com.compagnon.courses.android.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.CatalogStatusCard
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.feature.catalog.CatalogStatusEvent
import com.compagnon.courses.feature.catalog.CatalogStatusUiState
import com.compagnon.courses.feature.catalog.CatalogStatusViewModel
import com.compagnon.courses.feature.catalog.CatalogSyncUiState
import com.compagnon.courses.feature.lists.ShoppingListUiState
import com.compagnon.courses.feature.lists.ShoppingListViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * « Accueil »: « Faire le point » (a question session), « Ma liste » with its count, and the catalog status.
 * Creating the catalog ViewModel syncs the local catalog.
 */
@Composable
fun HomeScreen(
    onStartSession: () -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
    scope: CatalogScope = koinInject(),
    catalogViewModel: CatalogStatusViewModel = koinViewModel { parametersOf(scope.country) },
    listViewModel: ShoppingListViewModel = koinViewModel(),
) {
    val status by catalogViewModel.state.collectAsStateWithLifecycle()
    val sync by catalogViewModel.sync.collectAsStateWithLifecycle()
    val list by listViewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        status = status,
        sync = sync,
        list = list,
        onStartSession = onStartSession,
        onOpenList = onOpenList,
        onRefresh = { catalogViewModel.onEvent(CatalogStatusEvent.Refresh) },
        modifier = modifier,
    )
}

/** Stateless home screen. The main action sits at the bottom, under the thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    status: CatalogStatusUiState,
    sync: CatalogSyncUiState,
    list: ShoppingListUiState,
    onStartSession: () -> Unit,
    onOpenList: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_title), modifier = Modifier.semantics { heading() })
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onStartSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = 64.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.home_start_session), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(text = stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.home_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListCard(list = list, onOpenList = onOpenList)
            CatalogStatusCard(status = status, sync = sync, onRefresh = onRefresh, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ListCard(list: ShoppingListUiState, onOpenList: () -> Unit) {
    val summary = when (list) {
        is ShoppingListUiState.Loaded -> when {
            list.isEmpty -> stringResource(R.string.home_list_empty)
            list.remainingCount == 0 -> pluralStringResource(R.plurals.home_list_all_checked, list.itemCount, list.itemCount)
            else -> pluralStringResource(R.plurals.home_list_count, list.itemCount, list.itemCount, list.remainingCount)
        }
        ShoppingListUiState.Loading -> stringResource(R.string.list_loading)
        is ShoppingListUiState.Error -> stringResource(R.string.list_error_storage)
    }
    ElevatedCard(onClick = onOpenList, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = {
                Icon(
                    Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            headlineContent = { Text(stringResource(R.string.home_my_list), style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(summary) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.heightIn(min = 72.dp),
        )
    }
}
