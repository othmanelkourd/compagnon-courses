package com.compagnon.courses.core.testing

import com.compagnon.courses.contract.CatalogSnapshotDto
import com.compagnon.courses.contract.CatalogVersionDto
import com.compagnon.courses.contract.ContractJson

/** The real Sprint 0 seed (FR, fr-FR, release 1): 19 categories, 9 zones, 33 units, 195 products. */
object SeedCatalog {
    val json: String get() = SEED_SNAPSHOT_JSON

    val snapshot: CatalogSnapshotDto by lazy { ContractJson.decodeFromString(CatalogSnapshotDto.serializer(), json) }

    /** The snapshot as release [version] (same content). */
    fun snapshot(version: Long): CatalogSnapshotDto = snapshot.copy(version = version)

    fun version(version: Long = 1, snapshot: CatalogSnapshotDto = this.snapshot): CatalogVersionDto =
        CatalogVersionDto(
            country = snapshot.country,
            version = version,
            publishedAt = "2026-10-24T08:30:00Z",
            locales = listOf("en", "fr", "fr-FR"),
            categoryCount = snapshot.categories.size,
            productCount = snapshot.products.size,
        )

    fun productId(slug: String): String = snapshot.products.first { it.slug == slug }.id

    fun categoryId(slug: String): String = snapshot.categories.first { it.slug == slug }.id
}
