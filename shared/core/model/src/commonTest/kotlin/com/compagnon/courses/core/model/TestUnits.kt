package com.compagnon.courses.core.model

/** The units of the seed with their exact definition constants (`docs/data-model.md` §5.3). */
internal object TestUnits {
    val BOTTLE = UnitCode("bottle")
    val BOX = UnitCode("box")
    val CAN = UnitCode("can")
    val DOZEN = UnitCode("dozen")
    val POUND = UnitCode("pound")
    val OUNCE = UnitCode("ounce")
    val FLUID_OUNCE = UnitCode("fluid-ounce")
    val FLUID_OUNCE_IMPERIAL = UnitCode("fluid-ounce-imperial")
    val GALLON = UnitCode("gallon")
    val GALLON_IMPERIAL = UnitCode("gallon-imperial")
    val PINT = UnitCode("pint")
    val PINT_IMPERIAL = UnitCode("pint-imperial")
    val CENTILITER = UnitCode("centiliter")

    val definitions: List<UnitDefinition> = listOf(
        unit(UnitCode.GRAM, Dimension.MASS, "1"),
        unit(UnitCode.KILOGRAM, Dimension.MASS, "1000"),
        unit(POUND, Dimension.MASS, "453.59237"),
        unit(OUNCE, Dimension.MASS, "28.349523125"),
        unit(UnitCode.MILLILITER, Dimension.VOLUME, "1"),
        unit(CENTILITER, Dimension.VOLUME, "10"),
        unit(UnitCode.LITER, Dimension.VOLUME, "1000"),
        unit(FLUID_OUNCE, Dimension.VOLUME, "29.5735295625"),
        unit(FLUID_OUNCE_IMPERIAL, Dimension.VOLUME, "28.4130625"),
        unit(PINT, Dimension.VOLUME, "473.176473"),
        unit(PINT_IMPERIAL, Dimension.VOLUME, "568.26125"),
        unit(GALLON, Dimension.VOLUME, "3785.411784"),
        unit(GALLON_IMPERIAL, Dimension.VOLUME, "4546.09"),
        unit(UnitCode.PIECE, Dimension.COUNT, "1"),
        unit(DOZEN, Dimension.COUNT, "12"),
        UnitDefinition(BOTTLE, Dimension.PACKAGE, toBase = null),
        UnitDefinition(BOX, Dimension.PACKAGE, toBase = null),
        UnitDefinition(CAN, Dimension.PACKAGE, toBase = null),
    )

    val catalog: UnitCatalog = UnitCatalog(definitions)

    private fun unit(code: UnitCode, dimension: Dimension, toBase: String) =
        UnitDefinition(code, dimension, Rational.parse(toBase))
}

internal fun qty(text: String, unit: UnitCode): Quantity = Quantity.parse(text, unit)
