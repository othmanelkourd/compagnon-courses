package com.compagnon.courses.core.testing

import com.compagnon.courses.core.catalogbundle.BundledCatalog
import com.compagnon.courses.core.catalogformat.CatalogJson
import com.compagnon.courses.core.catalogformat.CatalogSnapshotDto

/**
 * The real seed, as the apps ship it (`BundledCatalog`): FR, fr-FR, release [release]; 19 categories, 9 zones,
 * 33 units, 312 products. [english] is the same release in `en`.
 *
 * Tests number later releases from [release] (`release + 1` is the next one), so that bumping
 * `catalog-content/release.json` never breaks them.
 */
object SeedCatalog {
    /** The bundled release (`catalog-content/release.json`). */
    val release: Long get() = BundledCatalog.VERSION

    val json: String get() = checkNotNull(BundledCatalog.json("FR", "fr-FR"))

    val snapshot: CatalogSnapshotDto by lazy { CatalogJson.decodeFromString(CatalogSnapshotDto.serializer(), json) }

    val english: CatalogSnapshotDto by lazy {
        CatalogJson.decodeFromString(CatalogSnapshotDto.serializer(), checkNotNull(BundledCatalog.json("FR", "en")))
    }

    /** The snapshot as release [version] (same content). */
    fun snapshot(version: Long): CatalogSnapshotDto = snapshot.copy(version = version)

    fun productId(slug: String): String = snapshot.products.first { it.slug == slug }.id

    fun categoryId(slug: String): String = snapshot.categories.first { it.slug == slug }.id
}
