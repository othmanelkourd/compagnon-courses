package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId

/** A meta question with its pre-filled value (answerable in one tap when nothing changed). */
data class MetaQuestion(val key: MetaKey, val prefilled: Int)

/** A product as presented to the household, with its score, tier, proposed quantity and reasons. */
data class ProposedItem(
    val product: Product,
    val score: Double,
    val tier: Tier,
    val proposal: QuantityProposal,
    val reasons: List<ReasonCode>,
    val preChecked: Boolean,
) {
    val productId: ProductId get() = product.id
}

enum class CardKind {
    /** A T2 product. */
    STANDARD,

    /** « Il en reste un peu » last time: always a card, asked first within its category. */
    WATCH,

    /** Reserved slot of the « radar des oubliés » (long-cycle products). */
    RADAR,

    /** Reserved exploration slot (seeded). */
    EXPLORATION,
}

/** A question after the T1 screen. Each one costs one unit of budget. */
sealed interface PlannedQuestion {
    /** One card for one product: « Oui · 2 L », « Non », nuanced answers. */
    data class Card(val item: ProposedItem, val kind: CardKind) : PlannedQuestion

    /** « Besoin de quelque chose en Entretien ? » Yes shows [items]; no skips the whole category. */
    data class Gate(val category: Category, val items: List<ProposedItem>) : PlannedQuestion
}

/**
 * The questions of a session, in presentation order: [meta], then the T1 screen ([suggested], if not empty),
 * then [questions].
 *
 * @property budget Q_max, or null when unlimited.
 * @property deferred products not asked (T4, or dropped by the budget), most likely first: they feed search
 *   and « Autre chose ? ».
 */
data class QuestionPlan(
    val meta: List<MetaQuestion>,
    val suggested: List<ProposedItem>,
    val questions: List<PlannedQuestion>,
    val deferred: List<ProductId>,
    val budget: Int?,
) {
    /** Budget units used: one per meta question, one for the T1 screen, one per card or gate. */
    val cost: Int get() = meta.size + (if (suggested.isEmpty()) 0 else 1) + questions.size

    /** Every product the household may be asked about, wherever it appears. */
    val askedProducts: List<ProposedItem>
        get() = suggested + questions.flatMap { question ->
            when (question) {
                is PlannedQuestion.Card -> listOf(question.item)
                is PlannedQuestion.Gate -> question.items
            }
        }
}
