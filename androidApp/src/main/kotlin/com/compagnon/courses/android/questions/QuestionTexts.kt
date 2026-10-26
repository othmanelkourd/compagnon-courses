package com.compagnon.courses.android.questions

import androidx.annotation.StringRes
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.UiText
import com.compagnon.courses.feature.questionflow.CategoryGateUi
import com.compagnon.courses.feature.questionflow.ItemOrigin
import com.compagnon.courses.feature.questionflow.MetaQuestionKind
import com.compagnon.courses.feature.questionflow.NuancedAnswer
import com.compagnon.courses.feature.questionflow.ProductCardKind
import com.compagnon.courses.feature.questionflow.ProductCardUi
import com.compagnon.courses.feature.questionflow.QuestionErrorKind
import com.compagnon.courses.feature.questionflow.ReasonKind
import com.compagnon.courses.feature.questionflow.ReasonUi

/**
 * The fixed copy of the questionnaire, chosen from the shared UI enums. Pure functions: the wording rules are
 * unit tested without Android resources.
 */
internal object QuestionTexts {

    /**
     * « Pourquoi cette question ? »: one line without a final period (`design/TEXTES.md`). A purchase cycle reads
     * « Tous les 8 jours environ · dernier il y a 9 jours » on a card; a [compact] line (suggestion rows) keeps only
     * « Dernier achat il y a 9 jours », or « Acheté tous les 8 jours environ » when the last purchase is unknown.
     */
    fun reason(reason: ReasonUi, compact: Boolean = false): UiText = when (reason.kind) {
        ReasonKind.DUE -> {
            val days = reason.daysSinceLast
            val interval = reason.intervalDays
            when {
                days != null && interval != null && !compact -> UiText.Res(
                    R.string.reason_due_both,
                    listOf(UiText.Plural(R.plurals.reason_due_every, interval), UiText.Plural(R.plurals.reason_due_last_short, days)),
                )
                days != null -> UiText.Plural(R.plurals.reason_due_last, days)
                interval != null -> UiText.Plural(R.plurals.reason_due_interval, interval)
                else -> UiText.Res(R.string.reason_due)
            }
        }
        ReasonKind.STAPLE -> UiText.Res(R.string.reason_staple)
        ReasonKind.SEASONAL -> UiText.Res(R.string.reason_seasonal)
        ReasonKind.EVENT -> UiText.Res(R.string.reason_event)
        ReasonKind.RADAR ->
            reason.weeksSinceLast?.let { UiText.Plural(R.plurals.reason_radar_weeks, it) } ?: UiText.Res(R.string.reason_radar)
        ReasonKind.PRIOR ->
            reason.householdSize?.let { UiText.Plural(R.plurals.reason_prior_household, it) } ?: UiText.Res(R.string.reason_prior)
        ReasonKind.EXPLORATION -> UiText.Res(R.string.reason_exploration)
        ReasonKind.LITTLE_LEFT -> UiText.Res(R.string.reason_little_left)
        ReasonKind.LAST_LIST -> UiText.Res(R.string.reason_last_list)
    }

    /** The question of a gate: the catalog's own wording, else « Besoin de quelque chose en {name} ? ». */
    fun gateQuestion(gate: CategoryGateUi): UiText =
        gate.question?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
            ?: UiText.Res(R.string.gate_question_fallback, listOf(gate.name))

    @StringRes
    fun metaTitle(kind: MetaQuestionKind): Int = when (kind) {
        MetaQuestionKind.HORIZON_DAYS -> R.string.meta_horizon_title
        MetaQuestionKind.PERSONS -> R.string.meta_persons_title
        MetaQuestionKind.GUESTS -> R.string.meta_guests_title
    }

    /** « 7 jours », « 2 personnes », « Aucun » / « 3 invités ». */
    fun metaValue(kind: MetaQuestionKind, value: Int): UiText = when (kind) {
        MetaQuestionKind.HORIZON_DAYS -> UiText.Plural(R.plurals.meta_days, value)
        MetaQuestionKind.PERSONS -> UiText.Plural(R.plurals.meta_persons, value)
        MetaQuestionKind.GUESTS ->
            if (value == 0) UiText.Res(R.string.meta_guests_none) else UiText.Plural(R.plurals.meta_guests, value)
    }

    /**
     * The chip of a card: « À surveiller » ([ProductCardKind.WATCH], tomato), « Radar », « À découvrir », and
     * « Essentiel » for a standard card asked because it is a staple. Null for other standard cards.
     */
    fun cardBadge(card: ProductCardUi): CardBadge? = when (card.kind) {
        ProductCardKind.STANDARD ->
            if (card.reason?.kind == ReasonKind.STAPLE) CardBadge(R.string.card_badge_staple, watch = false) else null
        ProductCardKind.WATCH -> CardBadge(R.string.card_badge_watch, watch = true)
        ProductCardKind.RADAR -> CardBadge(R.string.card_badge_radar, watch = false)
        ProductCardKind.EXPLORATION -> CardBadge(R.string.card_badge_exploration, watch = false)
    }

    @StringRes
    fun metaSubtitle(kind: MetaQuestionKind): Int = when (kind) {
        MetaQuestionKind.HORIZON_DAYS -> R.string.meta_horizon_subtitle
        MetaQuestionKind.PERSONS -> R.string.meta_persons_subtitle
        MetaQuestionKind.GUESTS -> R.string.meta_guests_subtitle
    }

    /** « Autre durée : 7 jours » / « Autre nombre : 3 personnes ». */
    @StringRes
    fun metaOther(kind: MetaQuestionKind): Int = when (kind) {
        MetaQuestionKind.HORIZON_DAYS -> R.string.meta_other_days
        MetaQuestionKind.PERSONS, MetaQuestionKind.GUESTS -> R.string.meta_other_count
    }

    /**
     * A large choice: the number (« 7 ») and its unit below (« jours »); no guest is said in words (« Aucun »),
     * without a unit.
     */
    fun metaChoice(kind: MetaQuestionKind, value: Int): MetaChoiceTexts = when {
        kind == MetaQuestionKind.GUESTS && value == 0 -> MetaChoiceTexts(UiText.Res(R.string.meta_guests_none), unit = null)
        else -> MetaChoiceTexts(
            UiText.Raw(value.toString()),
            UiText.Plural(
                when (kind) {
                    MetaQuestionKind.HORIZON_DAYS -> R.plurals.meta_days_unit
                    MetaQuestionKind.PERSONS -> R.plurals.meta_persons_unit
                    MetaQuestionKind.GUESTS -> R.plurals.meta_guests_unit
                },
                value,
                args = emptyList(),
            ),
        )
    }

    /** « Il m'en reste »: the answers that say how much is left. */
    val stockAnswers: List<NuancedAnswer> = listOf(
        NuancedAnswer.LITTLE_LEFT,
        NuancedAnswer.ENOUGH_FOR_A_WEEK,
        NuancedAnswer.ENOUGH_FOR_A_MONTH,
    )

    /** « Pas maintenant »: the answers that put the product off. */
    val notNowAnswers: List<NuancedAnswer> = listOf(
        NuancedAnswer.LATER,
        NuancedAnswer.NOT_THIS_TIME,
        NuancedAnswer.NEVER,
    )

    /** The nuanced answers of « Plus d'options », in display order (the two groups). */
    val nuancedAnswers: List<NuancedAnswer> = stockAnswers + notNowAnswers

    @StringRes
    fun nuancedLabel(answer: NuancedAnswer): Int = when (answer) {
        NuancedAnswer.LITTLE_LEFT -> R.string.nuanced_little_left
        NuancedAnswer.ENOUGH_FOR_A_WEEK -> R.string.nuanced_enough_week
        NuancedAnswer.ENOUGH_FOR_A_MONTH -> R.string.nuanced_enough_month
        NuancedAnswer.LATER -> R.string.nuanced_later
        NuancedAnswer.NOT_THIS_TIME -> R.string.nuanced_not_this_time
        NuancedAnswer.NEVER -> R.string.nuanced_never
    }

    @StringRes
    fun nuancedHint(answer: NuancedAnswer): Int = when (answer) {
        NuancedAnswer.LITTLE_LEFT -> R.string.nuanced_little_left_hint
        NuancedAnswer.ENOUGH_FOR_A_WEEK -> R.string.nuanced_enough_week_hint
        NuancedAnswer.ENOUGH_FOR_A_MONTH -> R.string.nuanced_enough_month_hint
        NuancedAnswer.LATER -> R.string.nuanced_later_hint
        NuancedAnswer.NOT_THIS_TIME -> R.string.nuanced_not_this_time_hint
        NuancedAnswer.NEVER -> R.string.nuanced_never_hint
    }

    @StringRes
    fun origin(origin: ItemOrigin): Int = when (origin) {
        ItemOrigin.SUGGESTED -> R.string.origin_suggested
        ItemOrigin.CARD -> R.string.origin_card
        ItemOrigin.GATE -> R.string.origin_gate
        ItemOrigin.OTHER -> R.string.origin_other
    }

    @StringRes
    fun errorTitle(kind: QuestionErrorKind): Int = when (kind) {
        QuestionErrorKind.NO_CATALOG -> R.string.question_error_no_catalog_title
        QuestionErrorKind.PLANNING_FAILED -> R.string.question_error_planning_title
        QuestionErrorKind.STORAGE_FAILED -> R.string.question_error_storage_title
    }

    @StringRes
    fun errorMessage(kind: QuestionErrorKind): Int = when (kind) {
        QuestionErrorKind.NO_CATALOG -> R.string.question_error_no_catalog
        QuestionErrorKind.PLANNING_FAILED -> R.string.question_error_planning
        QuestionErrorKind.STORAGE_FAILED -> R.string.question_error_storage
    }
}

/** The chip of a product card: [text], in the « À surveiller » tone when [watch], else « Essentiel »'s. */
internal data class CardBadge(@param:StringRes val text: Int, val watch: Boolean)

/** A large meta choice: [value] (« 7 », « Aucun ») and its [unit] (« jours »), if any. */
internal data class MetaChoiceTexts(val value: UiText, val unit: UiText?)
