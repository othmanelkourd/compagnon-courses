package com.compagnon.courses.android.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.CompagnonIconButton
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.ProgressSegments
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.onboarding.OnboardingEvent
import com.compagnon.courses.feature.onboarding.OnboardingExit
import com.compagnon.courses.feature.onboarding.OnboardingMode
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.OnboardingViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The onboarding « Mon foyer » (`design/maquette/Onboarding-*.dc.html`, `Household-Edit.dc.html`), backed by the
 * shared [OnboardingViewModel]: the first launch ([OnboardingMode.FIRST_RUN]) or « Mon foyer » from the home screen
 * ([OnboardingMode.EDIT]). [onExit] is called once when the onboarding closes.
 */
@Composable
fun OnboardingScreen(
    mode: OnboardingMode,
    onExit: (OnboardingExit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel { parametersOf(mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnExit by rememberUpdatedState(onExit)
    val exit = (state as? OnboardingUiState.Ready)?.exit
    LaunchedEffect(exit) {
        if (exit != null) {
            currentOnExit(exit)
            viewModel.onEvent(OnboardingEvent.ExitHandled)
        }
    }
    OnboardingContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Stateless onboarding: renders [state], reports answers as [OnboardingEvent]s. The system back goes one screen
 * back ([OnboardingEvent.Back]), except on the first-launch welcome, where it leaves the app.
 */
@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state as? OnboardingUiState.Ready
    BackHandler(enabled = ready != null && !(ready.mode == OnboardingMode.FIRST_RUN && ready.step == OnboardingStep.WELCOME)) {
        onEvent(OnboardingEvent.Back)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompagnonColors.Background)
            .statusBarsPadding(),
    ) {
        // Loading: the plain background (reading the household takes a few milliseconds).
        if (ready == null) return@Column
        OnboardingTopBar(state = ready, onEvent = onEvent)
        if (ready.error != null) ErrorBanner(onRetry = { onEvent(OnboardingEvent.Retry) })
        AnimatedContent(
            targetState = ready,
            contentKey = { it.step },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding step",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { step ->
            when (step.step) {
                OnboardingStep.WELCOME -> WelcomeStep(step, onEvent)
                OnboardingStep.OVERVIEW -> OverviewStep(step, onEvent)
                OnboardingStep.HOUSEHOLD -> HouseholdStep(step, onEvent)
                OnboardingStep.DIET -> DietStep(step, onEvent)
                OnboardingStep.AVOID -> AvoidStep(step, onEvent)
                OnboardingStep.ESSENTIALS -> EssentialsStep(step, onEvent)
                OnboardingStep.RECURRING -> RecurringStep(step, onEvent)
                OnboardingStep.RHYTHM -> RhythmStep(step, onEvent)
                OnboardingStep.RECAP -> RecapStep(step, onEvent)
            }
        }
    }
}

/**
 * [←] round button (✕ for a section opened from « Mon foyer »), « Étape n sur 4 » above 4 segments, « Passer » on
 * the right. « Mon foyer » keeps the back button only; the welcome and recap screens have no bar.
 */
@Composable
private fun OnboardingTopBar(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val number = state.stepNumber
    if (number == null && state.step != OnboardingStep.OVERVIEW) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The text button « Passer » has its own 12 of padding: its text ends at the screen margin.
            .padding(start = CompagnonSpacing.Screen - 2.dp, end = CompagnonSpacing.Screen - 12.dp, top = CompagnonSpacing.Top),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompagnonIconButton(
            icon = if (state.closeInsteadOfBack) AppIcons.Close else AppIcons.ChevronLeft,
            contentDescription = stringResource(
                when {
                    state.closeInsteadOfBack -> R.string.action_close
                    state.step == OnboardingStep.OVERVIEW -> R.string.household_back
                    else -> R.string.onboarding_back
                },
            ),
            onClick = { onEvent(OnboardingEvent.Back) },
            enabled = !state.saving,
        )
        if (number != null) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step, number, state.stepCount),
                    style = CompagnonType.Counter,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                ProgressSegments(current = number, total = state.stepCount, gap = 4.dp)
            }
            // « Passer », or a blank as wide as the back button so the progress stays centred.
            if (state.canSkip) {
                TextActionButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = { onEvent(OnboardingEvent.Skip) },
                    enabled = !state.saving,
                )
            } else {
                Spacer(Modifier.width(56.dp))
            }
        }
    }
}

/** « Votre foyer n'a pas pu être enregistré sur l'appareil », Tomate, with « Réessayer ». */
@Composable
private fun ErrorBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = CompagnonSpacing.Screen)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(CompagnonShapes.Field)
            .background(CompagnonColors.TomatoLight)
            .padding(start = 14.dp, end = 4.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Info, contentDescription = null, tint = CompagnonColors.TomatoText, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.onboarding_error_storage),
            style = CompagnonType.Help,
            color = CompagnonColors.TomatoText,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
        TextActionButton(text = stringResource(R.string.action_retry), onClick = onRetry, color = CompagnonColors.TomatoText)
    }
}

/**
 * A step that fits on one screen: [content] scrolls under the top bar ([top] below it), the [bottom] actions stay
 * above the navigation bar.
 */
@Composable
internal fun StepLayout(
    top: Dp = 32.dp,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(top = top, bottom = 16.dp),
            content = content,
        )
        Column(modifier = Modifier.fillMaxWidth(), content = bottom)
    }
}

/** The Basilic overline (section), the step title (Bricolage 32) and its sentence. */
@Composable
internal fun StepHeader(overline: String?, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (overline != null) 10.dp else 8.dp)) {
        if (overline != null) Overline(text = overline, color = CompagnonColors.Basil, heading = false)
        Text(
            text = title,
            style = CompagnonType.ScreenTitle,
            color = CompagnonColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = text, style = CompagnonType.BodyCompact, color = CompagnonColors.InkSoft)
    }
}

/** « Vos réponses restent sur votre téléphone. » with the lock. */
@Composable
internal fun PrivacyNote(text: String, modifier: Modifier = Modifier, centered: Boolean = true) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Lock, contentDescription = null, tint = CompagnonColors.InkSoft, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = CompagnonType.Help,
            color = CompagnonColors.InkSoft,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** The main button of a step; « Enregistrement… » and disabled while the household is saved. */
@Composable
internal fun StepPrimaryButton(text: String, saving: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    PrimaryButton(
        text = if (saving) stringResource(R.string.onboarding_saving) else text,
        onClick = onClick,
        enabled = enabled && !saving,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A centred text button under the main button (« Plus tard », « Rien à éviter », « Aller à l'accueil »). */
@Composable
internal fun StepTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextActionButton(text = text, onClick = onClick, enabled = enabled)
    }
}

/** A blank of [height] in a column. */
@Composable
internal fun Gap(height: Dp) {
    Spacer(Modifier.height(height))
}
