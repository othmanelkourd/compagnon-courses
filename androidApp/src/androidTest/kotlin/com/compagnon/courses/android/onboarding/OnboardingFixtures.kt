package com.compagnon.courses.android.onboarding

import com.compagnon.courses.feature.onboarding.AvoidChoice
import com.compagnon.courses.feature.onboarding.AvoidOptionUi
import com.compagnon.courses.feature.onboarding.CadenceChoice
import com.compagnon.courses.feature.onboarding.DietChoice
import com.compagnon.courses.feature.onboarding.EssentialsAisleUi
import com.compagnon.courses.feature.onboarding.EssentialsStepUi
import com.compagnon.courses.feature.onboarding.HouseholdStepUi
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import com.compagnon.courses.feature.onboarding.OnboardingMode
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.OnboardingUiState
import com.compagnon.courses.feature.onboarding.PetChoice
import com.compagnon.courses.feature.onboarding.ProductChipUi
import com.compagnon.courses.feature.onboarding.RecurringRowUi
import com.compagnon.courses.feature.onboarding.RecurringStepUi
import com.compagnon.courses.feature.onboarding.RhythmChoice
import com.compagnon.courses.feature.onboarding.RhythmStepUi

/** Fake onboarding states: the household of the mockups (2 adults, a baby, a child, a cat; halal, no lactose). */
internal object OnboardingFixtures {

    val summary = HouseholdSummaryUi(
        adults = 2,
        babies = 1,
        children = 1,
        teens = 0,
        pets = listOf(PetChoice.CAT),
        diet = DietChoice.HALAL,
        avoid = listOf(AvoidChoice.LACTOSE),
        essentials = 14,
        recurring = 3,
        horizonDays = 7,
        onboarded = true,
    )

    private val stepNumbers = mapOf(
        OnboardingStep.HOUSEHOLD to 1,
        OnboardingStep.DIET to 2,
        OnboardingStep.AVOID to 2,
        OnboardingStep.ESSENTIALS to 3,
        OnboardingStep.RECURRING to 4,
        OnboardingStep.RHYTHM to 4,
    )

    fun ready(
        step: OnboardingStep,
        mode: OnboardingMode = OnboardingMode.FIRST_RUN,
        essentials: EssentialsStepUi = essentialsStep,
        recurring: RecurringStepUi = recurringStep,
    ) = OnboardingUiState.Ready(
        mode = mode,
        step = step,
        stepNumber = stepNumbers[step],
        stepCount = 4,
        canSkip = mode == OnboardingMode.FIRST_RUN && step != OnboardingStep.HOUSEHOLD && step in stepNumbers,
        closeInsteadOfBack = mode == OnboardingMode.EDIT && step in stepNumbers,
        household = HouseholdStepUi(adults = 2, babies = 1, children = 1, teens = 0, pets = listOf(PetChoice.CAT), maxMembers = 12),
        diet = DietChoice.HALAL,
        avoid = listOf(
            AvoidOptionUi(AvoidChoice.GLUTEN, checked = false, locked = false),
            AvoidOptionUi(AvoidChoice.LACTOSE, checked = true, locked = false),
            AvoidOptionUi(AvoidChoice.NUTS, checked = false, locked = false),
            AvoidOptionUi(AvoidChoice.EGGS, checked = false, locked = false),
            AvoidOptionUi(AvoidChoice.ALCOHOL, checked = true, locked = true),
        ),
        essentials = essentials,
        recurring = recurring,
        rhythm = RhythmStepUi(days = 7, choice = RhythmChoice.WEEK, maxDays = 30),
        summary = summary,
        saving = false,
        error = null,
        exit = null,
    )

    val essentialsStep = EssentialsStepUi(
        query = "",
        aisles = listOf(
            EssentialsAisleUi(
                categoryId = "fruits-legumes",
                title = "Fruits et légumes",
                items = listOf(
                    ProductChipUi("banane", "Bananes", checked = true, imageKey = "banana"),
                    ProductChipUi("tomate", "Tomates", checked = false, imageKey = "tomato"),
                ),
                total = 44,
                expanded = false,
                imageKey = "green_apple",
            ),
        ),
        results = emptyList(),
        checkedCount = 14,
        catalogLoading = false,
        catalogMissing = false,
    )

    val recurringStep = RecurringStepUi(
        rows = listOf(
            RecurringRowUi("baguette", "Baguette", CadenceChoice.WEEK, imageKey = "baguette_bread"),
            RecurringRowUi("couches", "Couches", CadenceChoice.TWO_WEEKS, imageKey = "safety_pin"),
        ),
        adding = false,
        query = "",
        results = emptyList(),
    )
}
