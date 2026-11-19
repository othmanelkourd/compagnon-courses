package com.compagnon.courses.android.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.R
import com.compagnon.courses.android.onboarding.OnboardingFixtures.essentialsStep
import com.compagnon.courses.android.onboarding.OnboardingFixtures.ready
import com.compagnon.courses.android.testing.overline
import com.compagnon.courses.android.testing.plural
import com.compagnon.courses.android.testing.string
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.feature.onboarding.AvoidChoice
import com.compagnon.courses.feature.onboarding.AvoidOptionUi
import com.compagnon.courses.feature.onboarding.CadenceChoice
import com.compagnon.courses.feature.onboarding.DietChoice
import com.compagnon.courses.feature.onboarding.HouseholdSection
import com.compagnon.courses.feature.onboarding.MemberKind
import com.compagnon.courses.feature.onboarding.OnboardingErrorKind
import com.compagnon.courses.feature.onboarding.OnboardingEvent
import com.compagnon.courses.feature.onboarding.OnboardingMode
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.PetChoice
import com.compagnon.courses.feature.onboarding.RhythmChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The onboarding « Mon foyer » rendered from fake states: each step's texts and the events it sends. */
@RunWith(AndroidJUnit4::class)
class OnboardingStepsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<OnboardingEvent>()

    private fun setState(state: OnboardingUiState) {
        composeRule.setContent {
            CompagnonTheme {
                OnboardingContent(state = state, onEvent = { events += it })
            }
        }
    }

    @Test
    fun theWelcomeStartsOrPutsOff() {
        setState(ready(OnboardingStep.WELCOME))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_section_rhythm)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_start)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_later)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(OnboardingEvent.Start, OnboardingEvent.Later), events) }
    }

    @Test
    fun theHouseholdStepCountsThePersonsAndTogglesThePets() {
        setState(ready(OnboardingStep.HOUSEHOLD))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_step, 1, 4)).assertIsDisplayed()
        // No « Passer » on the first step.
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_skip)).assertDoesNotExist()
        val babies = composeRule.string(R.string.onboarding_babies).lowercase(composeRule.activity.resources.configuration.locales[0])
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.onboarding_member_more, babies)).performClick()
        // No teen: one fewer is disabled.
        val teens = composeRule.string(R.string.onboarding_teens).lowercase(composeRule.activity.resources.configuration.locales[0])
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.onboarding_member_less, teens)).assertIsNotEnabled()
        composeRule.onNode(hasText(composeRule.string(R.string.onboarding_pet_cat)) and isToggleable(), useUnmergedTree = false)
            .assertIsOn()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_pet_dog)).performClick()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.onboarding_household_continue, 4)).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    OnboardingEvent.StepMember(MemberKind.BABIES, increment = true),
                    OnboardingEvent.TogglePet(PetChoice.DOG),
                    OnboardingEvent.Next,
                ),
                events,
            )
        }
    }

    @Test
    fun theDietIsOneChoiceAndCanBeSkipped() {
        setState(ready(OnboardingStep.DIET))

        composeRule.onNodeWithText(composeRule.string(R.string.diet_vegetarian)).assertIsDisplayed()
        composeRule.onNode(hasText(composeRule.string(R.string.diet_vegetarian_hint)), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.diet_vegan)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_skip)).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(OnboardingEvent.SelectDiet(DietChoice.VEGAN), OnboardingEvent.Skip),
                events,
            )
        }
    }

    @Test
    fun aBoxExcludedByTheDietIsCheckedAndDisabled() {
        // A vegan diet already excludes lactose and eggs; the person checked nuts.
        val vegan = ready(OnboardingStep.AVOID).copy(
            diet = DietChoice.VEGAN,
            avoid = listOf(
                AvoidOptionUi(AvoidChoice.GLUTEN, checked = false, locked = false),
                AvoidOptionUi(AvoidChoice.LACTOSE, checked = true, locked = true),
                AvoidOptionUi(AvoidChoice.NUTS, checked = true, locked = false),
                AvoidOptionUi(AvoidChoice.EGGS, checked = true, locked = true),
                AvoidOptionUi(AvoidChoice.ALCOHOL, checked = false, locked = false),
            ),
        )
        setState(vegan)

        val locale = composeRule.activity.resources.configuration.locales[0]
        val diet = composeRule.string(R.string.diet_vegan).lowercase(locale)
        composeRule.onAllNodesWithText(composeRule.string(R.string.onboarding_avoid_locked, diet), substring = true)
            .onFirst()
            .assertIsDisplayed()
        composeRule.onNode(hasText(composeRule.string(R.string.avoid_lactose)) and isToggleable())
            .assertIsOn()
            .assertIsNotEnabled()
        composeRule.onNode(hasText(composeRule.string(R.string.avoid_gluten)) and isToggleable()).assertIsOff().performClick()
        // Only the box the person checked counts: « Continuer · 1 à éviter ».
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_avoid_continue, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_avoid_none)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(OnboardingEvent.ToggleAvoid(AvoidChoice.GLUTEN), OnboardingEvent.NothingToAvoid), events)
        }
    }

    @Test
    fun theEssentialsShowTheAislesAndTheCount() {
        setState(ready(OnboardingStep.ESSENTIALS))

        composeRule.onNodeWithText(composeRule.overline("Fruits et légumes")).assertIsDisplayed()
        composeRule.onNode(hasText("Bananes") and isToggleable()).assertIsOn()
        composeRule.onNode(hasText("Tomates") and isToggleable()).assertIsOff().performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_aisle_more, 44)).performClick()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.onboarding_essentials_continue, 14)).assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(listOf(OnboardingEvent.ToggleEssential("tomate"), OnboardingEvent.ExpandAisle("fruits-legumes")), events)
        }
    }

    @Test
    fun withoutCatalogTheEssentialsOfferToRetry() {
        setState(ready(OnboardingStep.ESSENTIALS, essentials = essentialsStep.copy(aisles = emptyList(), checkedCount = 0, catalogMissing = true)))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_catalog_missing)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.action_retry)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_continue)).assertIsDisplayed()

        composeRule.runOnIdle { assertEquals(listOf(OnboardingEvent.Retry), events) }
    }

    @Test
    fun recurringProductsChangeTheirRhythmOrAreRemoved() {
        setState(ready(OnboardingStep.RECURRING))

        composeRule.onNodeWithText("Baguette").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.onboarding_cadence_group, "Couches"))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.onboarding_recurring_remove, "Baguette")).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_recurring_add)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(OnboardingEvent.RemoveRecurring("baguette"), OnboardingEvent.AddingRecurring(open = true)), events)
        }
    }

    @Test
    fun aCadenceSegmentSetsTheRhythm() {
        setState(ready(OnboardingStep.RECURRING, recurring = OnboardingFixtures.recurringStep.copy(rows = OnboardingFixtures.recurringStep.rows.take(1))))

        composeRule.onNodeWithText(composeRule.string(R.string.cadence_week)).assertIsSelected()
        composeRule.onNodeWithText(composeRule.string(R.string.cadence_month)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(OnboardingEvent.SetCadence("baguette", CadenceChoice.MONTH)), events) }
    }

    @Test
    fun theRhythmIsATileOrAnotherDuration() {
        setState(ready(OnboardingStep.RHYTHM))

        val days = composeRule.plural(R.plurals.onboarding_days, 7)
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_rhythm_other, days)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.rhythm_month)).performClick()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.onboarding_rhythm_more)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_rhythm_continue, days)).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(OnboardingEvent.SelectRhythm(RhythmChoice.MONTH), OnboardingEvent.StepRhythm(increment = true), OnboardingEvent.Next),
                events,
            )
        }
    }

    @Test
    fun theRecapSummarisesEachSectionAndFinishes() {
        setState(ready(OnboardingStep.RECAP))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_recap_title)).assertIsDisplayed()
        val household = listOf(
            composeRule.plural(R.plurals.household_adults, 2),
            composeRule.plural(R.plurals.household_babies, 1),
            composeRule.plural(R.plurals.household_children, 1),
            composeRule.string(R.string.household_pet_cat),
        ).joinToString(" · ")
        composeRule.onNodeWithText(household, substring = true).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(
            composeRule.string(R.string.diet_vegetarian) + " · " + composeRule.string(R.string.household_avoid_lactose),
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_start_session)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_go_home)).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    OnboardingEvent.Edit(HouseholdSection.HOUSEHOLD),
                    OnboardingEvent.Finish(startSession = true),
                    OnboardingEvent.Finish(startSession = false),
                ),
                events,
            )
        }
    }

    @Test
    fun myHouseholdOpensASectionOrRestarts() {
        setState(ready(OnboardingStep.OVERVIEW, mode = OnboardingMode.EDIT))

        composeRule.onNodeWithText(composeRule.string(R.string.household_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.plural(R.plurals.household_essentials, 14)).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.household_restart)).performClick()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.household_back)).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(OnboardingEvent.Edit(HouseholdSection.ESSENTIALS), OnboardingEvent.Start, OnboardingEvent.Back),
                events,
            )
        }
    }

    @Test
    fun aSectionOpenedFromMyHouseholdClosesWithoutSkip() {
        setState(ready(OnboardingStep.DIET, mode = OnboardingMode.EDIT))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_skip)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.action_close)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(OnboardingEvent.Back), events) }
    }

    @Test
    fun aStorageErrorShowsTheBannerAndRetries() {
        setState(ready(OnboardingStep.HOUSEHOLD).copy(error = OnboardingErrorKind.STORAGE_FAILED))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_error_storage)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.action_retry)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(OnboardingEvent.Retry), events) }
    }

    @Test
    fun savingDisablesTheMainButton() {
        setState(ready(OnboardingStep.RHYTHM).copy(saving = true))

        composeRule.onNodeWithText(composeRule.string(R.string.onboarding_saving)).assertIsNotEnabled()
        composeRule.runOnIdle { assertTrue(events.isEmpty()) }
    }
}
