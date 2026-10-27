package com.compagnon.courses.android.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.currentLocale
import com.compagnon.courses.android.questions.BigChoice
import com.compagnon.courses.android.questions.BigChoiceQuestion
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.CheckMark
import com.compagnon.courses.android.ui.components.CountStepper
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.onboarding.DietChoice
import com.compagnon.courses.feature.onboarding.MemberKind
import com.compagnon.courses.feature.onboarding.OnboardingEvent
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.PetChoice

/** `Onboarding-Welcome`: the logo, « Des courses à votre image », the four sections, « Commencer », « Plus tard ». */
@Composable
internal fun WelcomeStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    StepLayout(
        top = 40.dp,
        bottom = {
            PrivacyNote(text = stringResource(R.string.onboarding_private), modifier = Modifier.padding(bottom = 14.dp))
            StepPrimaryButton(
                text = stringResource(R.string.onboarding_start),
                saving = state.saving,
                onClick = { onEvent(OnboardingEvent.Start) },
            )
            StepTextButton(
                text = stringResource(R.string.onboarding_later),
                onClick = { onEvent(OnboardingEvent.Later) },
                enabled = !state.saving,
            )
        },
    ) {
        Image(
            painter = rememberVectorPainter(OcimioLogo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(88.dp),
        )
        Gap(28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = CompagnonType.ScreenTitleLarge,
                color = CompagnonColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.onboarding_welcome_text), style = CompagnonType.Body, color = CompagnonColors.InkSoft)
        }
        Gap(28.dp)
        ListBlock(shape = CompagnonShapes.BlockLarge) {
            OnboardingTexts.sections.forEachIndexed { index, section ->
                if (index > 0) BlockDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CompagnonShapes.Small)
                            .background(CompagnonColors.BasilLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = CompagnonType.ButtonText.copy(fontWeight = FontWeight.Bold),
                            color = CompagnonColors.Basil,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stringResource(OnboardingTexts.sectionTitle(section)), style = CompagnonType.ListRow)
                        Text(
                            text = stringResource(OnboardingTexts.sectionHint(section)),
                            style = CompagnonType.Caption,
                            color = CompagnonColors.InkSoft,
                        )
                    }
                }
            }
        }
    }
}

/** `Onboarding-Household`: four counts (adults from 1), the pets, « Continuer · 4 personnes ». */
@Composable
internal fun HouseholdStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val household = state.household
    val locale = currentLocale()
    StepLayout(
        bottom = {
            StepPrimaryButton(
                text = pluralStringResource(R.plurals.onboarding_household_continue, household.persons, household.persons),
                saving = state.saving,
                enabled = household.persons > 0,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        StepHeader(
            overline = stringResource(R.string.onboarding_section_household),
            title = stringResource(R.string.onboarding_household_title),
            text = stringResource(R.string.onboarding_household_text),
        )
        Gap(24.dp)
        ListBlock(shape = CompagnonShapes.BlockLarge) {
            MemberKind.entries.forEachIndexed { index, kind ->
                if (index > 0) BlockDivider()
                val label = stringResource(OnboardingTexts.memberLabel(kind))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = label, style = CompagnonType.ListRow)
                        Text(
                            text = stringResource(OnboardingTexts.memberHint(kind)),
                            style = CompagnonType.Caption,
                            color = CompagnonColors.InkSoft,
                        )
                    }
                    CountStepper(
                        value = household.count(kind),
                        canDecrement = household.canDecrement(kind),
                        canIncrement = household.canIncrement(kind),
                        onStep = { onEvent(OnboardingEvent.StepMember(kind, it)) },
                        decreaseDescription = stringResource(R.string.onboarding_member_less, label.lowercase(locale)),
                        increaseDescription = stringResource(R.string.onboarding_member_more, label.lowercase(locale)),
                    )
                }
            }
        }
        Overline(
            text = stringResource(R.string.onboarding_pets_title),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PetChoice.entries.forEach { pet ->
                ToggleTile(
                    text = stringResource(OnboardingTexts.petLabel(pet)),
                    checked = pet in household.pets,
                    onToggle = { onEvent(OnboardingEvent.TogglePet(pet)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A pet: a 52 tile that toggles (checked: Basilic clair, Basilic border of 2 and a tick). */
@Composable
private fun ToggleTile(text: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val ink = if (checked) CompagnonColors.BasilDark else CompagnonColors.Ink
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(CompagnonShapes.Field)
            .background(if (checked) CompagnonColors.BasilLight else CompagnonColors.Surface)
            .border(if (checked) 2.dp else 1.dp, if (checked) CompagnonColors.Basil else CompagnonColors.Line, CompagnonShapes.Field)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked) Icon(AppIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(text = text, style = CompagnonType.ButtonText.copy(fontWeight = FontWeight.Bold), color = ink)
    }
}

/** `Onboarding-Diet`: six exclusive diets (radio blocks), « Reste sur votre téléphone », « Continuer ». */
@Composable
internal fun DietStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    StepLayout(
        bottom = {
            PrivacyNote(text = stringResource(R.string.onboarding_diet_private), modifier = Modifier.padding(bottom = 14.dp))
            StepPrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                saving = state.saving,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        StepHeader(
            overline = stringResource(R.string.onboarding_section_food),
            title = stringResource(R.string.onboarding_diet_title),
            text = stringResource(R.string.onboarding_diet_text),
        )
        Gap(22.dp)
        val groupLabel = stringResource(R.string.onboarding_diet_group)
        Column(
            modifier = Modifier
                .selectableGroup()
                .semantics { contentDescription = groupLabel },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DietChoice.entries.forEach { diet ->
                DietOption(diet = diet, selected = diet == state.diet, onClick = { onEvent(OnboardingEvent.SelectDiet(diet)) })
            }
        }
    }
}

@Composable
private fun DietOption(diet: DietChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(CompagnonShapes.Button)
            .background(if (selected) CompagnonColors.BasilLight else CompagnonColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) CompagnonColors.Basil else CompagnonColors.Line, CompagnonShapes.Button)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) CompagnonColors.Basil else CompagnonColors.CheckboxOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CompagnonColors.Basil),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(OnboardingTexts.dietLabel(diet)), style = CompagnonType.ListRow)
            Text(text = stringResource(OnboardingTexts.dietHint(diet)), style = CompagnonType.Caption, color = CompagnonColors.InkSoft)
        }
    }
}

/**
 * `Onboarding-Avoid`: five boxes; the ones the diet already excludes are checked in grey and disabled
 * (« Déjà exclu par le régime halal »). « Continuer · 1 à éviter », « Rien à éviter ».
 */
@Composable
internal fun AvoidStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val locale = currentLocale()
    val dietName = stringResource(OnboardingTexts.dietLabel(state.diet)).lowercase(locale)
    StepLayout(
        bottom = {
            StepPrimaryButton(
                text = OnboardingTexts.avoidContinue(state.avoid).resolve(),
                saving = state.saving,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
            StepTextButton(
                text = stringResource(R.string.onboarding_avoid_none),
                onClick = { onEvent(OnboardingEvent.NothingToAvoid) },
                enabled = !state.saving,
            )
        },
    ) {
        StepHeader(
            overline = stringResource(R.string.onboarding_section_food),
            title = stringResource(R.string.onboarding_avoid_title),
            text = stringResource(R.string.onboarding_avoid_text),
        )
        Gap(22.dp)
        ListBlock(shape = CompagnonShapes.BlockLarge) {
            state.avoid.forEachIndexed { index, option ->
                if (index > 0) BlockDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .toggleable(
                            value = option.checked,
                            enabled = !option.locked,
                            role = Role.Checkbox,
                            onValueChange = { onEvent(OnboardingEvent.ToggleAvoid(option.choice)) },
                        )
                        .padding(start = 5.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckMark(checked = option.checked, round = false, enabled = !option.locked)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stringResource(OnboardingTexts.avoidLabel(option.choice)), style = CompagnonType.ListRow)
                        Text(
                            text = if (option.locked) {
                                stringResource(R.string.onboarding_avoid_locked, dietName)
                            } else {
                                stringResource(OnboardingTexts.avoidHint(option.choice))
                            },
                            style = CompagnonType.Caption,
                            color = CompagnonColors.InkSoft,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `Onboarding-Rhythm`: « Vous faites les courses… », three large tiles (7, 14, 30 days) and « Autre durée », like
 * the meta questions; « Continuer avec 7 jours ».
 */
@Composable
internal fun RhythmStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val rhythm = state.rhythm
    val days = pluralStringResource(R.plurals.onboarding_days, rhythm.days, rhythm.days)
    StepLayout(
        bottom = {
            StepPrimaryButton(
                text = stringResource(R.string.onboarding_rhythm_continue, days),
                saving = state.saving,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        StepHeader(
            overline = stringResource(R.string.onboarding_section_rhythm),
            title = stringResource(R.string.onboarding_rhythm_title),
            text = stringResource(R.string.onboarding_rhythm_text),
        )
        BigChoiceQuestion(
            choices = OnboardingTexts.rhythmTiles.map { tile ->
                val label = stringResource(tile.label)
                val sub = pluralStringResource(R.plurals.onboarding_days, tile.days, tile.days)
                BigChoice(
                    value = label,
                    subline = sub,
                    selected = rhythm.choice == tile.choice,
                    contentDescription = "$label, $sub",
                    onClick = { onEvent(OnboardingEvent.SelectRhythm(tile.choice)) },
                )
            },
            otherLabel = stringResource(R.string.onboarding_rhythm_other, days),
            canDecrement = rhythm.days > 1,
            canIncrement = rhythm.days < rhythm.maxDays,
            onStep = { onEvent(OnboardingEvent.StepRhythm(it)) },
            decreaseDescription = stringResource(R.string.onboarding_rhythm_less),
            increaseDescription = stringResource(R.string.onboarding_rhythm_more),
            choicesTop = 28.dp,
            valueStyle = CompagnonType.ListRow.copy(fontWeight = FontWeight.Bold),
            tileHeight = 96.dp,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** The Ocimio icon of the welcome screen (`design/brand/ocimio-icon.svg`): a basil leaf whose midrib is a tick. */
private val OcimioLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "Ocimio",
        defaultWidth = 88.dp,
        defaultHeight = 88.dp,
        viewportWidth = 100f,
        viewportHeight = 100f,
    ).apply {
        addPath(
            pathData = addPathNodes("M24 0h52a24 24 0 0 1 24 24v52a24 24 0 0 1-24 24H24A24 24 0 0 1 0 76V24A24 24 0 0 1 24 0z"),
            fill = SolidColor(CompagnonColors.Basil),
        )
        addPath(
            pathData = addPathNodes("M50 10C70 22 84 46 72 68C66 80 58 86 50 86C42 86 34 80 28 68C16 46 30 22 50 10Z"),
            fill = SolidColor(Color.White),
        )
        addPath(
            pathData = addPathNodes("M50 86L50 93"),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = addPathNodes("M36 54L46 64L65 41"),
            stroke = SolidColor(CompagnonColors.Basil),
            strokeLineWidth = 6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
