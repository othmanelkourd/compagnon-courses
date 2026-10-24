package com.compagnon.courses.core.model

/** A localized name with its CLDR plural forms (`{"one": "œuf", "other": "œufs"}`). */
data class LocalizedName(
    val text: String,
    val forms: Map<String, String> = emptyMap(),
) {
    /** The form for a CLDR plural category, falling back to `other`, then to [text]. */
    fun form(pluralCategory: String): String = forms[pluralCategory] ?: forms[PLURAL_OTHER] ?: text

    companion object {
        const val PLURAL_ONE: String = "one"
        const val PLURAL_OTHER: String = "other"
    }
}

/** An aisle of the catalog; [gateQuestion] covers the whole aisle (« Besoin de quelque chose en Entretien ? »). */
data class Category(
    val id: CategoryId,
    val slug: String,
    val parentId: CategoryId? = null,
    val position: Int,
    val name: String,
    val gateQuestion: String? = null,
)

data class StorageZone(
    val id: ZoneId,
    val slug: String,
    val position: Int,
    val name: String,
)

/**
 * Market data of a product in the household's country.
 *
 * @property typicalRepurchaseDays prior repurchase interval for a reference household.
 * @property householdPenetration share of households buying the product, in [0, 1].
 * @property popularityRank 1 = most popular; null when unknown.
 */
data class ProductMarket(
    val availability: Availability,
    val typicalRepurchaseDays: Int? = null,
    val householdPenetration: Double? = null,
    val isQuestionCandidate: Boolean,
    val popularityRank: Int? = null,
) {
    init {
        require(typicalRepurchaseDays == null || typicalRepurchaseDays > 0) {
            "typicalRepurchaseDays must be positive: $typicalRepurchaseDays"
        }
        require(householdPenetration == null || householdPenetration in 0.0..1.0) {
            "householdPenetration must be within [0, 1]: $householdPenetration"
        }
        require(popularityRank == null || popularityRank > 0) { "popularityRank must be positive: $popularityRank" }
    }
}

/**
 * A generic product projected onto one (country, locale).
 *
 * Invariant: when the product has unit policies, exactly one of them is the default, and each unit has at
 * most one policy.
 */
data class Product(
    val id: ProductId,
    val slug: String,
    val categoryId: CategoryId,
    val parentId: ProductId? = null,
    val name: LocalizedName,
    val needPhrase: String? = null,
    val dimension: Dimension,
    val storageZone: ZoneId,
    val cycleClass: CycleClass,
    val isStaple: Boolean,
    val market: ProductMarket,
    val unitPolicies: List<UnitPolicy>,
    val packages: List<PackageFormat> = emptyList(),
    val tags: Set<TagId> = emptySet(),
) {
    init {
        require(unitPolicies.isEmpty() || unitPolicies.count { it.isDefault } == 1) {
            "Product $slug must have exactly one default unit policy"
        }
        require(unitPolicies.distinctBy { it.unit }.size == unitPolicies.size) {
            "Product $slug has several policies for the same unit"
        }
    }

    val defaultPolicy: UnitPolicy? get() = unitPolicies.firstOrNull { it.isDefault }

    fun policyFor(unit: UnitCode): UnitPolicy? = unitPolicies.firstOrNull { it.unit == unit }
}

/** The catalog projected onto one (country, locale), as served by the snapshot endpoint. */
data class Catalog(
    val country: CountryCode,
    val locale: String,
    val version: Long,
    val categories: List<Category>,
    val storageZones: List<StorageZone>,
    val units: UnitCatalog,
    val products: List<Product>,
)
