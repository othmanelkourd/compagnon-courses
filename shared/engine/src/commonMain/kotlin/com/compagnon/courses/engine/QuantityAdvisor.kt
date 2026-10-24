package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.Rational
import com.compagnon.courses.core.model.Rounding
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitPolicy
import kotlin.math.roundToLong

/** Where the base of a proposal came from (step 7.1). */
enum class QuantitySource {
    /** The last answered quantity, in the same unit. */
    USUAL,

    /** The household's smoothed quantity, converted to the policy unit. */
    HISTORY_AVERAGE,

    /** The first preset of the unit policy. */
    PRESET,

    /** One step of the unit policy. */
    STEP,

    /** The quantity of the last list (« Reprendre ma dernière liste »), kept as is. */
    LAST_LIST,
}

/**
 * Scaling of step 7.2: `q = q_base × (H / H_usual) × (persons / household persons)`.
 * [NONE] keeps the base quantity.
 */
data class QuantityScaling(
    val horizonDays: Int,
    val usualHorizonDays: Int,
    val persons: Int,
    val householdPersons: Int,
) {
    init {
        require(horizonDays > 0 && usualHorizonDays > 0 && persons > 0 && householdPersons > 0) {
            "Scaling terms must be positive"
        }
    }

    /** The exact scaling factor. */
    val factor: Rational
        get() = Rational.of(
            horizonDays.toLong() * persons.toLong(),
            usualHorizonDays.toLong() * householdPersons.toLong(),
        )

    companion object {
        val NONE: QuantityScaling = QuantityScaling(1, 1, 1, 1)
    }
}

/**
 * A proposed quantity: the main answer (« Oui · 2 L ») plus up to three quick choices around it.
 * Every quantity is a multiple of the policy step within its bounds.
 *
 * @property isSameAsLastTime the main button repeats the previous answer (« Comme la dernière fois »).
 * @property alternatives the other unit policies of the product (bulk / pack toggle).
 */
data class QuantityProposal(
    val policy: UnitPolicy,
    val quantity: Quantity,
    val choices: List<Quantity>,
    val source: QuantitySource,
    val isSameAsLastTime: Boolean,
    val alternatives: List<UnitPolicy>,
)

/**
 * Proposes the quantity of a product (step 7): base selection, scaling, rounding to the step (up for a
 * count or a package, to the nearest for a mass or a volume, ties going up) and clamping to the bounds.
 * It never throws: overflows saturate at the policy bounds.
 */
class QuantityAdvisor(private val units: UnitCatalog = UnitCatalog.EMPTY) {

    /** Null when the product has no unit policy. */
    fun propose(
        product: Product,
        stats: ProductStats?,
        scaling: QuantityScaling = QuantityScaling.NONE,
    ): QuantityProposal? {
        val usual = stats?.usualQty?.takeIf { it.milli > 0 }
        val usualPolicy = usual?.let { product.policyFor(it.unit) }
        if (usual != null && usualPolicy != null) {
            val quantity = fit(product, usualPolicy, usual, scaling.factor)
            return proposal(product, usualPolicy, quantity, QuantitySource.USUAL, sameAsLast = quantity == usual)
        }

        val policy = product.defaultPolicy ?: return null
        val history = stats?.ewmaQtyBase?.let { historyBase(product, policy, it) }
        val (base, source) = when {
            history != null -> history to QuantitySource.HISTORY_AVERAGE
            policy.presets.isNotEmpty() -> policy.presets.first() to QuantitySource.PRESET
            else -> policy.step to QuantitySource.STEP
        }
        return proposal(product, policy, fit(product, policy, base, scaling.factor), source, sameAsLast = false)
    }

    /** A proposal for an exact [quantity] (last list, user edit), fitted to the policy of its unit. */
    fun proposeExact(product: Product, quantity: Quantity): QuantityProposal? {
        val policy = product.policyFor(quantity.unit) ?: return null
        return proposal(product, policy, fit(product, policy, quantity, Rational.ONE), QuantitySource.LAST_LIST, false)
    }

    /** Rounds `base × factor` to a whole number of steps of [policy] and clamps it to the bounds. */
    fun fit(product: Product, policy: UnitPolicy, base: Quantity, factor: Rational): Quantity {
        val step = policy.step.milli
        val upper = policy.maxSteps ?: (Long.MAX_VALUE / step)
        val steps = try {
            (factor * Rational.of(base.milli, step)).multiply(1L, roundingFor(product, policy))
        } catch (_: ArithmeticException) {
            upper
        }
        return policy.quantityOf(steps.coerceIn(policy.minSteps, upper))
    }

    private fun proposal(
        product: Product,
        policy: UnitPolicy,
        quantity: Quantity,
        source: QuantitySource,
        sameAsLast: Boolean,
    ): QuantityProposal = QuantityProposal(
        policy = policy,
        quantity = quantity,
        choices = quickChoices(policy, quantity),
        source = source,
        isSameAsLastTime = sameAsLast,
        alternatives = product.unitPolicies.filter { it != policy },
    )

    /** Up for counts and packages (never short of an egg), nearest for masses and volumes. */
    private fun roundingFor(product: Product, policy: UnitPolicy): Rounding =
        when (units[policy.unit]?.dimension ?: product.dimension) {
            Dimension.COUNT, Dimension.PACKAGE -> Rounding.UP
            Dimension.MASS, Dimension.VOLUME -> Rounding.HALF_UP
        }

    /** The smoothed quantity (base unit of the product's dimension) converted to the policy unit. */
    private fun historyBase(product: Product, policy: UnitPolicy, ewmaQtyBase: Double): Quantity? {
        if (!ewmaQtyBase.isFinite() || ewmaQtyBase <= 0.0 || ewmaQtyBase >= MAX_BASE_UNITS) return null
        val baseUnit = units.baseUnit(product.dimension) ?: return null
        val inBase = Quantity((ewmaQtyBase * Quantity.SCALE).roundToLong(), baseUnit.code)
        return units.convertOrNull(inBase, policy.unit, product.packages)?.takeIf { it.milli > 0 }
    }

    /**
     * Up to three distinct quantities around [quantity] (included), extended on one side when the other hits a
     * bound. Within the range of the presets the neighbours are presets (« 1 · 2 · 6 »); outside it, or when no
     * preset is left on a side, they are one step away.
     */
    private fun quickChoices(policy: UnitPolicy, quantity: Quantity): List<Quantity> {
        val step = policy.step.milli
        val lowest = policy.minSteps * step
        val highest = policy.maxSteps?.let { it * step } ?: Long.MAX_VALUE
        val presets = policy.presets.map { it.milli }.distinct().sorted()
        val usePresets = presets.isNotEmpty() && quantity.milli in presets.first()..presets.last()
        fun stepBelow(value: Long): Long? = (value - step).takeIf { it >= lowest }
        fun stepAbove(value: Long): Long? = if (value <= highest - step) value + step else null
        fun below(value: Long): Long? =
            (if (usePresets) presets.lastOrNull { it < value } else null) ?: stepBelow(value)
        fun above(value: Long): Long? =
            (if (usePresets) presets.firstOrNull { it > value } else null) ?: stepAbove(value)

        val choices = mutableListOf(quantity.milli)
        below(quantity.milli)?.let { choices.add(0, it) }
        above(quantity.milli)?.let { choices.add(it) }
        while (choices.size < MAX_CHOICES) {
            val next = above(choices.last())
            if (next != null) {
                choices.add(next)
                continue
            }
            val previous = below(choices.first()) ?: break
            choices.add(0, previous)
        }
        return choices.map { Quantity(it, policy.unit) }
    }

    private companion object {
        const val MAX_CHOICES: Int = 3

        /** Above that (in g, mL or pieces) a smoothed quantity is garbage, not a household purchase. */
        const val MAX_BASE_UNITS: Double = 1e12
    }
}
