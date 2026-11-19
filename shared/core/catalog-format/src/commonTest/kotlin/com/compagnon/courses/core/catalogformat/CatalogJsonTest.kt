package com.compagnon.courses.core.catalogformat

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behaviour of [CatalogJson] that the bundled catalog relies on. */
class CatalogJsonTest {

    @Test
    fun nullValuesAreOmitted() {
        val category = CategoryDto(id = "c1", slug = "cremerie", position = 1, name = "Crèmerie")

        val json = CatalogJson.encodeToJsonElement(CategoryDto.serializer(), category).jsonObject

        assertFalse("parentId" in json)
        assertFalse("gateQuestion" in json)
    }

    @Test
    fun missingOptionalFieldsDecodeToDefaults() {
        val product = CatalogJson.decodeFromString(
            ProductDto.serializer(),
            """
            {
              "id": "p1", "slug": "sel", "categoryId": "c1", "storageZoneId": "z1", "dimension": "mass",
              "name": "sel", "forms": { "one": "sel", "other": "sels" }, "cycleClass": "long",
              "isStaple": true, "isQuestionCandidate": false,
              "unitPolicies": [ { "unit": "kilogram", "isDefault": true, "step": "1", "presets": ["1"] } ]
            }
            """,
        )

        assertNull(product.needPhrase)
        assertNull(product.typicalRepurchaseDays)
        assertNull(product.householdPenetration)
        assertEquals(emptyList(), product.packages)
        assertEquals(emptyList(), product.tags, "tags are optional: older catalog files keep decoding")
        assertNull(product.unitPolicies.single().min)
        assertNull(product.unitPolicies.single().max)
    }

    @Test
    fun explicitNullsAreAccepted() {
        val category = CatalogJson.decodeFromString(
            CategoryDto.serializer(),
            """
            { "id": "c1", "slug": "cremerie", "parentId": null, "position": 1, "name": "Crèmerie", "gateQuestion": null }
            """,
        )

        assertNull(category.parentId)
        assertNull(category.gateQuestion)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val category = CatalogJson.decodeFromString(
            CategoryDto.serializer(),
            """
            { "id": "c1", "slug": "cremerie", "position": 1, "name": "Crèmerie", "addedLater": { "nested": [1, 2, 3] } }
            """,
        )

        assertEquals("cremerie", category.slug)
    }

    @Test
    fun enumsUseTheirWireNames() {
        assertEquals(
            listOf("\"mass\"", "\"volume\"", "\"count\"", "\"package\""),
            DimensionDto.entries.map { CatalogJson.encodeToString(DimensionDto.serializer(), it) },
        )
        assertEquals(
            listOf("\"short\"", "\"medium\"", "\"long\""),
            CycleClassDto.entries.map { CatalogJson.encodeToString(CycleClassDto.serializer(), it) },
        )
    }

    @Test
    fun decimalsStayStrings() {
        val policy = UnitPolicyDto(unit = "kilogram", isDefault = true, step = "0.25", presets = listOf("0.5", "1.25"))

        val json = CatalogJson.encodeToJsonElement(UnitPolicyDto.serializer(), policy) as JsonObject

        assertTrue((json["step"] as JsonPrimitive).isString)
        assertEquals("0.25", CatalogJson.decodeFromJsonElement(String.serializer(), json.getValue("step")))
    }
}
