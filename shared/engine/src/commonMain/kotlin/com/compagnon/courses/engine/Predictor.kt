package com.compagnon.courses.engine

import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.PurchaseSource
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCatalog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * Coefficients of the statistics updates (`docs/question-engine.md` §6.4 and §6.5). To be calibrated.
 *
 * @property alpha EWMA weight of a new observation (interval and quantity).
 * @property implicitWeight weight of an implicit purchase (a « yes » never checked).
 * @property sameTripHours two purchases closer than that belong to the same trip: no interval is observed.
 * @property noDelayFactor after a « no », the product is due again after `max(factor × Î, minNoDelay)`.
 * @property gateNoPenalty `consecutiveNo` increment of the products behind a gate answered no.
 */
data class PredictorConfig(
    val alpha: Double = 0.3,
    val implicitWeight: Double = 0.5,
    val sameTripHours: Int = 12,
    val noDelayFactor: Double = 0.5,
    val minNoDelay: Duration = 3.days,
    val gateNoPenalty: Double = 0.5,
) {
    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be within (0, 1]: $alpha" }
        require(implicitWeight > 0.0 && implicitWeight <= 1.0) { "implicitWeight must be within (0, 1]" }
        require(sameTripHours >= 0 && noDelayFactor >= 0.0 && gateNoPenalty >= 0.0) { "Invalid predictor settings" }
    }
}

/**
 * Updates the statistics and preferences of one (household, product) from purchases and answers. Pure: the
 * caller persists the result. It never throws on valid statistics.
 */
class Predictor(
    private val config: PredictorConfig = PredictorConfig(),
    scoreConfig: ScoreConfig = ScoreConfig(),
) {
    private val scorer = NeedScorer(scoreConfig)

    /**
     * A confirmed (or implicit) purchase: EWMA of the interval since the previous purchase and of the quantity
     * (in the base unit of the product's dimension), `consecutiveNo` reset, « il en reste un peu » cleared.
     * A purchase within [PredictorConfig.sameTripHours] of the previous one is the same trip: its quantity is
     * added to the last one and no interval is observed.
     */
    fun onPurchase(
        stats: ProductStats?,
        product: Product,
        quantity: Quantity,
        at: Instant,
        units: UnitCatalog,
        source: PurchaseSource = PurchaseSource.LIST_CHECKED,
    ): ProductStats {
        val current = stats ?: ProductStats()
        val weight = if (source == PurchaseSource.IMPLICIT) config.implicitWeight else 1.0
        val alpha = config.alpha * weight
        val qtyBase = baseQuantity(product, quantity, units)
        val lastAt = current.lastAt

        if (lastAt != null && at - lastAt < config.sameTripHours.hours) {
            val lastQty = sum(current.lastQtyBase, qtyBase)
            return current.copy(
                lastAt = maxOf(lastAt, at),
                lastQtyBase = lastQty,
                ewmaQtyBase = current.ewmaQtyBase ?: lastQty,
                consecutiveNo = 0.0,
                watch = false,
                nextDueAt = null,
            )
        }

        val interval = lastAt?.let { (at - it).toDouble(DurationUnit.DAYS) }?.takeIf { it > 0.0 }
        return current.copy(
            nPurchases = current.nPurchases + 1,
            lastAt = if (lastAt == null || at > lastAt) at else lastAt,
            ewmaIntervalDays = ewma(current.ewmaIntervalDays, interval, alpha),
            ewmaQtyBase = ewma(current.ewmaQtyBase, qtyBase, alpha),
            lastQtyBase = qtyBase ?: current.lastQtyBase,
            consecutiveNo = 0.0,
            watch = false,
            nextDueAt = null,
        )
    }

    /**
     * The effect of one product answer (§6.4). [quantity] is the answered quantity of a « yes », [days] the
     * horizon of « j'en ai encore pour… ». Meta and gate answers do not change a product: see [onGateNo].
     */
    fun onAnswer(
        stats: ProductStats?,
        product: Product,
        kind: AnswerKind,
        now: Instant,
        household: HouseholdProfile,
        quantity: Quantity? = null,
        days: Int? = null,
    ): ProductStats {
        val current = stats ?: ProductStats()
        return when (kind) {
            AnswerKind.YES -> current.copy(
                usualQty = quantity?.takeIf { it.milli > 0 } ?: current.usualQty,
                consecutiveNo = 0.0,
                watch = false,
                snoozeUntil = null,
            )
            AnswerKind.NO -> {
                val interval = scorer.estimatedInterval(product, current, household)
                val delay = interval?.let { (it * config.noDelayFactor).days }
                    ?.coerceAtLeast(config.minNoDelay) ?: config.minNoDelay
                current.copy(
                    consecutiveNo = current.consecutiveNo + 1.0,
                    lastNoAt = now,
                    nextDueAt = now + delay,
                    watch = false,
                )
            }
            AnswerKind.LITTLE_LEFT -> current.copy(watch = true)
            AnswerKind.HAVE_ENOUGH -> current.copy(
                snoozeUntil = days?.takeIf { it > 0 }?.let { now + it.days } ?: current.snoozeUntil,
                watch = false,
            )
            AnswerKind.NEVER -> current.copy(neverAsk = true, watch = false)
            AnswerKind.LATER, AnswerKind.NOT_THIS_TIME, AnswerKind.META, AnswerKind.GATE_YES, AnswerKind.GATE_NO ->
                current
        }
    }

    /** A product behind a category gate answered no: `consecutiveNo` grows by [PredictorConfig.gateNoPenalty]. */
    fun onGateNo(stats: ProductStats?): ProductStats {
        val current = stats ?: ProductStats()
        return current.copy(consecutiveNo = current.consecutiveNo + config.gateNoPenalty)
    }

    /** [quantity] in the base unit of the product's dimension (g, mL, piece), or null when not convertible. */
    fun baseQuantity(product: Product, quantity: Quantity, units: UnitCatalog): Double? {
        val base = units.baseUnit(product.dimension) ?: return null
        val converted = units.convertOrNull(quantity, base.code, product.packages) ?: return null
        return (converted.milli.toDouble() / Quantity.SCALE).takeIf { it > 0.0 && it.isFinite() }
    }

    private fun ewma(previous: Double?, observation: Double?, alpha: Double): Double? = when {
        observation == null -> previous
        previous == null -> observation
        else -> alpha * observation + (1.0 - alpha) * previous
    }

    private fun sum(a: Double?, b: Double?): Double? = when {
        a == null -> b
        b == null -> a
        else -> a + b
    }
}
