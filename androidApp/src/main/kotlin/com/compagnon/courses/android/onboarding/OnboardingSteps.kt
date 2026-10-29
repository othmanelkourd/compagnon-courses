package com.compagnon.courses.android.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.catalog.currentLocale
import com.compagnon.courses.android.questions.BigChoice
import com.compagnon.courses.android.questions.BigChoiceQuestion
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.CheckBadge
import com.compagnon.courses.android.ui.components.CheckMark
import com.compagnon.courses.android.ui.components.CountStepper
import com.compagnon.courses.android.ui.components.ListBlock
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.bounceOnChange
import com.compagnon.courses.android.ui.components.popIn
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.components.riseIn
import com.compagnon.courses.android.ui.illustration.Illustration
import com.compagnon.courses.android.ui.illustration.LeafPattern
import com.compagnon.courses.android.ui.illustration.PatternLeaf
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.feature.onboarding.DietChoice
import com.compagnon.courses.feature.onboarding.HouseholdSection
import com.compagnon.courses.feature.onboarding.MemberKind
import com.compagnon.courses.feature.onboarding.OnboardingEvent
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.PetChoice

/** The groceries floating next to the logo (`Onboarding-Welcome.dc.html`): key, size, rotation, top, right. */
private data class WelcomeProduct(val key: String, val size: Int, val rotation: Float, val top: Int, val right: Int)

/** In the mockup's frame, 68 above the logo; the top of the screen is 48 above the content (status bar). */
private val WelcomeProducts = listOf(
    WelcomeProduct(Illustrations.Screen.DECOR_APPLE, 58, -8f, top = 54, right = 128),
    WelcomeProduct(Illustrations.Screen.DECOR_BAGUETTE, 86, 18f, top = 38, right = 30),
    WelcomeProduct(Illustrations.Screen.DECOR_MILK, 62, 0f, top = 120, right = 86),
    WelcomeProduct(Illustrations.Screen.DECOR_CARROT, 56, 22f, top = 112, right = 16),
    WelcomeProduct(Illustrations.Screen.DECOR_CHEESE, 46, -12f, top = 122, right = 158),
    WelcomeProduct(Illustrations.Screen.DECOR_LEAFY, 40, -20f, top = 14, right = 190),
)

/** The pop delays of [WelcomeProducts] (the mockup's `oc-d2` to `oc-d6`). */
private val WelcomeProductDelays = listOf(80, 120, 160, 240, 240, 320)

private val WelcomeLeaves = listOf(
    PatternLeaf(40f, 40f, -30f),
    PatternLeaf(222f, 196f, 50f, 0.8f),
    PatternLeaf(120f, 210f, -70f, 0.6f),
)

/** The mockup's frame top is this far above the onboarding's content (the status bar). */
private const val MOCKUP_STATUS_BAR: Int = 48

/**
 * `Onboarding-Welcome`: the logo with groceries floating next to it, « Des courses à votre image », the four
 * sections (each with its image and number), « Commencer », « Plus tard ».
 */
@Composable
internal fun WelcomeStep(state: OnboardingUiState.Ready, onEvent: (OnboardingEvent) -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        // The decor scrolls with the content but is not clipped by it: it reaches into the status bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, -scroll.value) },
        ) {
            LeafPattern(
                leaves = WelcomeLeaves,
                width = 250.dp,
                height = 230.dp,
                color = CompagnonColors.Basil.copy(alpha = 0.09f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (40 - MOCKUP_STATUS_BAR).dp),
            )
            WelcomeProducts.forEachIndexed { index, product ->
                Illustration(
                    key = product.key,
                    size = product.size.dp,
                    rotation = product.rotation,
                    popDelayMillis = WelcomeProductDelays[index],
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-product.right).dp, y = (product.top - MOCKUP_STATUS_BAR).dp),
                )
            }
        }
        StepLayout(
            top = (68 - MOCKUP_STATUS_BAR).dp,
            scrollState = scroll,
            bottom = {
                PrivacyNote(text = stringResource(R.string.onboarding_private), modifier = Modifier.padding(bottom = 14.dp))
                StepPrimaryButton(
                    text = stringResource(R.string.onboarding_start),
                    saving = state.saving,
                    onClick = { onEvent(OnboardingEvent.Start) },
                    modifier = Modifier.riseIn(index = 4),
                )
                StepTextButton(
                    text = stringResource(R.string.onboarding_later),
                    onClick = { onEvent(OnboardingEvent.Later) },
                    enabled = !state.saving,
                )
            },
        ) {
            // The app icon (`design/brand/generate_icons.py`), on its soft shadow.
            Image(
                painter = painterResource(R.drawable.ocimio_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .popIn()
                    .size(88.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = CompagnonColors.Basil, spotColor = CompagnonColors.Basil),
            )
            Gap(28.dp)
            Column(modifier = Modifier.riseIn(index = 2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = CompagnonType.ScreenTitleLarge,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(R.string.onboarding_welcome_text), style = CompagnonType.Body, color = CompagnonColors.InkSoft)
            }
            Gap(28.dp)
            ListBlock(shape = CompagnonShapes.BlockLarge, modifier = Modifier.riseIn(index = 3)) {
                OnboardingTexts.sections.forEachIndexed { index, section ->
                    if (index > 0) BlockDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTile(section = section, number = index + 1)
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
}

/** A section of the welcome list: its image on a 44 Basilic clair tile, its number in a badge at the top left. */
@Composable
private fun SectionTile(section: HouseholdSection, number: Int) {
    Box {
        Illustration(
            key = OnboardingTexts.sectionImage(section),
            size = 30.dp,
            plate = 44.dp,
            plateShape = CompagnonShapes.Control,
            pop = false,
        )
        Box(
            modifier = Modifier
                .offset(x = (-5).dp, y = (-5).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(CompagnonColors.Basil)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = CompagnonType.Chip.copy(fontSize = 12.sp, lineHeight = 12.sp),
                color = Color.White,
            )
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
                count = household.persons,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        StepHeader(
            overline = stringResource(R.string.onboarding_section_household),
            title = stringResource(R.string.onboarding_household_title),
            text = stringResource(R.string.onboarding_household_text),
            imageKey = OnboardingTexts.stepImage(OnboardingStep.HOUSEHOLD),
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
                    imageKey = OnboardingTexts.petImage(pet),
                    checked = pet in household.pets,
                    onToggle = { onEvent(OnboardingEvent.TogglePet(pet)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A pet: a 52 tile that toggles, its image and name (checked: Basilic clair, Basilic border of 2 and a tick badge on
 * the image). It « pops » when toggled.
 */
@Composable
private fun ToggleTile(
    text: String,
    imageKey: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (checked) CompagnonColors.BasilDark else CompagnonColors.Ink
    Row(
        modifier = modifier
            .bounceOnChange(checked)
            .height(52.dp)
            .clip(CompagnonShapes.Field)
            .background(if (checked) CompagnonColors.BasilLight else CompagnonColors.Surface)
            .border(if (checked) 2.dp else 1.dp, if (checked) CompagnonColors.Basil else CompagnonColors.Line, CompagnonShapes.Field)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Illustration(key = imageKey, size = 28.dp, pop = false)
            CheckBadge(
                checked = checked,
                size = 16.dp,
                border = 2.dp,
                borderColor = CompagnonColors.BasilLight,
                tick = 9.dp,
                tickStroke = 4f,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 4.dp),
            )
        }
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
            imageKey = OnboardingTexts.stepImage(OnboardingStep.DIET),
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
 * `Onboarding-Avoid`: five boxes, each with its image at the end; the ones the diet already excludes are checked in
 * grey and disabled (« Déjà exclu par le régime halal »). « Continuer · 1 à éviter », « Rien à éviter ».
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
                count = OnboardingTexts.avoidChosen(state.avoid).takeIf { it > 0 },
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
            imageKey = OnboardingTexts.stepImage(OnboardingStep.AVOID),
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
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    Illustration(
                        key = OnboardingTexts.avoidImage(option.choice),
                        size = 34.dp,
                        pop = false,
                        modifier = Modifier.padding(start = 11.dp),
                    )
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
                count = rhythm.days,
                onClick = { onEvent(OnboardingEvent.Next) },
            )
        },
    ) {
        StepHeader(
            overline = stringResource(R.string.onboarding_section_rhythm),
            title = stringResource(R.string.onboarding_rhythm_title),
            text = stringResource(R.string.onboarding_rhythm_text),
            imageKey = OnboardingTexts.stepImage(OnboardingStep.RHYTHM),
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
