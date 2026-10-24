package com.compagnon.courses.core.model.format

import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.LocalizedName
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitCode

/**
 * CLDR plural categories (`one`, `other`) of the catalog languages, for the forms of units and products.
 * Only French and English are needed in V0; other languages use the English rule.
 */
object PluralRules {
    /**
     * French: `one` when the integer part is 0 or 1 (« 0,5 kilo », « 1,5 kilo »). English: `one` for exactly 1
     * without visible decimals.
     */
    fun categoryOf(language: String, quantity: Quantity): String {
        val whole = quantity.milli / Quantity.SCALE
        val hasFraction = quantity.milli % Quantity.SCALE != 0L
        val one = when (language.lowercase().substringBefore('-')) {
            "fr" -> whole == 0L || whole == 1L
            else -> whole == 1L && !hasFraction
        }
        return if (one) LocalizedName.PLURAL_ONE else LocalizedName.PLURAL_OTHER
    }
}

/**
 * Formats quantities for display in the catalog [locale] (`fr-FR`, `fr`, `en`…): « 1,5 kg », « 2 bouteilles »,
 * « 6 œufs ». Masses and volumes use the unit symbol; counts and packages use the plural form of the unit, or of
 * the product itself for pieces. A no-break space separates the number from its unit.
 */
class QuantityFormatter(private val units: UnitCatalog, locale: String) {
    private val language: String = locale.lowercase().substringBefore('-')
    private val decimalSeparator: Char = if (language == "fr") ',' else '.'

    /** « 1,5 » in French, « 1.5 » in English; no grouping, at most three decimals, no trailing zeros. */
    fun formatNumber(quantity: Quantity): String {
        val text = quantity.toDecimalString()
        return if (decimalSeparator == '.') text else text.replace('.', decimalSeparator)
    }

    /** The unit as written after [quantity] (« kg », « bouteilles », « œufs » for [product] counted in pieces). */
    fun unitLabel(quantity: Quantity, product: Product? = null): String {
        val category = PluralRules.categoryOf(language, quantity)
        val definition = units[quantity.unit]
        return when {
            definition == null -> quantity.unit.value
            definition.dimension == Dimension.MASS || definition.dimension == Dimension.VOLUME -> definition.symbol
            quantity.unit == UnitCode.PIECE && product != null -> product.name.form(category)
            else -> definition.forms[category] ?: definition.forms[LocalizedName.PLURAL_OTHER] ?: definition.symbol
        }
    }

    /** « 1,5 kg », « 2 bouteilles », « 6 œufs ». */
    fun format(quantity: Quantity, product: Product? = null): String =
        formatNumber(quantity) + NO_BREAK_SPACE + unitLabel(quantity, product)

    companion object {
        const val NO_BREAK_SPACE: Char = ' '
    }
}

/** « pommes » → « Pommes »: catalog names are lower case, the UI capitalizes them. */
fun String.capitalizedFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
