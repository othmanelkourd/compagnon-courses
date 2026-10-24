package com.compagnon.courses.core.model

import kotlin.time.Instant

data class Country(
    val code: CountryCode,
    val defaultLocale: String,
    val measurementSystem: MeasurementSystem,
    val currency: String,
    val launchStatus: String,
)

/** Age band of a child (never a birth date). */
enum class AgeBand { BABY, CHILD, TEEN }

enum class PetKind { DOG, CAT, OTHER }

data class Pet(val kind: PetKind)

/**
 * The household as seen by the question engine.
 *
 * @property excludedTags tags never to be asked about (F14: no « porc ? » for a halal household).
 */
data class HouseholdProfile(
    val adults: Int,
    val children: List<AgeBand> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val country: CountryCode,
    val system: MeasurementSystem,
    val excludedTags: Set<TagId> = emptySet(),
) {
    init {
        require(adults >= 0) { "adults cannot be negative: $adults" }
        require(size >= 1) { "A household has at least one member" }
    }

    /** Number of members. */
    val size: Int get() = adults + children.size
}

/**
 * Purchase statistics and preferences of a household for one product (`household_product_stats` and
 * `household_product_pref`). The EWMA fields are statistical estimates, hence Doubles; quantities shown to
 * users are always [Quantity].
 *
 * @property ewmaQtyBase smoothed purchased quantity, in the base unit of the product's dimension (g, mL, piece).
 * @property lastQtyBase last purchased quantity, in the same base unit.
 * @property consecutiveNo consecutive "no" answers; a "no" to a category gate counts 0.5.
 * @property watch « il en reste un peu »: ask again first at the next session.
 * @property userCadenceDays cadence set by the user (F03), overriding the estimate.
 * @property usualQty last answered quantity, in the unit it was answered in.
 */
data class ProductStats(
    val nPurchases: Int = 0,
    val lastAt: Instant? = null,
    val ewmaIntervalDays: Double? = null,
    val ewmaQtyBase: Double? = null,
    val lastQtyBase: Double? = null,
    val consecutiveNo: Double = 0.0,
    val snoozeUntil: Instant? = null,
    val watch: Boolean = false,
    val neverAsk: Boolean = false,
    val userCadenceDays: Int? = null,
    val usualQty: Quantity? = null,
) {
    init {
        require(nPurchases >= 0) { "nPurchases cannot be negative: $nPurchases" }
        require(consecutiveNo >= 0.0 && consecutiveNo.isFinite()) { "consecutiveNo must be ≥ 0: $consecutiveNo" }
        require(userCadenceDays == null || userCadenceDays > 0) { "userCadenceDays must be positive: $userCadenceDays" }
        require(ewmaIntervalDays.isPositiveOrNull()) { "ewmaIntervalDays must be finite and > 0: $ewmaIntervalDays" }
        require(ewmaQtyBase.isPositiveOrNull()) { "ewmaQtyBase must be finite and > 0: $ewmaQtyBase" }
        require(lastQtyBase.isPositiveOrNull()) { "lastQtyBase must be finite and > 0: $lastQtyBase" }
    }
}

private fun Double?.isPositiveOrNull(): Boolean = this == null || (this > 0.0 && isFinite())
