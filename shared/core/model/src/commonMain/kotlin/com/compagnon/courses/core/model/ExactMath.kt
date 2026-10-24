package com.compagnon.courses.core.model

/**
 * Overflow-checked integer arithmetic for quantities and unit factors. Every overflow throws
 * [ArithmeticException]; nothing silently wraps around.
 */
internal object ExactMath {
    private const val LOW_32_BITS: ULong = 0xFFFF_FFFFuL

    fun addExact(a: Long, b: Long): Long {
        val result = a + b
        if (((a xor result) and (b xor result)) < 0) throw ArithmeticException("Long overflow: $a + $b")
        return result
    }

    fun multiplyExact(a: Long, b: Long): Long {
        val result = a * b
        val absA = if (a < 0) -a else a
        val absB = if (b < 0) -b else b
        if ((absA or absB) ushr 31 != 0L) {
            if ((b != 0L && result / b != a) || (a == Long.MIN_VALUE && b == -1L)) {
                throw ArithmeticException("Long overflow: $a * $b")
            }
        }
        return result
    }

    fun gcd(a: Long, b: Long): Long {
        var x = if (a < 0) -a else a
        var y = if (b < 0) -b else b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    /** `10^exponent` for exponent in 0..18. */
    fun pow10(exponent: Int): Long {
        require(exponent in 0..18) { "10^$exponent does not fit in a Long" }
        var result = 1L
        repeat(exponent) { result *= 10 }
        return result
    }

    /**
     * `round(a × b / c)` computed with a 128-bit intermediate product, so it only fails when the
     * final result does not fit in a Long. Requires `a ≥ 0`, `b ≥ 0`, `c > 0`.
     */
    fun multiplyDivide(a: Long, b: Long, c: Long, rounding: Rounding): Long {
        require(a >= 0 && b >= 0) { "Operands must be non-negative: $a, $b" }
        require(c > 0) { "Divisor must be positive: $c" }
        val (high, low) = multiplyFull(a.toULong(), b.toULong())
        val divisor = c.toULong()
        if (high >= divisor) throw ArithmeticException("Overflow: $a * $b / $c")

        // Shift-subtract long division of the 128-bit (high:low) by a 64-bit divisor; high < divisor.
        var remainder = high
        var quotient = 0uL
        for (bit in 63 downTo 0) {
            val carry = (remainder shr 63) != 0uL
            remainder = (remainder shl 1) or ((low shr bit) and 1uL)
            quotient = quotient shl 1
            if (carry || remainder >= divisor) {
                remainder -= divisor // wraps correctly when carry is set: the true value is < divisor
                quotient = quotient or 1uL
            }
        }

        val roundUp = when (rounding) {
            Rounding.DOWN -> false
            Rounding.UP -> remainder != 0uL
            Rounding.HALF_UP -> remainder != 0uL && remainder >= divisor - remainder
            Rounding.HALF_EVEN -> {
                val distanceUp = divisor - remainder
                remainder > distanceUp || (remainder == distanceUp && (quotient and 1uL) == 1uL)
            }
        }
        val max = Long.MAX_VALUE.toULong()
        if (quotient > max || (roundUp && quotient == max)) throw ArithmeticException("Overflow: $a * $b / $c")
        return (if (roundUp) quotient + 1uL else quotient).toLong()
    }

    /** Compares `a × b` with `c × d` exactly (all operands non-negative). */
    fun compareProducts(a: Long, b: Long, c: Long, d: Long): Int {
        val (h1, l1) = multiplyFull(a.toULong(), b.toULong())
        val (h2, l2) = multiplyFull(c.toULong(), d.toULong())
        return if (h1 != h2) h1.compareTo(h2) else l1.compareTo(l2)
    }

    /** Full 128-bit product of two unsigned 64-bit values, as (high, low) words. */
    private fun multiplyFull(a: ULong, b: ULong): Pair<ULong, ULong> {
        val aLow = a and LOW_32_BITS
        val aHigh = a shr 32
        val bLow = b and LOW_32_BITS
        val bHigh = b shr 32
        val lowLow = aLow * bLow
        val lowHigh = aLow * bHigh
        val highLow = aHigh * bLow
        val highHigh = aHigh * bHigh
        val middle = (lowLow shr 32) + (lowHigh and LOW_32_BITS) + (highLow and LOW_32_BITS)
        val low = (lowLow and LOW_32_BITS) or (middle shl 32)
        val high = highHigh + (lowHigh shr 32) + (highLow shr 32) + (middle shr 32)
        return high to low
    }
}

/** A decimal literal split into an unscaled integer and a scale: "1.50" → (15, 1). */
internal class ParsedDecimal(val unscaled: Long, val scale: Int)

/**
 * Parses a canonical non-negative decimal (`digits[.digits]`, dot separator, no sign or exponent).
 * Trailing fractional zeros are dropped. Returns null when the text is malformed or too large.
 */
internal fun parseDecimal(text: String): ParsedDecimal? {
    val dot = text.indexOf('.')
    val integerPart = if (dot < 0) text else text.substring(0, dot)
    val fractionPart = if (dot < 0) "" else text.substring(dot + 1)
    if (integerPart.isEmpty() || (dot >= 0 && fractionPart.isEmpty())) return null
    if (!integerPart.all { it in '0'..'9' } || !fractionPart.all { it in '0'..'9' }) return null
    val significantFraction = fractionPart.trimEnd('0')
    if (significantFraction.length > 18) return null
    var unscaled = 0L
    try {
        for (digit in integerPart + significantFraction) {
            unscaled = ExactMath.addExact(ExactMath.multiplyExact(unscaled, 10), (digit - '0').toLong())
        }
    } catch (_: ArithmeticException) {
        return null
    }
    return ParsedDecimal(unscaled, significantFraction.length)
}
