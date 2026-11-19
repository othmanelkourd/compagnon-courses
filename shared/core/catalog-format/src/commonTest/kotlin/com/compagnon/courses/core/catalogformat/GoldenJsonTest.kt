package com.compagnon.courses.core.catalogformat

import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The golden document decodes to the expected value, the value encodes to the
 * golden document (compared as JSON trees, so formatting and key order do not matter), and an
 * encode/decode round trip is lossless.
 */
class GoldenJsonTest {

    private fun <T> assertGolden(serializer: KSerializer<T>, expected: T, golden: String) {
        assertEquals(expected, CatalogJson.decodeFromString(serializer, golden), "golden -> value")
        assertEquals(
            CatalogJson.parseToJsonElement(golden),
            CatalogJson.encodeToJsonElement(serializer, expected),
            "value -> golden",
        )
        val roundTripped = CatalogJson.decodeFromString(serializer, CatalogJson.encodeToString(serializer, expected))
        assertEquals(expected, roundTripped, "round trip")
    }

    @Test
    fun catalogSnapshot() =
        assertGolden(CatalogSnapshotDto.serializer(), Samples.catalogSnapshot, GoldenJson.CATALOG_SNAPSHOT)
}
