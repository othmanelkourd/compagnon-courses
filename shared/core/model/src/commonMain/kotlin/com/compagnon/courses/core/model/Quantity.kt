package com.compagnon.courses.core.model

/**
 * An exact, non-negative amount expressed in thousandths of [unit]: 1.5 kg is `Quantity(1500, kilogram)`.
 * Quantities never use floating point; arithmetic throws [ArithmeticException] on overflow.
 */
data class Quantity(val milli: Long, val unit: UnitCode) : Comparable<Quantity> {
    init {
        require(milli >= 0) { "A quantity cannot be negative: $milli milli-${unit.value}" }
    }

    val isZero: Boolean get() = milli == 0L

    operator fun plus(other: Quantity): Quantity {
        requireSameUnit(other)
        return Quantity(ExactMath.addExact(milli, other.milli), unit)
    }

    operator fun times(factor: Long): Quantity {
        require(factor >= 0) { "A quantity cannot be scaled by a negative factor: $factor" }
        return Quantity(ExactMath.multiplyExact(milli, factor), unit)
    }

    /** Orders quantities of the same unit; comparing different units is a programming error. */
    override fun compareTo(other: Quantity): Int {
        requireSameUnit(other)
        return milli.compareTo(other.milli)
    }

    /** Canonical decimal form without trailing zeros: `"1.5"`, `"2"`, `"0.25"`. */
    fun toDecimalString(): String {
        val whole = milli / SCALE
        val fraction = milli % SCALE
        if (fraction == 0L) return whole.toString()
        return "$whole." + fraction.toString().padStart(DECIMALS, '0').trimEnd('0')
    }

    override fun toString(): String = "${toDecimalString()} ${unit.value}"

    private fun requireSameUnit(other: Quantity) {
        require(unit == other.unit) { "Unit mismatch: ${unit.value} vs ${other.unit.value}" }
    }

    companion object {
        /** Number of milli units in one unit. */
        const val SCALE: Long = 1000L

        /** Number of decimals a quantity can carry. */
        const val DECIMALS: Int = 3

        /** `whole` units, e.g. `Quantity.of(2, liter)`. */
        fun of(whole: Long, unit: UnitCode): Quantity = Quantity(ExactMath.multiplyExact(whole, SCALE), unit)

        /**
         * Parses the canonical wire form of a decimal (`"1.5"`, `"2"`, `"0.250"`): digits, an optional dot and
         * at most [DECIMALS] significant decimals. Returns null for anything else (sign, comma, exponent…).
         */
        fun parseOrNull(text: String, unit: UnitCode): Quantity? {
            val parsed = parseDecimal(text) ?: return null
            if (parsed.scale > DECIMALS) return null
            val milli = try {
                ExactMath.multiplyExact(parsed.unscaled, ExactMath.pow10(DECIMALS - parsed.scale))
            } catch (_: ArithmeticException) {
                return null
            }
            return Quantity(milli, unit)
        }

        /** Like [parseOrNull] but throws [IllegalArgumentException] on invalid input. */
        fun parse(text: String, unit: UnitCode): Quantity =
            parseOrNull(text, unit) ?: throw IllegalArgumentException(
                "Invalid quantity \"$text\": expected a non-negative decimal with at most $DECIMALS decimals",
            )
    }
}
