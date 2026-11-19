package com.compagnon.courses.core.catalogformat

/** Diet and allergen tags of a product ([ProductDto.tags]). The list is closed. */
object ProductTags {
    const val CONTAINS_PORK: String = "contains-pork"
    const val CONTAINS_ALCOHOL: String = "contains-alcohol"
    const val CONTAINS_GLUTEN: String = "contains-gluten"
    const val CONTAINS_LACTOSE: String = "contains-lactose"
    const val CONTAINS_MEAT: String = "contains-meat"
    const val CONTAINS_FISH: String = "contains-fish"
    const val CONTAINS_NUTS: String = "contains-nuts"
    const val CONTAINS_EGGS: String = "contains-eggs"
    const val VEGAN: String = "vegan"
    const val BABY: String = "baby"
    const val PET: String = "pet"

    val ALL: List<String> = listOf(
        CONTAINS_PORK, CONTAINS_ALCOHOL, CONTAINS_GLUTEN, CONTAINS_LACTOSE, CONTAINS_MEAT, CONTAINS_FISH,
        CONTAINS_NUTS, CONTAINS_EGGS, VEGAN, BABY, PET,
    )
}
