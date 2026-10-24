package com.compagnon.courses.engine

import com.compagnon.courses.core.model.EventId
import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.Instant

/** Typed « Pourquoi cette question ? » reasons (F03), translated by the native UIs. */
sealed interface ReasonCode {
    /** Bought [daysSinceLast] days ago, usually every [intervalDays] days, usually [usualQuantity]. */
    data class Due(val daysSinceLast: Int, val intervalDays: Int, val usualQuantity: Quantity?) : ReasonCode

    data object Staple : ReasonCode

    data object Seasonal : ReasonCode

    data class Event(val eventId: EventId) : ReasonCode

    /** A long-cycle product last bought [weeksSinceLast] weeks ago. */
    data class Radar(val weeksSinceLast: Int) : ReasonCode

    /** No history: the score comes from the country prior for a household of [householdSize]. */
    data class Prior(val householdSize: Int) : ReasonCode

    data object Exploration : ReasonCode

    /** The household said « il en reste un peu » last time. */
    data object LittleLeft : ReasonCode

    /** Pre-checked from the last list (« Reprendre ma dernière liste »). */
    data object LastList : ReasonCode
}

/**
 * The need score of a product: [probability] p that it is needed within the horizon.
 *
 * @property intervalDays the estimated repurchase interval Î_q, when one could be estimated.
 */
data class NeedScore(
    val probability: Double,
    val reasons: List<ReasonCode>,
    val intervalDays: Double?,
)

/** Computes p(need within H) with the V1 EMA heuristic of `docs/question-engine.md` step 3. */
class NeedScorer(private val config: ScoreConfig = ScoreConfig()) {

    fun score(
        product: Product,
        stats: ProductStats?,
        household: HouseholdProfile,
        context: SessionContext,
        signals: MarketSignals,
        now: Instant,
    ): NeedScore {
        val reasons = mutableListOf<ReasonCode>()
        val interval = estimatedInterval(product, stats, household)
        val lastAt = stats?.lastAt

        val base = if (lastAt != null && interval != null) {
            val daysSince = (now - lastAt).toDouble(DurationUnit.DAYS).coerceAtLeast(0.0)
            reasons += ReasonCode.Due(
                daysSinceLast = floor(daysSince).toInt(),
                intervalDays = interval.roundToInt().coerceAtLeast(1),
                usualQuantity = stats.usualQty,
            )
            val ratio = (daysSince + context.horizonDays) / interval
            1.0 / (1.0 + exp(-config.slope * (ratio - 1.0)))
        } else {
            if (product.isStaple) reasons += ReasonCode.Staple
            reasons += ReasonCode.Prior(household.size)
            val penetration = product.market.householdPenetration ?: config.defaultPenetration
            penetration * if (product.isStaple) config.stapleWeight else config.nonStapleWeight
        }

        var odds = toOdds(base)
        when (signals.seasonality[product.id]) {
            SeasonLevel.HIGH -> {
                odds *= config.highSeasonOdds
                reasons += ReasonCode.Seasonal
            }
            SeasonLevel.LOW -> odds *= config.lowSeasonOdds
            SeasonLevel.NORMAL, null -> Unit
        }
        signals.events[product.id]?.let { event ->
            odds *= event.probabilityBoost
            reasons += ReasonCode.Event(event.eventId)
        }
        if (context.guests > 0 && product.categoryId in signals.receptionCategories) odds *= config.guestsOdds
        val consecutiveNo = stats?.consecutiveNo ?: 0.0
        if (consecutiveNo > 0.0) odds *= config.consecutiveNoOdds.pow(consecutiveNo)

        var probability = (odds / (1.0 + odds)).coerceIn(0.0, 1.0)
        if (stats?.watch == true) {
            probability = maxOf(probability, config.watchFloor)
            reasons.add(0, ReasonCode.LittleLeft)
        }
        return NeedScore(probability, reasons, interval)
    }

    /**
     * Î_q: the household interval shrunk toward the size-corrected country prior, then corrected by the last
     * purchased quantity. A cadence set by the user wins as is. Null when nothing is known.
     */
    fun estimatedInterval(product: Product, stats: ProductStats?, household: HouseholdProfile): Double? {
        stats?.userCadenceDays?.let { return it.toDouble() }
        val prior = product.market.typicalRepurchaseDays?.let { it * householdSizeFactor(household) }
        val ewma = stats?.ewmaIntervalDays
        // n counts the observed intervals, i.e. purchases after the first one.
        val observedIntervals = ((stats?.nPurchases ?: 0) - 1).coerceAtLeast(0).toDouble()
        val interval = when {
            ewma != null && prior != null && observedIntervals + config.shrinkage > 0.0 ->
                (observedIntervals * ewma + config.shrinkage * prior) / (observedIntervals + config.shrinkage)
            ewma != null -> ewma
            else -> prior
        } ?: return null
        val last = stats?.lastQtyBase
        val average = stats?.ewmaQtyBase
        val correction = if (last != null && average != null) {
            (last / average).coerceIn(config.quantityCorrectionMin, config.quantityCorrectionMax)
        } else {
            1.0
        }
        return (interval * correction).takeIf { it > 0.0 }
    }

    /** `(P_ref / P_eq)^β`, bounded: bigger households run out sooner. */
    fun householdSizeFactor(household: HouseholdProfile): Double {
        val equivalentPersons = (household.adults + config.childWeight * household.children.size).coerceAtLeast(0.5)
        return (config.referenceHouseholdSize / equivalentPersons).pow(config.sizeExponent)
            .coerceIn(config.sizeFactorMin, config.sizeFactorMax)
    }

    private fun toOdds(probability: Double): Double {
        val p = probability.coerceIn(EPSILON, 1.0 - EPSILON)
        return p / (1.0 - p)
    }

    private companion object {
        const val EPSILON: Double = 1e-9
    }
}
