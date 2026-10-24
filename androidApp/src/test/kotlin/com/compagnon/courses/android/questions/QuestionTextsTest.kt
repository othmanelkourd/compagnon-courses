package com.compagnon.courses.android.questions

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTextsTest {

    @Test
    fun dueReasonSaysWhenAndHowOftenAndHowMuch() {
        val texts = QuestionTexts.reason(
            ReasonUi(kind = ReasonKind.DUE, daysSinceLast = 9, intervalDays = 8, usualQuantity = "2 L"),
        )

        assertEquals(
            listOf(
                UiText.Plural(R.plurals.reason_due_last, 9),
                UiText.Plural(R.plurals.reason_due_interval, 8),
                UiText.Res(R.string.reason_due_usual, listOf("2 L")),
            ),
            texts,
        )
    }

    @Test
    fun dueReasonWithoutHistoryFallsBackToAGenericSentence() {
        assertEquals(listOf(UiText.Res(R.string.reason_due)), QuestionTexts.reason(ReasonUi(ReasonKind.DUE)))
    }

    @Test
    fun radarAndPriorUseTheirCountsWhenKnown() {
        assertEquals(
            listOf(UiText.Plural(R.plurals.reason_radar_weeks, 7)),
            QuestionTexts.reason(ReasonUi(ReasonKind.RADAR, weeksSinceLast = 7)),
        )
        assertEquals(listOf(UiText.Res(R.string.reason_radar)), QuestionTexts.reason(ReasonUi(ReasonKind.RADAR)))
        assertEquals(
            listOf(UiText.Plural(R.plurals.reason_prior_household, 4)),
            QuestionTexts.reason(ReasonUi(ReasonKind.PRIOR, householdSize = 4)),
        )
        assertEquals(listOf(UiText.Res(R.string.reason_prior)), QuestionTexts.reason(ReasonUi(ReasonKind.PRIOR)))
    }

    @Test
    fun everyReasonKindHasAText() {
        ReasonKind.entries.forEach { kind ->
            assertTrue(kind.name, QuestionTexts.reason(ReasonUi(kind)).isNotEmpty())
        }
    }

    @Test
    fun gateUsesTheCatalogQuestionElseTheFallback() {
        val gate = CategoryGateUi(categoryId = "entretien", name = "Entretien", question = null, productCount = 12)

        assertEquals(UiText.Res(R.string.gate_question_fallback, listOf("Entretien")), QuestionTexts.gateQuestion(gate))
        assertEquals(
            UiText.Res(R.string.gate_question_fallback, listOf("Entretien")),
            QuestionTexts.gateQuestion(gate.copy(question = "  ")),
        )
        assertEquals(
            UiText.Raw("Besoin de produits ménagers ?"),
            QuestionTexts.gateQuestion(gate.copy(question = "Besoin de produits ménagers ?")),
        )
    }

    @Test
    fun noGuestIsSaidInWords() {
        assertEquals(UiText.Res(R.string.meta_guests_none), QuestionTexts.metaValue(MetaQuestionKind.GUESTS, 0))
        assertEquals(UiText.Plural(R.plurals.meta_guests, 3), QuestionTexts.metaValue(MetaQuestionKind.GUESTS, 3))
        assertEquals(UiText.Plural(R.plurals.meta_days, 7), QuestionTexts.metaValue(MetaQuestionKind.HORIZON_DAYS, 7))
        assertEquals(UiText.Plural(R.plurals.meta_persons, 2), QuestionTexts.metaValue(MetaQuestionKind.PERSONS, 2))
    }

    @Test
    fun onlyNonStandardCardsHaveABadge() {
        assertNull(QuestionTexts.cardBadge(ProductCardKind.STANDARD))
        assertEquals(R.string.card_badge_watch, QuestionTexts.cardBadge(ProductCardKind.WATCH))
        assertEquals(R.string.card_badge_radar, QuestionTexts.cardBadge(ProductCardKind.RADAR))
        assertEquals(R.string.card_badge_exploration, QuestionTexts.cardBadge(ProductCardKind.EXPLORATION))
    }

    @Test
    fun theOptionsSheetOffersEveryNuancedAnswerOnceWithDistinctTexts() {
        assertEquals(NuancedAnswer.entries.toSet(), QuestionTexts.nuancedAnswers.toSet())
        assertEquals(NuancedAnswer.entries.size, QuestionTexts.nuancedAnswers.size)
        assertEquals(NuancedAnswer.entries.size, NuancedAnswer.entries.map(QuestionTexts::nuancedLabel).toSet().size)
        assertEquals(NuancedAnswer.entries.size, NuancedAnswer.entries.map(QuestionTexts::nuancedHint).toSet().size)
    }

    @Test
    fun originsAndErrorsHaveDistinctTexts() {
        assertEquals(ItemOrigin.entries.size, ItemOrigin.entries.map(QuestionTexts::origin).toSet().size)
        assertEquals(
            QuestionErrorKind.entries.size,
            QuestionErrorKind.entries.map(QuestionTexts::errorMessage).toSet().size,
        )
    }
}
