package com.compagnon.courses.android.onboarding

import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.UiText
import com.compagnon.courses.android.ui.illustration.illustrationRes
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.feature.onboarding.AvoidChoice
import com.compagnon.courses.feature.onboarding.AvoidOptionUi
import com.compagnon.courses.feature.onboarding.DietChoice
import com.compagnon.courses.feature.onboarding.HouseholdSection
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import com.compagnon.courses.feature.onboarding.MemberKind
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.PetChoice
import com.compagnon.courses.feature.onboarding.RhythmChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTextsTest {

    /** The household of the mockups: 2 adults, a baby, a child, a cat; halal without lactose. */
    private val summary = HouseholdSummaryUi(
        adults = 2,
        babies = 1,
        children = 1,
        teens = 0,
        pets = listOf(PetChoice.CAT),
        diet = DietChoice.HALAL,
        avoid = listOf(AvoidChoice.LACTOSE),
        essentials = 14,
        recurring = 4,
        horizonDays = 7,
        onboarded = true,
    )

    @Test
    fun theHouseholdListsTheAdultsThenEachMemberAndPetPresent() {
        // « 2 adultes · 1 bébé · 1 enfant · 1 chat » (mockup `Onboarding-Recap`): no teen, so no « 0 ado ».
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Plural(R.plurals.household_adults, 2),
                    UiText.Plural(R.plurals.household_babies, 1),
                    UiText.Plural(R.plurals.household_children, 1),
                    UiText.Res(R.string.household_pet_cat),
                ),
            ),
            OnboardingTexts.householdSummary(summary),
        )
        assertEquals(
            UiText.Joined(listOf(UiText.Plural(R.plurals.household_adults, 1))),
            OnboardingTexts.householdSummary(summary.copy(adults = 1, babies = 0, children = 0, pets = emptyList())),
        )
    }

    @Test
    fun foodIsTheDietThenTheProductsAvoided() {
        // « Halal · sans lactose »; « Je mange de tout » alone when nothing is avoided.
        assertEquals(
            UiText.Joined(listOf(UiText.Res(R.string.diet_halal), UiText.Res(R.string.household_avoid_lactose))),
            OnboardingTexts.foodSummary(summary),
        )
        assertEquals(
            UiText.Joined(listOf(UiText.Res(R.string.diet_omnivore))),
            OnboardingTexts.foodSummary(summary.copy(diet = DietChoice.OMNIVORE, avoid = emptyList())),
        )
    }

    @Test
    fun theRhythmUsesWordsForTheUsualDurationsAndCountsTheRecurringProducts() {
        assertEquals(
            UiText.Joined(listOf(UiText.Res(R.string.household_rhythm_week), UiText.Plural(R.plurals.household_recurring, 4))),
            OnboardingTexts.rhythmSummary(summary),
        )
        assertEquals(
            UiText.Joined(listOf(UiText.Res(R.string.household_rhythm_month))),
            OnboardingTexts.rhythmSummary(summary.copy(horizonDays = 30, recurring = 0)),
        )
        assertEquals(
            UiText.Joined(listOf(UiText.Plural(R.plurals.household_rhythm_days, 10))),
            OnboardingTexts.rhythmSummary(summary.copy(horizonDays = 10, recurring = 0)),
        )
    }

    @Test
    fun theHomeCardSaysPersonsDietAndEssentials() {
        // « 4 personnes · Halal · 14 essentiels » (mockup `Main`).
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Plural(R.plurals.home_household_persons, 4),
                    UiText.Res(R.string.diet_halal),
                    UiText.Plural(R.plurals.home_household_essentials, 14),
                ),
            ),
            OnboardingTexts.homeSummary(summary),
        )
    }

    @Test
    fun eachSectionHasItsSummary() {
        assertEquals(OnboardingTexts.householdSummary(summary), OnboardingTexts.sectionSummary(HouseholdSection.HOUSEHOLD, summary))
        assertEquals(OnboardingTexts.foodSummary(summary), OnboardingTexts.sectionSummary(HouseholdSection.FOOD, summary))
        assertEquals(UiText.Plural(R.plurals.household_essentials, 14), OnboardingTexts.sectionSummary(HouseholdSection.ESSENTIALS, summary))
        assertEquals(OnboardingTexts.rhythmSummary(summary), OnboardingTexts.sectionSummary(HouseholdSection.RHYTHM, summary))
    }

    @Test
    fun theAvoidButtonCountsOnlyTheBoxesThePersonChecked() {
        val halal = listOf(
            AvoidOptionUi(AvoidChoice.LACTOSE, checked = true, locked = false),
            AvoidOptionUi(AvoidChoice.NUTS, checked = false, locked = false),
            AvoidOptionUi(AvoidChoice.ALCOHOL, checked = true, locked = true),
        )
        assertEquals(UiText.Res(R.string.onboarding_avoid_continue, listOf(1)), OnboardingTexts.avoidContinue(halal))
        // Only the diet's own exclusion: plain « Continuer ».
        assertEquals(UiText.Res(R.string.onboarding_continue), OnboardingTexts.avoidContinue(halal.drop(1)))
    }

    @Test
    fun everyChoiceHasItsOwnTexts() {
        assertEquals(DietChoice.entries.size, DietChoice.entries.map(OnboardingTexts::dietLabel).toSet().size)
        assertEquals(DietChoice.entries.size, DietChoice.entries.map(OnboardingTexts::dietHint).toSet().size)
        assertEquals(AvoidChoice.entries.size, AvoidChoice.entries.map(OnboardingTexts::avoidLabel).toSet().size)
        assertEquals(AvoidChoice.entries.size, AvoidChoice.entries.map(OnboardingTexts::avoidHint).toSet().size)
        assertEquals(MemberKind.entries.size, MemberKind.entries.map(OnboardingTexts::memberLabel).toSet().size)
        assertEquals(PetChoice.entries.size, PetChoice.entries.map(OnboardingTexts::petLabel).toSet().size)
        assertEquals(
            listOf(RhythmChoice.WEEK to 7, RhythmChoice.TWO_WEEKS to 14, RhythmChoice.MONTH to 30),
            OnboardingTexts.rhythmTiles.map { it.choice to it.days },
        )
    }

    @Test
    fun everySectionStepAvoidAndPetHasABundledImage() {
        val keys = HouseholdSection.entries.map(OnboardingTexts::sectionImage) +
            OnboardingStep.entries.mapNotNull(OnboardingTexts::stepImage) +
            AvoidChoice.entries.map(OnboardingTexts::avoidImage) +
            PetChoice.entries.map(OnboardingTexts::petImage)

        assertTrue(keys.all { illustrationRes(it) != null })
    }

    @Test
    fun theWelcomeAndMyHouseholdHaveNoStepImage() {
        // Their sections have an image each instead; the diet options have none at all.
        assertNull(OnboardingTexts.stepImage(OnboardingStep.WELCOME))
        assertNull(OnboardingTexts.stepImage(OnboardingStep.OVERVIEW))
        assertEquals(Illustrations.Screen.ONBOARDING_AVOID, OnboardingTexts.stepImage(OnboardingStep.AVOID))
    }

    @Test
    fun onlyTheBoxesCheckedByThePersonAreCounted() {
        val options = listOf(
            AvoidOptionUi(AvoidChoice.LACTOSE, checked = true, locked = false),
            AvoidOptionUi(AvoidChoice.ALCOHOL, checked = true, locked = true),
            AvoidOptionUi(AvoidChoice.EGGS, checked = false, locked = false),
        )

        assertEquals(1, OnboardingTexts.avoidChosen(options))
    }
}
