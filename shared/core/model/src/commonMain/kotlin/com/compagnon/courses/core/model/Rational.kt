package com.compagnon.courses.core.model

/**
 * An exact non-negative fraction, always stored in lowest terms. Used for unit factors
 * (1 lb = 45359237/100000 g) and quantity scaling, so that no conversion ever goes through a Double.
 * Operations throw [ArithmeticException] when a result does not fit in 64-bit integers.
 */
class Rational private constructor(val numerator: Long, val denominator: Long) : Comparable<Rational> {

    val isZero: Boolean get() = numerator == 0L

    operator fun times(other: Rational): Rational {
        if (isZero || other.isZero) return ZERO
        val g1 = ExactMath.gcd(numerator, other.denominator)
        val g2 = ExactMath.gcd(other.numerator, denominator)
        return Rational(
            ExactMath.multiplyExact(numerator / g1, other.numerator / g2),
            ExactMath.multiplyExact(denominator / g2, other.denominator / g1),
        )
    }

    operator fun div(other: Rational): Rational {
        if (other.isZero) throw ArithmeticException("Division by zero")
        return times(Rational(other.denominator, other.numerator))
    }

    /** `round(value × this)`; [value] must be non-negative. */
    fun multiply(value: Long, rounding: Rounding = Rounding.HALF_EVEN): Long =
        ExactMath.multiplyDivide(value, numerator, denominator, rounding)

    override fun compareTo(other: Rational): Int =
        ExactMath.compareProducts(numerator, other.denominator, other.numerator, denominator)

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = if (denominator == 1L) "$numerator" else "$numerator/$denominator"

    companion object {
        val ZERO: Rational = Rational(0, 1)
        val ONE: Rational = Rational(1, 1)

        fun of(numerator: Long, denominator: Long = 1): Rational {
            require(numerator >= 0) { "A rational must be non-negative: $numerator/$denominator" }
            require(denominator > 0) { "A rational needs a positive denominator: $numerator/$denominator" }
            if (numerator == 0L) return ZERO
            val g = ExactMath.gcd(numerator, denominator)
            return Rational(numerator / g, denominator / g)
        }

        /** Parses a canonical decimal such as `"453.59237"`; null when malformed or out of range. */
        fun parseOrNull(decimal: String): Rational? {
            val parsed = parseDecimal(decimal) ?: return null
            return of(parsed.unscaled, ExactMath.pow10(parsed.scale))
        }

        /** Parses a canonical decimal such as `"453.59237"`. */
        fun parse(decimal: String): Rational =
            parseOrNull(decimal) ?: throw IllegalArgumentException("Invalid decimal \"$decimal\"")
    }
}
