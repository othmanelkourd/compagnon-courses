package com.compagnon.courses.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ExactMathTest {

    @Test
    fun addAndMultiplyDetectOverflow() {
        assertEquals(Long.MAX_VALUE, ExactMath.addExact(Long.MAX_VALUE - 1, 1))
        assertFailsWith<ArithmeticException> { ExactMath.addExact(Long.MAX_VALUE, 1) }
        assertEquals(6_000_000_000L, ExactMath.multiplyExact(3_000_000_000L, 2))
        assertFailsWith<ArithmeticException> { ExactMath.multiplyExact(Long.MAX_VALUE / 2 + 1, 2) }
        assertFailsWith<ArithmeticException> { ExactMath.multiplyExact(Long.MIN_VALUE, -1) }
    }

    @Test
    fun multiplyDivideUsesA128BitIntermediate() {
        // MAX × MAX overflows 64 bits, but dividing by MAX brings it back.
        val max = Long.MAX_VALUE
        assertEquals(max, ExactMath.multiplyDivide(max, max, max, Rounding.DOWN))
        val big = 4_000_000_000_000L
        assertEquals(3_000_000_000_000L, ExactMath.multiplyDivide(3_000_000_000_000L, big, big, Rounding.DOWN))
        assertFailsWith<ArithmeticException> { ExactMath.multiplyDivide(Long.MAX_VALUE, 2, 1, Rounding.DOWN) }
        assertFailsWith<ArithmeticException> { ExactMath.multiplyDivide(Long.MAX_VALUE, 3, 2, Rounding.DOWN) }
        assertFailsWith<IllegalArgumentException> { ExactMath.multiplyDivide(-1, 1, 1, Rounding.DOWN) }
        assertFailsWith<IllegalArgumentException> { ExactMath.multiplyDivide(1, 1, 0, Rounding.DOWN) }
    }

    @Test
    fun roundingModes() {
        // 5 / 2 = 2.5, 7 / 2 = 3.5, 7 / 3 = 2.33…, 8 / 3 = 2.66…
        assertEquals(2, ExactMath.multiplyDivide(5, 1, 2, Rounding.DOWN))
        assertEquals(3, ExactMath.multiplyDivide(5, 1, 2, Rounding.UP))
        assertEquals(3, ExactMath.multiplyDivide(5, 1, 2, Rounding.HALF_UP))
        assertEquals(2, ExactMath.multiplyDivide(5, 1, 2, Rounding.HALF_EVEN))
        assertEquals(4, ExactMath.multiplyDivide(7, 1, 2, Rounding.HALF_EVEN))
        assertEquals(2, ExactMath.multiplyDivide(7, 1, 3, Rounding.HALF_EVEN))
        assertEquals(3, ExactMath.multiplyDivide(8, 1, 3, Rounding.HALF_EVEN))
        assertEquals(3, ExactMath.multiplyDivide(7, 1, 3, Rounding.UP))
        assertEquals(4, ExactMath.multiplyDivide(4, 1, 1, Rounding.UP))
        assertEquals(0, ExactMath.multiplyDivide(0, 5, 3, Rounding.UP))
    }

    @Test
    fun roundingUpAtTheLimitOverflows() {
        assertEquals(Long.MAX_VALUE, ExactMath.multiplyDivide(Long.MAX_VALUE, 2, 2, Rounding.UP))
        // (2^32 − 1)(2^32 + 1) = 2^64 − 1 = 2 × MAX + 1, so the exact quotient by 2 is MAX + 0.5.
        val a = 4_294_967_295L
        val b = 4_294_967_297L
        assertEquals(Long.MAX_VALUE, ExactMath.multiplyDivide(a, b, 2, Rounding.DOWN))
        assertFailsWith<ArithmeticException> { ExactMath.multiplyDivide(a, b, 2, Rounding.UP) }
        assertFailsWith<ArithmeticException> { ExactMath.multiplyDivide(a, b, 2, Rounding.HALF_UP) }
        assertFailsWith<ArithmeticException> { ExactMath.multiplyDivide(a, b, 2, Rounding.HALF_EVEN) } // MAX is odd
    }

    @Test
    fun compareProductsIsExact() {
        assertEquals(0, ExactMath.compareProducts(Long.MAX_VALUE, 2, 2, Long.MAX_VALUE))
        assertEquals(1, ExactMath.compareProducts(Long.MAX_VALUE, 3, 2, Long.MAX_VALUE).coerceIn(-1, 1))
        assertEquals(-1, ExactMath.compareProducts(1, 1, 1, 2).coerceIn(-1, 1))
    }

    @Test
    fun parseDecimalAcceptsOnlyCanonicalDecimals() {
        assertEquals(15L, parseDecimal("1.50")?.unscaled)
        assertEquals(1, parseDecimal("1.50")?.scale)
        assertEquals(2L, parseDecimal("2")?.unscaled)
        assertEquals(0, parseDecimal("2.000")?.scale)
        listOf("", ".5", "1.", "-1", "+1", "1,5", "1e3", " 1", "1 ", "abc", "1.2.3", "99999999999999999999").forEach {
            assertNull(parseDecimal(it), "\"$it\" should be rejected")
        }
    }
}
