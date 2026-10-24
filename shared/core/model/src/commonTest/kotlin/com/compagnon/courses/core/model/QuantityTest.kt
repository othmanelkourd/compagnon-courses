package com.compagnon.courses.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuantityTest {
    private val kg = UnitCode.KILOGRAM

    @Test
    fun parsesCanonicalDecimals() {
        assertEquals(Quantity(1500, kg), Quantity.parse("1.5", kg))
        assertEquals(Quantity(2000, kg), Quantity.parse("2", kg))
        assertEquals(Quantity(250, kg), Quantity.parse("0.25", kg))
        assertEquals(Quantity(5, kg), Quantity.parse("0.005", kg))
        assertEquals(Quantity(1000, kg), Quantity.parse("1.000000", kg))
        assertEquals(Quantity(7500, kg), Quantity.parse("007.5", kg))
        assertEquals(Quantity(0, kg), Quantity.parse("0", kg))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf("", "1.", ".5", "-1", "+1", "1,5", "1e3", "1.0005", "abc", " 2", "9223372036854775.808").forEach {
            assertNull(Quantity.parseOrNull(it, kg), "\"$it\" should be rejected")
        }
        val error = assertFailsWith<IllegalArgumentException> { Quantity.parse("1,5", kg) }
        assertTrue(error.message!!.contains("1,5"))
    }

    @Test
    fun formatsWithoutTrailingZeros() {
        assertEquals("1.5", Quantity(1500, kg).toDecimalString())
        assertEquals("2", Quantity(2000, kg).toDecimalString())
        assertEquals("0.25", Quantity(250, kg).toDecimalString())
        assertEquals("0.005", Quantity(5, kg).toDecimalString())
        assertEquals("1.05", Quantity(1050, kg).toDecimalString())
        assertEquals("0", Quantity(0, kg).toDecimalString())
        assertEquals("1.5 kilogram", Quantity(1500, kg).toString())
        assertEquals("9223372036854775.807", Quantity(Long.MAX_VALUE, kg).toDecimalString())
    }

    @Test
    fun cannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { Quantity(-1, kg) }
    }

    @Test
    fun arithmeticIsExactAndUnitSafe() {
        assertEquals(Quantity(3500, kg), Quantity(1500, kg) + Quantity(2000, kg))
        assertEquals(Quantity(4500, kg), Quantity(1500, kg) * 3)
        assertEquals(Quantity.of(2, kg), Quantity(2000, kg))
        assertFailsWith<ArithmeticException> { Quantity(Long.MAX_VALUE, kg) + Quantity(1, kg) }
        assertFailsWith<ArithmeticException> { Quantity(Long.MAX_VALUE, kg) * 2 }
        assertFailsWith<IllegalArgumentException> { Quantity(1, kg) + Quantity(1, UnitCode.LITER) }
        assertFailsWith<IllegalArgumentException> { Quantity(1, kg) < Quantity(1, UnitCode.LITER) }
        assertTrue(Quantity(1500, kg) > Quantity(1000, kg))
    }
}
