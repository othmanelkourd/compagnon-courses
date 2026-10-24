package com.compagnon.courses.core.model

/**
 * A unit of the catalog.
 *
 * @property toBase exact factor to the base unit of [dimension] (g, mL or piece): 1 kg = 1000 g. Null for
 *   packages (can, bottle…), which convert only through a product's [PackageFormat].
 * @property system `metric`, `us`, `imperial` or `universal` (informational).
 * @property forms CLDR plural category (`one`, `other`, …) to the localized form.
 */
data class UnitDefinition(
    val code: UnitCode,
    val dimension: Dimension,
    val toBase: Rational?,
    val system: String = "universal",
    val symbol: String = code.value,
    val forms: Map<String, String> = emptyMap(),
) {
    init {
        if (dimension == Dimension.PACKAGE) {
            require(toBase == null) { "Package unit ${code.value} cannot have a base factor" }
        } else {
            require(toBase != null && !toBase.isZero) { "Unit ${code.value} needs a positive base factor" }
        }
    }

    val isBase: Boolean get() = toBase == Rational.ONE
}

/**
 * The units of a catalog, with exact conversions between them.
 *
 * Units of the same dimension convert through their base factor. A package unit converts only through the
 * [PackageFormat]s of a product (1 bottle = 1 L of milk), passed as `packages`. Results are rounded to the
 * milli unit with [Rounding.HALF_EVEN] unless stated otherwise.
 */
class UnitCatalog(units: Iterable<UnitDefinition>) {
    private val byCode: Map<UnitCode, UnitDefinition> = buildMap {
        for (unit in units) {
            require(put(unit.code, unit) == null) { "Duplicate unit ${unit.code.value}" }
        }
    }

    val units: Collection<UnitDefinition> get() = byCode.values

    operator fun get(code: UnitCode): UnitDefinition? = byCode[code]

    operator fun contains(code: UnitCode): Boolean = code in byCode

    /** The base unit (factor 1) of [dimension], preferring gram, milliliter and piece. */
    fun baseUnit(dimension: Dimension): UnitDefinition? {
        val preferred = when (dimension) {
            Dimension.MASS -> UnitCode.GRAM
            Dimension.VOLUME -> UnitCode.MILLILITER
            Dimension.COUNT -> UnitCode.PIECE
            Dimension.PACKAGE -> return null
        }
        return byCode[preferred]?.takeIf { it.isBase && it.dimension == dimension }
            ?: byCode.values.filter { it.dimension == dimension && it.isBase }.minByOrNull { it.code.value }
    }

    /** The dimension [code] measures, resolving package units through [packages]; null when unknown. */
    fun dimensionOf(code: UnitCode, packages: List<PackageFormat> = emptyList()): Dimension? =
        resolve(code, packages)?.dimension ?: byCode[code]?.dimension

    /** Exact factor such that 1 [from] = factor × [to], or null when the units are not convertible. */
    fun factor(from: UnitCode, to: UnitCode, packages: List<PackageFormat> = emptyList()): Rational? {
        if (from == to) return Rational.ONE
        val source = resolve(from, packages) ?: return null
        val target = resolve(to, packages) ?: return null
        if (source.dimension != target.dimension) return null
        return source.toBase / target.toBase
    }

    fun canConvert(from: UnitCode, to: UnitCode, packages: List<PackageFormat> = emptyList()): Boolean =
        factor(from, to, packages) != null

    /**
     * Converts [quantity] to [target].
     *
     * @throws IllegalArgumentException when the units are unknown or measure different dimensions.
     * @throws ArithmeticException when the result does not fit in a [Quantity].
     */
    fun convert(
        quantity: Quantity,
        target: UnitCode,
        packages: List<PackageFormat> = emptyList(),
        rounding: Rounding = Rounding.HALF_EVEN,
    ): Quantity {
        if (quantity.unit == target) return quantity
        val factor = factor(quantity.unit, target, packages)
            ?: throw IllegalArgumentException("Cannot convert ${quantity.unit.value} to ${target.value}")
        return Quantity(factor.multiply(quantity.milli, rounding), target)
    }

    /** Like [convert], but returns null instead of throwing. */
    fun convertOrNull(
        quantity: Quantity,
        target: UnitCode,
        packages: List<PackageFormat> = emptyList(),
        rounding: Rounding = Rounding.HALF_EVEN,
    ): Quantity? = try {
        convert(quantity, target, packages, rounding)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private class Resolved(val dimension: Dimension, val toBase: Rational)

    private fun resolve(code: UnitCode, packages: List<PackageFormat>): Resolved? {
        val definition = byCode[code]
        if (definition?.toBase != null) return Resolved(definition.dimension, definition.toBase)
        val candidates = packages.filter { it.packageUnit == code }
        val format = candidates.firstOrNull { it.typical } ?: candidates.firstOrNull() ?: return null
        val content = byCode[format.content.unit] ?: return null
        val contentFactor = content.toBase ?: return null // nested packages are not convertible
        return try {
            Resolved(content.dimension, Rational.of(format.content.milli, Quantity.SCALE) * contentFactor)
        } catch (_: ArithmeticException) {
            null
        }
    }

    override fun equals(other: Any?): Boolean = other is UnitCatalog && byCode == other.byCode

    override fun hashCode(): Int = byCode.hashCode()

    override fun toString(): String = "UnitCatalog(${byCode.keys.joinToString { it.value }})"

    companion object {
        val EMPTY: UnitCatalog = UnitCatalog(emptyList())
    }
}
