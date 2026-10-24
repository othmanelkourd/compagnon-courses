package com.compagnon.courses.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RationalTest {

    @Test
    fun isAlwaysInLowestTerms() {
        val half = Rational.of(2, 4)
        assertEquals(1L, half.numerator)
        assertEquals(2L, half.denominator)
        assertEquals(Rational.of(1, 2), half)
        assertEquals(Rational.of(1, 2).hashCode(), half.hashCode())
        assertEquals(Rational.ZERO, Rational.of(0, 7))
    }

    @Test
    fun parsesDecimalFactors() {
        // 453.59237 = 45359237 / 100000 (already irreducible).
        val pound = Rational.parse("453.59237")
        assertEquals(45_359_237L, pound.numerator)
        assertEquals(100_000L, pound.denominator)
        // 28.349523125 = 5^4 × 45359237 / 10^9 = 45359237 / 1600000: one ounce is a sixteenth of a pound.
        assertEquals(Rational.of(45_359_237, 1_600_000), Rational.parse("28.349523125"))
        assertEquals(Rational.of(16), Rational.parse("453.59237") / Rational.parse("28.349523125"))
        assertEquals(Rational.of(1000), Rational.parse("1000.000"))
        assertNull(Rational.parseOrNull("-1"))
        assertNull(Rational.parseOrNull("1/2"))
        assertFailsWith<IllegalArgumentException> { Rational.parse("abc") }
    }

    @Test
    fun timesAndDivCrossReduce() {
        val gallon = Rational.parse("3785.411784")
        val fluidOunce = Rational.parse("29.5735295625")
        assertEquals(Rational.of(128), gallon / fluidOunce)
        assertEquals(Rational.of(3, 4), Rational.of(1, 2) * Rational.of(3, 2))
        assertEquals(Rational.ZERO, Rational.ZERO * gallon)
        assertFailsWith<ArithmeticException> { gallon / Rational.ZERO }
        assertFailsWith<ArithmeticException> { Rational.of(Long.MAX_VALUE, 1) * Rational.of(2, 1) }
    }

    @Test
    fun multipliesIntegersWithRounding() {
        val third = Rational.of(1, 3)
        assertEquals(333L, third.multiply(1000))
        assertEquals(334L, third.multiply(1000, Rounding.UP))
        assertEquals(2L, Rational.of(1, 2).multiply(5, Rounding.HALF_EVEN))
        assertEquals(3L, Rational.of(1, 2).multiply(5, Rounding.HALF_UP))
    }

    @Test
    fun comparesExactly() {
        assertTrue(Rational.of(1, 3) < Rational.of(334, 1000))
        assertTrue(Rational.of(Long.MAX_VALUE, Long.MAX_VALUE - 1) > Rational.ONE)
        assertEquals(0, Rational.of(2, 6).compareTo(Rational.of(1, 3)))
    }

    @Test
    fun rejectsInvalidFractions() {
        assertFailsWith<IllegalArgumentException> { Rational.of(-1, 2) }
        assertFailsWith<IllegalArgumentException> { Rational.of(1, 0) }
    }
}
