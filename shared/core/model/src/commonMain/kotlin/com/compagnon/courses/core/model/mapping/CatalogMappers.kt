package com.compagnon.courses.core.model.mapping

import com.compagnon.courses.core.catalogformat.CatalogSnapshotDto
import com.compagnon.courses.core.catalogformat.CategoryDto
import com.compagnon.courses.core.catalogformat.CycleClassDto
import com.compagnon.courses.core.catalogformat.DimensionDto
import com.compagnon.courses.core.catalogformat.PackageDto
import com.compagnon.courses.core.catalogformat.ProductDto
import com.compagnon.courses.core.catalogformat.StorageZoneDto
import com.compagnon.courses.core.catalogformat.UnitDto
import com.compagnon.courses.core.catalogformat.UnitPolicyDto
import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CountryCode
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.LocalizedName
import com.compagnon.courses.core.model.PackageFormat
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ProductMarket
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.Rational
import com.compagnon.courses.core.model.StorageZone
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.UnitDefinition
import com.compagnon.courses.core.model.UnitPolicy
import com.compagnon.courses.core.model.ZoneId

/** A catalog DTO that cannot be turned into a valid domain object. */
class CatalogMappingException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** An entity skipped while mapping a snapshot; [entity] is `unit`, `category`, `zone` or `product`. */
data class MappingIssue(val entity: String, val id: String, val message: String)

/** A mapped catalog plus the entities that were skipped because they were invalid. */
data class CatalogMappingResult(val catalog: Catalog, val issues: List<MappingIssue>)

fun DimensionDto.toDomain(): Dimension = when (this) {
    DimensionDto.MASS -> Dimension.MASS
    DimensionDto.VOLUME -> Dimension.VOLUME
    DimensionDto.COUNT -> Dimension.COUNT
    DimensionDto.PACKAGE -> Dimension.PACKAGE
}

fun CycleClassDto.toDomain(): CycleClass = when (this) {
    CycleClassDto.SHORT -> CycleClass.SHORT
    CycleClassDto.MEDIUM -> CycleClass.MEDIUM
    CycleClassDto.LONG -> CycleClass.LONG
}

/** @throws CatalogMappingException when the base factor is malformed or inconsistent with the dimension. */
fun UnitDto.toDomain(): UnitDefinition = mapping("unit $code") {
    UnitDefinition(
        code = UnitCode(code),
        dimension = dimension.toDomain(),
        toBase = toBase?.let { decimal ->
            Rational.parseOrNull(decimal) ?: throw CatalogMappingException("invalid toBase \"$decimal\"")
        },
        system = system,
        symbol = symbol,
        forms = forms,
    )
}

/** @throws CatalogMappingException when a decimal is malformed or the policy rules are violated. */
fun UnitPolicyDto.toDomain(): UnitPolicy = mapping("unit policy $unit") {
    val unitCode = UnitCode(unit)
    UnitPolicy(
        unit = unitCode,
        isDefault = isDefault,
        step = quantity("step", step, unitCode),
        presets = presets.map { quantity("preset", it, unitCode) },
        min = min?.let { quantity("min", it, unitCode) },
        max = max?.let { quantity("max", it, unitCode) },
    )
}

/** @throws CatalogMappingException when the content quantity is malformed. */
fun PackageDto.toDomain(): PackageFormat = mapping("package $packageUnit") {
    PackageFormat(
        packageUnit = UnitCode(packageUnit),
        content = quantity("contentQty", contentQty, UnitCode(contentUnit)),
        typical = typical,
    )
}

fun CategoryDto.toDomain(): Category = Category(
    id = CategoryId(id),
    slug = slug,
    parentId = parentId?.let(::CategoryId),
    position = position,
    name = name,
    gateQuestion = gateQuestion,
)

fun StorageZoneDto.toDomain(): StorageZone = StorageZone(
    id = ZoneId(id),
    slug = slug,
    position = position,
    name = name,
)

/**
 * Maps a projected product. When [units] is given, every unit the product references must be part of it.
 * The snapshot only carries products that are not unavailable, hence the [availability] default.
 *
 * @throws CatalogMappingException when the product is invalid.
 */
fun ProductDto.toDomain(
    units: UnitCatalog? = null,
    availability: Availability = Availability.AVAILABLE,
): Product = mapping("product $slug") {
    val policies = unitPolicies.map { it.toDomain() }
    val packageFormats = packages.map { it.toDomain() }
    if (units != null) {
        val referenced = policies.map { it.unit } + packageFormats.flatMap { listOf(it.packageUnit, it.content.unit) }
        val unknown = referenced.filterNot { it in units }.distinct()
        if (unknown.isNotEmpty()) {
            throw CatalogMappingException("unknown units ${unknown.joinToString { it.value }}")
        }
    }
    Product(
        id = ProductId(id),
        slug = slug,
        categoryId = CategoryId(categoryId),
        name = LocalizedName(name, forms),
        needPhrase = needPhrase,
        dimension = dimension.toDomain(),
        storageZone = ZoneId(storageZoneId),
        cycleClass = cycleClass.toDomain(),
        isStaple = isStaple,
        market = ProductMarket(
            availability = availability,
            typicalRepurchaseDays = typicalRepurchaseDays,
            householdPenetration = householdPenetration,
            isQuestionCandidate = isQuestionCandidate,
        ),
        unitPolicies = policies,
        packages = packageFormats,
    )
}

/**
 * Maps a catalog snapshot. Invalid entities (malformed decimals, broken unit policies, dangling references)
 * are skipped and reported in [CatalogMappingResult.issues] instead of failing the whole catalog.
 */
fun CatalogSnapshotDto.toDomain(): CatalogMappingResult {
    val issues = mutableListOf<MappingIssue>()

    val unitDefinitions = units.mapNotNull { dto ->
        dto.attempt(issues, entity = "unit", id = dto.code) { it.toDomain() }
    }.distinctBy { it.code }
    val unitCatalog = UnitCatalog(unitDefinitions)
    val domainCategories = categories.map { it.toDomain() }
    val zones = storageZones.map { it.toDomain() }
    val categoryIds = domainCategories.mapTo(HashSet()) { it.id }
    val zoneIds = zones.mapTo(HashSet()) { it.id }

    val domainProducts = products.mapNotNull { dto ->
        dto.attempt(issues, entity = "product", id = dto.id) {
            val product = it.toDomain(unitCatalog)
            when {
                product.categoryId !in categoryIds ->
                    throw CatalogMappingException("product ${it.slug}: unknown category ${it.categoryId}")
                product.storageZone !in zoneIds ->
                    throw CatalogMappingException("product ${it.slug}: unknown storage zone ${it.storageZoneId}")
                else -> product
            }
        }
    }

    return CatalogMappingResult(
        catalog = Catalog(
            country = CountryCode(country),
            locale = locale,
            version = version,
            categories = domainCategories,
            storageZones = zones,
            units = unitCatalog,
            products = domainProducts,
        ),
        issues = issues,
    )
}

private fun quantity(field: String, text: String, unit: UnitCode): Quantity =
    Quantity.parseOrNull(text, unit) ?: throw CatalogMappingException("invalid $field \"$text\"")

/** Runs [block], prefixing any validation failure with [context]. */
private inline fun <T> mapping(context: String, block: () -> T): T = try {
    block()
} catch (e: CatalogMappingException) {
    throw CatalogMappingException("$context: ${e.message}", e)
} catch (e: IllegalArgumentException) {
    throw CatalogMappingException("$context: ${e.message}", e)
} catch (e: ArithmeticException) {
    throw CatalogMappingException("$context: ${e.message}", e)
}

private inline fun <D, T> D.attempt(
    issues: MutableList<MappingIssue>,
    entity: String,
    id: String,
    block: (D) -> T,
): T? = try {
    block(this)
} catch (e: CatalogMappingException) {
    issues += MappingIssue(entity, id, e.message.orEmpty())
    null
}
