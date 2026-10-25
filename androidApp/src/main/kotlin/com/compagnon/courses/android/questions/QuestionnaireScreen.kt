package com.compagnon.courses.android.questions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionSessionViewModel
import com.compagnon.courses.feature.questionflow.QuestionUiState
import org.koin.compose.viewmodel.koinViewModel

/**
 * « Questionnaire »: one question session, backed by the shared [QuestionSessionViewModel] (created here only,
 * scoped to this navigation entry). Leaving keeps the session open: the next visit resumes it.
 */
@Composable
fun QuestionnaireScreen(
    onClose: () -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuestionSessionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel, haptics) {
        viewModel.haptics.collect { haptics.performHapticFeedback(it.feedbackType()) }
    }
    QuestionnaireContent(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        onOpenList = onOpenList,
        modifier = modifier,
    )
}

/** Stateless questionnaire: renders [state], reports answers as [QuestionEvent]s. */
@Composable
internal fun QuestionnaireContent(
    state: QuestionUiState,
    onEvent: (QuestionEvent) -> Unit,
    onClose: () -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system back goes back one question while there is one; otherwise it leaves the questionnaire.
    BackHandler(enabled = state.canUndo) { onEvent(QuestionEvent.Undo) }

    Scaffold(
        modifier = modifier,
        topBar = { QuestionnaireTopBar(state = state, onEvent = onEvent, onClose = onClose) },
    ) { contentPadding ->
        AnimatedContent(
            targetState = state,
            contentKey = { it.stepKey() },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "question step",
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) { step ->
            when (step) {
                QuestionUiState.Loading -> ProgressStep(text = stringResource(R.string.question_loading))
                QuestionUiState.Committing -> ProgressStep(text = stringResource(R.string.question_committing))
                is QuestionUiState.Meta -> MetaStep(step, onEvent)
                is QuestionUiState.Suggested -> SuggestedStep(step, onEvent)
                is QuestionUiState.Card -> CardStep(step, onEvent)
                is QuestionUiState.Gate -> GateStep(step, onEvent)
                is QuestionUiState.GateItems -> GateItemsStep(step, onEvent)
                is QuestionUiState.Review -> ReviewStep(step, onEvent)
                is QuestionUiState.Done -> DoneStep(step, onOpenList = onOpenList, onClose = onClose)
                is QuestionUiState.Error -> ErrorStep(step, onEvent = onEvent, onClose = onClose)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionnaireTopBar(
    state: QuestionUiState,
    onEvent: (QuestionEvent) -> Unit,
    onClose: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }
    val position = state.progress?.position()

    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.question_close))
                }
            },
            title = {
                when {
                    state is QuestionUiState.Review -> Text(
                        text = stringResource(R.string.review_title),
                        modifier = Modifier.semantics { heading() },
                    )
                    position != null -> {
                        val spoken = stringResource(R.string.question_progress_spoken, position.current, position.total)
                        Text(
                            text = stringResource(R.string.question_progress, position.current, position.total),
                            modifier = Modifier.semantics {
                                contentDescription = spoken
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                    else -> Text(text = stringResource(R.string.question_title))
                }
            },
            actions = {
                if (state.progress != null) {
                    IconButton(onClick = { onEvent(QuestionEvent.Undo) }, enabled = state.canUndo) {
                        Icon(AppIcons.Undo, contentDescription = stringResource(R.string.question_undo))
                    }
                }
                if (state.isAsking) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.question_end_questions)) },
                                onClick = {
                                    menuOpen = false
                                    onEvent(QuestionEvent.EndQuestions)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.question_abandon)) },
                                onClick = {
                                    menuOpen = false
                                    confirmAbandon = true
                                },
                            )
                        }
                    }
                }
            },
        )
        if (position != null) {
            LinearProgressIndicator(
                progress = { if (state is QuestionUiState.Review) 1f else position.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text(stringResource(R.string.question_abandon_title)) },
            text = { Text(stringResource(R.string.question_abandon_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAbandon = false
                        onEvent(QuestionEvent.Abandon)
                    },
                ) { Text(stringResource(R.string.question_abandon_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ProgressStep(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = text })
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun DoneStep(state: QuestionUiState.Done, onOpenList: () -> Unit, onClose: () -> Unit) {
    val title = when {
        state.abandoned -> stringResource(R.string.done_abandoned_title)
        state.itemsAdded == 0 -> stringResource(R.string.done_nothing_title)
        else -> pluralStringResource(R.plurals.done_items_added, state.itemsAdded, state.itemsAdded)
    }
    val text = when {
        state.abandoned -> stringResource(R.string.done_abandoned_text)
        state.itemsAdded == 0 -> stringResource(R.string.done_nothing_text)
        else -> stringResource(R.string.done_items_text)
    }
    OutcomeLayout(
        icon = {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        },
        title = title,
        text = text,
    ) {
        Button(
            onClick = onOpenList,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text(stringResource(R.string.done_open_list)) }
        TextButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { Text(stringResource(R.string.done_back_home)) }
    }
}

@Composable
private fun ErrorStep(state: QuestionUiState.Error, onEvent: (QuestionEvent) -> Unit, onClose: () -> Unit) {
    OutcomeLayout(
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp),
            )
        },
        title = stringResource(R.string.question_error_title),
        text = stringResource(QuestionTexts.errorMessage(state.kind)),
    ) {
        if (state.retryable) {
            Button(
                onClick = { onEvent(QuestionEvent.Retry) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text(stringResource(R.string.action_retry)) }
        }
        OutlinedButton(
            onClick = { onEvent(QuestionEvent.StartOver) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text(stringResource(R.string.question_start_over)) }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.question_close))
        }
    }
}

@Composable
private fun OutcomeLayout(
    icon: @Composable () -> Unit,
    title: String,
    text: String,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        icon()
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        actions()
    }
}
