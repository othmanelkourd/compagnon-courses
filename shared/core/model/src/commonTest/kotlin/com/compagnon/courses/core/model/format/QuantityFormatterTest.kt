package com.compagnon.courses.core.model.format

import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.LocalizedName
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ProductMarket
import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.Rational
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.UnitDefinition
import com.compagnon.courses.core.model.UnitPolicy
import com.compagnon.courses.core.model.ZoneId
import com.compagnon.courses.core.model.text.normalizeForSearch
import kotlin.test.Test
import kotlin.test.assertEquals

class QuantityFormatterTest {
    private val bottle = UnitCode("bottle")
    private val nbsp = QuantityFormatter.NO_BREAK_SPACE

    private fun units(language: String) = UnitCatalog(
        listOf(
            UnitDefinition(UnitCode.KILOGRAM, Dimension.MASS, Rational.of(1000), symbol = "kg"),
            UnitDefinition(UnitCode.LITER, Dimension.VOLUME, Rational.of(1000), symbol = "L"),
            UnitDefinition(
                UnitCode.PIECE,
                Dimension.COUNT,
                Rational.ONE,
                symbol = "pc",
                forms = if (language == "fr") mapOf("one" to "pièce", "other" to "pièces") else mapOf("one" to "piece", "other" to "pieces"),
            ),
            UnitDefinition(
                bottle,
                Dimension.PACKAGE,
                toBase = null,
                symbol = "btl",
                forms = if (language == "fr") mapOf("one" to "bouteille", "other" to "bouteilles") else mapOf("one" to "bottle", "other" to "bottles"),
            ),
        ),
    )

    private val eggs = Product(
        id = ProductId("p-oeufs"),
        slug = "oeufs",
        categoryId = CategoryId("c"),
        name = LocalizedName("œuf", mapOf("one" to "œuf", "other" to "œufs")),
        dimension = Dimension.COUNT,
        storageZone = ZoneId("z"),
        cycleClass = CycleClass.SHORT,
        isStaple = true,
        market = ProductMarket(Availability.COMMON, isQuestionCandidate = true),
        unitPolicies = listOf(UnitPolicy(UnitCode.PIECE, true, Quantity.of(1, UnitCode.PIECE), emptyList())),
    )

    @Test
    fun frenchUsesACommaAndTheFrenchPluralRule() {
        val formatter = QuantityFormatter(units("fr"), "fr-FR")

        assertEquals("1,5${nbsp}kg", formatter.format(Quantity(1500, UnitCode.KILOGRAM)))
        assertEquals("2${nbsp}bouteilles", formatter.format(Quantity.of(2, bottle)))
        assertEquals("1${nbsp}bouteille", formatter.format(Quantity.of(1, bottle)))
        assertEquals("1,5${nbsp}bouteille", formatter.format(Quantity(1500, bottle)))
        assertEquals("6${nbsp}œufs", formatter.format(Quantity.of(6, UnitCode.PIECE), eggs))
        assertEquals("1${nbsp}œuf", formatter.format(Quantity.of(1, UnitCode.PIECE), eggs))
        assertEquals("3${nbsp}pièces", formatter.format(Quantity.of(3, UnitCode.PIECE)))
    }

    @Test
    fun englishUsesADotAndOneOnlyForExactlyOne() {
        val formatter = QuantityFormatter(units("en"), "en")

        assertEquals("0.25${nbsp}L", formatter.format(Quantity(250, UnitCode.LITER)))
        assertEquals("1${nbsp}bottle", formatter.format(Quantity.of(1, bottle)))
        assertEquals("1.5${nbsp}bottles", formatter.format(Quantity(1500, bottle)))
        assertEquals("0${nbsp}bottles", formatter.format(Quantity(0, bottle)))
    }

    @Test
    fun unknownUnitsFallBackToTheirCode() {
        val formatter = QuantityFormatter(UnitCatalog.EMPTY, "fr")
        assertEquals("2${nbsp}jar", formatter.format(Quantity.of(2, UnitCode("jar"))))
    }

    @Test
    fun namesAreCapitalizedForDisplay() {
        assertEquals("Œufs", "œufs".capitalizedFirst())
        assertEquals("Lait demi-écrémé", "lait demi-écrémé".capitalizedFirst())
        assertEquals("", "".capitalizedFirst())
    }

    @Test
    fun searchTextIsFoldedAndCollapsed() {
        assertEquals("oeufs", normalizeForSearch("Œufs"))
        assertEquals("creme fraiche epaisse", normalizeForSearch("  Crème fraîche\népaisse "))
        assertEquals("huile d olive", normalizeForSearch("huile d’olive"))
        assertEquals("pates", normalizeForSearch("Pâtes!"))
    }
}
