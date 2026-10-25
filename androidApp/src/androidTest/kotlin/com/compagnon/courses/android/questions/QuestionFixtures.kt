package com.compagnon.courses.android.questions

import com.compagnon.courses.feature.questionflow.CategoryGateUi
import com.compagnon.courses.feature.questionflow.ItemOrigin
import com.compagnon.courses.feature.questionflow.ProductCardKind
import com.compagnon.courses.feature.questionflow.ProductCardUi
import com.compagnon.courses.feature.questionflow.ProductSearchResultUi
import com.compagnon.courses.feature.questionflow.QuantityChoice
import com.compagnon.courses.feature.questionflow.QuantityStepper
import com.compagnon.courses.feature.questionflow.QuantityValue
import com.compagnon.courses.feature.questionflow.ReasonKind
import com.compagnon.courses.feature.questionflow.ReasonUi
import com.compagnon.courses.feature.questionflow.ReviewItemUi
import com.compagnon.courses.feature.questionflow.SuggestedItemUi
import com.compagnon.courses.feature.questionflow.UnitOptionUi

/** Fake UI states shaped like the real seed (French labels, as the shared layer formats them). */
internal object QuestionFixtures {

    fun bottles(count: Int) = QuantityChoice(QuantityValue(count * 1_000L, "bottle"), "$count bouteilles")

    val milkCard = ProductCardUi(
        productId = "lait-demi-ecreme",
        name = "Lait demi-écrémé",
        needPhrase = "du lait demi-écrémé",
        categoryName = "Crèmerie",
        kind = ProductCardKind.STANDARD,
        presets = listOf(bottles(2), bottles(4), bottles(6)),
        quantity = QuantityStepper(bottles(2), canDecrement = false, canIncrement = true),
        units = listOf(
            UnitOptionUi(unit = "bottle", label = "Bouteille", selected = true),
            UnitOptionUi(unit = "pack", label = "Pack", selected = false),
        ),
        isSameAsLastTime = true,
        reason = ReasonUi(kind = ReasonKind.DUE, daysSinceLast = 9, intervalDays = 8, usualQuantity = "2 bouteilles"),
    )

    val cleaningGate = CategoryGateUi(categoryId = "entretien", name = "Entretien", question = null, productCount = 12)

    val suggested = listOf(
        SuggestedItemUi(
            productId = "pain",
            name = "Baguette",
            checked = true,
            quantity = QuantityStepper(QuantityChoice(QuantityValue(2_000, "piece"), "2 baguettes"), true, true),
            reason = ReasonUi(ReasonKind.STAPLE),
        ),
        SuggestedItemUi(
            productId = "oeufs",
            name = "Œufs",
            checked = false,
            quantity = QuantityStepper(QuantityChoice(QuantityValue(6_000, "egg"), "6 œufs"), true, true),
            reason = null,
        ),
    )

    val reviewItems = listOf(
        ReviewItemUi("lait-demi-ecreme", "Lait demi-écrémé", bottles(2), ItemOrigin.CARD),
        ReviewItemUi("pain", "Baguette", QuantityChoice(QuantityValue(2_000, "piece"), "2 baguettes"), ItemOrigin.SUGGESTED),
    )

    val pastaResult = ProductSearchResultUi(
        productId = "pates",
        name = "Pâtes",
        quantity = QuantityChoice(QuantityValue(500, "kilogram"), "500 g"),
    )
}
