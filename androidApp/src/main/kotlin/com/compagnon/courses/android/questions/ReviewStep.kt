package com.compagnon.courses.android.questions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.SearchField
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState

/**
 * The recap before writing the list, with « Autre chose ? » (search the catalog, tap a result to add it).
 * « Annuler » in the top bar goes back to the last question.
 */
@Composable
internal fun ReviewStep(state: QuestionUiState.Review, onEvent: (QuestionEvent) -> Unit) {
    val query = rememberTextFieldState(state.searchQuery)
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "summary") {
                Text(
                    text = if (state.items.isEmpty()) {
                        stringResource(R.string.review_empty)
                    } else {
                        pluralStringResource(R.plurals.review_summary, state.items.size, state.items.size)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            items(state.items, key = { "item:${it.productId}" }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(stringResource(QuestionTexts.origin(item.origin))) },
                    trailingContent = {
                        Text(text = item.quantity.label, style = MaterialTheme.typography.titleMedium)
                    },
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                )
            }
            item(key = "other") {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.review_other_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    SearchField(
                        state = query,
                        onQueryChange = { onEvent(QuestionEvent.Search(it)) },
                        placeholder = stringResource(R.string.review_search_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.searchResults, key = { "result:${it.productId}" }) { result ->
                val addLabel = stringResource(R.string.review_add_result, result.name)
                ListItem(
                    headlineContent = { Text(result.name) },
                    supportingContent = { Text(result.quantity.label) },
                    leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = addLabel) {
                        onEvent(QuestionEvent.AddOther(result.productId))
                        query.clearText()
                    },
                )
            }
            if (state.searchQuery.isNotBlank() && state.searchResults.isEmpty()) {
                item(key = "no-result") {
                    Text(
                        text = stringResource(R.string.search_no_result, state.searchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
        BottomAction(
            text = if (state.items.isEmpty()) {
                stringResource(R.string.review_finish_empty)
            } else {
                pluralStringResource(R.plurals.review_confirm, state.items.size, state.items.size)
            },
            onClick = { onEvent(QuestionEvent.Confirm) },
        )
    }
}
