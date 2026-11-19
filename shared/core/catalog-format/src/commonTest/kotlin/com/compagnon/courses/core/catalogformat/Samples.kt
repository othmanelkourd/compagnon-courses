package com.compagnon.courses.core.catalogformat

/** Kotlin values matching the golden JSON documents of [GoldenJson], the catalog snapshot. */
internal object Samples {

    private val cremerie = CategoryDto(
        id = "4f9d8b1e-2c3a-3b5d-9e7f-0a1b2c3d4e5f",
        slug = "cremerie",
        position = 20,
        name = "Crèmerie",
        gateQuestion = "Besoin de quelque chose en crèmerie ?",
    )

    private val yaourts = CategoryDto(
        id = "7a6b5c4d-3e2f-3a1b-8c9d-0e1f2a3b4c5d",
        slug = "yaourts",
        parentId = "4f9d8b1e-2c3a-3b5d-9e7f-0a1b2c3d4e5f",
        position = 10,
        name = "Yaourts",
    )

    private val frigo = StorageZoneDto(
        id = "1b2c3d4e-5f6a-3b7c-8d9e-0f1a2b3c4d5e",
        slug = "frigo",
        position = 10,
        name = "Frigo",
    )

    private val liter = UnitDto(
        code = "liter",
        dimension = DimensionDto.VOLUME,
        system = "metric",
        toBase = "1000",
        symbol = "L",
        forms = mapOf("one" to "litre", "other" to "litres"),
    )

    private val bottle = UnitDto(
        code = "bottle",
        dimension = DimensionDto.PACKAGE,
        system = "any",
        symbol = "bout.",
        forms = mapOf("one" to "bouteille", "other" to "bouteilles"),
    )

    private val dozen = UnitDto(
        code = "dozen",
        dimension = DimensionDto.COUNT,
        system = "any",
        toBase = "12",
        symbol = "douz.",
        forms = mapOf("one" to "douzaine", "other" to "douzaines"),
    )

    private val milk = ProductDto(
        id = "9c8b7a6d-5e4f-3a2b-9c1d-0e2f3a4b5c6d",
        slug = "lait-demi-ecreme",
        categoryId = cremerie.id,
        storageZoneId = frigo.id,
        dimension = DimensionDto.VOLUME,
        name = "lait demi-écrémé",
        forms = mapOf("one" to "lait demi-écrémé", "other" to "laits demi-écrémés"),
        needPhrase = "du lait demi-écrémé",
        cycleClass = CycleClassDto.SHORT,
        isStaple = true,
        typicalRepurchaseDays = 6,
        householdPenetration = 0.85,
        isQuestionCandidate = true,
        unitPolicies = listOf(
            UnitPolicyDto(
                unit = "liter",
                isDefault = false,
                step = "1",
                presets = listOf("1", "2", "6"),
                min = "1",
                max = "24",
            ),
            UnitPolicyDto(unit = "bottle", isDefault = true, step = "1", presets = listOf("1", "2", "6")),
        ),
        packages = listOf(PackageDto(packageUnit = "bottle", contentQty = "1", contentUnit = "liter", typical = true)),
        tags = listOf(ProductTags.CONTAINS_LACTOSE),
    )

    private val eggs = ProductDto(
        id = "2d3e4f5a-6b7c-3d8e-9f0a-1b2c3d4e5f6a",
        slug = "oeufs",
        categoryId = cremerie.id,
        storageZoneId = frigo.id,
        dimension = DimensionDto.COUNT,
        name = "œufs",
        forms = mapOf("one" to "œuf", "other" to "œufs"),
        cycleClass = CycleClassDto.MEDIUM,
        isStaple = false,
        isQuestionCandidate = false,
        unitPolicies = listOf(
            UnitPolicyDto(unit = "dozen", isDefault = true, step = "0.5", presets = listOf("0.5", "1")),
        ),
    )

    val catalogSnapshot: CatalogSnapshotDto = CatalogSnapshotDto(
        country = "FR",
        locale = "fr-FR",
        version = 3,
        categories = listOf(cremerie, yaourts),
        storageZones = listOf(frigo),
        units = listOf(liter, bottle, dozen),
        products = listOf(milk, eggs),
    )
}
