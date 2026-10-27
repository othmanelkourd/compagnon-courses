package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.DietaryTags
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerPropertiesTest {

    @Test
    fun theBudgetIsNeverExceeded() = runTest {
        checkAll(ITERATIONS, Worlds.world) { world ->
            val plan = QuestionPlanner(world.config).plan(world.input)
            val budget = world.budget
            if (budget != null) assertTrue(plan.cost <= budget, "cost ${plan.cost} > budget $budget")
            assertEquals(budget, plan.budget)
            assertTrue(plan.meta.size <= world.config.maxMetaQuestions)
        }
    }

    @Test
    fun theT1ScreenIsCappedAndCardsComeFirst() = runTest {
        checkAll(ITERATIONS, Worlds.world) { world ->
            val plan = QuestionPlanner(world.config).plan(world.input)
            val reused = plan.suggested.filter { it.reasons.firstOrNull() == ReasonCode.LastList }
            val cap = maxOf(world.config.maxSuggested, reused.size)
            assertTrue(plan.suggested.size <= cap, "${plan.suggested.size} items on the screen")
            // A T2 product without its own card (on the screen unchecked, or behind a gate) is never likelier
            // than a standard card: cards are served first, by decreasing p (by bands of p when the session
            // seed rotates close products), unless its category already has its maximum of cards.
            val cards = plan.questions.filterIsInstance<PlannedQuestion.Card>()
                .filter { it.kind == CardKind.STANDARD || it.kind == CardKind.WATCH }
            val weakest = cards.filter { it.kind == CardKind.STANDARD }.minOfOrNull { it.item.score } ?: return@checkAll
            val perCategory = cards.groupingBy { it.item.product.categoryId }.eachCount()
            val band = world.config.priorityBand
            fun rank(score: Double): Double = if (band > 0.0) kotlin.math.floor(score / band) else score
            val gated = plan.questions.filterIsInstance<PlannedQuestion.Gate>().flatMap { it.items }
            val uncarded = (plan.suggested - reused.toSet() + gated).filter { it.tier == Tier.T2 }
            val cardCap = world.config.maxCardsPerCategory
            uncarded
                .filter { cardCap == null || (perCategory[it.product.categoryId] ?: 0) < cardCap }
                .forEach { assertTrue(rank(it.score) <= rank(weakest), "${it.product.slug} (${it.score}) > card $weakest") }
        }
    }

    @Test
    fun noExcludedProductIsEverAsked() = runTest {
        checkAll(ITERATIONS, Worlds.world) { world ->
            val input = world.input
            val plan = QuestionPlanner(world.config).plan(input)
            val household = input.household
            val excluded = household.effectiveExcludedTags
            for (item in plan.askedProducts) {
                val stats = input.stats[item.productId]
                assertTrue(item.product.tags.none { it in excluded }, "${item.product.slug} has an excluded tag")
                val known = (stats?.nPurchases ?: 0) > 0 || stats?.userCadenceDays != null ||
                    (household.customEssentials && stats?.essential == true)
                if (!known && DietaryTags.BABY in item.product.tags) assertTrue(household.hasBaby, "baby product")
                if (!known && DietaryTags.PET in item.product.tags) assertTrue(household.hasPet, "pet product")
                assertTrue(stats?.neverAsk != true, "${item.product.slug} is never-ask")
                assertTrue(stats?.snoozeUntil.let { it == null || it <= input.now }, "${item.product.slug} is snoozed")
                assertTrue(item.product.market.availability != Availability.UNAVAILABLE)
                if (input.context.mode == SessionMode.ZONE) assertEquals(input.context.zone, item.product.storageZone)
            }
            assertTrue(plan.deferred.none { id -> input.products.first { it.id == id }.tags.any { it in excluded } })
        }
    }

    @Test
    fun tiersNeverDependOnTheSeed() = runTest {
        checkAll(ITERATIONS, Worlds.world, Arb.long()) { world, otherSeed ->
            val planner = QuestionPlanner(world.config)
            val tiers = planner.score(world.input).associate { it.product.id to it.tier }
            assertEquals(tiers, planner.score(world.input.copy(seed = otherSeed)).associate { it.product.id to it.tier })
        }
    }

    @Test
    fun planningIsDeterministicForAFixedSeedAndClock() = runTest {
        checkAll(ITERATIONS, Worlds.world, Arb.long()) { world, shuffleSeed ->
            val planner = QuestionPlanner(world.config)
            val plan = planner.plan(world.input)
            assertEquals(plan, planner.plan(world.input))
            assertEquals(plan, planner.plan(Worlds.shuffled(world.input, Random(shuffleSeed))))
        }
    }

    @Test
    fun eachProductIsAskedAtMostOnceWithAValidQuantity() = runTest {
        checkAll(ITERATIONS, Worlds.world) { world ->
            val plan = QuestionPlanner(world.config).plan(world.input)
            val asked = plan.askedProducts.map { it.productId }
            assertEquals(asked.size, asked.toSet().size, "a product is asked twice")
            assertTrue(asked.none { it in plan.deferred.toSet() }, "a product is both asked and deferred")
            for (item in plan.askedProducts) {
                val proposal = item.proposal
                assertTrue(proposal.policy.isAllowed(proposal.quantity), "${item.product.slug}: ${proposal.quantity}")
                assertTrue(proposal.choices.all { proposal.policy.isAllowed(it) })
                assertEquals(world.config.tierOf(item.score), item.tier)
            }
        }
    }

    private companion object {
        const val ITERATIONS: Int = 500
    }
}
