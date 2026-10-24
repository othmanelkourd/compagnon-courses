package com.compagnon.courses.core.model

import kotlin.jvm.JvmInline

// Typed identifiers. The catalog ids are the UUID strings served by the API.

@JvmInline
value class ProductId(val value: String)

@JvmInline
value class CategoryId(val value: String)

/** A storage zone (fridge, freezer, cupboard…). */
@JvmInline
value class ZoneId(val value: String)

/** A diet, allergen, label or storage tag (pork, alcohol, gluten…). */
@JvmInline
value class TagId(val value: String)

/** A calendar event (Ramadan, Christmas…). */
@JvmInline
value class EventId(val value: String)

/** ISO 3166-1 alpha-2 country code, e.g. `FR`. */
@JvmInline
value class CountryCode(val value: String)

@JvmInline
value class ListId(val value: String)

/** CLDR unit identifier (`kilogram`, `liter`, `fluid-ounce`) or internal package code (`can`, `bunch`). */
@JvmInline
value class UnitCode(val value: String) {
    init {
        require(value.isNotBlank()) { "A unit code cannot be blank" }
    }

    companion object {
        val GRAM: UnitCode = UnitCode("gram")
        val KILOGRAM: UnitCode = UnitCode("kilogram")
        val MILLILITER: UnitCode = UnitCode("milliliter")
        val LITER: UnitCode = UnitCode("liter")
        val PIECE: UnitCode = UnitCode("piece")
    }
}
