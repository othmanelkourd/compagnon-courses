package com.compagnon.courses.android.onboarding

import androidx.annotation.StringRes
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.UiText
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.feature.onboarding.AvoidChoice
import com.compagnon.courses.feature.onboarding.AvoidOptionUi
import com.compagnon.courses.feature.onboarding.CadenceChoice
import com.compagnon.courses.feature.onboarding.DietChoice
import com.compagnon.courses.feature.onboarding.HouseholdSection
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import com.compagnon.courses.feature.onboarding.MemberKind
import com.compagnon.courses.feature.onboarding.OnboardingStep
import com.compagnon.courses.feature.onboarding.PetChoice
import com.compagnon.courses.feature.onboarding.RhythmChoice

/**
 * The fixed copy of the onboarding « Mon foyer » (`design/TEXTES.md`, « Bienvenue et Mon foyer »), chosen from the
 * shared UI enums. Pure functions: the summaries are unit tested without Android resources.
 */
internal object OnboardingTexts {

    /** The four sections, in the order of the welcome, recap and « Mon foyer » screens. */
    val sections: List<HouseholdSection> = HouseholdSection.entries

    /** « Votre foyer » (welcome list, overline of step 1). */
    @StringRes
    fun sectionTitle(section: HouseholdSection): Int = when (section) {
        HouseholdSection.HOUSEHOLD -> R.string.onboarding_section_household
        HouseholdSection.FOOD -> R.string.onboarding_section_food
        HouseholdSection.ESSENTIALS -> R.string.onboarding_section_essentials
        HouseholdSection.RHYTHM -> R.string.onboarding_section_rhythm
    }

    @StringRes
    fun sectionHint(section: HouseholdSection): Int = when (section) {
        HouseholdSection.HOUSEHOLD -> R.string.onboarding_section_household_hint
        HouseholdSection.FOOD -> R.string.onboarding_section_food_hint
        HouseholdSection.ESSENTIALS -> R.string.onboarding_section_essentials_hint
        HouseholdSection.RHYTHM -> R.string.onboarding_section_rhythm_hint
    }

    /** « Foyer », « Alimentation »… (recap and « Mon foyer » rows). */
    @StringRes
    fun sectionRow(section: HouseholdSection): Int = when (section) {
        HouseholdSection.HOUSEHOLD -> R.string.household_row_household
        HouseholdSection.FOOD -> R.string.household_row_food
        HouseholdSection.ESSENTIALS -> R.string.household_row_essentials
        HouseholdSection.RHYTHM -> R.string.household_row_rhythm
    }

    @StringRes
    fun memberLabel(kind: MemberKind): Int = when (kind) {
        MemberKind.ADULTS -> R.string.onboarding_adults
        MemberKind.BABIES -> R.string.onboarding_babies
        MemberKind.CHILDREN -> R.string.onboarding_children
        MemberKind.TEENS -> R.string.onboarding_teens
    }

    @StringRes
    fun memberHint(kind: MemberKind): Int = when (kind) {
        MemberKind.ADULTS -> R.string.onboarding_adults_hint
        MemberKind.BABIES -> R.string.onboarding_babies_hint
        MemberKind.CHILDREN -> R.string.onboarding_children_hint
        MemberKind.TEENS -> R.string.onboarding_teens_hint
    }

    @StringRes
    fun petLabel(pet: PetChoice): Int = when (pet) {
        PetChoice.DOG -> R.string.onboarding_pet_dog
        PetChoice.CAT -> R.string.onboarding_pet_cat
        PetChoice.OTHER -> R.string.onboarding_pet_other
    }

    @StringRes
    fun dietLabel(diet: DietChoice): Int = when (diet) {
        DietChoice.OMNIVORE -> R.string.diet_omnivore
        DietChoice.NO_PORK -> R.string.diet_no_pork
        DietChoice.HALAL -> R.string.diet_halal
        DietChoice.PESCATARIAN -> R.string.diet_pescatarian
        DietChoice.VEGETARIAN -> R.string.diet_vegetarian
        DietChoice.VEGAN -> R.string.diet_vegan
    }

    @StringRes
    fun dietHint(diet: DietChoice): Int = when (diet) {
        DietChoice.OMNIVORE -> R.string.diet_omnivore_hint
        DietChoice.NO_PORK -> R.string.diet_no_pork_hint
        DietChoice.HALAL -> R.string.diet_halal_hint
        DietChoice.PESCATARIAN -> R.string.diet_pescatarian_hint
        DietChoice.VEGETARIAN -> R.string.diet_vegetarian_hint
        DietChoice.VEGAN -> R.string.diet_vegan_hint
    }

    @StringRes
    fun avoidLabel(avoid: AvoidChoice): Int = when (avoid) {
        AvoidChoice.GLUTEN -> R.string.avoid_gluten
        AvoidChoice.LACTOSE -> R.string.avoid_lactose
        AvoidChoice.NUTS -> R.string.avoid_nuts
        AvoidChoice.EGGS -> R.string.avoid_eggs
        AvoidChoice.ALCOHOL -> R.string.avoid_alcohol
    }

    @StringRes
    fun avoidHint(avoid: AvoidChoice): Int = when (avoid) {
        AvoidChoice.GLUTEN -> R.string.avoid_gluten_hint
        AvoidChoice.LACTOSE -> R.string.avoid_lactose_hint
        AvoidChoice.NUTS -> R.string.avoid_nuts_hint
        AvoidChoice.EGGS -> R.string.avoid_eggs_hint
        AvoidChoice.ALCOHOL -> R.string.avoid_alcohol_hint
    }

    /** « Continuer · 1 à éviter » when the person checked a box themselves (not the ones of the diet), else « Continuer ». */
    fun avoidContinue(options: List<AvoidOptionUi>): UiText {
        val chosen = avoidChosen(options)
        return if (chosen > 0) UiText.Res(R.string.onboarding_avoid_continue, listOf(chosen)) else UiText.Res(R.string.onboarding_continue)
    }

    /** The boxes the person checked themselves (the number of « Continuer · 1 à éviter »). */
    fun avoidChosen(options: List<AvoidOptionUi>): Int = options.count { it.checked && !it.locked }

    @StringRes
    fun cadenceLabel(cadence: CadenceChoice): Int = when (cadence) {
        CadenceChoice.WEEK -> R.string.cadence_week
        CadenceChoice.TWO_WEEKS -> R.string.cadence_two_weeks
        CadenceChoice.MONTH -> R.string.cadence_month
    }

    /** The three rhythm tiles: « Chaque semaine » and its days. */
    val rhythmTiles: List<RhythmTile> = listOf(
        RhythmTile(RhythmChoice.WEEK, R.string.rhythm_week, CadenceChoice.WEEK.days),
        RhythmTile(RhythmChoice.TWO_WEEKS, R.string.rhythm_two_weeks, CadenceChoice.TWO_WEEKS.days),
        RhythmTile(RhythmChoice.MONTH, R.string.rhythm_month, CadenceChoice.MONTH.days),
    )

    // --- Images (`Illustrations` keys) ---

    /** The image of a section (welcome list, « Mon foyer »). */
    fun sectionImage(section: HouseholdSection): String = when (section) {
        HouseholdSection.HOUSEHOLD -> Illustrations.Screen.ONBOARDING_HOUSEHOLD
        HouseholdSection.FOOD -> Illustrations.Screen.ONBOARDING_FOOD
        HouseholdSection.ESSENTIALS -> Illustrations.Screen.ONBOARDING_ESSENTIALS
        HouseholdSection.RHYTHM -> Illustrations.Screen.ONBOARDING_RECURRING
    }

    /** The image next to a step's overline (or title, for the essentials); none on the welcome and « Mon foyer ». */
    fun stepImage(step: OnboardingStep): String? = when (step) {
        OnboardingStep.HOUSEHOLD -> Illustrations.Screen.ONBOARDING_HOUSEHOLD
        OnboardingStep.DIET -> Illustrations.Screen.ONBOARDING_FOOD
        OnboardingStep.AVOID -> Illustrations.Screen.ONBOARDING_AVOID
        OnboardingStep.ESSENTIALS -> Illustrations.Screen.ONBOARDING_ESSENTIALS
        OnboardingStep.RECURRING -> Illustrations.Screen.ONBOARDING_RECURRING
        OnboardingStep.RHYTHM -> Illustrations.Screen.ONBOARDING_RHYTHM
        OnboardingStep.RECAP -> Illustrations.Screen.ONBOARDING_RECAP
        OnboardingStep.WELCOME, OnboardingStep.OVERVIEW -> null
    }

    /** The image at the end of an « à éviter » line. The diets have none. */
    fun avoidImage(avoid: AvoidChoice): String = when (avoid) {
        AvoidChoice.GLUTEN -> Illustrations.Screen.AVOID_GLUTEN
        AvoidChoice.LACTOSE -> Illustrations.Screen.AVOID_LACTOSE
        AvoidChoice.NUTS -> Illustrations.Screen.AVOID_NUTS
        AvoidChoice.EGGS -> Illustrations.Screen.AVOID_EGGS
        AvoidChoice.ALCOHOL -> Illustrations.Screen.AVOID_ALCOHOL
    }

    fun petImage(pet: PetChoice): String = when (pet) {
        PetChoice.DOG -> Illustrations.Screen.PET_DOG
        PetChoice.CAT -> Illustrations.Screen.PET_CAT
        PetChoice.OTHER -> Illustrations.Screen.PET_OTHER
    }

    // --- Summaries (recap, « Mon foyer », home card) ---

    /** « 2 adultes · 1 bébé · 1 enfant · 1 chat »: the adults, then each age band and pet present. */
    fun householdSummary(summary: HouseholdSummaryUi): UiText = UiText.Joined(
        buildList {
            add(UiText.Plural(R.plurals.household_adults, summary.adults))
            if (summary.babies > 0) add(UiText.Plural(R.plurals.household_babies, summary.babies))
            if (summary.children > 0) add(UiText.Plural(R.plurals.household_children, summary.children))
            if (summary.teens > 0) add(UiText.Plural(R.plurals.household_teens, summary.teens))
            summary.pets.forEach { pet ->
                add(
                    UiText.Res(
                        when (pet) {
                            PetChoice.DOG -> R.string.household_pet_dog
                            PetChoice.CAT -> R.string.household_pet_cat
                            PetChoice.OTHER -> R.string.household_pet_other
                        },
                    ),
                )
            }
        },
    )

    /** « Halal · sans lactose »: the diet, then the products avoided (« Je mange de tout » alone if nothing). */
    fun foodSummary(summary: HouseholdSummaryUi): UiText = UiText.Joined(
        listOf(UiText.Res(dietLabel(summary.diet))) + summary.avoid.map { avoid ->
            UiText.Res(
                when (avoid) {
                    AvoidChoice.GLUTEN -> R.string.household_avoid_gluten
                    AvoidChoice.LACTOSE -> R.string.household_avoid_lactose
                    AvoidChoice.NUTS -> R.string.household_avoid_nuts
                    AvoidChoice.EGGS -> R.string.household_avoid_eggs
                    AvoidChoice.ALCOHOL -> R.string.household_avoid_alcohol
                },
            )
        },
    )

    /** « 14 produits ». */
    fun essentialsSummary(
        summary: HouseholdSummaryUi,
    ): UiText = UiText.Plural(R.plurals.household_essentials, summary.essentials)

    /** « Courses chaque semaine · 4 récurrents » (no recurring part when there is none). */
    fun rhythmSummary(summary: HouseholdSummaryUi): UiText = UiText.Joined(
        buildList {
            add(
                when (summary.horizonDays) {
                    CadenceChoice.WEEK.days -> UiText.Res(R.string.household_rhythm_week)
                    CadenceChoice.TWO_WEEKS.days -> UiText.Res(R.string.household_rhythm_two_weeks)
                    CadenceChoice.MONTH.days -> UiText.Res(R.string.household_rhythm_month)
                    else -> UiText.Plural(R.plurals.household_rhythm_days, summary.horizonDays)
                },
            )
            if (summary.recurring > 0) add(UiText.Plural(R.plurals.household_recurring, summary.recurring))
        },
    )

    fun sectionSummary(section: HouseholdSection, summary: HouseholdSummaryUi): UiText = when (section) {
        HouseholdSection.HOUSEHOLD -> householdSummary(summary)
        HouseholdSection.FOOD -> foodSummary(summary)
        HouseholdSection.ESSENTIALS -> essentialsSummary(summary)
        HouseholdSection.RHYTHM -> rhythmSummary(summary)
    }

    /** The « Mon foyer » card of the home screen: « 4 personnes · Halal · 14 essentiels ». */
    fun homeSummary(summary: HouseholdSummaryUi): UiText = UiText.Joined(
        listOf(
            UiText.Plural(R.plurals.home_household_persons, summary.persons),
            UiText.Res(dietLabel(summary.diet)),
            UiText.Plural(R.plurals.home_household_essentials, summary.essentials),
        ),
    )
}

/** A rhythm tile: [choice], its words ([label]) and its [days] (sub-line « 7 jours »). */
internal data class RhythmTile(val choice: RhythmChoice, @param:StringRes val label: Int, val days: Int)
