package com.compagnon.courses.android.questions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AnimatedCounter
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.CompagnonIconButton
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.ProgressSegments
import com.compagnon.courses.android.ui.components.SecondaryButton
import com.compagnon.courses.android.ui.components.UndoTextButton
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.components.splitSpace
import com.compagnon.courses.android.ui.illustration.HaloAnchor
import com.compagnon.courses.android.ui.illustration.HeroBadge
import com.compagnon.courses.android.ui.illustration.HeroIllustration
import com.compagnon.courses.android.ui.illustration.LeafConfetti
import com.compagnon.courses.android.ui.illustration.anchoredHalo
import com.compagnon.courses.android.ui.illustration.haloSource
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.android.ui.theme.LocalReduceMotion
import com.compagnon.courses.core.model.illustration.Illustrations
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

/**
 * Stateless questionnaire: renders [state], reports answers as [QuestionEvent]s. Edge to edge: the top bar sits
 * under the status bar, each step keeps its actions above the navigation bar.
 */
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

    // A card that follows a card was already on screen, behind it: its image does not pop in again.
    val stepKey = state.stepKey()
    val shown = remember { ShownStep() }
    val afterCard = remember(stepKey) { shown.wasCard }
    SideEffect { shown.wasCard = state is QuestionUiState.Card }
    val reduceMotion = LocalReduceMotion.current
    val shift = with(LocalDensity.current) { STEP_SHIFT.roundToPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompagnonColors.Background)
            .statusBarsPadding(),
    ) {
        QuestionnaireTopBar(state = state, onEvent = onEvent, onClose = onClose)
        AnimatedContent(
            targetState = state,
            contentKey = { it.stepKey() },
            transitionSpec = { stepTransition(shift, reduceMotion) },
            label = "question step",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { step ->
            when (step) {
                QuestionUiState.Loading -> ProgressStep(text = stringResource(R.string.question_loading))
                QuestionUiState.Committing -> ProgressStep(text = stringResource(R.string.question_committing))
                is QuestionUiState.Meta -> MetaStep(step, onEvent)
                is QuestionUiState.Suggested -> SuggestedStep(step, onEvent)
                is QuestionUiState.Card -> CardStep(step, onEvent, popImage = !(afterCard && step.stepKey() == stepKey))
                is QuestionUiState.Gate -> GateStep(step, onEvent)
                is QuestionUiState.GateItems -> GateItemsStep(step, onEvent)
                is QuestionUiState.Review -> ReviewStep(step, onEvent)
                is QuestionUiState.Done -> DoneStep(step, onOpenList = onOpenList, onClose = onClose)
                is QuestionUiState.Error -> ErrorStep(step, onEvent = onEvent)
            }
        }
    }
}

/** How far a step slides in or out (animation 1). */
private val STEP_SHIFT = 24.dp

/** The last step drawn, read once per new step. */
private class ShownStep {
    var wasCard: Boolean = false
}

/**
 * Animation 1, « Passage d'étape »: the next step comes from the right (24 and a fade, standard 250), a step
 * undone from the left. A card after a card only cross-fades: the stack already brought it forward. Fades only
 * with « Réduire les animations ».
 */
private fun AnimatedContentTransitionScope<QuestionUiState>.stepTransition(
    shift: Int,
    reduceMotion: Boolean,
): ContentTransform {
    val fade = CompagnonMotion.standard<Float>()
    val forward = (targetState.progress?.answered ?: Int.MAX_VALUE) >= (initialState.progress?.answered ?: 0)
    val transform = when {
        reduceMotion -> fadeIn(fade) togetherWith fadeOut(fade)
        forward && initialState is QuestionUiState.Card && targetState is QuestionUiState.Card -> {
            val quick = CompagnonMotion.standard<Float>(CompagnonMotion.QUICK)
            fadeIn(quick) togetherWith fadeOut(quick)
        }
        else -> {
            val slide = CompagnonMotion.standard<IntOffset>()
            val from = if (forward) shift else -shift
            (slideInHorizontally(slide) { from } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(slide) { -from } + fadeOut(fade))
        }
    }
    // No clipping: the cards' shadows and the flying card go past the step's bounds.
    return transform using SizeTransform(clip = false)
}

/**
 * [Fermer] on the left; while questions are asked, « n / 12 » above one segment per question in the middle; [⋯] on
 * the right while questions are asked and on the review (« Terminer » while asking, « Recommencer », « Abandonner la
 * séance »). Loading and errors keep [Fermer] only; nothing while the list is written or once it is done. Same bar
 * on iOS.
 */
@Composable
private fun QuestionnaireTopBar(
    state: QuestionUiState,
    onEvent: (QuestionEvent) -> Unit,
    onClose: () -> Unit,
) {
    if (state is QuestionUiState.Done || state == QuestionUiState.Committing) return
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }
    val position = state.progress?.position()?.takeIf { state.isAsking }
    val showsMenu = state.isAsking || state is QuestionUiState.Review

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CompagnonSpacing.Screen - 2.dp, end = CompagnonSpacing.Screen - 2.dp, top = CompagnonSpacing.Top),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompagnonIconButton(
            icon = AppIcons.Close,
            contentDescription = stringResource(R.string.question_close),
            onClick = onClose,
        )
        if (position != null) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val spoken = stringResource(R.string.question_progress_spoken, position.current, position.total)
                AnimatedCounter(
                    text = stringResource(R.string.question_progress, position.current, position.total),
                    count = position.current,
                    style = CompagnonType.Counter,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                ProgressSegments(current = position.current, total = position.total)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showsMenu) {
            Box {
                CompagnonIconButton(
                    icon = AppIcons.More,
                    contentDescription = stringResource(R.string.action_more),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = CompagnonColors.Surface,
                    shape = CompagnonShapes.Field,
                ) {
                    if (state.isAsking) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.question_end_questions), style = CompagnonType.ListRow) },
                            onClick = {
                                menuOpen = false
                                onEvent(QuestionEvent.EndQuestions)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.question_start_over), style = CompagnonType.ListRow) },
                        onClick = {
                            menuOpen = false
                            onEvent(QuestionEvent.StartOver)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.question_abandon),
                                style = CompagnonType.ListRow,
                                color = CompagnonColors.TomatoText,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            confirmAbandon = true
                        },
                    )
                }
            }
        }
    }

    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            containerColor = CompagnonColors.Surface,
            title = { Text(stringResource(R.string.question_abandon_title), style = CompagnonType.SheetTitle) },
            text = { Text(stringResource(R.string.question_abandon_text), style = CompagnonType.BodyCompact) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAbandon = false
                        onEvent(QuestionEvent.Abandon)
                    },
                ) {
                    Text(
                        stringResource(R.string.question_abandon_confirm),
                        style = CompagnonType.ButtonText,
                        color = CompagnonColors.TomatoText,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }) {
                    Text(stringResource(R.string.action_cancel), style = CompagnonType.ButtonText, color = CompagnonColors.Ink)
                }
            },
        )
    }
}

/** « Annuler » (bottom left) for every question; its TalkBack label says what it undoes. */
@Composable
internal fun UndoAnswerButton(canUndo: Boolean, onEvent: (QuestionEvent) -> Unit) {
    val description = stringResource(R.string.question_undo)
    UndoTextButton(
        text = stringResource(R.string.action_undo),
        onClick = { onEvent(QuestionEvent.Undo) },
        enabled = canUndo,
        modifier = Modifier.semantics { contentDescription = description },
    )
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
        CircularProgressIndicator(
            color = CompagnonColors.Basil,
            trackColor = CompagnonColors.SegmentEmpty,
            modifier = Modifier.semantics { contentDescription = text },
        )
        Text(
            text = text,
            style = CompagnonType.Body,
            color = CompagnonColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun DoneStep(state: QuestionUiState.Done, onOpenList: () -> Unit, onClose: () -> Unit) {
    if (!state.abandoned && state.itemsAdded > 0) {
        SessionDone(itemsAdded = state.itemsAdded, onOpenList = onOpenList, onClose = onClose)
        return
    }
    OutcomeLayout(
        icon = if (state.abandoned) AppIcons.Close else AppIcons.Check,
        tone = if (state.abandoned) OutcomeTone.NEUTRAL else OutcomeTone.POSITIVE,
        title = stringResource(if (state.abandoned) R.string.done_abandoned_title else R.string.done_nothing_title),
        text = stringResource(if (state.abandoned) R.string.done_abandoned_text else R.string.done_nothing_text),
    ) {
        DoneButtons(onOpenList = onOpenList, onClose = onClose)
    }
}

/**
 * `Done.dc.html`, items were added: the shopping bags on their disc with a tick that pops, « 8 articles ajoutés à
 * votre liste », « Cochez-les pendant vos courses. », on a halo; a rain of leaves and the success haptic, once.
 */
@Composable
private fun SessionDone(itemsAdded: Int, onOpenList: () -> Unit, onClose: () -> Unit) {
    val halo = remember { HaloAnchor() }
    var celebrated by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .anchoredHalo(halo, size = 560.dp, offsetY = 90.dp),
    ) {
        if (!celebrated) {
            LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
            LeafConfetti(modifier = Modifier.fillMaxSize(), onFinished = { celebrated = true })
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = CompagnonSpacing.Screen)
                .padding(bottom = CompagnonSpacing.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // The image and the text, the free space split 1 : 1.3 above and below them; they scroll if needed.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = splitSpace(above = 1f, below = 1.3f),
                ) {
                    HeroIllustration(
                        key = Illustrations.Screen.SESSION_DONE,
                        disc = 160.dp,
                        image = 108.dp,
                        ring = 12.dp,
                        badge = HeroBadge(size = 44.dp, border = 4.dp, tick = 22.dp, offset = DpOffset((-4).dp, (-6).dp)),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .haloSource(halo),
                    )
                    Column(
                        modifier = Modifier
                            .padding(top = 30.dp)
                            .riseIn(delayMillis = 120)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.done_items_added, itemsAdded, itemsAdded),
                            style = CompagnonType.ScreenTitle,
                            color = CompagnonColors.Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.done_items_text),
                            style = CompagnonType.Body,
                            color = CompagnonColors.InkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DoneButtons(onOpenList = onOpenList, onClose = onClose, rise = true)
            }
        }
    }
}

/** « Voir ma liste » and « Retour à l'accueil »; [rise]: they come in one after the other. */
@Composable
private fun DoneButtons(onOpenList: () -> Unit, onClose: () -> Unit, rise: Boolean = false) {
    PrimaryButton(
        text = stringResource(R.string.done_open_list),
        onClick = onOpenList,
        leadingIcon = AppIcons.Basket,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rise) Modifier.riseIn(delayMillis = 240) else Modifier),
    )
    SecondaryButton(
        text = stringResource(R.string.done_back_home),
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rise) Modifier.riseIn(delayMillis = 320) else Modifier),
    )
}

@Composable
private fun ErrorStep(state: QuestionUiState.Error, onEvent: (QuestionEvent) -> Unit) {
    OutcomeLayout(
        icon = AppIcons.Info,
        tone = OutcomeTone.PROBLEM,
        title = stringResource(QuestionTexts.errorTitle(state.kind)),
        text = stringResource(QuestionTexts.errorMessage(state.kind)),
    ) {
        if (state.retryable) {
            PrimaryButton(
                text = stringResource(R.string.action_retry),
                onClick = { onEvent(QuestionEvent.Retry) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.question_start_over),
            onClick = { onEvent(QuestionEvent.StartOver) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The tile of [OutcomeLayout]: Basilic (done), Pause (abandoned), Tomate (error). */
private enum class OutcomeTone(val tint: Color, val ink: Color) {
    POSITIVE(CompagnonColors.BasilLight, CompagnonColors.Basil),
    NEUTRAL(CompagnonColors.PauseTint, CompagnonColors.InkSoft),
    PROBLEM(CompagnonColors.TomatoLight, CompagnonColors.TomatoText),
}

/** Done and error: an icon tile, a Bricolage title, a sentence, and the charter's buttons at the bottom. */
@Composable
private fun OutcomeLayout(
    icon: ImageVector,
    tone: OutcomeTone,
    title: String,
    text: String,
    actions: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CompagnonSpacing.Screen)
            .padding(bottom = CompagnonSpacing.Bottom),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(tone.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.ink, modifier = Modifier.size(40.dp))
            }
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = CompagnonType.ScreenTitle,
                    color = CompagnonColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = text,
                    style = CompagnonType.Body,
                    color = CompagnonColors.InkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = actions,
        )
    }
}
