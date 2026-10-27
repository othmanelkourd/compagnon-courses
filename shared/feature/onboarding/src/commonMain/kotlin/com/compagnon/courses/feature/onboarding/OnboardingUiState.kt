package com.compagnon.courses.feature.onboarding

/** First launch (shown once, before the home screen) or « Mon foyer » (from the home screen). */
enum class OnboardingMode { FIRST_RUN, EDIT }

/**
 * The screens of the onboarding (`design/maquette/Onboarding-*.dc.html`). [OVERVIEW] is « Mon foyer »
 * (`Household-Edit.dc.html`), only in [OnboardingMode.EDIT]; [WELCOME] only in [OnboardingMode.FIRST_RUN].
 */
enum class OnboardingStep { WELCOME, OVERVIEW, HOUSEHOLD, DIET, AVOID, ESSENTIALS, RECURRING, RHYTHM, RECAP }

/** The four parts of the questionnaire, as listed on the welcome, recap and « Mon foyer » screens. */
enum class HouseholdSection { HOUSEHOLD, FOOD, ESSENTIALS, RHYTHM }

/** « Je mange de tout », « Sans porc », « Halal », « Pescétarien », « Végétarien », « Végan ». */
enum class DietChoice { OMNIVORE, NO_PORK, HALAL, PESCATARIAN, VEGETARIAN, VEGAN }

/** « Gluten », « Lactose », « Fruits à coque », « Œufs », « Alcool ». */
enum class AvoidChoice { GLUTEN, LACTOSE, NUTS, EGGS, ALCOHOL }

/** « Chien », « Chat », « Autre ». */
enum class PetChoice { DOG, CAT, OTHER }

/** The members counted on the household step, each with its stepper. */
enum class MemberKind { ADULTS, BABIES, CHILDREN, TEENS }

/** « Semaine », « 2 semaines », « Mois » (7, 14 and 30 days). */
enum class CadenceChoice(val days: Int) { WEEK(7), TWO_WEEKS(14), MONTH(30) }

/** The rhythm tiles: 7, 14 and 30 days, or [OTHER] (« Autre durée »). */
enum class RhythmChoice { WEEK, TWO_WEEKS, MONTH, OTHER }

enum class OnboardingErrorKind {
    /** The household could not be read or saved on the device. */
    STORAGE_FAILED,
}

/** Where the person goes when the onboarding closes. */
enum class OnboardingExit {
    /** « Faire le point maintenant ». */
    START_SESSION,

    /** « Aller à l'accueil », « Plus tard », or the close button of « Mon foyer ». */
    HOME,
}

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState

    /**
     * One screen of the onboarding.
     *
     * @property stepNumber « Étape n sur 4 » (1 to 4), or null on the welcome, overview and recap screens.
     * @property canSkip « Passer » is shown (questionnaire from the start, not a single section).
     * @property closeInsteadOfBack the top bar shows ✕ (a section opened from « Mon foyer ») instead of ←.
     * @property exit set once the onboarding is done: the app leaves it, then sends [OnboardingEvent.ExitHandled].
     */
    data class Ready(
        val mode: OnboardingMode,
        val step: OnboardingStep,
        val stepNumber: Int?,
        val stepCount: Int,
        val canSkip: Boolean,
        val closeInsteadOfBack: Boolean,
        val household: HouseholdStepUi,
        val diet: DietChoice,
        val avoid: List<AvoidOptionUi>,
        val essentials: EssentialsStepUi,
        val recurring: RecurringStepUi,
        val rhythm: RhythmStepUi,
        val summary: HouseholdSummaryUi,
        val saving: Boolean,
        val error: OnboardingErrorKind?,
        val exit: OnboardingExit?,
    ) : OnboardingUiState
}

/**
 * The household step. Adults go from 1 to [maxMembers], the others from 0.
 *
 * @property persons everyone at home (« Continuer · 4 personnes »).
 */
data class HouseholdStepUi(
    val adults: Int,
    val babies: Int,
    val children: Int,
    val teens: Int,
    val pets: List<PetChoice>,
    val maxMembers: Int,
) {
    val persons: Int get() = adults + babies + children + teens

    fun count(kind: MemberKind): Int = when (kind) {
        MemberKind.ADULTS -> adults
        MemberKind.BABIES -> babies
        MemberKind.CHILDREN -> children
        MemberKind.TEENS -> teens
    }

    fun canDecrement(kind: MemberKind): Boolean = count(kind) > (if (kind == MemberKind.ADULTS) 1 else 0)

    fun canIncrement(kind: MemberKind): Boolean = count(kind) < maxMembers
}

/**
 * One « à éviter » line. [locked]: already excluded by the diet (« Déjà exclu par le régime halal »), shown
 * checked and disabled.
 */
data class AvoidOptionUi(val choice: AvoidChoice, val checked: Boolean, val locked: Boolean)

/** A product chip (essentials) or search result, with its illustration ([imageKey], `Illustrations`). */
data class ProductChipUi(val productId: String, val name: String, val checked: Boolean, val imageKey: String)

/**
 * One aisle of the essentials grid: the checked products and the most common ones, or the whole aisle when
 * [expanded]. [total] is the aisle's product count (« Voir tout (44) »); [imageKey] the aisle's illustration.
 */
data class EssentialsAisleUi(
    val categoryId: String,
    val title: String,
    val items: List<ProductChipUi>,
    val total: Int,
    val expanded: Boolean,
    val imageKey: String,
)

/**
 * The essentials step.
 *
 * @property catalogLoading the bundled catalog is being written (first launch, before the app's own start does it).
 * @property catalogMissing the bundled catalog could not be written on the device: the step can be skipped or retried.
 * @property results search results when [query] is not blank (they replace the aisles).
 * @property checkedCount « Continuer · 14 essentiels ».
 */
data class EssentialsStepUi(
    val query: String,
    val aisles: List<EssentialsAisleUi>,
    val results: List<ProductChipUi>,
    val checkedCount: Int,
    val catalogLoading: Boolean,
    val catalogMissing: Boolean,
)

/** A recurring product and its rhythm, with its illustration ([imageKey]). */
data class RecurringRowUi(val productId: String, val name: String, val cadence: CadenceChoice, val imageKey: String)

/**
 * The « Ce que vous rachetez souvent » step. [adding]: the search field of « Ajouter un produit » is open,
 * with [results] for [query].
 */
data class RecurringStepUi(
    val rows: List<RecurringRowUi>,
    val adding: Boolean,
    val query: String,
    val results: List<ProductChipUi>,
)

/** « Vous faites les courses… »: [days] with its tile, from 1 to [maxDays] days. */
data class RhythmStepUi(val days: Int, val choice: RhythmChoice, val maxDays: Int)

/**
 * The household in a few words, for the recap, « Mon foyer » and its card on the home screen (formatted by the
 * apps: « 4 personnes · Halal · 14 essentiels »).
 *
 * @property onboarded false until the onboarding was saved or put off.
 */
data class HouseholdSummaryUi(
    val adults: Int,
    val babies: Int,
    val children: Int,
    val teens: Int,
    val pets: List<PetChoice>,
    val diet: DietChoice,
    val avoid: List<AvoidChoice>,
    val essentials: Int,
    val recurring: Int,
    val horizonDays: Int,
    val onboarded: Boolean,
) {
    val persons: Int get() = adults + babies + children + teens
}

sealed interface OnboardingEvent {
    /** « Commencer », or « Tout reprendre depuis le début » on « Mon foyer ». */
    data object Start : OnboardingEvent

    /** « Plus tard » on the welcome screen: the onboarding is not shown again. */
    data object Later : OnboardingEvent

    /** Opens one section from « Mon foyer » or from the recap (« Modifier »). */
    data class Edit(val section: HouseholdSection) : OnboardingEvent

    /** ← (previous screen) or ✕ (closes a section without saving it). */
    data object Back : OnboardingEvent

    /** « Continuer » (and « Rien à éviter » on the avoid step, after clearing it). */
    data object Next : OnboardingEvent

    /** « Passer »: the step keeps its default answer. */
    data object Skip : OnboardingEvent

    data class StepMember(val kind: MemberKind, val increment: Boolean) : OnboardingEvent

    data class TogglePet(val pet: PetChoice) : OnboardingEvent

    data class SelectDiet(val diet: DietChoice) : OnboardingEvent

    data class ToggleAvoid(val avoid: AvoidChoice) : OnboardingEvent

    /** « Rien à éviter ». */
    data object NothingToAvoid : OnboardingEvent

    data class SearchEssentials(val query: String) : OnboardingEvent

    data class ToggleEssential(val productId: String) : OnboardingEvent

    /** « Voir tout (n) » / « Voir moins ». */
    data class ExpandAisle(val categoryId: String) : OnboardingEvent

    data class SetCadence(val productId: String, val cadence: CadenceChoice) : OnboardingEvent

    data class RemoveRecurring(val productId: String) : OnboardingEvent

    /** « Ajouter un produit »: opens (true) or closes the search field. */
    data class AddingRecurring(val open: Boolean) : OnboardingEvent

    data class SearchRecurring(val query: String) : OnboardingEvent

    data class AddRecurring(val productId: String) : OnboardingEvent

    data class SelectRhythm(val choice: RhythmChoice) : OnboardingEvent

    /** « Autre durée »: one day less or more. */
    data class StepRhythm(val increment: Boolean) : OnboardingEvent

    /** « Faire le point maintenant » (true) or « Aller à l'accueil » (false) on the recap. */
    data class Finish(val startSession: Boolean) : OnboardingEvent

    /** Retries a failed save, or the catalog download of the essentials step. */
    data object Retry : OnboardingEvent

    data object DismissError : OnboardingEvent

    /** The app left the onboarding after [OnboardingUiState.Ready.exit]. */
    data object ExitHandled : OnboardingEvent
}
