package com.compagnon.courses.core.data

import com.compagnon.courses.contract.CatalogSnapshotDto
import com.compagnon.courses.contract.CategoryDto
import com.compagnon.courses.contract.ContractJson
import com.compagnon.courses.contract.CycleClassDto
import com.compagnon.courses.contract.DimensionDto
import com.compagnon.courses.contract.PackageDto
import com.compagnon.courses.contract.ProductDto
import com.compagnon.courses.contract.StorageZoneDto
import com.compagnon.courses.contract.UnitDto
import com.compagnon.courses.contract.UnitPolicyDto
import com.compagnon.courses.core.database.Catalog_category
import com.compagnon.courses.core.database.Catalog_product
import com.compagnon.courses.core.database.Catalog_state
import com.compagnon.courses.core.database.Catalog_storage_zone
import com.compagnon.courses.core.database.Catalog_unit
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.mapping.CatalogMappingException
import com.compagnon.courses.core.model.mapping.MappingIssue
import com.compagnon.courses.core.model.mapping.toDomain
import com.compagnon.courses.core.model.text.normalizeForSearch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Blocking access to the local catalog tables. Rows keep the contract DTO shape (JSON columns for the nested
 * lists), so that reading them back reuses the contract → domain mappers. Callers run it off the main thread,
 * inside a transaction when they write.
 */
internal class CatalogStore(private val database: CompagnonDatabase) {
    private val catalog get() = database.catalogQueries
    private val state get() = database.catalogStateQueries

    fun currentState(): Catalog_state? = state.selectCurrent().executeAsOneOrNull()

    fun stateFor(country: String, locale: String): Catalog_state? = state.selectFor(country, locale).executeAsOneOrNull()

    /** Replaces the whole local catalog with [snapshot]. */
    fun replaceWith(snapshot: CatalogSnapshotDto, etag: String?, syncedAt: Long) {
        catalog.deleteAllProducts()
        catalog.deleteAllTags()
        catalog.deleteAllCategories()
        catalog.deleteAllStorageZones()
        catalog.deleteAllUnits()
        state.deleteAll()
        snapshot.units.forEach(::upsertUnit)
        snapshot.categories.forEach(::upsertCategory)
        snapshot.storageZones.forEach(::upsertZone)
        snapshot.products.forEach(::upsertProduct)
        state.upsert(snapshot.country, snapshot.locale, snapshot.version, etag, syncedAt)
    }

    fun upsertUnit(dto: UnitDto) = catalog.upsertUnit(
        code = dto.code,
        dimension = dto.dimension.name,
        system = dto.system,
        toBase = dto.toBase,
        symbol = dto.symbol,
        forms = encodeForms(dto.forms),
    )

    fun upsertCategory(dto: CategoryDto) = catalog.upsertCategory(
        id = dto.id,
        slug = dto.slug,
        parentId = dto.parentId,
        position = dto.position.toLong(),
        name = dto.name,
        gateQuestion = dto.gateQuestion,
    )

    fun upsertZone(dto: StorageZoneDto) = catalog.upsertStorageZone(dto.id, dto.slug, dto.position.toLong(), dto.name)

    fun upsertProduct(dto: ProductDto) {
        catalog.upsertProduct(
            id = dto.id,
            slug = dto.slug,
            categoryId = dto.categoryId,
            storageZoneId = dto.storageZoneId,
            dimension = dto.dimension.name,
            name = dto.name,
            forms = encodeForms(dto.forms),
            needPhrase = dto.needPhrase,
            cycleClass = dto.cycleClass.name,
            isStaple = dto.isStaple.toLong(),
            typicalRepurchaseDays = dto.typicalRepurchaseDays?.toLong(),
            householdPenetration = dto.householdPenetration,
            isQuestionCandidate = dto.isQuestionCandidate.toLong(),
            unitPolicies = ContractJson.encodeToString(POLICIES, dto.unitPolicies),
            packages = ContractJson.encodeToString(PACKAGES, dto.packages),
            searchText = searchTextOf(dto),
        )
        catalog.deleteTagsOf(dto.id)
        tagsOf(dto).forEach { catalog.insertTag(dto.id, it) }
    }

    /** Applies one tombstone; [entity] is `category`, `zone`, `unit` or `product`. Unknown entities are ignored. */
    fun delete(entity: String, id: String) {
        when (entity) {
            "category" -> catalog.deleteCategory(id)
            "zone", "storage_zone", "storageZone" -> catalog.deleteStorageZone(id)
            "unit" -> catalog.deleteUnit(id)
            "product" -> {
                catalog.deleteProduct(id)
                catalog.deleteTagsOf(id)
            }
        }
    }

    fun setState(country: String, locale: String, version: Long, etag: String?, syncedAt: Long) =
        state.upsert(country, locale, version, etag, syncedAt)

    fun touch(country: String, locale: String, syncedAt: Long) = state.touch(syncedAt, country, locale)

    fun productCount(): Long = catalog.countProducts().executeAsOne()

    fun categoryCount(): Long = catalog.countCategories().executeAsOne()

    /** The local catalog as domain objects; invalid rows are skipped (and reported in the result). */
    fun load(): LoadedCatalog? {
        val current = currentState() ?: return null
        val snapshot = CatalogSnapshotDto(
            country = current.country,
            locale = current.locale,
            version = current.version,
            categories = catalog.selectAllCategories().executeAsList().map { it.toDto() },
            storageZones = catalog.selectAllStorageZones().executeAsList().map { it.toDto() },
            units = catalog.selectAllUnits().executeAsList().mapNotNull { it.toDtoOrNull() },
            products = catalog.selectAllProducts().executeAsList().mapNotNull { it.toDtoOrNull() },
        )
        val mapped = snapshot.toDomain()
        val tags = catalog.selectAllTags().executeAsList().groupBy({ it.product_id }, { TagId(it.tag) })
        val products = if (tags.isEmpty()) {
            mapped.catalog.products
        } else {
            mapped.catalog.products.map { product ->
                tags[product.id.value]?.let { product.copy(tags = it.toSet()) } ?: product
            }
        }
        return LoadedCatalog(mapped.catalog.copy(products = products), mapped.issues)
    }

    /** The units of the local catalog (invalid rows skipped). */
    fun units(): UnitCatalog = UnitCatalog(
        catalog.selectAllUnits().executeAsList().mapNotNull { row ->
            val dto = row.toDtoOrNull() ?: return@mapNotNull null
            try {
                dto.toDomain()
            } catch (_: CatalogMappingException) {
                null
            }
        }.distinctBy { it.code },
    )

    /** One product of the local catalog, or null when unknown or invalid. */
    fun product(id: ProductId): Product? {
        val dto = catalog.selectProduct(id.value).executeAsOneOrNull()?.toDtoOrNull() ?: return null
        return try {
            dto.toDomain()
        } catch (_: CatalogMappingException) {
            null
        }
    }

    /** Products whose normalized name or plural forms contain [query] (prefix matches first). */
    fun search(query: String, limit: Int): List<Product> {
        val normalized = normalizeForSearch(query)
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val pattern = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return catalog.searchProducts(pattern, limit.toLong()).executeAsList().mapNotNull { row ->
            val dto = row.toDtoOrNull() ?: return@mapNotNull null
            try {
                dto.toDomain()
            } catch (_: CatalogMappingException) {
                null
            }
        }
    }

    private fun Catalog_category.toDto() = CategoryDto(
        id = id,
        slug = slug,
        parentId = parent_id,
        position = position.toInt(),
        name = name,
        gateQuestion = gate_question,
    )

    private fun Catalog_storage_zone.toDto() = StorageZoneDto(id, slug, position.toInt(), name)

    private fun Catalog_unit.toDtoOrNull(): UnitDto? {
        val dimension = enumOrNull<DimensionDto>(dimension) ?: return null
        return UnitDto(code, dimension, system, to_base, symbol, decodeForms(forms))
    }

    private fun Catalog_product.toDtoOrNull(): ProductDto? = try {
        ProductDto(
            id = id,
            slug = slug,
            categoryId = category_id,
            storageZoneId = storage_zone_id,
            dimension = enumOrNull<DimensionDto>(dimension) ?: return null,
            name = name,
            forms = decodeForms(forms),
            needPhrase = need_phrase,
            cycleClass = enumOrNull<CycleClassDto>(cycle_class) ?: return null,
            isStaple = is_staple != 0L,
            typicalRepurchaseDays = typical_repurchase_days?.toInt(),
            householdPenetration = household_penetration,
            isQuestionCandidate = is_question_candidate != 0L,
            unitPolicies = ContractJson.decodeFromString(POLICIES, unit_policies),
            packages = ContractJson.decodeFromString(PACKAGES, packages),
        )
    } catch (_: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException: a corrupted JSON column skips the product.
        null
    }

    private companion object {
        val FORMS = MapSerializer(String.serializer(), String.serializer())
        val POLICIES = ListSerializer(UnitPolicyDto.serializer())
        val PACKAGES = ListSerializer(PackageDto.serializer())

        fun encodeForms(forms: Map<String, String>): String = ContractJson.encodeToString(FORMS, forms)

        fun decodeForms(json: String): Map<String, String> = try {
            ContractJson.decodeFromString(FORMS, json)
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }

        fun searchTextOf(dto: ProductDto): String =
            normalizeForSearch((listOf(dto.name) + dto.forms.values).distinct().joinToString(" "))

        fun tagsOf(dto: ProductDto): List<String> = dto.tags

        inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
    }
}

/** The local catalog, plus the rows that could not be mapped (skipped). */
internal data class LoadedCatalog(val catalog: Catalog, val issues: List<MappingIssue>)

internal fun Boolean.toLong(): Long = if (this) 1L else 0L
