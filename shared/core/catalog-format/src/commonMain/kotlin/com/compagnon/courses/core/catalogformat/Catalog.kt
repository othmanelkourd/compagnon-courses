package com.compagnon.courses.core.catalogformat

import kotlinx.serialization.Serializable

// Decimal quantities are carried as strings (e.g. "1.5") so that no client ever parses them as Double.

@Serializable
data class CategoryDto(
    val id: String,
    val slug: String,
    val parentId: String? = null,
    val position: Int,
    val name: String,
    val gateQuestion: String? = null,
)

@Serializable
data class StorageZoneDto(
    val id: String,
    val slug: String,
    val position: Int,
    val name: String,
)

/**
 * @property toBase decimal factor to the base unit of the dimension (g, mL or piece); null for packages.
 * @property forms CLDR plural category (`one`, `other`, …) to the localized form.
 */
@Serializable
data class UnitDto(
    val code: String,
    val dimension: DimensionDto,
    val system: String,
    val toBase: String? = null,
    val symbol: String,
    val forms: Map<String, String>,
)

/** Allowed quantities of a product in one unit. [step], [presets], [min] and [max] are decimal strings. */
@Serializable
data class UnitPolicyDto(
    val unit: String,
    val isDefault: Boolean,
    val step: String,
    val presets: List<String>,
    val min: String? = null,
    val max: String? = null,
)

/** A package unit (bottle, can…) holding [contentQty] (decimal string) of [contentUnit]. */
@Serializable
data class PackageDto(
    val packageUnit: String,
    val contentQty: String,
    val contentUnit: String,
    val typical: Boolean,
)

/**
 * A product projected onto one (country, locale). [forms] maps CLDR plural categories to names.
 * [tags] are diet and allergen slugs from [ProductTags] (`contains-pork`, `vegan`…), sorted.
 */
@Serializable
data class ProductDto(
    val id: String,
    val slug: String,
    val categoryId: String,
    val storageZoneId: String,
    val dimension: DimensionDto,
    val name: String,
    val forms: Map<String, String>,
    val needPhrase: String? = null,
    val cycleClass: CycleClassDto,
    val isStaple: Boolean,
    val typicalRepurchaseDays: Int? = null,
    val householdPenetration: Double? = null,
    val isQuestionCandidate: Boolean,
    val unitPolicies: List<UnitPolicyDto>,
    val packages: List<PackageDto> = emptyList(),
    val tags: List<String> = emptyList(),
)

/** A whole catalog projected onto one (country, locale): the format of the catalog bundled in the app. */
@Serializable
data class CatalogSnapshotDto(
    val country: String,
    val locale: String,
    val version: Long,
    val categories: List<CategoryDto>,
    val storageZones: List<StorageZoneDto>,
    val units: List<UnitDto>,
    val products: List<ProductDto>,
)
