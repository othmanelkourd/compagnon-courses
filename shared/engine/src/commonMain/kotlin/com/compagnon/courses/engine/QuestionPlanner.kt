package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.ZoneId
import kotlin.random.Random

/** A scored product: what the planner knows about one candidate. */
data class ScoredProduct(
    val product: Product,
    val stats: ProductStats?,
    val score: NeedScore,
    val tier: Tier,
)

/**
 * Builds the question plan of a session (`docs/question-engine.md` steps 1 to 8). Pure and deterministic:
 * the same [PlanningInput] (including `now` and `seed`) always yields the same plan, whatever the order of
 * its collections.
 *
 * Budget allocation: meta questions come first, then the T1 screen, then the reserved slots (radar,
 * exploration), then cards and gates. When cards and gates do not fit, the least valuable one is removed
 * until they do: a card is worth p, a gate `1 − Π(1 − pᵢ)` over its products. A removed card with p > 0.6
 * joins the T1 screen unchecked, any other one joins its category gate; products of a removed gate are
 * deferred.
 */
class QuestionPlanner(private val config: PlannerConfig = PlannerConfig()) {
    private val scorer = NeedScorer(config.score)

    /** Scores every candidate (available, editorial candidate or bought before, not excluded). */
    fun score(input: PlanningInput): List<ScoredProduct> = candidates(input)
        .sortedWith(byDecreasingScore)
        .map { it.toScored() }

    fun plan(input: PlanningInput): QuestionPlan {
        val context = input.context
        val budget = config.budgetFor(context)
        val meta = context.metaQuestions.distinct()
            .take(minOf(config.maxMetaQuestions, budget ?: Int.MAX_VALUE))
            .map { MetaQuestion(it, context.valueOf(it)) }
        val ordering = Ordering(input)
        val builder = ItemBuilder(input)

        val lastList = if (context.reuseLastList) {
            eligibleProducts(input).filter { it.id in input.lastList }.map { candidate(it, input) }
        } else {
            emptyList()
        }
        val lastListIds = lastList.mapTo(HashSet()) { it.product.id }
        val scored = candidates(input).filter { it.product.id !in lastListIds }

        val watched = scored.filter { it.watch }
        val unwatched = scored.filterNot { it.watch }
        val t1 = unwatched.filter { it.tier == Tier.T1 }
        var t2 = unwatched.filter { it.tier == Tier.T2 }
        var t3 = unwatched.filter { it.tier == Tier.T3 }
        var t4 = unwatched.filter { it.tier == Tier.T4 }

        val available = budget?.let { it - meta.size }
        val hasSuggested = t1.isNotEmpty() || lastList.isNotEmpty()
        val suggestedCost = if (hasSuggested && (available == null || available > 0)) 1 else 0
        var room = available?.let { it - suggestedCost } ?: Int.MAX_VALUE

        val radar = t2.filter { it.product.cycleClass == CycleClass.LONG && it.hasHistory }
            .sortedWith(ordering.byPriority)
            .take(minOf(config.radarSlots, room))
        room -= radar.size
        t2 = t2 - radar.toSet()

        val exploration = (t3 + t4).filterNot { it.hasHistory }
            .sortedBy { it.product.id.value }
            .shuffled(Random(input.seed))
            .take(minOf(config.explorationSlots, room))
        val explored = exploration.toSet()
        t3 = t3 - explored
        t4 = t4 - explored

        val gated = if (context.mode == SessionMode.FULL) t3 + t4 else t3
        val (gatable, ungatable) = gated.partition { ordering.category(it.product.categoryId) != null }
        val allocation = Allocation(
            left = available?.let { it - radar.size - exploration.size },
            overflowToSuggestedAbove = config.overflowToSuggestedAbove,
            ordering = ordering,
            suggested = lastList.map { SuggestedEntry(it, preChecked = true, input.lastList[it.product.id]) } +
                t1.map { SuggestedEntry(it, preChecked = true, lastQuantity = null) },
            cards = (watched + t2).sortedWith(ordering.byPriority),
            gates = gatable.groupBy { it.product.categoryId },
        )
        allocation.run()

        val suggested = allocation.suggested
            .sortedWith(compareBy<SuggestedEntry, Candidate>(ordering.forScreen(context.mode)) { it.candidate })
            .map { builder.suggestedItem(it) }

        val slots = buildList {
            allocation.cards.forEach { candidate ->
                val kind = if (candidate.watch) CardKind.WATCH else CardKind.STANDARD
                add(ordering.cardSlot(builder.card(candidate, kind), candidate))
            }
            allocation.gates.forEach { (categoryId, items) ->
                val category = checkNotNull(ordering.category(categoryId))
                val sortedItems = items.sortedWith(ordering.byPriority)
                val gate = PlannedQuestion.Gate(category, sortedItems.map { builder.item(it, preChecked = false) })
                add(ordering.gateSlot(gate, sortedItems))
            }
            radar.forEach { add(ordering.cardSlot(builder.card(it, CardKind.RADAR), it)) }
            exploration.forEach { add(ordering.cardSlot(builder.card(it, CardKind.EXPLORATION), it)) }
        }
        val questions = slots.sortedWith(ordering.slotComparator(context.mode)).map { it.question }

        val deferred = (allocation.deferred + ungatable + (if (context.mode == SessionMode.FULL) emptyList() else t4))
            .sortedWith(byDecreasingScore)
            .map { it.product.id }

        return QuestionPlan(meta, suggested, questions, deferred, budget)
    }

    private fun eligibleProducts(input: PlanningInput): List<Product> {
        val excluded = input.household.excludedTags
        return input.products.distinctBy { it.id }.filter { product ->
            val stats = input.stats[product.id]
            product.market.availability != Availability.UNAVAILABLE &&
                product.unitPolicies.isNotEmpty() &&
                product.tags.none { it in excluded } &&
                stats?.neverAsk != true &&
                stats?.snoozeUntil.let { it == null || it <= input.now } &&
                (input.context.mode != SessionMode.ZONE || product.storageZone == input.context.zone)
        }
    }

    private fun candidates(input: PlanningInput): List<Candidate> =
        eligibleProducts(input)
            .filter { it.market.isQuestionCandidate || (input.stats[it.id]?.nPurchases ?: 0) > 0 }
            .map { candidate(it, input) }

    private fun candidate(product: Product, input: PlanningInput): Candidate {
        val stats = input.stats[product.id]
        val score = scorer.score(product, stats, input.household, input.context, input.signals, input.now)
        return Candidate(product, stats, score, config.tierOf(score.probability))
    }

    /** Creates the presented items (quantity proposals and reasons) of one plan. */
    private class ItemBuilder(private val input: PlanningInput) {
        private val advisor = QuantityAdvisor(input.units)
        private val scaling = QuantityScaling(
            horizonDays = input.context.horizonDays,
            usualHorizonDays = input.context.usualHorizonDays,
            persons = input.context.persons,
            householdPersons = input.household.size,
        )

        fun item(
            candidate: Candidate,
            preChecked: Boolean,
            lead: ReasonCode? = null,
            lastQuantity: Quantity? = null,
        ): ProposedItem {
            val proposal = lastQuantity?.let { advisor.proposeExact(candidate.product, it) }
                ?: checkNotNull(advisor.propose(candidate.product, candidate.stats, scaling)) {
                    "Eligible product ${candidate.product.slug} has no default unit policy"
                }
            return ProposedItem(
                product = candidate.product,
                score = candidate.probability,
                tier = candidate.tier,
                proposal = proposal,
                reasons = listOfNotNull(lead) + candidate.score.reasons,
                preChecked = preChecked,
            )
        }

        fun suggestedItem(entry: SuggestedEntry): ProposedItem = item(
            candidate = entry.candidate,
            preChecked = entry.preChecked,
            lead = if (entry.lastQuantity != null) ReasonCode.LastList else null,
            lastQuantity = entry.lastQuantity,
        )

        fun card(candidate: Candidate, kind: CardKind): PlannedQuestion.Card {
            val lead = when (kind) {
                CardKind.RADAR -> ReasonCode.Radar(weeksSinceLast = candidate.weeksSinceLast(input))
                CardKind.EXPLORATION -> ReasonCode.Exploration
                CardKind.STANDARD, CardKind.WATCH -> null
            }
            return PlannedQuestion.Card(item(candidate, preChecked = false, lead = lead), kind)
        }
    }
}

internal class Candidate(
    val product: Product,
    val stats: ProductStats?,
    val score: NeedScore,
    val tier: Tier,
) {
    val probability: Double get() = score.probability
    val watch: Boolean get() = stats?.watch == true
    val hasHistory: Boolean get() = (stats?.nPurchases ?: 0) > 0

    /** Value of a card in the budget allocation; watched products are removed last. */
    val cardValue: Double get() = if (watch) 1.0 else probability

    fun weeksSinceLast(input: PlanningInput): Int =
        stats?.lastAt?.let { ((input.now - it).inWholeDays / 7).toInt().coerceAtLeast(0) } ?: 0

    fun toScored(): ScoredProduct = ScoredProduct(product, stats, score, tier)
}

private val byDecreasingScore: Comparator<Candidate> =
    compareByDescending<Candidate> { it.probability }.thenBy { it.product.id.value }

internal class SuggestedEntry(val candidate: Candidate, val preChecked: Boolean, val lastQuantity: Quantity?)

/** Fits the T1 screen, the cards and the gates into [left] budget units (null = unlimited). */
private class Allocation(
    private val left: Int?,
    private val overflowToSuggestedAbove: Double,
    private val ordering: Ordering,
    suggested: List<SuggestedEntry>,
    cards: List<Candidate>,
    gates: Map<CategoryId, List<Candidate>>,
) {
    val suggested: MutableList<SuggestedEntry> = suggested.toMutableList()

    /** Sorted by decreasing value: the last one is the least valuable. */
    val cards: MutableList<Candidate> = cards.toMutableList()
    val gates: MutableMap<CategoryId, MutableList<Candidate>> =
        gates.mapValuesTo(LinkedHashMap()) { it.value.toMutableList() }
    val deferred: MutableList<Candidate> = mutableListOf()

    private val closedGates = mutableSetOf<CategoryId>()
    private var suggestedOpen = true

    private val cost: Int get() = (if (suggested.isEmpty()) 0 else 1) + cards.size + gates.size

    fun run() {
        val budget = left ?: return
        while (cost > budget) {
            val lowestCard = cards.lastOrNull()
            val lowestGate = gates.entries.minWithOrNull(gateComparator)
            when {
                lowestCard != null && (lowestGate == null || lowestCard.cardValue < gateValue(lowestGate.value)) -> {
                    cards.removeAt(cards.lastIndex)
                    overflow(lowestCard)
                }
                lowestGate != null -> {
                    // Read the entry before removing it: on Kotlin/Native an entry is invalid once its map changes.
                    val categoryId = lowestGate.key
                    val items = lowestGate.value
                    gates.remove(categoryId)
                    closedGates += categoryId
                    deferred += items
                }
                else -> {
                    deferred += suggested.map { it.candidate }
                    suggested.clear()
                    suggestedOpen = false
                }
            }
        }
    }

    private fun overflow(card: Candidate) {
        val categoryId = card.product.categoryId
        when {
            suggestedOpen && card.probability > overflowToSuggestedAbove ->
                suggested += SuggestedEntry(card, preChecked = false, lastQuantity = null)
            categoryId !in closedGates && ordering.category(categoryId) != null ->
                gates.getOrPut(categoryId) { mutableListOf() } += card
            else -> deferred += card
        }
    }

    /** Lowest value first; on a tie, the category that comes later in the store goes first. */
    private val gateComparator: Comparator<Map.Entry<CategoryId, List<Candidate>>> =
        compareBy<Map.Entry<CategoryId, List<Candidate>>> { gateValue(it.value) }
            .thenByDescending { ordering.categoryRank(it.key) }
            .thenByDescending { it.key.value }

    /** Probability that at least one product of the gate is needed. */
    private fun gateValue(items: List<Candidate>): Double =
        1.0 - items.fold(1.0) { acc, item -> acc * (1.0 - item.probability) }
}

/** A question with its sort keys. */
private class Slot(
    val question: PlannedQuestion,
    val group: Int,
    val zoneRank: Int,
    val categoryRank: Int,
    val kindRank: Int,
    val lead: Candidate?,
    val key: String,
)

/** Category (store) and zone orders of one plan. */
private class Ordering(input: PlanningInput) {
    private val categories: Map<CategoryId, Category> = input.categories.associateBy { it.id }
    private val categoryRanks: Map<CategoryId, Int>
    private val zoneRanks: Map<ZoneId, Int>

    init {
        val explicit = input.context.categoryOrder.filter { it in categories }.distinct()
        val explicitSet = explicit.toSet()
        val others = categories.values.filter { it.id !in explicitSet }
            .sortedWith(compareBy<Category> { it.position }.thenBy { it.id.value })
            .map { it.id }
        categoryRanks = (explicit + others).withIndex().associate { (index, id) -> id to index }
        zoneRanks = input.storageZones.distinctBy { it.id }
            .sortedWith(compareBy({ it.position }, { it.id.value }))
            .withIndex()
            .associate { (index, zone) -> zone.id to index }
    }

    fun category(id: CategoryId): Category? = categories[id]

    fun categoryRank(id: CategoryId): Int = categoryRanks[id] ?: Int.MAX_VALUE

    fun zoneRank(id: ZoneId): Int = zoneRanks[id] ?: Int.MAX_VALUE

    /** Within a group: watched first, then p decreasing, then popularity, then id. */
    val byPriority: Comparator<Candidate> = compareByDescending<Candidate> { it.watch }
        .thenByDescending { it.probability }
        .thenBy { it.product.market.popularityRank ?: Int.MAX_VALUE }
        .thenBy { it.product.id.value }

    /** Order of the T1 screen: by store category (by zone first in a home tour), then priority. */
    fun forScreen(mode: SessionMode): Comparator<Candidate> {
        val byCategory = compareBy<Candidate> { categoryRank(it.product.categoryId) }.then(byPriority)
        if (!mode.byZone) return byCategory
        return compareBy<Candidate> { zoneRank(it.product.storageZone) }.then(byCategory)
    }

    fun cardSlot(card: PlannedQuestion.Card, candidate: Candidate): Slot = Slot(
        question = card,
        group = when (card.kind) {
            CardKind.STANDARD, CardKind.WATCH -> GROUP_CARDS
            CardKind.RADAR -> GROUP_RADAR
            CardKind.EXPLORATION -> GROUP_EXPLORATION
        },
        zoneRank = zoneRank(candidate.product.storageZone),
        categoryRank = categoryRank(candidate.product.categoryId),
        kindRank = if (card.kind == CardKind.RADAR) KIND_RADAR else KIND_CARD,
        lead = candidate,
        key = "product:${candidate.product.id.value}",
    )

    fun gateSlot(gate: PlannedQuestion.Gate, items: List<Candidate>): Slot = Slot(
        question = gate,
        group = GROUP_GATES,
        zoneRank = items.minOfOrNull { zoneRank(it.product.storageZone) } ?: Int.MAX_VALUE,
        categoryRank = categoryRank(gate.category.id),
        kindRank = KIND_GATE,
        lead = null,
        key = "category:${gate.category.id.value}",
    )

    /**
     * Quick and full sessions: cards grouped by category in store order, then gates, radar and exploration.
     * Home tour and zone sessions: cards, radar and gates by storage zone, then exploration.
     */
    fun slotComparator(mode: SessionMode): Comparator<Slot> {
        val tail = compareBy<Slot> { it.categoryRank }
            .thenBy { it.kindRank }
            .then(
                Comparator { a, b ->
                    val leadA = a.lead
                    val leadB = b.lead
                    if (leadA != null && leadB != null) byPriority.compare(leadA, leadB) else 0
                },
            )
            .thenBy { it.key }
        return if (mode.byZone) {
            compareBy<Slot> { if (it.group == GROUP_EXPLORATION) 1 else 0 }.thenBy { it.zoneRank }.then(tail)
        } else {
            compareBy<Slot> { it.group }.then(tail)
        }
    }

    private val SessionMode.byZone: Boolean
        get() = this == SessionMode.HOME_TOUR || this == SessionMode.ZONE

    private companion object {
        const val GROUP_CARDS = 0
        const val GROUP_GATES = 1
        const val GROUP_RADAR = 2
        const val GROUP_EXPLORATION = 3
        const val KIND_CARD = 0
        const val KIND_RADAR = 1
        const val KIND_GATE = 2
    }
}
