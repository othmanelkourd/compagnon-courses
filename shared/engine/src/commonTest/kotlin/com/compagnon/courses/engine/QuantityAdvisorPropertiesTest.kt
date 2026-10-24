package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.Rational
import com.compagnon.courses.engine.Fixtures.BOTTLE
import com.compagnon.courses.engine.Fixtures.KILOGRAM
import com.compagnon.courses.engine.Fixtures.LITER
import com.compagnon.courses.engine.Fixtures.PIECE
import com.compagnon.courses.engine.Fixtures.UNITS
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuantityAdvisorPropertiesTest {
    private val advisor = QuantityAdvisor(UNITS)

    private val unit = Arb.element(listOf(KILOGRAM, LITER, PIECE, BOTTLE))
    private val scaling = arbitrary {
        QuantityScaling(Arb.int(1..90).bind(), Arb.int(1..90).bind(), Arb.int(1..30).bind(), Arb.int(1..30).bind())
    }
    private val case = arbitrary {
        val policy = Worlds.policy(unit.bind(), isDefault = true).bind()
        val dimension = Arb.element(Dimension.entries).bind()
        val product = Fixtures.product("x", policies = listOf(policy), dimension = dimension)
        val stats = ProductStats(
            usualQty = if (Arb.boolean().bind()) Quantity(Arb.long(1L..10_000_000L).bind(), policy.unit) else null,
            ewmaQtyBase = Arb.double(1.0, 100_000.0, false).orNull(0.5).bind(),
        )
        Triple(product, stats, scaling.bind())
    }

    @Test
    fun proposalsAreMultiplesOfTheStepWithinTheBounds() = runTest {
        checkAll(3_000, case) { (product, stats, scaling) ->
            val proposal = advisor.propose(product, stats, scaling)!!
            val policy = proposal.policy
            val quantity = proposal.quantity
            assertEquals(0L, quantity.milli % policy.step.milli, "$quantity is not a multiple of ${policy.step}")
            assertTrue(quantity.milli > 0)
            policy.min?.let { assertTrue(quantity >= it, "$quantity < min $it") }
            policy.max?.let { assertTrue(quantity <= it, "$quantity > max $it") }

            val choices = proposal.choices
            assertTrue(quantity in choices)
            assertTrue(choices.size in 1..3)
            assertEquals(choices.sorted().distinct(), choices)
            assertTrue(choices.all { policy.isAllowed(it) }, "invalid choices $choices for $policy")
        }
    }

    @Test
    fun roundingGoesUpForCountsAndToTheNearestForMasses() = runTest {
        checkAll(3_000, case, Arb.long(1L..10_000_000L)) { (product, _, scaling), baseMilli ->
            val policy = product.defaultPolicy!!
            val base = Quantity(baseMilli, policy.unit)
            val steps = advisor.fit(product, policy, base, scaling.factor).milli / policy.step.milli
            val exact = scaling.factor * Rational.of(base.milli, policy.step.milli)
            val clamped = steps == policy.minSteps || steps == policy.maxSteps
            if (!clamped) {
                when (UNITS[policy.unit]!!.dimension) {
                    Dimension.COUNT, Dimension.PACKAGE -> {
                        // steps - 1 < exact ≤ steps
                        val roundedUp = Rational.of(steps - 1) < exact && exact <= Rational.of(steps)
                        assertTrue(roundedUp, "$steps steps for $exact")
                    }
                    Dimension.MASS, Dimension.VOLUME -> {
                        // steps - 0.5 ≤ exact < steps + 0.5
                        val nearest = Rational.of(2 * steps - 1, 2) <= exact && exact < Rational.of(2 * steps + 1, 2)
                        assertTrue(nearest, "$steps steps for $exact")
                    }
                }
            }
        }
    }
}
