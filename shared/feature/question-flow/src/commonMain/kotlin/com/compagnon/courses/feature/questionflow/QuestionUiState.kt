package com.compagnon.courses.feature.questionflow

/**
 * UI state of a question session (`docs/question-engine.md` §6.6), ready to render: names are capitalized,
 * quantities are labelled in the product's unit and the catalog locale (« 2 bouteilles », « 1,5 kg »).
 * Fixed copy (titles, buttons, reasons) is the platform's job, from the enums below.
 */
sealed interface QuestionUiState {

    /** Loading the catalog, planning, or rebuilding a saved session. */
    data object Loading : QuestionUiState

    /** « Courses pour combien de jours ? », « Combien de personnes ? », « Des invités ? ». */
    data class Meta(
        val question: MetaQuestionUi,
        val progress: QuestionProgress,
        val canUndo: Boolean,
    ) : QuestionUiState

    /** The T1 screen « Probablement nécessaire »: pre-checked items, validated in one tap ([QuestionEvent.ConfirmItems]). */
    data class Suggested(
        val items: List<SuggestedItemUi>,
        val checkedCount: Int,
        val progress: QuestionProgress,
        val canUndo: Boolean,
    ) : QuestionUiState

    /**
     * One full-screen product card: yes (with a quantity), no, or a nuanced answer.
     *
     * @property nextImageKey the illustration of the next question (product or aisle), shown on the card behind;
     *   null when this card is the last one.
     */
    data class Card(
        val card: ProductCardUi,
        val progress: QuestionProgress,
        val canUndo: Boolean,
        val nextImageKey: String? = null,
    ) : QuestionUiState

    /** « Besoin de quelque chose en Entretien ? » */
    data class Gate(
        val gate: CategoryGateUi,
        val progress: QuestionProgress,
        val canUndo: Boolean,
    ) : QuestionUiState

    /** The products behind a gate answered yes, all unchecked; confirmed with [QuestionEvent.ConfirmItems]. */
    data class GateItems(
        val gate: CategoryGateUi,
        val items: List<SuggestedItemUi>,
        val checkedCount: Int,
        val progress: QuestionProgress,
        val canUndo: Boolean,
    ) : QuestionUiState

    /**
     * Recap before writing the list, with « Autre chose ? » (search, [QuestionEvent.Search] then
     * [QuestionEvent.AddOther]). [QuestionEvent.Confirm] writes the list.
     */
    data class Review(
        val items: List<ReviewItemUi>,
        val searchQuery: String,
        val searchResults: List<ProductSearchResultUi>,
        val progress: QuestionProgress,
        val canUndo: Boolean,
    ) : QuestionUiState

    /** Writing the list, the answers and the statistics. */
    data object Committing : QuestionUiState

    /** Terminal: [itemsAdded] items were added to the list (none when [abandoned]). */
    data class Done(val itemsAdded: Int, val abandoned: Boolean) : QuestionUiState

    /** [QuestionEvent.Retry] when [retryable]; [QuestionEvent.StartOver] always starts a new session. */
    data class Error(val kind: QuestionErrorKind, val retryable: Boolean) : QuestionUiState
}

enum class QuestionErrorKind {
    /** No local catalog: the bundled one could not be written on the device (storage full…). */
    NO_CATALOG,

    /** The engine could not plan the session. */
    PLANNING_FAILED,

    /** The session or the list could not be saved. */
    STORAGE_FAILED,
}

/** « 8 / 12 »: [answered] questions out of [total]. */
data class QuestionProgress(val answered: Int, val total: Int)

/** A quantity as sent back in events: [milli] thousandths of [unit] (CLDR code: `kilogram`, `bottle`…). */
data class QuantityValue(val milli: Long, val unit: String)

/** A quantity with its label: « 2 bouteilles », « 1,5 kg », « 6 œufs ». */
data class QuantityChoice(val value: QuantityValue, val label: String)

/** The stepper of a quantity: [selected] moves by one policy step within the bounds. */
data class QuantityStepper(
    val selected: QuantityChoice,
    val canDecrement: Boolean,
    val canIncrement: Boolean,
)

enum class MetaQuestionKind {
    /** « Courses pour combien de jours ? » */
    HORIZON_DAYS,

    /** « Combien de personnes cette semaine ? » */
    PERSONS,

    /** « Des invités ? » (number of guests). */
    GUESTS,
}

/** A meta question with its pre-filled [value]; [choices] are quick answers, any value in [min]..[max] is valid. */
data class MetaQuestionUi(
    val kind: MetaQuestionKind,
    val value: Int,
    val min: Int,
    val max: Int,
    val choices: List<Int>,
)

enum class ReasonKind { DUE, STAPLE, SEASONAL, EVENT, RADAR, PRIOR, EXPLORATION, LITTLE_LEFT, LAST_LIST, CADENCE }

/**
 * « Pourquoi cette question ? ». Fields are set by kind: [DUE][ReasonKind.DUE] → [daysSinceLast],
 * [intervalDays] and [usualQuantity]; [RADAR][ReasonKind.RADAR] → [weeksSinceLast]; [PRIOR][ReasonKind.PRIOR]
 * → [householdSize]; [CADENCE][ReasonKind.CADENCE] → [intervalDays].
 */
data class ReasonUi(
    val kind: ReasonKind,
    val daysSinceLast: Int? = null,
    val intervalDays: Int? = null,
    val usualQuantity: String? = null,
    val weeksSinceLast: Int? = null,
    val householdSize: Int? = null,
)

/**
 * A line of the T1 screen or of a gate's products: toggled and stepped with events keyed by [productId].
 * [imageKey]: its illustration (`Illustrations`), on every product line and card below.
 */
data class SuggestedItemUi(
    val productId: String,
    val name: String,
    val checked: Boolean,
    val quantity: QuantityStepper,
    val reason: ReasonUi?,
    val imageKey: String,
)

enum class ProductCardKind {
    STANDARD,

    /** « Il en reste un peu » last time. */
    WATCH,

    /** « Radar des oubliés »: a long-cycle product bought a while ago. */
    RADAR,

    EXPLORATION,
}

/** Another unit the product can be bought in (bulk / pack toggle): [QuestionEvent.SelectUnit]. */
data class UnitOptionUi(val unit: String, val label: String, val selected: Boolean)

/**
 * A product card.
 *
 * @property needPhrase the noun phrase with its partitive (« du lait demi-écrémé », « des œufs »; English: « eggs »),
 *   to insert in the question: fr « Il vous faut %s ? », en « Do you need %s? ».
 * @property presets quick answers in the selected unit, one tap = yes with that quantity ([QuestionEvent.Yes]).
 * @property quantity the stepper; [QuestionEvent.Yes] without quantity answers its selection.
 * @property isSameAsLastTime the proposed quantity is the previous answer (« Comme la dernière fois »).
 */
data class ProductCardUi(
    val productId: String,
    val name: String,
    val needPhrase: String,
    val categoryName: String?,
    val kind: ProductCardKind,
    val presets: List<QuantityChoice>,
    val quantity: QuantityStepper,
    val units: List<UnitOptionUi>,
    val isSameAsLastTime: Boolean,
    val reason: ReasonUi?,
    val imageKey: String,
)

/** The nuanced answers of a card (« Plus d'options »). */
enum class NuancedAnswer {
    /** « Il en reste un peu »: asked first next time. */
    LITTLE_LEFT,

    /** « J'en ai encore pour ~1 semaine ». */
    ENOUGH_FOR_A_WEEK,

    /** « J'en ai encore pour ~1 mois ». */
    ENOUGH_FOR_A_MONTH,

    /** « Plus tard »: asked again at the end of the session. */
    LATER,

    /** « Pas cette fois ». */
    NOT_THIS_TIME,

    /** « Je n'en achète plus ». */
    NEVER,
}

/**
 * @property question the catalog's gate question, or null (fallback: « Besoin de quelque chose en {name} ? »).
 * @property imageKey the aisle's illustration.
 */
data class CategoryGateUi(
    val categoryId: String,
    val name: String,
    val question: String?,
    val productCount: Int,
    val imageKey: String,
)

enum class ItemOrigin {
    /** The T1 screen. */
    SUGGESTED,
    CARD,
    GATE,

    /** « Autre chose ? » during the review. */
    OTHER,
}

data class ReviewItemUi(
    val productId: String,
    val name: String,
    val quantity: QuantityChoice,
    val origin: ItemOrigin,
    val imageKey: String,
)

/** A search result of « Autre chose ? », added with its default [quantity] by [QuestionEvent.AddOther]. */
data class ProductSearchResultUi(
    val productId: String,
    val name: String,
    val quantity: QuantityChoice,
    val imageKey: String,
)

/** Short feedback for the platform haptics. */
enum class QuestionHaptic { ITEM_ADDED, ANSWERED, UNDONE }

sealed interface QuestionEvent {
    /** Answers the current meta question (any value in `min..max`). */
    data class AnswerMeta(val value: Int) : QuestionEvent

    /** Checks or unchecks a line of the T1 screen or of a gate's products. */
    data class ToggleItem(val productId: String) : QuestionEvent

    /** Moves the quantity of a line by one step. */
    data class StepItem(val productId: String, val increment: Boolean) : QuestionEvent

    /** Validates the T1 screen or the gate's products: checked lines are added with their quantities. */
    data object ConfirmItems : QuestionEvent

    /** Yes on a card: [quantity] (a preset), or the stepper's selection when null. */
    data class Yes(val quantity: QuantityValue? = null) : QuestionEvent

    data object No : QuestionEvent

    data class Nuanced(val answer: NuancedAnswer) : QuestionEvent

    /** Moves the card's stepper by one step. */
    data class StepQuantity(val increment: Boolean) : QuestionEvent

    /** Switches the card to another unit of the product ([UnitOptionUi.unit]). */
    data class SelectUnit(val unit: String) : QuestionEvent

    data class AnswerGate(val yes: Boolean) : QuestionEvent

    /** « Autre chose ? »: searches the catalog (review only). */
    data class Search(val query: String) : QuestionEvent

    /** Adds a search result to the drafts (review only). */
    data class AddOther(val productId: String) : QuestionEvent

    /** Skips the remaining questions and goes to the review. */
    data object EndQuestions : QuestionEvent

    /** Writes the list (review only). */
    data object Confirm : QuestionEvent

    /** Back to the previous question; the answers it gave are compensated. */
    data object Undo : QuestionEvent

    /** Ends the session without adding anything; the answers given are kept. */
    data object Abandon : QuestionEvent

    /** After a retryable [QuestionUiState.Error]. */
    data object Retry : QuestionEvent

    /** Abandons the current session (if any) and starts a new one. */
    data object StartOver : QuestionEvent
}
