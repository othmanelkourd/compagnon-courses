package com.compagnon.courses.core.data

import com.compagnon.courses.engine.PlannedQuestion
import com.compagnon.courses.engine.QuestionPlan
import com.compagnon.courses.engine.QuestionPlanner
import com.compagnon.courses.engine.SessionMode
import com.compagnon.courses.engine.Tier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The budget allocation on the real Sprint 0 seed (195 products, FR), cold start, default household
 * (2 adults, no history): `docs/question-engine.md` §6.3 steps 4 and 5. Cards come before gates, so a
 * quick session asks about products and not only about aisles.
 */
class SeedPlanningTest {
    private val planner = QuestionPlanner()

    @Test
    fun aColdQuickSessionOnTheSeedAsksProductCards() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 42))
        val input = setup.input

        val plan = planner.plan(input)
        println("seed quick cold start: ${plan.describe()}")

        val budget = assertNotNull(plan.budget)
        assertEquals(12, budget)
        assertTrue(plan.cost <= budget, "cost ${plan.cost} > budget $budget")
        assertTrue(plan.meta.size <= 3)
        assertTrue(plan.suggested.isNotEmpty(), "a cold start has staples to suggest")
        assertTrue(plan.suggested.size <= 15, "T1 screen of ${plan.suggested.size} items")
        assertTrue(plan.cards.size >= 4, "only ${plan.cards.size} cards: ${plan.describe()}")
        assertTrue(plan.cards.all { it.item.tier == Tier.T2 || it.item.tier == Tier.T1 })

        val asked = plan.askedProducts.map { it.productId }
        assertEquals(asked.size, asked.toSet().size, "a product is asked twice")
        assertTrue(asked.none { it in plan.deferred.toSet() }, "a product is both asked and deferred")
        val excluded = input.household.excludedTags
        assertTrue(plan.askedProducts.none { item -> item.product.tags.any { it in excluded } })

        // Cards are the likeliest T2 products: no T2 product behind a gate is likelier than a card. (T1 products
        // beyond the screen cap do fold into gates, by design.)
        val lowestCard = plan.cards.minOf { it.item.score }
        plan.gates.flatMap { it.items }.filter { it.tier == Tier.T2 }
            .forEach { assertTrue(it.score <= lowestCard, "${it.product.slug} ${it.score} > $lowestCard") }

        // The same plan with known meta answers frees their slots for more cards.
        val known = planner.plan(input.copy(context = input.context.copy(metaQuestions = emptyList())))
        println("seed quick, meta known: ${known.describe()}")
        assertTrue(known.cards.size > plan.cards.size || known.gates.size > plan.gates.size)
        assertTrue(known.cost <= budget)
    }

    @Test
    fun aColdQuickSessionNeverAsksAboutExcludedProducts() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 42))
        val tags = setup.input.products.flatMap { it.tags }.toSet()
        tags.forEach { tag ->
            val household = setup.input.household.copy(excludedTags = setOf(tag))
            val plan = planner.plan(setup.input.copy(household = household))
            assertTrue(plan.askedProducts.none { tag in it.product.tags }, "$tag asked")
            assertTrue(plan.cost <= 12)
            assertTrue(plan.suggested.size <= 15)
        }
    }
}

private val QuestionPlan.cards: List<PlannedQuestion.Card> get() = questions.filterIsInstance<PlannedQuestion.Card>()

private val QuestionPlan.gates: List<PlannedQuestion.Gate> get() = questions.filterIsInstance<PlannedQuestion.Gate>()

private fun QuestionPlan.describe(): String =
    "meta=${meta.size} t1=${suggested.size} cards=${cards.size} gates=${gates.size} " +
        "(gate items ${gates.sumOf { it.items.size }}) deferred=${deferred.size} cost=$cost; " +
        "cards ${cards.map { it.item.product.slug }}, gates ${gates.map { it.category.slug + "/" + it.items.size }}"
