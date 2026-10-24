package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.PackageFormat
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.engine.Fixtures.BOTTLE
import com.compagnon.courses.engine.Fixtures.KILOGRAM
import com.compagnon.courses.engine.Fixtures.LITER
import com.compagnon.courses.engine.Fixtures.PIECE
import com.compagnon.courses.engine.Fixtures.UNITS
import com.compagnon.courses.engine.Fixtures.policy
import com.compagnon.courses.engine.Fixtures.product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuantityAdvisorTest {
    private val advisor = QuantityAdvisor(UNITS)

    private val milk = product(
        "lait",
        policies = listOf(
            policy(BOTTLE, step = "1", presets = listOf("1", "2", "6")),
            policy(LITER, step = "0.5", presets = listOf("1", "2"), min = "0.5", max = "12", isDefault = false),
        ),
        packages = listOf(PackageFormat(BOTTLE, Quantity(1000, LITER), typical = true)),
    )
    private val apples = product(
        "pommes",
        dimension = Dimension.MASS,
        policies = listOf(
            policy(KILOGRAM, step = "0.5", presets = listOf("0.5", "1", "2"), min = "0.5", max = "5"),
            policy(PIECE, step = "1", presets = listOf("4", "6"), isDefault = false),
        ),
    )
    private val eggs = product(
        "oeufs",
        dimension = Dimension.COUNT,
        policies = listOf(policy(PIECE, step = "1", presets = listOf("6", "12"))),
    )

    private fun q(text: String, unit: UnitCode) = Quantity.parse(text, unit)

    private fun scaling(horizon: Int = 7, usual: Int = 7, persons: Int = 2, household: Int = 2) =
        QuantityScaling(horizon, usual, persons, household)

    @Test
    fun theUsualQuantityComesFirst() {
        val proposal = advisor.propose(milk, ProductStats(usualQty = q("1.5", LITER)), scaling())!!
        assertEquals(LITER, proposal.policy.unit)
        assertEquals(q("1.5", LITER), proposal.quantity)
        assertEquals(QuantitySource.USUAL, proposal.source)
        assertTrue(proposal.isSameAsLastTime)
        assertEquals(listOf(BOTTLE), proposal.alternatives.map { it.unit })
    }

    @Test
    fun thenTheSmoothedHistoryConvertedToTheDefaultUnit() {
        // 2 L on average → 2 bottles of 1 L.
        val proposal = advisor.propose(milk, ProductStats(ewmaQtyBase = 2000.0), scaling())!!
        assertEquals(q("2", BOTTLE), proposal.quantity)
        assertEquals(QuantitySource.HISTORY_AVERAGE, proposal.source)
        assertFalse(proposal.isSameAsLastTime)
        // A usual quantity in a unit without policy is ignored.
        val other = advisor.propose(milk, ProductStats(usualQty = q("1", KILOGRAM), ewmaQtyBase = 3000.0), scaling())!!
        assertEquals(q("3", BOTTLE), other.quantity)
    }

    @Test
    fun thenTheFirstPresetThenTheStep() {
        val preset = advisor.propose(milk, null)!!
        assertEquals(q("1", BOTTLE) to QuantitySource.PRESET, preset.quantity to preset.source)
        val noPreset = product("sel", policies = listOf(policy(KILOGRAM, step = "0.25", presets = emptyList())))
        val step = advisor.propose(noPreset, null)!!
        assertEquals(q("0.25", KILOGRAM) to QuantitySource.STEP, step.quantity to step.source)
        assertNull(advisor.propose(product("vide", policies = emptyList()), null))
    }

    @Test
    fun scalesWithTheHorizonAndThePersons() {
        // 1.5 L × (14 / 7) × (3 / 2) = 4.5 L.
        val usual = ProductStats(usualQty = q("1.5", LITER))
        val proposal = advisor.propose(milk, usual, scaling(horizon = 14, persons = 3))!!
        assertEquals(q("4.5", LITER), proposal.quantity)
        assertFalse(proposal.isSameAsLastTime)
    }

    @Test
    fun roundsUpForCountsAndPackages() {
        // 6 eggs × 10 / 7 = 8.57 → 9.
        val sixEggs = ProductStats(usualQty = q("6", PIECE))
        assertEquals(q("9", PIECE), advisor.propose(eggs, sixEggs, scaling(horizon = 10))!!.quantity)
        // 1 bottle × 3 / 2 = 1.5 → 2.
        assertEquals(q("2", BOTTLE), advisor.propose(milk, null, scaling(persons = 3))!!.quantity)
        // Exact values are not rounded up.
        assertEquals(q("2", BOTTLE), advisor.propose(milk, null, scaling(persons = 4))!!.quantity)
    }

    @Test
    fun roundsToTheNearestStepForMassesAndVolumes() {
        fun apples(usual: String, persons: Int, household: Int) =
            advisor.propose(
                apples,
                ProductStats(usualQty = q(usual, KILOGRAM)),
                scaling(persons = persons, household = household),
            )!!.quantity
        assertEquals(q("1.5", KILOGRAM), apples("1.3", persons = 1, household = 1)) // 2.6 steps → 3
        assertEquals(q("1", KILOGRAM), apples("1.2", persons = 1, household = 1)) // 2.4 steps → 2
        assertEquals(q("1.5", KILOGRAM), apples("1.25", persons = 1, household = 1)) // tie → up
        assertEquals(q("1", KILOGRAM), apples("1.5", persons = 2, household = 3)) // 1.0 exactly
    }

    @Test
    fun clampsToTheBounds() {
        val twoKilos = ProductStats(usualQty = q("2", KILOGRAM))
        val big = advisor.propose(apples, twoKilos, scaling(horizon = 30, persons = 6))!!
        assertEquals(q("5", KILOGRAM), big.quantity)
        val tiny = advisor.propose(apples, ProductStats(usualQty = q("0.5", KILOGRAM)), scaling(horizon = 1))!!
        assertEquals(q("0.5", KILOGRAM), tiny.quantity)
        // Saturates instead of overflowing.
        val absurd = ProductStats(usualQty = Quantity(Long.MAX_VALUE / 2, PIECE))
        val huge = advisor.propose(eggs, absurd, scaling(horizon = 90, persons = 30, household = 1))!!
        assertEquals(0L, huge.quantity.milli % 1000)
        assertTrue(huge.quantity.milli > 0)
    }

    @Test
    fun offersThreeChoicesAroundTheProposal() {
        fun choices(quantity: String) =
            advisor.proposeExact(milk, q(quantity, BOTTLE))!!.choices.map { it.toDecimalString() }
        assertEquals(listOf("1", "2", "6"), choices("2"))
        assertEquals(listOf("1", "2", "6"), choices("1")) // nothing below 1: extend above
        assertEquals(listOf("2", "3", "6"), choices("3"))
        assertEquals(listOf("2", "6", "7"), choices("6")) // no max: one step above the last preset
        assertEquals(listOf("6", "7", "8"), choices("7")) // beyond the presets: steps
        assertEquals(
            listOf("6", "9", "12"),
            advisor.proposeExact(eggs, q("9", PIECE))!!.choices.map { it.toDecimalString() },
        )

        fun appleChoices(quantity: String) =
            advisor.proposeExact(apples, q(quantity, KILOGRAM))!!.choices.map { it.toDecimalString() }
        assertEquals(listOf("4", "4.5", "5"), appleChoices("5")) // max reached: extend below
        assertEquals(listOf("0.5", "1", "2"), appleChoices("0.5"))
    }

    @Test
    fun exactProposalsKeepTheQuantityFittedToItsPolicy() {
        val proposal = advisor.proposeExact(apples, q("1.3", KILOGRAM))!!
        assertEquals(q("1.5", KILOGRAM), proposal.quantity)
        assertEquals(QuantitySource.LAST_LIST, proposal.source)
        assertEquals(q("6", PIECE), advisor.proposeExact(apples, q("6", PIECE))!!.quantity)
        assertNull(advisor.proposeExact(apples, q("1", LITER)))
    }
}
