package com.compagnon.courses.core.catalogbundle

/**
 * The catalog shipped in the apps, so that they work from the first launch without a network: the server's release
 * [VERSION] projected onto [COUNTRY], as `/v1/catalog/snapshot` serves it (JSON of `CatalogSnapshotDto`), in the
 * locales [LOCALES]. Generated from `catalog-content/` by `catalog-content/tools/bundle.py`.
 */
object BundledCatalog {
    const val COUNTRY: String = "FR"

    /** The release it was projected from: a later release reaches the apps as a delta from this one. */
    const val VERSION: Long = BUNDLED_VERSION

    val LOCALES: Set<String> = setOf("fr-FR", "en")

    /** Whether ([country], [locale]) is bundled, without building its JSON. */
    fun contains(country: String, locale: String): Boolean = country == COUNTRY && locale in LOCALES

    /** The snapshot of ([country], [locale]), or null when it is not bundled. */
    fun json(country: String, locale: String): String? = when {
        country != COUNTRY -> null
        locale == "fr-FR" -> BUNDLED_FR_FR
        locale == "en" -> BUNDLED_EN
        else -> null
    }
}
