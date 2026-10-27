package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.DietaryTags
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.ZoneId
import kotlin.math.floor
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
 * Budget allocation (§6.3 steps 4 and 5), in this order:
 * 1. meta questions (at most [PlannerConfig.maxMetaQuestions], one unit each);
 * 2. the T1 screen (one unit): the last list if reused, then the T1 products by decreasing p, capped at
 *    [PlannerConfig.maxSuggested] items; T1 products beyond the cap fold into their category gate;
 * 3. the reserved slots: radar, then exploration (off by default);
 * 4. **cards first**: watched products, then T2 products by decreasing p, on every slot left except the
 *    gate reserve ([PlannerConfig.gateSlots], at most a quarter of those slots, and only when there are gates
 *    to ask). The T2 products without a card overflow: with p > [PlannerConfig.overflowToSuggestedAbove] they
 *    join the T1 screen unchecked while it has room, otherwise they fold into their category gate;
 * 5. gates (T3 products plus the folded overflow) take the slots left after the cards, by decreasing
 *    [gate value][gateValue]; the products of the gates that do not fit are deferred.
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
        val ordering = Ordering(input, config.priorityBand)
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
        val t1 = unwatched.filter { it.tier == Tier.T1 }.sortedWith(ordering.byPriority)
        var t2 = unwatched.filter { it.tier == Tier.T2 }
        var t3 = unwatched.filter { it.tier == Tier.T3 }
        var t4 = unwatched.filter { it.tier == Tier.T4 }

        // Budget units left after the meta questions (null = unlimited).
        val left = budget?.let { it - meta.size }
        val screenOpen = (left == null || left > 0) &&
            (lastList.isNotEmpty() || (t1.isNotEmpty() && config.maxSuggested > 0))
        var room = left?.let { (it - if (screenOpen) 1 else 0).coerceAtLeast(0) } ?: Int.MAX_VALUE

        val radar = t2.filter { it.product.cycleClass == CycleClass.LONG && it.hasHistory }
            .sortedWith(ordering.byPriority)
            .take(minOf(config.radarSlots, room))
        room -= radar.size
        t2 = t2 - radar.toSet()

        val exploration = (t3 + t4).filterNot { it.hasHistory }
            .sortedBy { it.product.id.value }
            .shuffled(Random(input.seed))
            .take(minOf(config.explorationSlots, room))
        room -= exploration.size
        val explored = exploration.toSet()
        t3 = t3 - explored
        t4 = t4 - explored

        val gated = if (context.mode == SessionMode.FULL) t3 + t4 else t3
        val (gatable, ungatable) = gated.partition { ordering.category(it.product.categoryId) != null }
        val allocator = Allocator(
            maxCardsPerCategory = config.maxCardsPerCategory,
            screenOpen = screenOpen,
            maxSuggested = config.maxSuggested,
            overflowToSuggestedAbove = config.overflowToSuggestedAbove,
            ordering = ordering,
            lastList = lastList.map { SuggestedEntry(it, preChecked = true, input.lastList[it.product.id]) },
            t1 = t1,
            pool = (watched + t2).sortedWith(ordering.byPriority),
            gatable = gatable,
        )
        val allocation = if (left == null) {
            allocator.allocate(cardCount = allocator.poolSize).fitGates(Int.MAX_VALUE, ordering)
        } else {
            // Cards first: a trial with a card on every slot tells which gates the rest would form.
            val trial = allocator.allocate(cardCount = minOf(room, allocator.poolSize))
            val reserve = minOf(config.gateSlots, trial.gates.size, room / GATE_RESERVE_DIVISOR)
            val cardCount = minOf(allocator.poolSize, maxOf(room - reserve, minOf(watched.size, room)))
            val final = if (cardCount == trial.cards.size) trial else allocator.allocate(cardCount)
            final.fitGates(room - final.cards.size, ordering)
        }

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

    /**
     * Products the household may be asked about: available, not excluded by its diet or avoidances, not
     * snoozed or « never ». Baby products need a baby and pet products a pet, unless the household bought or
     * declared them (onboarding essentials and cadences).
     */
    private fun eligibleProducts(input: PlanningInput): List<Product> {
        val household = input.household
        val excluded = household.effectiveExcludedTags
        return input.products.distinctBy { it.id }.filter { product ->
            val stats = input.stats[product.id]
            val known = (stats?.nPurchases ?: 0) > 0 || scorer.isDeclared(stats, household)
            product.market.availability != Availability.UNAVAILABLE &&
                product.unitPolicies.isNotEmpty() &&
                product.tags.none { it in excluded } &&
                (known || household.hasBaby || DietaryTags.BABY !in product.tags) &&
                (known || household.hasPet || DietaryTags.PET !in product.tags) &&
                stats?.neverAsk != true &&
                stats?.snoozeUntil.let { it == null || it <= input.now } &&
                (input.context.mode != SessionMode.ZONE || product.storageZone == input.context.zone)
        }
    }

    /** Eligible products the planner may ask about: editorial candidates, bought before, or declared. */
    private fun candidates(input: PlanningInput): List<Candidate> =
        eligibleProducts(input)
            .filter { product ->
                val stats = input.stats[product.id]
                product.market.isQuestionCandidate || (stats?.nPurchases ?: 0) > 0 ||
                    scorer.isDeclared(stats, input.household)
            }
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

    fun weeksSinceLast(input: PlanningInput): Int =
        stats?.lastAt?.let { ((input.now - it).inWholeDays / 7).toInt().coerceAtLeast(0) } ?: 0

    fun toScored(): ScoredProduct = ScoredProduct(product, stats, score, tier)
}

private val byDecreasingScore: Comparator<Candidate> =
    compareByDescending<Candidate> { it.probability }.thenBy { it.product.id.value }

internal class SuggestedEntry(val candidate: Candidate, val preChecked: Boolean, val lastQuantity: Quantity?)

/** Where every product of the T1 screen, the cards and the gates ends up, before the gates are fitted. */
private class Allocation(
    val suggested: List<SuggestedEntry>,
    val cards: List<Candidate>,
    val gates: Map<CategoryId, List<Candidate>>,
    val deferred: List<Candidate>,
) {
    /** Keeps the [slots] most valuable gates (by [gateValue]); the products of the others are deferred. */
    fun fitGates(slots: Int, ordering: Ordering): Allocation {
        if (gates.size <= slots) return this
        val ranked = gates.entries.sortedWith(
            compareByDescending<Map.Entry<CategoryId, List<Candidate>>> { gateValue(it.value) }
                .thenByDescending { expectedNeeds(it.value) }
                .thenBy { ordering.categoryRank(it.key) }
                .thenBy { it.key.value },
        )
        val kept = ranked.take(slots.coerceAtLeast(0))
        val dropped = ranked.drop(slots.coerceAtLeast(0))
        return Allocation(
            suggested = suggested,
            cards = cards,
            gates = kept.associateTo(LinkedHashMap()) { it.key to it.value },
            deferred = deferred + dropped.flatMap { it.value },
        )
    }
}

/**
 * Value of a gate when gates compete for the slots left after the cards: the p of its likeliest product.
 *
 * The former value, `1 − Π(1 − pᵢ)`, tends to 1 with the size of the aisle (≈ 0.99 for a big aisle of T3
 * products): gates always beat cards (p 0.3 to 0.8) and big aisles always beat small ones. Now gates never
 * compete with cards for a slot (cards are served first, the gate reserve is a fixed count), and among gates
 * the p of the likeliest member is the chance that a « yes » finds at least the product the household is most
 * likely to need. It does not grow with the number of unlikely products, and it is bounded by the weakest
 * card for every gate made of T3 products and T2 overflow (only the T1 products beyond the screen cap can
 * lift it higher, which rightly puts their gate first). Ties are broken by [expectedNeeds], then store order.
 */
private fun gateValue(items: List<Candidate>): Double = items.maxOfOrNull { it.probability } ?: 0.0

/** Expected number of products of the gate the household needs (Σp): a tie-break between gates. */
private fun expectedNeeds(items: List<Candidate>): Double = items.sumOf { it.probability }

/**
 * Distributes the T1 screen, the card candidates ([pool], in priority order) and the gates for a given
 * number of cards. Pure: [allocate] can be called several times.
 */
private class Allocator(
    private val maxCardsPerCategory: Int?,
    private val screenOpen: Boolean,
    private val maxSuggested: Int,
    private val overflowToSuggestedAbove: Double,
    private val ordering: Ordering,
    private val lastList: List<SuggestedEntry>,
    private val t1: List<Candidate>,
    private val pool: List<Candidate>,
    private val gatable: List<Candidate>,
) {
    val poolSize: Int get() = pool.size

    fun allocate(cardCount: Int): Allocation {
        val suggested = mutableListOf<SuggestedEntry>()
        val gates = LinkedHashMap<CategoryId, MutableList<Candidate>>()
        val deferred = mutableListOf<Candidate>()
        gatable.forEach { gates.getOrPut(it.product.categoryId) { mutableListOf() } += it }

        fun fold(candidate: Candidate) {
            val categoryId = candidate.product.categoryId
            if (ordering.category(categoryId) != null) {
                gates.getOrPut(categoryId) { mutableListOf() } += candidate
            } else {
                deferred += candidate
            }
        }

        if (screenOpen) {
            // The reused last list is shown whole; the T1 products fill the screen up to its cap.
            suggested += lastList
            t1.forEach { candidate ->
                if (suggested.size < maxSuggested) {
                    suggested += SuggestedEntry(candidate, preChecked = true, lastQuantity = null)
                } else {
                    fold(candidate)
                }
            }
        } else {
            deferred += lastList.map { it.candidate } + t1
        }

        val cards = mutableListOf<Candidate>()
        val perCategory = HashMap<CategoryId, Int>()
        val rest = mutableListOf<Candidate>()
        for (candidate in pool) {
            val categoryId = candidate.product.categoryId
            val inCategory = perCategory[categoryId] ?: 0
            // Watched products always get their card; the others spread over the aisles.
            if (cards.size < cardCount && (candidate.watch || maxCardsPerCategory == null || inCategory < maxCardsPerCategory)) {
                cards += candidate
                perCategory[categoryId] = inCategory + 1
            } else {
                rest += candidate
            }
        }
        rest.forEach { candidate ->
            if (screenOpen && candidate.probability > overflowToSuggestedAbove && suggested.size < maxSuggested) {
                suggested += SuggestedEntry(candidate, preChecked = false, lastQuantity = null)
            } else {
                fold(candidate)
            }
        }
        return Allocation(suggested, cards, gates, deferred)
    }
}

/** The gate reserve takes at most one slot in this many (cards keep at least three slots out of four). */
private const val GATE_RESERVE_DIVISOR = 4

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
private class Ordering(input: PlanningInput, bandWidth: Double) {
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

    /**
     * Within a group: watched first, then p decreasing by bands of [PlannerConfig.priorityBand], then a key
     * drawn from the session seed (products of close p take turns from one session to the next), then id.
     * Tiers never depend on the seed, and a resumed session keeps its seed. Without bands: p decreasing, then
     * popularity, then id.
     */
    val byPriority: Comparator<Candidate> = if (bandWidth > 0.0) {
        val seed = input.seed
        compareByDescending<Candidate> { it.watch }
            .thenByDescending { floor(it.probability / bandWidth).toLong() }
            .thenBy { rotationKey(seed, it.product.id.value) }
            .thenBy { it.product.id.value }
    } else {
        compareByDescending<Candidate> { it.watch }
            .thenByDescending { it.probability }
            .thenBy { it.product.market.popularityRank ?: Int.MAX_VALUE }
            .thenBy { it.product.id.value }
    }

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

/**
 * A pseudo-random key of [productId] for [seed]: FNV-1a over the UTF-16 code units of the id, mixed with the
 * seed by splitmix64. Pure Kotlin arithmetic, identical on every platform.
 */
internal fun rotationKey(seed: Long, productId: String): Long {
    var hash = FNV_OFFSET
    for (char in productId) {
        hash = (hash xor char.code.toLong()) * FNV_PRIME
    }
    var z = hash + seed * GOLDEN_GAMMA
    z = (z xor (z ushr 30)) * MIX_1
    z = (z xor (z ushr 27)) * MIX_2
    return z xor (z ushr 31)
}

private const val FNV_OFFSET: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
private const val FNV_PRIME: Long = 0x100000001b3L
private const val GOLDEN_GAMMA: Long = -0x61c8864680b583ebL // 0x9e3779b97f4a7c15
private const val MIX_1: Long = -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
private const val MIX_2: Long = -0x6b2fb644ecceee15L // 0x94d049bb133111eb
