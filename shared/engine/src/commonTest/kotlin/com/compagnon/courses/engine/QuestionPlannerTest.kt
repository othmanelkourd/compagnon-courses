package com.compagnon.courses.engine

import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Avoidance
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Diet
import com.compagnon.courses.core.model.DietaryTags
import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.Pet
import com.compagnon.courses.core.model.PetKind
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.engine.Fixtures.CLEANING
import com.compagnon.courses.engine.Fixtures.CUPBOARD
import com.compagnon.courses.engine.Fixtures.DAIRY
import com.compagnon.courses.engine.Fixtures.DRINKS
import com.compagnon.courses.engine.Fixtures.FRUIT_BASKET
import com.compagnon.courses.engine.Fixtures.LITER
import com.compagnon.courses.engine.Fixtures.NOW
import com.compagnon.courses.engine.Fixtures.PORK
import com.compagnon.courses.engine.Fixtures.PRODUCE
import com.compagnon.courses.engine.Fixtures.boughtDaysAgo
import com.compagnon.courses.engine.Fixtures.context
import com.compagnon.courses.engine.Fixtures.household
import com.compagnon.courses.engine.Fixtures.input
import com.compagnon.courses.engine.Fixtures.product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class QuestionPlannerTest {
    private val planner = QuestionPlanner(Fixtures.BY_SCORE)

    private val milk = product("lait", penetration = 0.9)
    private val yogurt = product("yaourt", penetration = 0.5)
    private val butter = product("beurre", penetration = 0.2)
    private val cream = product("creme", penetration = 0.15)
    private val bleach = product("javel", penetration = 0.25, category = CLEANING, zone = CUPBOARD)
    private val caviar = product("caviar", penetration = 0.05)

    @Test
    fun placesEachTierOnItsScreen() {
        val plan = planner.plan(input(listOf(caviar, bleach, cream, butter, yogurt, milk)))

        assertEquals(listOf("lait"), plan.suggested.slugs)
        assertTrue(plan.suggested.single().preChecked)
        assertEquals(Tier.T1, plan.suggested.single().tier)
        assertEquals(listOf("yaourt", "gate:cremerie", "gate:entretien"), plan.questions.map { it.label })
        assertEquals(listOf("beurre", "creme"), plan.gates().first().items.slugs)
        assertTrue(plan.gates().flatMap { it.items }.none { it.preChecked })
        assertEquals(listOf(caviar.id), plan.deferred)
        assertEquals(12, plan.budget)
        assertEquals(4, plan.cost)
    }

    @Test
    fun keepsOnlyEligibleCandidates() {
        val pork = product("saucisse", penetration = 0.5, tags = setOf(PORK))
        val unavailable = product("introuvable", penetration = 0.5, availability = Availability.UNAVAILABLE)
        val notCandidate = product("rare", penetration = 0.5, candidate = false)
        val boughtBefore = product("habitude", penetration = 0.5, candidate = false, repurchaseDays = 7)
        val never = product("jamais", penetration = 0.5)
        val snoozed = product("veille", penetration = 0.5)
        val snoozeOver = product("reveil", penetration = 0.5)
        val noPolicy = product("sans-unite", penetration = 0.5, policies = emptyList())
        val plan = planner.plan(
            input(
                products = listOf(
                    pork, unavailable, notCandidate, boughtBefore, never, snoozed, snoozeOver, noPolicy, yogurt,
                ),
                household = household(excludedTags = setOf(PORK)),
                stats = mapOf(
                    boughtBefore to boughtDaysAgo(5),
                    never to ProductStats(neverAsk = true),
                    snoozed to ProductStats(snoozeUntil = NOW + 3.days),
                    snoozeOver to ProductStats(snoozeUntil = NOW - 1.days),
                ),
            ),
        )
        assertEquals(setOf("habitude", "reveil", "yaourt"), plan.askedProducts.slugs.toSet())
        assertTrue(plan.deferred.isEmpty())
    }

    @Test
    fun overflowingCardsJoinTheT1ScreenOrTheirGate() {
        val a1 = product("a1", penetration = 0.78)
        val a2 = product("a2", penetration = 0.7)
        val b1 = product("b1", penetration = 0.66, category = DRINKS)
        val b2 = product("b2", penetration = 0.4, category = DRINKS)
        val plan = planner.plan(input(listOf(milk, a1, a2, b1, b2), context = context(budget = 3)))

        // T1 screen + 2 cards = 3: a1 and a2 get the cards; b1 (p > 0.6) joins the T1 screen, unchecked; b2 falls
        // into the drinks gate, which has no slot left (and no reserve: a quarter of 2 slots is 0).
        assertEquals(listOf("lait", "b1"), plan.suggested.slugs)
        assertEquals(listOf(true, false), plan.suggested.map { it.preChecked })
        assertEquals(listOf("a1", "a2"), plan.questions.map { it.label })
        assertEquals(listOf(b2.id), plan.deferred)
        assertEquals(3, plan.cost)
    }

    // Replaces gatesAbsorbTheLeastLikelyCards: the cleaning gate (1 − Π(1 − p) ≈ 0.75) used to beat the b1 card
    // (p = 0.35) and swallow it. Cards now come first (§6.3 step 5): gates only get the slots the cards leave.
    @Test
    fun cardsComeBeforeGates() {
        val a1 = product("a1", penetration = 0.7)
        val a2 = product("a2", penetration = 0.5)
        val b1 = product("b1", penetration = 0.35, category = CLEANING)
        val b3 = listOf(0.28, 0.25, 0.2).mapIndexed { i, p -> product("b-t3-$i", penetration = p, category = CLEANING) }
        val products = listOf(a1, a2, b1) + b3

        val three = planner.plan(input(products, context = context(budget = 3)))
        assertEquals(listOf("a1", "a2", "b1"), three.questions.map { it.label })
        assertEquals(b3.map { it.id }, three.deferred)
        assertTrue(three.suggested.isEmpty())

        // One more slot: the reserve (a quarter of 4) gives it to the cleaning gate, after the three cards.
        val four = planner.plan(input(products, context = context(budget = 4)))
        assertEquals(listOf("a1", "a2", "b1", "gate:entretien"), four.questions.map { it.label })
        assertEquals(listOf("b-t3-0", "b-t3-1", "b-t3-2"), four.gates().single().items.slugs)
    }

    @Test
    fun theGateReserveTakesTheLeastLikelyCards() {
        val cards = (1..10).map { product("c$it", penetration = 0.79 - it * 0.04) }
        val bleach = product("javel", penetration = 0.25, category = CLEANING, zone = CUPBOARD)
        val water = product("eau", penetration = 0.2, category = DRINKS)
        val plan = planner.plan(input(cards + bleach + water, context = context(budget = 8)))

        // 8 slots, no T1 screen: 2 reserved for the gates (a quarter), 6 for the likeliest cards.
        assertEquals((1..6).map { "c$it" }, plan.cards().map { it.item.product.slug })
        assertEquals(listOf("gate:cremerie", "gate:entretien"), plan.gates().map { it.label })
        // The 4 cards without a slot fold into the dairy gate, the likeliest one (max p 0.51 > 0.25 > 0.2).
        val dairy = plan.gates().single { it.category == DAIRY }
        assertEquals((7..10).map { "c$it" }, dairy.items.slugs)
        assertEquals(listOf("javel"), plan.gates().single { it.category == CLEANING }.items.slugs)
        assertEquals(listOf(water.id), plan.deferred)
        assertEquals(8, plan.cost)

        // Without a reserve, gates only get what the cards leave: here nothing.
        val strict = QuestionPlanner(Fixtures.BY_SCORE.copy(gateSlots = 0))
            .plan(input(cards + bleach + water, context = context(budget = 8)))
        assertEquals(8, strict.cards().size)
        assertTrue(strict.gates().isEmpty())
    }

    @Test
    fun watchedCardsAreNeverGivenUpForTheGateReserve() {
        val watched = (1..4).map { product("w$it", penetration = 0.5) }
        val bleach = product("javel", penetration = 0.25, category = CLEANING, zone = CUPBOARD)
        val plan = planner.plan(
            input(
                products = watched + bleach,
                stats = watched.associateWith { ProductStats(watch = true) },
                context = context(budget = 4),
            ),
        )
        assertEquals(4, plan.cards().size)
        assertTrue(plan.cards().all { it.kind == CardKind.WATCH })
        assertTrue(plan.gates().isEmpty())
    }

    @Test
    fun aGateIsWorthItsLikeliestProductNotItsSize() {
        // A big aisle of unlikely products against a small one with a likely product: 1 − Π(1 − p) preferred
        // the big aisle (≈ 0.9 against 0.29); the max p prefers the small one.
        val cleaning = (1..20).map { product("menage-$it", penetration = 0.11, category = CLEANING, zone = CUPBOARD) }
        val drink = product("jus", penetration = 0.29, category = DRINKS)
        val plan = planner.plan(input(cleaning + drink, context = context(budget = 1)))
        assertEquals(listOf("gate:boissons"), plan.questions.map { it.label })
        assertEquals(20, plan.deferred.size)
    }

    @Test
    fun theT1ScreenIsCapped() {
        val staples = (1..20).map { product("s$it", penetration = 0.99 - it * 0.005) }
        val overflow = product("presque", penetration = 0.7, category = DRINKS)
        val plan = planner.plan(input(staples + overflow, context = context(budget = 3)))

        assertEquals(staples.take(15).map { it.id }.toSet(), plan.suggested.map { it.productId }.toSet())
        assertTrue(plan.suggested.all { it.preChecked })
        // The 5 least likely staples fold into the dairy gate, which gets the slot the card leaves.
        assertEquals(listOf("presque", "gate:cremerie"), plan.questions.map { it.label })
        assertEquals((16..20).map { "s$it" }, plan.gates().single().items.slugs)
        assertTrue(plan.deferred.isEmpty())

        // The screen alone: the T2 overflow (p > 0.6) finds it full and is deferred with the dairy gate.
        val tight = planner.plan(input(staples + overflow, context = context(budget = 1)))
        assertEquals(15, tight.suggested.size)
        assertTrue(tight.questions.isEmpty())
        assertEquals(staples.drop(15).map { it.id } + overflow.id, tight.deferred)

        // With room on the screen, the overflow joins it unchecked.
        val roomy = QuestionPlanner(Fixtures.BY_SCORE.copy(maxSuggested = 30))
            .plan(input(staples + overflow, context = context(budget = 1)))
        assertEquals(21, roomy.suggested.size)
        assertEquals(listOf(overflow.id), roomy.suggested.filterNot { it.preChecked }.map { it.productId })
    }

    @Test
    fun metaQuestionsComeFirstAndCountInTheBudget() {
        val products = (1..20).map { product("p$it", penetration = 0.31 + it * 0.02) }
        val plan = planner.plan(input(products, context = context(meta = MetaKey.entries, persons = 3)))
        assertEquals(
            listOf(
                MetaQuestion(MetaKey.HORIZON_DAYS, 7),
                MetaQuestion(MetaKey.PERSONS, 3),
                MetaQuestion(MetaKey.GUESTS, 0),
            ),
            plan.meta,
        )
        assertEquals(12, plan.cost)

        // A budget smaller than the meta questions truncates them.
        val tiny = planner.plan(input(products, context = context(meta = MetaKey.entries, budget = 2)))
        assertEquals(2, tiny.meta.size)
        assertEquals(2, tiny.cost)
        assertTrue(tiny.askedProducts.isEmpty())
        assertEquals(products.size, tiny.deferred.size)
    }

    @Test
    fun ordersCardsByStoreCategoryThenScore() {
        val apples = product("pommes", penetration = 0.4, category = PRODUCE)
        val pears = product("poires", penetration = 0.6, category = PRODUCE, popularityRank = 3)
        val kiwis = product("kiwis", penetration = 0.6, category = PRODUCE, popularityRank = 1)
        val water = product("eau", penetration = 0.7, category = DRINKS)
        val products = listOf(yogurt, apples, pears, kiwis, water)

        val byPosition = planner.plan(input(products, context = context(budget = 20)))
        assertEquals(listOf("kiwis", "poires", "pommes", "yaourt", "eau"), byPosition.questions.map { it.label })

        val store = planner.plan(input(products, context = context(budget = 20, categoryOrder = listOf(DRINKS, DAIRY))))
        assertEquals(listOf("eau", "yaourt", "kiwis", "poires", "pommes"), store.questions.map { it.label })
    }

    @Test
    fun homeTourOrdersByStorageZone() {
        val apples = product("pommes", penetration = 0.5, category = PRODUCE, zone = FRUIT_BASKET)
        val pasta = product("pates", penetration = 0.6, category = DRINKS, zone = CUPBOARD)
        val products = listOf(apples, pasta, yogurt, bleach, butter)
        val plan = planner.plan(input(products, context = context(mode = SessionMode.HOME_TOUR)))
        // Fridge (dairy card, then its gate), cupboard (cleaning before drinks, in store order), fruit basket.
        assertEquals(
            listOf("yaourt", "gate:cremerie", "gate:entretien", "pates", "pommes"),
            plan.questions.map { it.label },
        )
        assertEquals(15, plan.budget)
    }

    @Test
    fun zoneModeKeepsASingleZone() {
        val cupboard = context(mode = SessionMode.ZONE, zone = CUPBOARD)
        val plan = planner.plan(input(listOf(yogurt, bleach, milk), context = cupboard))
        assertEquals(listOf("gate:entretien"), plan.questions.map { it.label })
        assertTrue(plan.suggested.isEmpty())
    }

    @Test
    fun radarSlotsAreReservedForLongCycleProducts() {
        val shampoo = product(
            "shampooing",
            penetration = null,
            cycleClass = CycleClass.LONG,
            repurchaseDays = 60,
            category = CLEANING,
        )
        val others = (1..12).map { product("p$it", penetration = 0.6 + it * 0.01) }
        val stats = mapOf(shampoo to boughtDaysAgo(56))
        val plan = planner.plan(input(others + shampoo, stats = stats, context = context(budget = 6)))

        val radar = plan.cards().last()
        assertEquals(CardKind.RADAR, radar.kind)
        assertEquals("shampooing", radar.item.product.slug)
        assertEquals(ReasonCode.Radar(weeksSinceLast = 8), radar.item.reasons.first())
        assertEquals(6, plan.cost)

        // Without radar slots, the shampoo (p ≈ 0.57) loses against the other cards.
        val noRadar = QuestionPlanner(Fixtures.BY_SCORE.copy(radarSlots = 0))
            .plan(input(others + shampoo, stats = stats, context = context(budget = 6)))
        assertTrue(noRadar.cards().none { it.item.product.slug == "shampooing" })
    }

    @Test
    fun watchedProductsAreAlwaysCardsAndComeFirst() {
        val oil = product("huile", penetration = 0.95)
        val plan = planner.plan(input(listOf(oil, yogurt), stats = mapOf(oil to ProductStats(watch = true))))
        val first = plan.cards().first()
        assertEquals(CardKind.WATCH, first.kind)
        assertEquals("huile", first.item.product.slug)
        assertEquals(ReasonCode.LittleLeft, first.item.reasons.first())
        assertTrue(plan.suggested.isEmpty())
    }

    @Test
    fun explorationIsReproducibleWithTheSeed() {
        val planner = QuestionPlanner(Fixtures.BY_SCORE.copy(explorationSlots = 1))
        val unknown = (1..10).map { product("inconnu-$it", penetration = 0.05) }
        fun explored(seed: Long) = planner.plan(input(unknown + yogurt, seed = seed)).cards()
            .single { it.kind == CardKind.EXPLORATION }.item.product.slug

        assertEquals(explored(42), explored(42))
        assertTrue((0L..20L).map { explored(it) }.toSet().size > 1)
        val card = planner.plan(input(unknown + yogurt, seed = 7)).cards().last()
        assertEquals(ReasonCode.Exploration, card.item.reasons.first())
    }

    @Test
    fun reusesTheLastList() {
        val pork = product("saucisse", penetration = 0.5, tags = setOf(PORK))
        val lastList = mapOf(
            yogurt to Quantity(3000, LITER),
            pork to Quantity(1000, LITER),
            caviar to Quantity(1000, LITER),
        )
        val plan = planner.plan(
            input(
                products = listOf(yogurt, pork, caviar, milk),
                household = household(excludedTags = setOf(PORK)),
                lastList = lastList,
                context = context(reuseLastList = true),
            ),
        )
        assertEquals(setOf("lait", "yaourt", "caviar"), plan.suggested.slugs.toSet())
        assertTrue(plan.suggested.all { it.preChecked })
        val reused = plan.suggested.single { it.product.slug == "yaourt" }
        assertEquals(Quantity(3000, LITER), reused.proposal.quantity)
        assertEquals(ReasonCode.LastList, reused.reasons.first())
        assertTrue(plan.questions.isEmpty())
    }

    @Test
    fun fullTourPutsEveryCategoryBehindAGateWithoutBudget() {
        val products = (1..30).map { product("p$it", penetration = 0.31 + it * 0.01) } + caviar
        val plan = planner.plan(input(products, context = context(mode = SessionMode.FULL, budget = 3)))
        assertNull(plan.budget)
        assertEquals(30, plan.cards().size)
        assertEquals(listOf("caviar"), plan.gates().single().items.slugs)
        assertTrue(plan.deferred.isEmpty())
    }

    @Test
    fun proposesScaledQuantities() {
        val plan = planner.plan(input(listOf(yogurt), context = context(persons = 4)))
        // First preset (1 L) × 4 persons / 2 household members.
        assertEquals(Quantity(2000, LITER), plan.cards().single().item.proposal.quantity)
    }

    @Test
    fun scoresAreExposedForDebugging() {
        val scored = planner.score(input(listOf(caviar, milk, yogurt)))
        assertEquals(listOf("lait", "yaourt", "caviar"), scored.map { it.product.slug })
        assertEquals(listOf(Tier.T1, Tier.T2, Tier.T4), scored.map { it.tier })
    }

    @Test
    fun deferredProductsAreSortedByScore() {
        val x = product("x", penetration = 0.02)
        val y = product("y", penetration = 0.08)
        val plan = planner.plan(input(listOf(x, y, caviar)))
        assertEquals(listOf("prod-y", "prod-caviar", "prod-x"), plan.deferred.map { it.value })
    }

    @Test
    fun unknownCategoriesAreNeverGated() {
        val orphan = product("orphelin", penetration = 0.2, category = Fixtures.category("inconnue", 99))
        val plan = planner.plan(input(listOf(orphan)))
        assertTrue(plan.questions.isEmpty())
        assertEquals(listOf(orphan.id), plan.deferred)
        // A card in an unknown category is still asked.
        val card = product("carte", penetration = 0.5, category = Fixtures.category("inconnue", 99))
        assertEquals(listOf("carte"), planner.plan(input(listOf(card))).questions.map { it.label })
    }

    @Test
    fun theDietAndAvoidancesExcludeTheirTags() {
        val ham = product("jambon", tags = setOf(TagId("contains-pork"), DietaryTags.MEAT))
        val beer = product("biere", tags = setOf(DietaryTags.ALCOHOL, DietaryTags.GLUTEN))
        val salmon = product("saumon", tags = setOf(DietaryTags.FISH))
        val cheese = product("fromage", tags = setOf(DietaryTags.LACTOSE))
        val rice = product("riz", tags = setOf(TagId("vegan")))
        val products = listOf(ham, beer, salmon, cheese, rice)
        fun asked(diet: Diet, avoid: Set<Avoidance> = emptySet()): List<String> {
            val plan = planner.plan(input(products, household = household().copy(diet = diet, avoid = avoid)))
            return (plan.askedProducts.map { it.product.slug } + plan.deferred.map { it.value.removePrefix("prod-") }).sorted()
        }

        assertEquals(listOf("fromage", "jambon", "riz", "saumon"), asked(Diet.OMNIVORE, setOf(Avoidance.ALCOHOL)))
        assertEquals(listOf("biere", "fromage", "riz", "saumon"), asked(Diet.PESCATARIAN))
        assertEquals(listOf("biere", "fromage", "riz"), asked(Diet.VEGETARIAN))
        assertEquals(listOf("biere", "riz"), asked(Diet.VEGAN))
        assertEquals(listOf("jambon", "riz", "saumon"), asked(Diet.OMNIVORE, setOf(Avoidance.GLUTEN, Avoidance.LACTOSE)))
    }

    @Test
    fun babyAndPetProductsNeedABabyOrAPet() {
        val diapers = product("couches", tags = setOf(DietaryTags.BABY))
        val kibble = product("croquettes", tags = setOf(DietaryTags.PET))
        val products = listOf(diapers, kibble, milk)
        fun asked(household: HouseholdProfile, stats: Map<Product, ProductStats> = emptyMap()) =
            planner.score(input(products, household = household, stats = stats)).map { it.product.slug }.sorted()

        assertEquals(listOf("lait"), asked(household()))
        assertEquals(listOf("couches", "lait"), asked(household().copy(children = listOf(AgeBand.BABY))))
        assertEquals(listOf("croquettes", "lait"), asked(household().copy(pets = listOf(Pet(PetKind.CAT)))))
        // Bought before, or declared at onboarding: still asked.
        assertEquals(listOf("couches", "croquettes", "lait"), asked(household(), mapOf(diapers to boughtDaysAgo(3), kibble to ProductStats(userCadenceDays = 30))))
    }

    @Test
    fun essentialsAndCadencesMakeANonCandidateEligible() {
        val baguette = product("baguette", penetration = 0.1, candidate = false, isStaple = false, repurchaseDays = 7)
        val stats = mapOf(baguette to ProductStats(userCadenceDays = 7))

        assertTrue(planner.score(input(listOf(baguette))).isEmpty())
        val plan = planner.plan(input(listOf(baguette), stats = stats))
        assertEquals(listOf("baguette"), plan.suggested.slugs)
        assertEquals(ReasonCode.Cadence(7), plan.suggested.single().reasons.first())
    }

    @Test
    fun cardsAreCappedPerCategory() {
        val dairy = (1..6).map { product("d$it", penetration = 0.7 - it * 0.01) }
        val drink = product("eau", penetration = 0.4, category = DRINKS)
        val capped = QuestionPlanner(Fixtures.BY_SCORE.copy(maxCardsPerCategory = 3))

        val plan = capped.plan(input(dairy + drink, context(budget = 12)))

        val cards = plan.questions.filterIsInstance<PlannedQuestion.Card>().map { it.item.product.slug }
        assertEquals(listOf("d1", "d2", "d3", "eau"), cards)
        assertEquals(listOf("d4", "d5", "d6"), plan.gates().single().items.slugs)
    }

    @Test
    fun theSeedRotatesProductsOfCloseScoresWithoutChangingTiers() {
        val products = (1..12).map { product("p$it", penetration = 0.5) }
        val rotating = QuestionPlanner(PlannerConfig(maxCardsPerCategory = null))
        fun cards(seed: Long) = rotating.plan(input(products, context(budget = 6)).copy(seed = seed))
            .questions.filterIsInstance<PlannedQuestion.Card>().map { it.item.product.slug }

        val first = cards(1L)
        assertEquals(first, cards(1L))
        assertTrue((2L..20L).any { cards(it).toSet() != first.toSet() }, "the same cards whatever the seed")
        val tiers = rotating.score(input(products)).map { it.tier }.toSet()
        assertEquals(setOf(Tier.T2), tiers)
    }

    @Test
    fun rotationKeysAreStable() {
        // Pinned values: the same on Android, iOS and the JVM, and across releases (resumed sessions).
        assertEquals(996580060897260808L, rotationKey(0L, "abc"))
        assertEquals(7080716837051220213L, rotationKey(42L, "prod-lait"))
        assertTrue(rotationKey(0L, "abc") != rotationKey(1L, "abc"))
        assertTrue(rotationKey(0L, "abc") != rotationKey(0L, "abd"))
    }
}
