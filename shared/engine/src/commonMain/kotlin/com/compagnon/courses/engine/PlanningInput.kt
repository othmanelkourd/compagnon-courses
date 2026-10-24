package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.EventId
import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.StorageZone
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.ZoneId
import kotlin.time.Instant

enum class SessionMode {
    /** Default: meta → T1 → cards grouped by category in store order → gates → radar. */
    QUICK,

    /** F05: everything ordered by storage zone (fridge → freezer → cupboards…). */
    HOME_TOUR,

    /** « J'ai le frigo ouvert » : a single storage zone. */
    ZONE,

    /** Opt-in: every category behind a gate, without budget. */
    FULL,
}

/** The meta questions asked before the products (at most three, pre-filled). */
enum class MetaKey {
    /** « Courses pour combien de jours ? » */
    HORIZON_DAYS,

    /** « Combien de personnes cette semaine ? » */
    PERSONS,

    /** « Des invités ? » (number of guests). */
    GUESTS,
}

/**
 * What the household told us (or what we pre-filled) for this session.
 *
 * @property horizonDays H: the shopping covers that many days.
 * @property usualHorizonDays the household's usual horizon (learned cadence), used to scale quantities.
 * @property persons persons eating at home this time (defaults to the household size).
 * @property zone the zone of a [SessionMode.ZONE] session.
 * @property categoryOrder store category order, most specific source first (store → chain → country → global),
 *   already resolved by the caller. Categories missing from it follow, by [Category.position].
 * @property budgetOverride Q_max set by a `question_flow`; ignored in [SessionMode.FULL].
 * @property reuseLastList « Reprendre ma dernière liste »: pre-check the last list on the T1 screen.
 */
data class SessionContext(
    val mode: SessionMode = SessionMode.QUICK,
    val horizonDays: Int = DEFAULT_HORIZON_DAYS,
    val usualHorizonDays: Int = horizonDays,
    val persons: Int,
    val guests: Int = 0,
    val zone: ZoneId? = null,
    val categoryOrder: List<CategoryId> = emptyList(),
    val budgetOverride: Int? = null,
    val metaQuestions: List<MetaKey> = MetaKey.entries,
    val reuseLastList: Boolean = false,
) {
    init {
        require(isValid(MetaKey.HORIZON_DAYS, horizonDays)) { "horizonDays out of range: $horizonDays" }
        require(isValid(MetaKey.HORIZON_DAYS, usualHorizonDays)) { "usualHorizonDays out of range: $usualHorizonDays" }
        require(isValid(MetaKey.PERSONS, persons)) { "persons out of range: $persons" }
        require(isValid(MetaKey.GUESTS, guests)) { "guests out of range: $guests" }
        require(mode != SessionMode.ZONE || zone != null) { "A ZONE session needs a zone" }
        require(budgetOverride == null || budgetOverride >= 0) { "budgetOverride cannot be negative" }
    }

    /** The current (pre-filled or answered) value of a meta question. */
    fun valueOf(key: MetaKey): Int = when (key) {
        MetaKey.HORIZON_DAYS -> horizonDays
        MetaKey.PERSONS -> persons
        MetaKey.GUESTS -> guests
    }

    /** A copy with the answer to a meta question; [value] must satisfy [isValid]. */
    fun with(key: MetaKey, value: Int): SessionContext = when (key) {
        MetaKey.HORIZON_DAYS -> copy(horizonDays = value)
        MetaKey.PERSONS -> copy(persons = value)
        MetaKey.GUESTS -> copy(guests = value)
    }

    companion object {
        const val DEFAULT_HORIZON_DAYS: Int = 7
        const val MAX_HORIZON_DAYS: Int = 90
        const val MAX_PERSONS: Int = 30
        const val MAX_GUESTS: Int = 50

        fun isValid(key: MetaKey, value: Int): Boolean = when (key) {
            MetaKey.HORIZON_DAYS -> value in 1..MAX_HORIZON_DAYS
            MetaKey.PERSONS -> value in 1..MAX_PERSONS
            MetaKey.GUESTS -> value in 0..MAX_GUESTS
        }

        /** A quick session for [household], pre-filled with its size and usual horizon. */
        fun forHousehold(
            household: HouseholdProfile,
            usualHorizonDays: Int = DEFAULT_HORIZON_DAYS,
            mode: SessionMode = SessionMode.QUICK,
        ): SessionContext = SessionContext(
            mode = mode,
            horizonDays = usualHorizonDays,
            usualHorizonDays = usualHorizonDays,
            persons = household.size.coerceAtMost(MAX_PERSONS),
        )
    }
}

enum class SeasonLevel { LOW, NORMAL, HIGH }

/** An active calendar event boosting a product (F12). */
data class EventBoost(val eventId: EventId, val probabilityBoost: Double) {
    init {
        require(probabilityBoost > 0.0 && probabilityBoost.isFinite()) {
            "probabilityBoost must be positive and finite: $probabilityBoost"
        }
    }
}

/**
 * Country-level signals for the session date, resolved by the caller.
 *
 * @property receptionCategories categories boosted when the household has guests.
 */
data class MarketSignals(
    val seasonality: Map<ProductId, SeasonLevel> = emptyMap(),
    val events: Map<ProductId, EventBoost> = emptyMap(),
    val receptionCategories: Set<CategoryId> = emptySet(),
) {
    companion object {
        val NONE: MarketSignals = MarketSignals()
    }
}

/**
 * Everything the planner needs; it performs no I/O. [now] and [seed] make a plan fully reproducible.
 *
 * @property products the catalog projected on the household's (country, locale).
 * @property lastList the last list of the household (product → quantity), for [SessionContext.reuseLastList].
 */
data class PlanningInput(
    val products: List<Product>,
    val categories: List<Category>,
    val storageZones: List<StorageZone> = emptyList(),
    val units: UnitCatalog = UnitCatalog.EMPTY,
    val household: HouseholdProfile,
    val stats: Map<ProductId, ProductStats> = emptyMap(),
    val context: SessionContext,
    val signals: MarketSignals = MarketSignals.NONE,
    val lastList: Map<ProductId, Quantity> = emptyMap(),
    val now: Instant,
    val seed: Long = 0L,
) {
    override fun toString(): String =
        "PlanningInput(products=${products.size}, categories=${categories.size}, stats=${stats.size}, " +
            "context=$context, now=$now, seed=$seed)"
}
