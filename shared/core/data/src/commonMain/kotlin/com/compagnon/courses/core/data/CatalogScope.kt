package com.compagnon.courses.core.data

/**
 * The (country, locale) projection of the catalog the app works with. [locale] must be one of the catalog
 * locales (`fr-FR`, `fr`, `en`…): see [forDevice].
 */
data class CatalogScope(val country: String, val locale: String) {
    init {
        require(country.length == 2 && country.all { it in 'A'..'Z' }) { "country must be an upper-case ISO code: $country" }
        require(locale.isNotBlank()) { "locale must not be blank" }
    }

    /** The language of [locale] (`fr`, `en`), for number formatting and plural rules. */
    val language: String get() = locale.lowercase().substringBefore('-')

    companion object {
        val DEFAULT: CatalogScope = CatalogScope(country = "FR", locale = "fr-FR")

        private val FRENCH_REGIONS = setOf("FR", "BE", "CH")

        /**
         * The catalog scope for a device language (`fr`, `en-GB`, `fr_CA`…) in [country]: French gets the regional
         * French catalog locale (`fr-FR`, `fr-BE`, `fr-CH`, else `fr`), every other language gets `en`.
         */
        fun forDevice(languageTag: String, country: String = DEFAULT.country): CatalogScope {
            val language = languageTag.lowercase().substringBefore('-').substringBefore('_')
            val locale = when {
                language != "fr" -> "en"
                country in FRENCH_REGIONS -> "fr-$country"
                else -> "fr"
            }
            return CatalogScope(country, locale)
        }
    }
}
