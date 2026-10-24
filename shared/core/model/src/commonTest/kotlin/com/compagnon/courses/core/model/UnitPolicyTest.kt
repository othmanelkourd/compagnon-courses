package com.compagnon.courses.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitPolicyTest {
    private val kg = UnitCode.KILOGRAM

    private fun policy(step: String, presets: List<String> = emptyList(), min: String? = null, max: String? = null) =
        UnitPolicy(
            unit = kg,
            isDefault = true,
            step = qty(step, kg),
            presets = presets.map { qty(it, kg) },
            min = min?.let { qty(it, kg) },
            max = max?.let { qty(it, kg) },
        )

    @Test
    fun acceptsAValidPolicy() {
        val apples = policy("0.5", listOf("0.5", "1", "2"), min = "0.5", max = "10")
        assertEquals(1L, apples.minSteps)
        assertEquals(20L, apples.maxSteps)
        assertEquals(qty("1.5", kg), apples.quantityOf(3))
        assertTrue(apples.isAllowed(qty("1.5", kg)))
        assertFalse(apples.isAllowed(qty("1.25", kg)))
        assertFalse(apples.isAllowed(qty("10.5", kg)))
        assertFalse(apples.isAllowed(qty("0", kg)))
        assertFalse(apples.isAllowed(qty("1", UnitCode.LITER)))
    }

    @Test
    fun boundsAreAlignedOnTheStep() {
        val unaligned = policy("0.5", min = "0.7", max = "2.2")
        assertEquals(2L, unaligned.minSteps) // 1.0 kg
        assertEquals(4L, unaligned.maxSteps) // 2.0 kg
        val unbounded = policy("1")
        assertEquals(1L, unbounded.minSteps)
        assertNull(unbounded.maxSteps)
    }

    @Test
    fun rejectsBrokenPolicies() {
        assertFailsWith<IllegalArgumentException> { policy("0") }
        assertFailsWith<IllegalArgumentException> { policy("0.5", listOf("0.75")) } // not a multiple
        assertFailsWith<IllegalArgumentException> { policy("0.5", listOf("12"), max = "10") } // above max
        assertFailsWith<IllegalArgumentException> { policy("0.5", listOf("0.5"), min = "1") } // below min
        assertFailsWith<IllegalArgumentException> { policy("1", min = "3", max = "2") }
        assertFailsWith<IllegalArgumentException> { policy("1", min = "1.2", max = "1.8") } // no multiple of 1 inside
        assertFailsWith<IllegalArgumentException> {
            UnitPolicy(kg, isDefault = true, step = qty("1", UnitCode.GRAM), presets = emptyList())
        }
    }

    @Test
    fun productsHaveExactlyOneDefaultPolicy() {
        val default = policy("1")
        val piece = UnitCode.PIECE
        val other = UnitPolicy(piece, isDefault = false, step = qty("1", piece), presets = emptyList())
        val product = product(listOf(other, default))
        assertEquals(default, product.defaultPolicy)
        assertEquals(other, product.policyFor(UnitCode.PIECE))
        assertNull(product.policyFor(UnitCode.LITER))
        assertNull(product(emptyList()).defaultPolicy)
        val grams = default.copy(unit = UnitCode.GRAM, step = qty("1", UnitCode.GRAM))
        assertFailsWith<IllegalArgumentException> { product(listOf(default, grams)) }
        assertFailsWith<IllegalArgumentException> { product(listOf(other)) }
        assertFailsWith<IllegalArgumentException> { product(listOf(default, default.copy(isDefault = false))) }
    }

    @Test
    fun marketDataIsValidated() {
        assertFailsWith<IllegalArgumentException> { market(penetration = 1.2) }
        assertFailsWith<IllegalArgumentException> { market(repurchaseDays = 0) }
        assertEquals(0.85, market(penetration = 0.85).householdPenetration)
    }

    @Test
    fun packageFormatsAreValidated() {
        val bottle = TestUnits.BOTTLE
        assertFailsWith<IllegalArgumentException> { PackageFormat(bottle, qty("0", UnitCode.LITER), typical = true) }
        assertFailsWith<IllegalArgumentException> { PackageFormat(bottle, qty("1", bottle), typical = true) }
    }

    @Test
    fun localizedNamesFallBackToOther() {
        val eggs = LocalizedName("œuf", mapOf("one" to "œuf", "other" to "œufs"))
        assertEquals("œuf", eggs.form(LocalizedName.PLURAL_ONE))
        assertEquals("œufs", eggs.form("many"))
        assertEquals("lait", LocalizedName("lait").form("other"))
    }

    private fun market(penetration: Double? = null, repurchaseDays: Int? = null) = ProductMarket(
        availability = Availability.COMMON,
        typicalRepurchaseDays = repurchaseDays,
        householdPenetration = penetration,
        isQuestionCandidate = true,
    )

    private fun product(policies: List<UnitPolicy>) = Product(
        id = ProductId("p"),
        slug = "pommes",
        categoryId = CategoryId("c"),
        name = LocalizedName("pomme"),
        dimension = Dimension.MASS,
        storageZone = ZoneId("z"),
        cycleClass = CycleClass.SHORT,
        isStaple = true,
        market = market(),
        unitPolicies = policies,
    )
}
