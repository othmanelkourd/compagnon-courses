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

    /**
     * How a product is bought in [unit], for the unit selector of a card: « Au poids » (masses), « Au litre »,
     * « À la pièce », « À la douzaine », else « En barquettes », « En packs »… from the unit's plural form.
     * English: « By weight », « By the liter », « By the piece », « By the dozen », « In trays ». Other languages
     * use English until they get their own phrases.
     */
    fun buyingLabel(unit: UnitCode): String {
        val french = language == "fr"
        val definition = units[unit] ?: return unit.value
        return when {
            definition.dimension == Dimension.MASS -> if (french) "Au poids" else "By weight"
            unit == UnitCode.LITER -> if (french) "Au litre" else "By the liter"
            unit == UnitCode.PIECE -> if (french) "À la pièce" else "By the piece"
            unit == DOZEN -> if (french) "À la douzaine" else "By the dozen"
            else -> {
                val plural = definition.forms[LocalizedName.PLURAL_OTHER] ?: definition.symbol
                (if (french) "En " else "In ") + plural
            }
        }
    }

    companion object {
        private val DOZEN = UnitCode("dozen")

        const val NO_BREAK_SPACE: Char = ' '
    }
}

/** « pommes » → « Pommes »: catalog names are lower case, the UI capitalizes them. */
fun String.capitalizedFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
