package com.compagnon.courses.android.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.SecondaryButton
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.illustration.HeroBadge
import com.compagnon.courses.android.ui.illustration.HeroIllustration
import com.compagnon.courses.android.ui.illustration.Illustration
import com.compagnon.courses.android.ui.illustration.LeafConfetti
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.feature.onboarding.OnboardingEvent
import com.compagnon.courses.feature.onboarding.OnboardingUiState

/**
 * `Onboarding-Recap`: the house on its disc with a tick that pops, « Votre foyer est prêt », the four sections with
 * « Modifier », then « Faire le point maintenant → » and « Aller à l'accueil ». The first time ([celebrate]), a rain
 * of leaves and the success haptic, then [onCelebrated].
 */
@Composable
internal fun RecapStep(
    state: OnboardingUiState.Ready,
    onEvent: (OnboardingEvent) -> Unit,
    celebrate: Boolean = false,
    onCelebrated: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxSize()) {
        // The disc's halo ring reaches 8 above it: the content starts 8 higher and the disc 8 lower.
        StepLayout(
            top = 8.dp,
            bottom = {
                PrimaryButton(
                    text = stringResource(if (state.saving) R.string.onboarding_saving else R.string.onboarding_start_session),
                    onClick = { onEvent(OnboardingEvent.Finish(startSession = true)) },
                    enabled = !state.saving,
                    trailingIcon = AppIcons.ArrowForward,
                    modifier = Modifier.fillMaxWidth(),
                )
                StepTextButton(
                    text = stringResource(R.string.onboarding_go_home),
                    onClick = { onEvent(OnboardingEvent.Finish(startSession = false)) },
                    enabled = !state.saving,
                )
            },
        ) {
            HeroIllustration(
                key = Illustrations.Screen.ONBOARDING_RECAP,
                disc = 104.dp,
                image = 72.dp,
                ring = 8.dp,
                ringAlpha = 0.45f,
                badge = HeroBadge(size = 36.dp, border = 3.dp, tick = 18.dp, offset = DpOffset(4.dp, 2.dp)),
                modifier = Modifier.padding(top = 8.dp),
            )
            Gap(22.dp)
            Column(modifier = Modifier.riseIn(index = 2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_recap_title),
                    style = CompagnonType.ScreenTitleLarge,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(R.string.onboarding_recap_text), style = CompagnonType.Body, color = CompagnonColors.InkSoft)
            }
            Gap(24.dp)
            val edit = stringResource(R.string.household_edit)
            ListBlock(shape = CompagnonShapes.BlockLarge, modifier = Modifier.riseIn(index = 3)) {
                OnboardingTexts.sections.forEachIndexed { index, section ->
                    if (index > 0) BlockDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !state.saving, role = Role.Button, onClickLabel = edit) {
                                onEvent(OnboardingEvent.Edit(section))
                            }
                            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Overline(text = stringResource(OnboardingTexts.sectionRow(section)), heading = false)
                            Text(
                                text = OnboardingTexts.sectionSummary(section, state.summary).resolve(),
                                style = CompagnonType.ButtonText,
                                color = CompagnonColors.Ink,
                            )
                        }
                        Text(
                            text = edit,
                            style = CompagnonType.Secondary.copy(fontWeight = FontWeight.SemiBold),
                            color = CompagnonColors.Basil,
                        )
                    }
                }
            }
        }
        if (celebrate) {
            LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
            LeafConfetti(modifier = Modifier.fillMaxSize(), onFinished = onCelebrated)
        }
    }
}

/**
 * `Household-Edit`, « Mon foyer »: the four sections with their image and chevron (each opens its section, saved on
 * its last « Continuer »), « Ces informations restent sur votre téléphone », « Tout reprendre depuis le début ».
 */
@Composable
internal fun OverviewStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    StepLayout(
        top = 16.dp,
        bottom = {
            SecondaryButton(
                text = stringResource(R.string.household_restart),
                onClick = { onEvent(OnboardingEvent.Start) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.household_title),
                style = CompagnonType.ScreenTitleLarge,
                color = CompagnonColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.household_text), style = CompagnonType.BodyCompact, color = CompagnonColors.InkSoft)
        }
        Gap(22.dp)
        ListBlock(shape = CompagnonShapes.BlockLarge, modifier = Modifier.riseIn(index = 1)) {
            OnboardingTexts.sections.forEachIndexed { index, section ->
                if (index > 0) BlockDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !state.saving, role = Role.Button) { onEvent(OnboardingEvent.Edit(section)) }
                        .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Illustration(
                        key = OnboardingTexts.sectionImage(section),
                        size = 28.dp,
                        plate = 40.dp,
                        plateShape = CompagnonShapes.Small,
                        pop = false,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stringResource(OnboardingTexts.sectionRow(section)), style = CompagnonType.ListRow)
                        Text(
                            text = OnboardingTexts.sectionSummary(section, state.summary).resolve(),
                            style = CompagnonType.Help,
                            color = CompagnonColors.InkSoft,
                        )
                    }
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = CompagnonColors.InkMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
        PrivacyNote(
            text = stringResource(R.string.household_private),
            centered = false,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 18.dp),
        )
    }
}
