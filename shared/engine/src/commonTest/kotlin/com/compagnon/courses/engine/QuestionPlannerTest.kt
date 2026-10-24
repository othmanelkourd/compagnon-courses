package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
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
    private val planner = QuestionPlanner()

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

        // 1 + 4 cards = 5 > 3: b2 falls into the drinks gate, which is then the least valuable unit and goes;
        // b1 (p > 0.6) joins the T1 screen, unchecked.
        assertEquals(listOf("lait", "b1"), plan.suggested.slugs)
        assertEquals(listOf(true, false), plan.suggested.map { it.preChecked })
        assertEquals(listOf("a1", "a2"), plan.questions.map { it.label })
        assertEquals(listOf(b2.id), plan.deferred)
        assertEquals(3, plan.cost)
    }

    @Test
    fun gatesAbsorbTheLeastLikelyCards() {
        val a1 = product("a1", penetration = 0.7)
        val a2 = product("a2", penetration = 0.5)
        val b1 = product("b1", penetration = 0.35, category = CLEANING)
        val b3 = listOf(0.28, 0.25, 0.2).mapIndexed { i, p -> product("b-t3-$i", penetration = p, category = CLEANING) }
        val plan = planner.plan(input(listOf(a1, a2, b1) + b3, context = context(budget = 3)))

        assertEquals(listOf("a1", "a2", "gate:entretien"), plan.questions.map { it.label })
        assertEquals(listOf("b1", "b-t3-0", "b-t3-1", "b-t3-2"), plan.gates().single().items.slugs)
        assertTrue(plan.suggested.isEmpty())
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
        val noRadar = QuestionPlanner(PlannerConfig(radarSlots = 0))
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
        val planner = QuestionPlanner(PlannerConfig(explorationSlots = 1))
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
}
