package com.compagnon.courses.android.questions

import androidx.annotation.StringRes
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.UiText
import com.compagnon.courses.feature.questionflow.CategoryGateUi
import com.compagnon.courses.feature.questionflow.ItemOrigin
import com.compagnon.courses.feature.questionflow.MetaQuestionKind
import com.compagnon.courses.feature.questionflow.NuancedAnswer
import com.compagnon.courses.feature.questionflow.ProductCardKind
import com.compagnon.courses.feature.questionflow.QuestionErrorKind
import com.compagnon.courses.feature.questionflow.ReasonKind
import com.compagnon.courses.feature.questionflow.ReasonUi

/**
 * The fixed copy of the questionnaire, chosen from the shared UI enums. Pure functions: the wording rules are
 * unit tested without Android resources.
 */
internal object QuestionTexts {

    /** « Pourquoi cette question ? »: one or more short sentences. */
    fun reason(reason: ReasonUi): List<UiText> = when (reason.kind) {
        ReasonKind.DUE -> buildList {
            reason.daysSinceLast?.let { add(UiText.Plural(R.plurals.reason_due_last, it)) }
            reason.intervalDays?.let { add(UiText.Plural(R.plurals.reason_due_interval, it)) }
            reason.usualQuantity?.let { add(UiText.Res(R.string.reason_due_usual, listOf(it))) }
            if (isEmpty()) add(UiText.Res(R.string.reason_due))
        }
        ReasonKind.STAPLE -> listOf(UiText.Res(R.string.reason_staple))
        ReasonKind.SEASONAL -> listOf(UiText.Res(R.string.reason_seasonal))
        ReasonKind.EVENT -> listOf(UiText.Res(R.string.reason_event))
        ReasonKind.RADAR -> listOf(
            reason.weeksSinceLast?.let { UiText.Plural(R.plurals.reason_radar_weeks, it) }
                ?: UiText.Res(R.string.reason_radar),
        )
        ReasonKind.PRIOR -> listOf(
            reason.householdSize?.let { UiText.Plural(R.plurals.reason_prior_household, it) }
                ?: UiText.Res(R.string.reason_prior),
        )
        ReasonKind.EXPLORATION -> listOf(UiText.Res(R.string.reason_exploration))
        ReasonKind.LITTLE_LEFT -> listOf(UiText.Res(R.string.reason_little_left))
        ReasonKind.LAST_LIST -> listOf(UiText.Res(R.string.reason_last_list))
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

    /** The badge above the question, or null for a standard card. */
    @StringRes
    fun cardBadge(kind: ProductCardKind): Int? = when (kind) {
        ProductCardKind.STANDARD -> null
        ProductCardKind.WATCH -> R.string.card_badge_watch
        ProductCardKind.RADAR -> R.string.card_badge_radar
        ProductCardKind.EXPLORATION -> R.string.card_badge_exploration
    }

    /** The nuanced answers of « Plus d'options », in display order. */
    val nuancedAnswers: List<NuancedAnswer> = listOf(
        NuancedAnswer.LITTLE_LEFT,
        NuancedAnswer.ENOUGH_FOR_A_WEEK,
        NuancedAnswer.ENOUGH_FOR_A_MONTH,
        NuancedAnswer.LATER,
        NuancedAnswer.NOT_THIS_TIME,
        NuancedAnswer.NEVER,
    )

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
    fun errorMessage(kind: QuestionErrorKind): Int = when (kind) {
        QuestionErrorKind.NO_CATALOG -> R.string.question_error_no_catalog
        QuestionErrorKind.PLANNING_FAILED -> R.string.question_error_planning
        QuestionErrorKind.STORAGE_FAILED -> R.string.question_error_storage
    }
}
