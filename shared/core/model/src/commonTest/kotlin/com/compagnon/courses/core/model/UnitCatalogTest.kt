package com.compagnon.courses.core.model

import com.compagnon.courses.core.model.TestUnits.BOTTLE
import com.compagnon.courses.core.model.TestUnits.BOX
import com.compagnon.courses.core.model.TestUnits.CENTILITER
import com.compagnon.courses.core.model.TestUnits.DOZEN
import com.compagnon.courses.core.model.TestUnits.FLUID_OUNCE
import com.compagnon.courses.core.model.TestUnits.FLUID_OUNCE_IMPERIAL
import com.compagnon.courses.core.model.TestUnits.GALLON
import com.compagnon.courses.core.model.TestUnits.OUNCE
import com.compagnon.courses.core.model.TestUnits.PINT
import com.compagnon.courses.core.model.TestUnits.PINT_IMPERIAL
import com.compagnon.courses.core.model.TestUnits.POUND
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitCatalogTest {
    private val units = TestUnits.catalog
    private val g = UnitCode.GRAM
    private val kg = UnitCode.KILOGRAM
    private val l = UnitCode.LITER

    @Test
    fun convertsWithExactDefinitionConstants() {
        // 1 lb = 453.59237 g → 453592.37 milli-g, rounded half-even.
        assertEquals(qty("453.592", g), units.convert(qty("1", POUND), g))
        // 1 kg = 2.2046226… lb.
        assertEquals(qty("2.205", POUND), units.convert(qty("1", kg), POUND))
        assertEquals(qty("16", OUNCE), units.convert(qty("1", POUND), OUNCE))
        assertEquals(qty("128", FLUID_OUNCE), units.convert(qty("1", GALLON), FLUID_OUNCE))
        assertEquals(qty("16", FLUID_OUNCE), units.convert(qty("1", PINT), FLUID_OUNCE))
        assertEquals(qty("20", FLUID_OUNCE_IMPERIAL), units.convert(qty("1", PINT_IMPERIAL), FLUID_OUNCE_IMPERIAL))
        assertEquals(qty("3.785", l), units.convert(qty("1", GALLON), l))
        assertEquals(qty("150", CENTILITER), units.convert(qty("1.5", l), CENTILITER))
        assertEquals(qty("0.5", DOZEN), units.convert(qty("6", UnitCode.PIECE), DOZEN))
        assertEquals(qty("1500", g), units.convert(qty("1.5", kg), g))
    }

    @Test
    fun roundsHalfEvenByDefault() {
        // 1.5 g, 2.5 g and 3.5 g are exactly halfway between two milli-kilograms.
        assertEquals(Quantity(2, kg), units.convert(Quantity(1500, g), kg))
        assertEquals(Quantity(2, kg), units.convert(Quantity(2500, g), kg))
        assertEquals(Quantity(4, kg), units.convert(Quantity(3500, g), kg))
        assertEquals(Quantity(3, kg), units.convert(Quantity(2500, g), kg, rounding = Rounding.UP))
        assertEquals(Quantity(2, kg), units.convert(Quantity(2999, g), kg, rounding = Rounding.DOWN))
    }

    @Test
    fun packagesConvertOnlyThroughTheProductFormats() {
        val milkBottle = listOf(PackageFormat(BOTTLE, qty("1", l), typical = true))
        assertEquals(qty("2", l), units.convert(qty("2", BOTTLE), l, milkBottle))
        assertEquals(qty("1.5", BOTTLE), units.convert(qty("1500", UnitCode.MILLILITER), BOTTLE, milkBottle))
        assertEquals(Dimension.VOLUME, units.dimensionOf(BOTTLE, milkBottle))
        assertEquals(Dimension.PACKAGE, units.dimensionOf(BOTTLE))
        assertFalse(units.canConvert(BOTTLE, l))
        assertNull(units.convertOrNull(qty("2", BOTTLE), l))

        // The typical format wins over another one for the same package unit.
        val formats = listOf(
            PackageFormat(BOTTLE, qty("0.5", l), typical = false),
            PackageFormat(BOTTLE, qty("1.5", l), typical = true),
        )
        assertEquals(qty("3", l), units.convert(qty("2", BOTTLE), l, formats))

        // Nested packages are not convertible.
        val nested = listOf(PackageFormat(BOX, qty("6", BOTTLE), typical = true)) + milkBottle
        assertNull(units.factor(BOX, l, nested))
    }

    @Test
    fun refusesIncompatibleDimensions() {
        assertFalse(units.canConvert(kg, l))
        assertFailsWith<IllegalArgumentException> { units.convert(qty("1", kg), l) }
        assertNull(units.convertOrNull(qty("1", UnitCode("unknown")), kg))
        // Identity always works, even for an unknown unit.
        assertEquals(qty("2", BOX), units.convert(qty("2", BOX), BOX))
    }

    @Test
    fun guardsAgainstOverflow() {
        assertFailsWith<ArithmeticException> { units.convert(Quantity(Long.MAX_VALUE, kg), g) }
        assertNull(units.convertOrNull(Quantity(Long.MAX_VALUE, kg), g))
    }

    @Test
    fun findsBaseUnits() {
        assertEquals(g, units.baseUnit(Dimension.MASS)?.code)
        assertEquals(UnitCode.MILLILITER, units.baseUnit(Dimension.VOLUME)?.code)
        assertEquals(UnitCode.PIECE, units.baseUnit(Dimension.COUNT)?.code)
        assertNull(units.baseUnit(Dimension.PACKAGE))
        assertTrue(units[g]!!.isBase)
    }

    @Test
    fun validatesDefinitions() {
        assertFailsWith<IllegalArgumentException> { UnitDefinition(BOTTLE, Dimension.PACKAGE, Rational.ONE) }
        assertFailsWith<IllegalArgumentException> { UnitDefinition(kg, Dimension.MASS, null) }
        assertFailsWith<IllegalArgumentException> { UnitDefinition(kg, Dimension.MASS, Rational.ZERO) }
        assertFailsWith<IllegalArgumentException> { UnitCatalog(TestUnits.definitions + TestUnits.definitions.first()) }
    }
}
