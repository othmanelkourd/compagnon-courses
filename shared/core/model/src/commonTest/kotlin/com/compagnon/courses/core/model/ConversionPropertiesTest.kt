package com.compagnon.courses.core.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversionPropertiesTest {
    private val units = TestUnits.catalog
    private val convertible: List<Pair<UnitDefinition, UnitDefinition>> = units.units.filter { it.toBase != null }
        .let { defs -> defs.flatMap { a -> defs.filter { it.dimension == a.dimension }.map { b -> a to b } } }

    /** Pairs of synthetic units with arbitrary exact factors (numerator and denominator up to 10⁴). */
    private val factor = arbitrary { Rational.of(Arb.long(1L..10_000L).bind(), Arb.long(1L..10_000L).bind()) }
    private val syntheticPair = arbitrary {
        val a = UnitDefinition(UnitCode("a"), Dimension.MASS, factor.bind())
        a to UnitDefinition(UnitCode("b"), Dimension.MASS, factor.bind())
    }

    @Test
    fun roundTripErrorIsAtMostOneMilliOfTheCoarserUnit() = runTest {
        checkAll(3_000, Arb.element(convertible), Arb.long(0L..1_000_000_000_000L)) { (a, b), milli ->
            assertRoundTrip(units, a, b, milli)
        }
    }

    @Test
    fun roundTripHoldsForArbitraryExactFactors() = runTest {
        checkAll(3_000, syntheticPair, Arb.long(0L..10_000_000_000L)) { (a, b), milli ->
            assertRoundTrip(UnitCatalog(listOf(a, b)), a, b, milli)
        }
    }

    @Test
    fun goingThroughAFinerUnitIsLossless() = runTest {
        val finer = convertible.filter { (a, b) -> b.toBase!! <= a.toBase!! }
        checkAll(2_000, Arb.element(finer), Arb.long(0L..1_000_000_000_000L)) { (a, b), milli ->
            val quantity = Quantity(milli, a.code)
            assertEquals(quantity, units.convert(units.convert(quantity, b.code), a.code))
        }
    }

    @Test
    fun decimalFormIsCanonical() = runTest {
        checkAll(3_000, Arb.long(0L..Long.MAX_VALUE)) { milli ->
            val quantity = Quantity(milli, UnitCode.LITER)
            assertEquals(quantity, Quantity.parse(quantity.toDecimalString(), UnitCode.LITER))
        }
    }

    /** |back − q| expressed in milli of the coarser unit is ≤ 1, i.e. |back − q| × f(a) ≤ max(f(a), f(b)). */
    private fun assertRoundTrip(catalog: UnitCatalog, a: UnitDefinition, b: UnitDefinition, milli: Long) {
        val quantity = Quantity(milli, a.code)
        val there = catalog.convert(quantity, b.code)
        val back = catalog.convert(there, a.code)
        val error = Rational.of(abs(back.milli - milli)) * a.toBase!!
        val coarser = maxOf(a.toBase, b.toBase!!)
        assertTrue(error <= coarser, "$quantity → $there → $back")
    }
}
