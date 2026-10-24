package com.compagnon.courses.core.model

/**
 * Allowed quantities of a product in one [unit] for a country or measurement system.
 *
 * Invariants: [step] is positive; every preset is a positive multiple of [step] within [[min], [max]]; and at
 * least one multiple of [step] lies within the bounds, so a policy can always propose a valid quantity.
 */
data class UnitPolicy(
    val unit: UnitCode,
    val isDefault: Boolean,
    val step: Quantity,
    val presets: List<Quantity>,
    val min: Quantity? = null,
    val max: Quantity? = null,
) {
    init {
        require(step.unit == unit && (min == null || min.unit == unit) && (max == null || max.unit == unit)) {
            "Unit policy ${unit.value}: step and bounds must be expressed in ${unit.value}"
        }
        require(step.milli > 0) { "Unit policy ${unit.value}: step must be positive" }
        require(min == null || max == null || min <= max) {
            "Unit policy ${unit.value}: min ${min?.toDecimalString()} > max ${max?.toDecimalString()}"
        }
        require(maxSteps.let { it == null || it >= minSteps }) {
            "Unit policy ${unit.value}: no multiple of ${step.toDecimalString()} within the bounds"
        }
        presets.forEach { preset ->
            require(isAllowed(preset)) {
                "Unit policy ${unit.value}: preset ${preset.toDecimalString()} must be a positive multiple of " +
                    "${step.toDecimalString()} within the bounds"
            }
        }
    }

    /** Smallest allowed number of steps; at least 1, so a proposal is never zero. */
    val minSteps: Long get() = maxOf(1L, min?.let { ceilDiv(it.milli, step.milli) } ?: 1L)

    /** Largest allowed number of steps, or null when there is no maximum. */
    val maxSteps: Long? get() = max?.let { it.milli / step.milli }

    /** `steps × step`, in [unit]. */
    fun quantityOf(steps: Long): Quantity = step * steps

    /** True when [quantity] is a positive multiple of [step] within the bounds. */
    fun isAllowed(quantity: Quantity): Boolean {
        if (quantity.unit != unit || quantity.milli <= 0 || quantity.milli % step.milli != 0L) return false
        val steps = quantity.milli / step.milli
        val upper = maxSteps
        return steps >= minSteps && (upper == null || steps <= upper)
    }

    private fun ceilDiv(a: Long, b: Long): Long = a / b + if (a % b == 0L) 0 else 1
}

/** A package of a product: 1 [packageUnit] (bottle) holds [content] (1 L). */
data class PackageFormat(
    val packageUnit: UnitCode,
    val content: Quantity,
    val typical: Boolean,
) {
    init {
        require(content.milli > 0) { "Package ${packageUnit.value} must have a positive content" }
        require(content.unit != packageUnit) { "Package ${packageUnit.value} cannot contain itself" }
    }
}
