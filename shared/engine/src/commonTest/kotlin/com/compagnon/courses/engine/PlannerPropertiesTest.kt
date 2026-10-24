package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Availability
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
    fun noExcludedProductIsEverAsked() = runTest {
        checkAll(ITERATIONS, Worlds.world) { world ->
            val input = world.input
            val plan = QuestionPlanner(world.config).plan(input)
            val excluded = input.household.excludedTags
            for (item in plan.askedProducts) {
                val stats = input.stats[item.productId]
                assertTrue(item.product.tags.none { it in excluded }, "${item.product.slug} has an excluded tag")
                assertTrue(stats?.neverAsk != true, "${item.product.slug} is never-ask")
                assertTrue(stats?.snoozeUntil.let { it == null || it <= input.now }, "${item.product.slug} is snoozed")
                assertTrue(item.product.market.availability != Availability.UNAVAILABLE)
                if (input.context.mode == SessionMode.ZONE) assertEquals(input.context.zone, item.product.storageZone)
            }
            assertTrue(plan.deferred.none { id -> input.products.first { it.id == id }.tags.any { it in excluded } })
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
