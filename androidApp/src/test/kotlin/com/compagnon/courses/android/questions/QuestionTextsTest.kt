package com.compagnon.courses.android.questions

import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.UiText
import com.compagnon.courses.feature.questionflow.CategoryGateUi
import com.compagnon.courses.feature.questionflow.ItemOrigin
import com.compagnon.courses.feature.questionflow.MetaQuestionKind
import com.compagnon.courses.feature.questionflow.NuancedAnswer
import com.compagnon.courses.feature.questionflow.ProductCardKind
import com.compagnon.courses.feature.questionflow.ProductCardUi
import com.compagnon.courses.feature.questionflow.QuantityChoice
import com.compagnon.courses.feature.questionflow.QuantityStepper
import com.compagnon.courses.feature.questionflow.QuantityValue
import com.compagnon.courses.feature.questionflow.QuestionErrorKind
import com.compagnon.courses.feature.questionflow.ReasonKind
import com.compagnon.courses.feature.questionflow.ReasonUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTextsTest {

    @Test
    fun aDueReasonIsOneLineWithTheCycleAndTheLastPurchaseOnTheCard() {
        val due = ReasonUi(kind = ReasonKind.DUE, daysSinceLast = 9, intervalDays = 8, usualQuantity = "2 L")

        // « Tous les 8 jours environ · dernier il y a 9 jours » (mockup `Card`).
        assertEquals(
            UiText.Res(
                R.string.reason_due_both,
                listOf(UiText.Plural(R.plurals.reason_due_every, 8), UiText.Plural(R.plurals.reason_due_last_short, 9)),
            ),
            QuestionTexts.reason(due),
        )
        // A suggestion row keeps the last purchase: « Dernier achat il y a 9 jours » (mockup `Suggested`).
        assertEquals(UiText.Plural(R.plurals.reason_due_last, 9), QuestionTexts.reason(due, compact = true))
        // Only the cycle known: « Acheté tous les 8 jours environ ».
        assertEquals(
            UiText.Plural(R.plurals.reason_due_interval, 8),
            QuestionTexts.reason(due.copy(daysSinceLast = null)),
        )
    }

    @Test
    fun dueReasonWithoutHistoryFallsBackToAGenericLine() {
        assertEquals(UiText.Res(R.string.reason_due), QuestionTexts.reason(ReasonUi(ReasonKind.DUE)))
    }

    @Test
    fun radarAndPriorUseTheirCountsWhenKnown() {
        assertEquals(
            UiText.Plural(R.plurals.reason_radar_weeks, 7),
            QuestionTexts.reason(ReasonUi(ReasonKind.RADAR, weeksSinceLast = 7)),
        )
        assertEquals(UiText.Res(R.string.reason_radar), QuestionTexts.reason(ReasonUi(ReasonKind.RADAR)))
        assertEquals(
            UiText.Plural(R.plurals.reason_prior_household, 4),
            QuestionTexts.reason(ReasonUi(ReasonKind.PRIOR, householdSize = 4)),
        )
        assertEquals(UiText.Res(R.string.reason_prior), QuestionTexts.reason(ReasonUi(ReasonKind.PRIOR)))
    }

    @Test
    fun everyReasonKindHasItsOwnText() {
        // A declared rhythm always comes with its interval (the shared mapper sets it).
        val texts = ReasonKind.entries.map { QuestionTexts.reason(ReasonUi(it, intervalDays = if (it == ReasonKind.CADENCE) 7 else null)) }

        assertEquals(ReasonKind.entries.size, texts.toSet().size)
    }

    @Test
    fun gateUsesTheCatalogQuestionElseTheFallback() {
        val gate = CategoryGateUi(categoryId = "entretien", name = "Entretien", question = null, productCount = 12, imageKey = "broom")

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
    fun theCardChipFollowsTheKindAndStaplesAreEssential() {
        val twoBottles = QuantityChoice(QuantityValue(2_000, "bottle"), "2 bouteilles")
        val card = ProductCardUi(
            productId = "lait",
            name = "Lait",
            needPhrase = "du lait",
            categoryName = "Crèmerie",
            kind = ProductCardKind.STANDARD,
            presets = listOf(twoBottles),
            quantity = QuantityStepper(twoBottles, canDecrement = true, canIncrement = true),
            units = emptyList(),
            isSameAsLastTime = false,
            reason = ReasonUi(ReasonKind.DUE),
            imageKey = "glass_of_milk",
        )

        assertNull(QuestionTexts.cardBadge(card))
        assertEquals(
            CardBadge(R.string.card_badge_staple, watch = false),
            QuestionTexts.cardBadge(card.copy(reason = ReasonUi(ReasonKind.STAPLE))),
        )
        assertEquals(CardBadge(R.string.card_badge_watch, watch = true), QuestionTexts.cardBadge(card.copy(kind = ProductCardKind.WATCH)))
        assertEquals(CardBadge(R.string.card_badge_radar, watch = false), QuestionTexts.cardBadge(card.copy(kind = ProductCardKind.RADAR)))
        assertEquals(
            CardBadge(R.string.card_badge_exploration, watch = false),
            QuestionTexts.cardBadge(card.copy(kind = ProductCardKind.EXPLORATION)),
        )
    }

    @Test
    fun aLargeMetaChoiceShowsTheNumberAboveItsUnit() {
        assertEquals(
            MetaChoiceTexts(UiText.Raw("7"), UiText.Plural(R.plurals.meta_days_unit, 7, args = emptyList())),
            QuestionTexts.metaChoice(MetaQuestionKind.HORIZON_DAYS, 7),
        )
        assertEquals(
            MetaChoiceTexts(UiText.Raw("2"), UiText.Plural(R.plurals.meta_persons_unit, 2, args = emptyList())),
            QuestionTexts.metaChoice(MetaQuestionKind.PERSONS, 2),
        )
        // No guest is said in words, without a unit.
        assertEquals(MetaChoiceTexts(UiText.Res(R.string.meta_guests_none), unit = null), QuestionTexts.metaChoice(MetaQuestionKind.GUESTS, 0))
        assertEquals(R.string.meta_other_days, QuestionTexts.metaOther(MetaQuestionKind.HORIZON_DAYS))
        assertEquals(R.string.meta_other_count, QuestionTexts.metaOther(MetaQuestionKind.GUESTS))
        assertEquals(
            MetaQuestionKind.entries.size,
            MetaQuestionKind.entries.map(QuestionTexts::metaSubtitle).toSet().size,
        )
    }

    @Test
    fun choicesAreLaidOutInTheSameRowsAsOnIos() {
        // Meta: up to four on one row, else rows of three (four for seven or eight).
        assertEquals(listOf(1, 3, 4, 3, 3, 4, 4, 3), listOf(1, 3, 4, 5, 6, 7, 8, 9).map(::metaColumns))
        // Card presets: three on one row, four on two rows.
        assertEquals(listOf(1, 2, 3, 2, 3), listOf(1, 2, 3, 4, 5).map(::presetColumns))
    }

    @Test
    fun theCardImageShrinksThenGoesWithALargerFont() {
        assertEquals(CardBannerSize(height = 100.dp, image = 84.dp), cardBannerSize(1f))
        assertEquals(CardBannerSize(height = 100.dp, image = 84.dp), cardBannerSize(1.15f))
        assertEquals(CardBannerSize(height = 72.dp, image = 56.dp), cardBannerSize(1.3f))
        assertNull(cardBannerSize(1.6f))
        assertNull(cardBannerSize(2f))
    }

    @Test
    fun theOptionsSheetOffersEveryNuancedAnswerOnceWithDistinctTexts() {
        assertEquals(NuancedAnswer.entries.toSet(), QuestionTexts.nuancedAnswers.toSet())
        assertEquals(NuancedAnswer.entries.size, QuestionTexts.nuancedAnswers.size)
        assertEquals(NuancedAnswer.entries.size, NuancedAnswer.entries.map(QuestionTexts::nuancedLabel).toSet().size)
        assertEquals(NuancedAnswer.entries.size, NuancedAnswer.entries.map(QuestionTexts::nuancedHint).toSet().size)
        // Two groups: « Il m'en reste » then « Pas maintenant ».
        assertEquals(QuestionTexts.stockAnswers + QuestionTexts.notNowAnswers, QuestionTexts.nuancedAnswers)
        assertTrue(QuestionTexts.stockAnswers.intersect(QuestionTexts.notNowAnswers.toSet()).isEmpty())
    }

    @Test
    fun originsAndErrorsHaveDistinctTexts() {
        assertEquals(ItemOrigin.entries.size, ItemOrigin.entries.map(QuestionTexts::origin).toSet().size)
        assertEquals(
            QuestionErrorKind.entries.size,
            QuestionErrorKind.entries.map(QuestionTexts::errorMessage).toSet().size,
        )
        assertEquals(
            QuestionErrorKind.entries.size,
            QuestionErrorKind.entries.map(QuestionTexts::errorTitle).toSet().size,
        )
    }

    @Test
    fun aDeclaredRhythmSaysHowOftenItIsBought() {
        // « Vous en achetez chaque semaine » for the three rhythms of « Mon foyer », a count for any other.
        assertEquals(UiText.Res(R.string.reason_cadence_week), QuestionTexts.reason(ReasonUi(ReasonKind.CADENCE, intervalDays = 7)))
        assertEquals(
            UiText.Res(R.string.reason_cadence_two_weeks),
            QuestionTexts.reason(ReasonUi(ReasonKind.CADENCE, intervalDays = 14)),
        )
        assertEquals(UiText.Res(R.string.reason_cadence_month), QuestionTexts.reason(ReasonUi(ReasonKind.CADENCE, intervalDays = 30)))
        assertEquals(
            UiText.Plural(R.plurals.reason_cadence_days, 10),
            QuestionTexts.reason(ReasonUi(ReasonKind.CADENCE, intervalDays = 10), compact = true),
        )
        assertEquals(UiText.Res(R.string.reason_due), QuestionTexts.reason(ReasonUi(ReasonKind.CADENCE)))
    }
}
