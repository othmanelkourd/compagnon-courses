package com.compagnon.courses.feature.questionflow

import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.LocalizedName
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.UnitPolicy
import com.compagnon.courses.core.model.format.QuantityFormatter
import com.compagnon.courses.core.model.format.capitalizedFirst
import com.compagnon.courses.core.model.illustration.Illustrations
import com.compagnon.courses.engine.CardKind
import com.compagnon.courses.engine.DomainError
import com.compagnon.courses.engine.DraftSource
import com.compagnon.courses.engine.MetaKey
import com.compagnon.courses.engine.PlannedQuestion
import com.compagnon.courses.engine.Progress
import com.compagnon.courses.engine.ProposedItem
import com.compagnon.courses.engine.QuantityAdvisor
import com.compagnon.courses.engine.ReasonCode
import com.compagnon.courses.engine.SessionContext
import com.compagnon.courses.engine.SessionData
import com.compagnon.courses.engine.SessionState

/**
 * What the user changed on the current screen without answering yet: the checked lines and quantities of a
 * list screen, the quantity of a card, the search of the review. Reset when the question changes.
 */
internal data class LocalSelection(
    /** Subject of the state this selection belongs to (see [SessionLog.subjectOf]). */
    val subject: String,
    val checked: Set<ProductId> = emptySet(),
    val edits: Map<ProductId, Quantity> = emptyMap(),
    val cardQuantity: Quantity? = null,
    val searchQuery: String = "",
    val searchResults: List<Product> = emptyList(),
)

/** Maps engine states to [QuestionUiState]; labels use the catalog [locale]. */
internal class QuestionUiMapper(private val session: SessionData, locale: String) {
    private val input = session.input
    private val formatter = QuantityFormatter(input.units, locale)
    private val categories: Map<CategoryId, Category> = input.categories.associateBy { it.id }

    /** Whether this mapper was built for the planning input of [other] (it changes after the meta questions). */
    fun isFor(other: SessionData): Boolean = other.input === input

    /** The initial selection of [state]: pre-checked lines, the proposed quantity of a card. */
    fun initialSelection(state: SessionState): LocalSelection {
        val subject = SessionLog.subjectOf(state)
        return when (state) {
            is SessionState.Suggested -> LocalSelection(subject, checked = state.items.filter { it.preChecked }.mapTo(HashSet()) { it.productId })
            is SessionState.GateItems -> LocalSelection(subject)
            is SessionState.Asking -> LocalSelection(subject, cardQuantity = state.card.item.proposal.quantity)
            else -> LocalSelection(subject)
        }
    }

    fun map(state: SessionState.Interactive, selection: LocalSelection): QuestionUiState {
        val progress = state.progress.toUi()
        val canUndo = state.session.canUndo
        return when (state) {
            is SessionState.Meta -> QuestionUiState.Meta(meta(state), progress, canUndo)
            is SessionState.Suggested -> {
                val items = state.items.map { suggestedItem(it, selection) }
                QuestionUiState.Suggested(items, items.count { it.checked }, progress, canUndo)
            }
            is SessionState.Asking -> QuestionUiState.Card(
                card = card(state, selection),
                progress = progress,
                canUndo = canUndo,
                nextImageKey = when (val next = state.session.remainingQuestions.firstOrNull()) {
                    is PlannedQuestion.Card -> imageOf(next.item.product)
                    is PlannedQuestion.Gate -> Illustrations.forCategory(next.category.slug)
                    null -> null
                },
            )
            is SessionState.CategoryGate -> QuestionUiState.Gate(gate(state.gate.category, state.gate.items.size), progress, canUndo)
            is SessionState.GateItems -> {
                val items = state.gate.items.map { suggestedItem(it, selection) }
                QuestionUiState.GateItems(
                    gate = gate(state.gate.category, state.gate.items.size),
                    items = items,
                    checkedCount = items.count { it.checked },
                    progress = progress,
                    canUndo = canUndo,
                )
            }
            is SessionState.Review -> QuestionUiState.Review(
                items = state.drafts.map { draft ->
                    ReviewItemUi(
                        productId = draft.productId.value,
                        name = draft.product.displayName,
                        quantity = choice(draft.quantity, draft.product),
                        origin = draft.source.toUi(),
                        imageKey = imageOf(draft.product),
                    )
                },
                searchQuery = selection.searchQuery,
                searchResults = selection.searchResults.mapNotNull { product ->
                    defaultQuantity(product)?.let { ProductSearchResultUi(product.id.value, product.displayName, choice(it, product), imageOf(product)) }
                },
                progress = progress,
                canUndo = canUndo,
            )
        }
    }

    /** The illustration of [product], or its aisle's. */
    fun imageOf(product: Product): String = Illustrations.forProduct(product.slug, categories[product.categoryId]?.slug)

    /** The quantity « Autre chose ? » adds for [product]: its usual or proposed quantity. */
    fun defaultQuantity(product: Product): Quantity? =
        QuantityAdvisor(input.units).propose(product, input.stats[product.id])?.quantity

    fun choice(quantity: Quantity, product: Product): QuantityChoice =
        QuantityChoice(QuantityValue(quantity.milli, quantity.unit.value), formatter.format(quantity, product))

    fun stepper(policy: UnitPolicy, quantity: Quantity, product: Product): QuantityStepper {
        val steps = quantity.milli / policy.step.milli
        val max = policy.maxSteps
        return QuantityStepper(
            selected = choice(quantity, product),
            canDecrement = steps > policy.minSteps,
            canIncrement = max == null || steps < max,
        )
    }

    private fun meta(state: SessionState.Meta): MetaQuestionUi {
        val key = state.question.key
        val value = state.question.prefilled
        val (min, max, choices) = when (key) {
            MetaKey.HORIZON_DAYS -> Triple(1, SessionContext.MAX_HORIZON_DAYS, listOf(3, 7, 14))
            MetaKey.PERSONS -> Triple(1, SessionContext.MAX_PERSONS, listOf(1, 2, 3, 4, 5, 6))
            MetaKey.GUESTS -> Triple(0, SessionContext.MAX_GUESTS, listOf(0, 2, 4, 6))
        }
        return MetaQuestionUi(
            kind = when (key) {
                MetaKey.HORIZON_DAYS -> MetaQuestionKind.HORIZON_DAYS
                MetaKey.PERSONS -> MetaQuestionKind.PERSONS
                MetaKey.GUESTS -> MetaQuestionKind.GUESTS
            },
            value = value,
            min = min,
            max = max,
            choices = (choices + value).distinct().sorted(),
        )
    }

    private fun suggestedItem(item: ProposedItem, selection: LocalSelection): SuggestedItemUi {
        val quantity = selection.edits[item.productId] ?: item.proposal.quantity
        val policy = item.product.policyFor(quantity.unit) ?: item.proposal.policy
        return SuggestedItemUi(
            productId = item.productId.value,
            name = item.product.displayName,
            checked = item.productId in selection.checked,
            quantity = stepper(policy, quantity, item.product),
            reason = item.reasons.firstOrNull()?.toUi(),
            imageKey = imageOf(item.product),
        )
    }

    private fun card(state: SessionState.Asking, selection: LocalSelection): ProductCardUi {
        val item = state.card.item
        val product = item.product
        val proposal = item.proposal
        val quantity = selection.cardQuantity ?: proposal.quantity
        val policy = product.policyFor(quantity.unit) ?: proposal.policy
        val presets = if (policy == proposal.policy) proposal.choices else policyPresets(policy)
        return ProductCardUi(
            productId = product.id.value,
            name = product.displayName,
            needPhrase = product.needPhrase ?: product.name.form(LocalizedName.PLURAL_OTHER),
            categoryName = categories[product.categoryId]?.name,
            kind = when (state.card.kind) {
                CardKind.STANDARD -> ProductCardKind.STANDARD
                CardKind.WATCH -> ProductCardKind.WATCH
                CardKind.RADAR -> ProductCardKind.RADAR
                CardKind.EXPLORATION -> ProductCardKind.EXPLORATION
            },
            presets = presets.map { choice(it, product) },
            quantity = stepper(policy, quantity, product),
            units = if (product.unitPolicies.size < 2) {
                emptyList()
            } else {
                product.unitPolicies.map { candidate ->
                    UnitOptionUi(
                        unit = candidate.unit.value,
                        label = formatter.buyingLabel(candidate.unit),
                        selected = candidate.unit == policy.unit,
                    )
                }
            },
            isSameAsLastTime = proposal.isSameAsLastTime && quantity == proposal.quantity,
            reason = item.reasons.firstOrNull()?.toUi(),
            imageKey = imageOf(product),
        )
    }

    private fun gate(category: Category, productCount: Int) = CategoryGateUi(
        categoryId = category.id.value,
        name = category.name,
        question = category.gateQuestion,
        productCount = productCount,
        imageKey = Illustrations.forCategory(category.slug),
    )

    private fun ReasonCode.toUi(): ReasonUi = when (this) {
        is ReasonCode.Due -> ReasonUi(
            kind = ReasonKind.DUE,
            daysSinceLast = daysSinceLast,
            intervalDays = intervalDays,
            usualQuantity = usualQuantity?.let { formatter.format(it) },
        )
        ReasonCode.Staple -> ReasonUi(ReasonKind.STAPLE)
        is ReasonCode.Cadence -> ReasonUi(ReasonKind.CADENCE, intervalDays = intervalDays)
        ReasonCode.Seasonal -> ReasonUi(ReasonKind.SEASONAL)
        is ReasonCode.Event -> ReasonUi(ReasonKind.EVENT)
        is ReasonCode.Radar -> ReasonUi(ReasonKind.RADAR, weeksSinceLast = weeksSinceLast)
        is ReasonCode.Prior -> ReasonUi(ReasonKind.PRIOR, householdSize = householdSize)
        ReasonCode.Exploration -> ReasonUi(ReasonKind.EXPLORATION)
        ReasonCode.LittleLeft -> ReasonUi(ReasonKind.LITTLE_LEFT)
        ReasonCode.LastList -> ReasonUi(ReasonKind.LAST_LIST)
    }

    companion object {
        /** Up to three presets of a policy, or its first allowed quantities when it has none. */
        fun policyPresets(policy: UnitPolicy): List<Quantity> =
            policy.presets.ifEmpty { (0L until 3L).map { policy.quantityOf(policy.minSteps + it) }.filter(policy::isAllowed) }

        /** The quantity a card shows after switching to [policy]. */
        fun firstQuantity(policy: UnitPolicy): Quantity = policyPresets(policy).first()

        fun unitOf(value: QuantityValue): Quantity? =
            if (value.milli >= 0 && value.unit.isNotBlank()) Quantity(value.milli, UnitCode(value.unit)) else null
    }
}

internal fun Progress.toUi() = QuestionProgress(answered = answered, total = total)

internal val Product.displayName: String get() = name.text.capitalizedFirst()

internal fun DraftSource.toUi(): ItemOrigin = when (this) {
    DraftSource.SUGGESTED -> ItemOrigin.SUGGESTED
    DraftSource.CARD -> ItemOrigin.CARD
    DraftSource.GATE -> ItemOrigin.GATE
    DraftSource.OTHER -> ItemOrigin.OTHER
}

internal fun DomainError.toUiKind(): QuestionErrorKind = when (this) {
    is DomainError.PlanningFailed -> QuestionErrorKind.PLANNING_FAILED
    is DomainError.StorageFailed -> QuestionErrorKind.STORAGE_FAILED
}
