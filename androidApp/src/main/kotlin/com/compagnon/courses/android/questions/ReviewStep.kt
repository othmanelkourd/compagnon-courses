package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AddRow
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.GradientBottomBar
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.ReviewRow
import com.compagnon.courses.android.ui.components.SearchField
import com.compagnon.courses.android.ui.components.SectionBlock
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.questionflow.ItemOrigin
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState

/**
 * The recap before writing the list: « ✓ N questions », « N articles à ajouter », « Autre chose ? » (search the
 * catalog, tap a result to add it), the items in blocks by origin (the state has no aisle), then « Ajouter à ma
 * liste » and « Revenir aux questions » (undo) on a gradient.
 */
@Composable
internal fun ReviewStep(state: QuestionUiState.Review, onEvent: (QuestionEvent) -> Unit) {
    val query = rememberTextFieldState(state.searchQuery)
    var barHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val groups = remember(state.items) {
        ItemOrigin.entries.mapNotNull { origin ->
            state.items.filter { it.origin == origin }.takeIf { it.isNotEmpty() }?.let { origin to it }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = with(density) { barHeight.toDp() } + 8.dp),
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val answered = state.progress.answered
                    if (answered > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(AppIcons.Check, contentDescription = null, tint = CompagnonColors.Basil, modifier = Modifier.size(18.dp))
                            Text(
                                text = pluralStringResource(R.plurals.review_questions, answered, answered),
                                style = CompagnonType.Counter,
                                color = CompagnonColors.Basil,
                            )
                        }
                    }
                    Text(
                        text = if (state.items.isEmpty()) {
                            stringResource(R.string.review_empty)
                        } else {
                            pluralStringResource(R.plurals.review_items_title, state.items.size, state.items.size)
                        },
                        style = CompagnonType.ScreenTitle,
                        color = CompagnonColors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "search") {
                SearchField(
                    state = query,
                    onQueryChange = { onEvent(QuestionEvent.Search(it)) },
                    placeholder = stringResource(R.string.review_search_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = CompagnonSpacing.Screen, end = CompagnonSpacing.Screen, top = 18.dp),
                )
            }
            if (state.searchResults.isNotEmpty()) {
                item(key = "results") {
                    ListBlock(modifier = Modifier.padding(start = CompagnonSpacing.Block, end = CompagnonSpacing.Block, top = 10.dp)) {
                        state.searchResults.forEachIndexed { index, result ->
                            key(result.productId) {
                                if (index > 0) BlockDivider()
                                AddRow(
                                    name = result.name,
                                    detail = result.quantity.label,
                                    imageKey = result.imageKey,
                                    onClickLabel = stringResource(R.string.review_add_result, result.name),
                                    onClick = {
                                        onEvent(QuestionEvent.AddOther(result.productId))
                                        query.clearText()
                                    },
                                )
                            }
                        }
                    }
                }
            } else if (state.searchQuery.isNotBlank()) {
                item(key = "no-result") {
                    Text(
                        text = stringResource(R.string.search_no_result, state.searchQuery),
                        style = CompagnonType.Help,
                        color = CompagnonColors.InkSoft,
                        modifier = Modifier.padding(horizontal = CompagnonSpacing.Screen + 6.dp, vertical = 10.dp),
                    )
                }
            }
            items(groups, key = { (origin, _) -> "origin:$origin" }) { (origin, items) ->
                SectionBlock(
                    title = stringResource(QuestionTexts.origin(origin)),
                    modifier = Modifier.padding(start = CompagnonSpacing.Block, end = CompagnonSpacing.Block, top = 16.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        key(item.productId) {
                            if (index > 0) BlockDivider()
                            ReviewRow(
                                name = item.name,
                                quantity = item.quantity.label,
                                imageKey = item.imageKey,
                                modifier = Modifier
                                    .riseIn(index = index)
                                    .semantics(mergeDescendants = true) {},
                            )
                        }
                    }
                }
            }
        }

        GradientBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight = it.height },
            topPadding = 24.dp,
            solidFrom = 0.3f,
        ) {
            PrimaryButton(
                text = if (state.items.isEmpty()) {
                    stringResource(R.string.review_finish_empty)
                } else {
                    stringResource(R.string.review_add_to_list)
                },
                onClick = { onEvent(QuestionEvent.Confirm) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextActionButton(
                text = stringResource(R.string.review_back),
                onClick = { onEvent(QuestionEvent.Undo) },
                enabled = state.canUndo,
                leadingIcon = AppIcons.Undo,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
