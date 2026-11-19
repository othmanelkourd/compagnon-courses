package com.compagnon.courses.core.model.mapping

import com.compagnon.courses.core.catalogformat.CatalogJson
import com.compagnon.courses.core.catalogformat.CatalogSnapshotDto
import com.compagnon.courses.core.catalogformat.CycleClassDto
import com.compagnon.courses.core.catalogformat.DimensionDto
import com.compagnon.courses.core.catalogformat.PackageDto
import com.compagnon.courses.core.catalogformat.UnitDto
import com.compagnon.courses.core.catalogformat.UnitPolicyDto
import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CountryCode
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.PackageFormat
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.Rational
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogMappersTest {
    private val liter = UnitCode.LITER
    private val bottle = UnitCode("bottle")

    @Test
    fun mapsEnumsExhaustively() {
        assertEquals(Dimension.entries, DimensionDto.entries.map { it.toDomain() })
        assertEquals(CycleClass.entries, CycleClassDto.entries.map { it.toDomain() })
        assertEquals(Availability.RARE, Availability.fromWireName("rare"))
        assertNull(Availability.fromWireName("sometimes"))
    }

    @Test
    fun mapsASnapshotFromTheWire() {
        val result = CatalogJson.decodeFromString(CatalogSnapshotDto.serializer(), SNAPSHOT).toDomain()

        assertEquals(emptyList(), result.issues)
        val catalog = result.catalog
        assertEquals(CountryCode("FR"), catalog.country)
        assertEquals("fr-FR", catalog.locale)
        assertEquals(3L, catalog.version)
        assertEquals(listOf("cremerie", "yaourts"), catalog.categories.map { it.slug })
        assertEquals(CategoryId("cat-cremerie"), catalog.categories[1].parentId)
        assertEquals("Besoin de quelque chose en crèmerie ?", catalog.categories[0].gateQuestion)
        assertEquals(ZoneId("zone-frigo"), catalog.storageZones.single().id)

        val units = catalog.units
        assertEquals(Rational.of(1000), units[liter]?.toBase)
        assertNull(units[bottle]?.toBase)
        assertEquals(Dimension.PACKAGE, units[bottle]?.dimension)
        assertEquals("bouteilles", units[bottle]?.forms?.get("other"))
        assertEquals(Rational.of(12), units[UnitCode("dozen")]?.toBase)

        val milk = catalog.products.single()
        assertEquals(ProductId("prod-lait"), milk.id)
        assertEquals("laits demi-écrémés", milk.name.form("other"))
        assertEquals("du lait demi-écrémé", milk.needPhrase)
        assertEquals(Dimension.VOLUME, milk.dimension)
        assertEquals(CycleClass.SHORT, milk.cycleClass)
        assertEquals(6, milk.market.typicalRepurchaseDays)
        assertEquals(0.85, milk.market.householdPenetration)
        assertEquals(Availability.AVAILABLE, milk.market.availability)
        assertTrue(milk.market.isQuestionCandidate)

        val default = milk.defaultPolicy!!
        assertEquals(bottle, default.unit)
        assertEquals(Quantity(1000, bottle), default.step)
        assertEquals(listOf("1", "2", "6"), default.presets.map { it.toDecimalString() })
        val liters = milk.policyFor(liter)!!
        assertEquals(Quantity(500, liter), liters.step)
        assertEquals(Quantity(500, liter), liters.min)
        assertEquals(Quantity(24_000, liter), liters.max)
        assertEquals(listOf(PackageFormat(bottle, Quantity(1000, liter), typical = true)), milk.packages)

        // The mapped product converts through its own package format.
        assertEquals(Quantity(2000, liter), units.convert(Quantity(2000, bottle), liter, milk.packages))
    }

    @Test
    fun skipsInvalidEntitiesAndReportsThem() {
        val valid = CatalogJson.decodeFromString(CatalogSnapshotDto.serializer(), SNAPSHOT)
        val milk = valid.products.single()
        val broken = UnitDto("broken", DimensionDto.MASS, "metric", toBase = "1,5", symbol = "?", forms = emptyMap())
        val unalignedPreset = policy(step = "0.5", presets = listOf("0.75"))
        val snapshot = valid.copy(
            units = valid.units + broken,
            products = listOf(
                milk,
                milk.copy(id = "p-preset", slug = "preset", unitPolicies = listOf(unalignedPreset)),
                milk.copy(id = "p-unit", slug = "unit", unitPolicies = listOf(policy(unit = "furlong"))),
                milk.copy(id = "p-category", slug = "category", categoryId = "nowhere"),
                milk.copy(id = "p-zone", slug = "zone", storageZoneId = "nowhere"),
                milk.copy(id = "p-defaults", slug = "defaults", unitPolicies = listOf(policy(), policy("bottle"))),
            ),
        )

        val result = snapshot.toDomain()

        assertEquals(listOf(ProductId("prod-lait")), result.catalog.products.map { it.id })
        assertNull(result.catalog.units[UnitCode("broken")])
        assertEquals(
            listOf("unit" to "broken") +
                listOf("p-preset", "p-unit", "p-category", "p-zone", "p-defaults").map { "product" to it },
            result.issues.map { it.entity to it.id },
        )
        assertTrue(result.issues[1].message.contains("preset 0.75"), result.issues[1].message)
        assertTrue(result.issues[2].message.contains("furlong"), result.issues[2].message)
    }

    @Test
    fun singleDtoMappersFailWithContext() {
        val error = assertFailsWith<CatalogMappingException> { policy(step = "abc").toDomain() }
        assertEquals("unit policy liter: invalid step \"abc\"", error.message)
        assertFailsWith<CatalogMappingException> {
            UnitDto("bottle", DimensionDto.PACKAGE, "any", toBase = "1", symbol = "b", forms = emptyMap()).toDomain()
        }
        assertFailsWith<CatalogMappingException> { PackageDto("bottle", "-1", "liter", typical = true).toDomain() }
        val product = CatalogJson.decodeFromString(CatalogSnapshotDto.serializer(), SNAPSHOT).products.single()
        assertFailsWith<CatalogMappingException> { product.toDomain(UnitCatalog.EMPTY) }
        assertEquals(Availability.RARE, product.toDomain(availability = Availability.RARE).market.availability)
    }

    private fun policy(unit: String = "liter", step: String = "1", presets: List<String> = listOf("1")) =
        UnitPolicyDto(unit = unit, isDefault = true, step = step, presets = presets)

    private companion object {
        const val SNAPSHOT: String = """
{
  "country": "FR",
  "locale": "fr-FR",
  "version": 3,
  "categories": [
    {
      "id": "cat-cremerie", "slug": "cremerie", "position": 20, "name": "Crèmerie",
      "gateQuestion": "Besoin de quelque chose en crèmerie ?"
    },
    { "id": "cat-yaourts", "slug": "yaourts", "parentId": "cat-cremerie", "position": 10, "name": "Yaourts" }
  ],
  "storageZones": [ { "id": "zone-frigo", "slug": "frigo", "position": 10, "name": "Frigo" } ],
  "units": [
    {
      "code": "liter", "dimension": "volume", "system": "metric", "toBase": "1000", "symbol": "L",
      "forms": { "one": "litre", "other": "litres" }
    },
    {
      "code": "bottle", "dimension": "package", "system": "any", "symbol": "bout.",
      "forms": { "one": "bouteille", "other": "bouteilles" }
    },
    {
      "code": "dozen", "dimension": "count", "system": "any", "toBase": "12", "symbol": "douz.",
      "forms": { "one": "douzaine", "other": "douzaines" }
    }
  ],
  "products": [
    {
      "id": "prod-lait",
      "slug": "lait-demi-ecreme",
      "categoryId": "cat-cremerie",
      "storageZoneId": "zone-frigo",
      "dimension": "volume",
      "name": "lait demi-écrémé",
      "forms": { "one": "lait demi-écrémé", "other": "laits demi-écrémés" },
      "needPhrase": "du lait demi-écrémé",
      "cycleClass": "short",
      "isStaple": true,
      "typicalRepurchaseDays": 6,
      "householdPenetration": 0.85,
      "isQuestionCandidate": true,
      "unitPolicies": [
        { "unit": "liter", "isDefault": false, "step": "0.5", "presets": ["1", "2", "6"], "min": "0.5", "max": "24" },
        { "unit": "bottle", "isDefault": true, "step": "1", "presets": ["1", "2", "6"] }
      ],
      "packages": [ { "packageUnit": "bottle", "contentQty": "1", "contentUnit": "liter", "typical": true } ],
      "someFutureField": "ignored"
    }
  ]
}
"""
    }
}
