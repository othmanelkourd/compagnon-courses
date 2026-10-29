package com.compagnon.courses.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.Household_profile
import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Avoidance
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CountryCode
import com.compagnon.courses.core.model.Diet
import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.MeasurementSystem
import com.compagnon.courses.core.model.Pet
import com.compagnon.courses.core.model.PetKind
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.engine.MetaKey
import com.compagnon.courses.engine.SessionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The household as the app knows it.
 *
 * @property usualHorizonDays how many days a shopping usually covers (pre-fills « Courses pour combien de jours ? »).
 * @property storeCategoryOrder categories in the order of the household's store; the others follow by position.
 * @property onboardedAt when the onboarding « Mon foyer » was first saved; null until then (it is shown at launch).
 */
data class Household(
    val profile: HouseholdProfile,
    val usualHorizonDays: Int = SessionContext.DEFAULT_HORIZON_DAYS,
    val storeCategoryOrder: List<CategoryId> = emptyList(),
    val onboardedAt: Instant? = null,
) {
    init {
        require(SessionContext.isValid(MetaKey.HORIZON_DAYS, usualHorizonDays)) {
            "usualHorizonDays out of range: $usualHorizonDays"
        }
    }

    companion object {
        /** Before the onboarding: two adults, metric, the catalog's essentials. */
        fun default(country: String): Household = Household(
            profile = HouseholdProfile(adults = 2, country = CountryCode(country), system = MeasurementSystem.METRIC),
        )
    }
}

/**
 * What the onboarding « Mon foyer » asks, saved at once by [HouseholdRepository.saveSetup].
 *
 * @property essentials the household's essentials; null keeps the current ones (step skipped). Once set, they
 *   replace the catalog's `isStaple` products for this household.
 * @property cadences products bought on a rhythm (product → days); null keeps the current ones.
 * @property usualHorizonDays how often the household shops (7, 14, 30 days or another duration).
 */
data class HouseholdSetup(
    val adults: Int,
    val children: List<AgeBand> = emptyList(),
    val pets: Set<PetKind> = emptySet(),
    val diet: Diet = Diet.OMNIVORE,
    val avoid: Set<Avoidance> = emptySet(),
    val essentials: Set<ProductId>? = null,
    val cadences: Map<ProductId, Int>? = null,
    val usualHorizonDays: Int = SessionContext.DEFAULT_HORIZON_DAYS,
) {
    init {
        require(adults >= 0 && adults + children.size >= 1) { "A household has at least one member" }
        require(SessionContext.isValid(MetaKey.HORIZON_DAYS, usualHorizonDays)) {
            "usualHorizonDays out of range: $usualHorizonDays"
        }
        require(cadences.orEmpty().values.all { it > 0 }) { "Cadences must be positive" }
    }

    val size: Int get() = adults + children.size
}

/**
 * The household profile (a single row), [Household.default] until it is saved once, plus the household's
 * essentials and cadences (`household_product_pref`).
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class HouseholdRepository(
    private val database: CompagnonDatabase,
    private val country: String = CatalogScope.DEFAULT.country,
    private val clock: Clock = Clock.System,
    private val context: CoroutineContext = Dispatchers.Default,
) {
    private val queries get() = database.householdQueries

    fun observe(): Flow<Household> = queries.selectProfile().asFlow().mapToOneOrNull(context).map { it.toHousehold() }

    /** True once the onboarding was saved (or put off with « Plus tard »). */
    fun observeOnboarded(): Flow<Boolean> = observe().map { it.onboardedAt != null }.distinctUntilChanged()

    /** The household's essentials (empty until it chose them, see [HouseholdProfile.customEssentials]). */
    fun observeEssentials(): Flow<Set<ProductId>> = queries.selectEssentialIds().asFlow().mapToList(context)
        .map { ids -> ids.mapTo(LinkedHashSet(), ::ProductId) }

    /** Products bought on a rhythm declared by the household (product → days). */
    fun observeCadences(): Flow<Map<ProductId, Int>> = queries.selectCadences().asFlow().mapToList(context)
        .map { rows -> rows.mapNotNull { row -> row.ask_every_days?.toInt()?.let { ProductId(row.product_id) to it } }.toMap() }

    suspend fun household(): Household = withContext(context) { read() }

    /** The current answers, to pre-fill « Mon foyer ». */
    suspend fun setup(): HouseholdSetup = withContext(context) {
        val household = read()
        val profile = household.profile
        HouseholdSetup(
            adults = profile.adults,
            children = profile.children,
            pets = profile.pets.mapTo(LinkedHashSet()) { it.kind },
            diet = profile.diet,
            avoid = profile.avoid,
            essentials = if (profile.customEssentials) {
                queries.selectEssentialIds().executeAsList().mapTo(LinkedHashSet(), ::ProductId)
            } else {
                null
            },
            cadences = queries.selectCadences().executeAsList()
                .mapNotNull { row -> row.ask_every_days?.toInt()?.takeIf { it > 0 }?.let { ProductId(row.product_id) to it } }
                .toMap(),
            usualHorizonDays = household.usualHorizonDays,
        )
    }

    suspend fun update(household: Household) = withContext(context) { write(household) }

    /**
     * Saves the onboarding in one transaction: the profile (the first [Household.onboardedAt] is kept), the
     * essentials and the cadences when they are not null, and closes the open question session: it was planned
     * for the former household and could not be rebuilt the same way.
     */
    suspend fun saveSetup(setup: HouseholdSetup) = withContext(context) {
        val now = clock.now()
        database.transaction {
            val current = read()
            val customEssentials = setup.essentials != null || current.profile.customEssentials
            write(
                current.copy(
                    profile = current.profile.copy(
                        adults = setup.adults,
                        children = setup.children,
                        pets = setup.pets.sortedBy { it.ordinal }.map(::Pet),
                        diet = setup.diet,
                        avoid = setup.avoid,
                        customEssentials = customEssentials,
                    ),
                    usualHorizonDays = setup.usualHorizonDays,
                    onboardedAt = current.onboardedAt ?: now,
                ),
            )
            setup.essentials?.let { essentials ->
                queries.clearEssentials()
                essentials.forEach { id ->
                    queries.insertPrefIfMissing(id.value)
                    queries.setEssential(id.value)
                }
            }
            setup.cadences?.let { cadences ->
                queries.clearCadences()
                cadences.forEach { (id, days) ->
                    queries.insertPrefIfMissing(id.value)
                    queries.setCadence(days.toLong(), id.value)
                }
            }
            database.questionQueries.selectOpenSession().executeAsOneOrNull()?.let { open ->
                database.questionQueries.abandonSession(
                    abandonedAt = now.toEpochMilliseconds(),
                    asked = open.asked,
                    answered = open.answered,
                    id = open.id,
                )
            }
        }
    }

    /** « Plus tard »: the onboarding is not shown again; the household keeps its defaults. */
    suspend fun skipSetup() = withContext(context) {
        database.transaction {
            val current = read()
            if (current.onboardedAt == null) write(current.copy(onboardedAt = clock.now()))
        }
    }

    /** Blocking read, for use inside a transaction. */
    internal fun read(): Household = queries.selectProfile().executeAsOneOrNull().toHousehold()

    private fun write(household: Household) {
        val profile = household.profile
        queries.upsertProfile(
            adults = profile.adults.toLong(),
            children = json.encodeToString(STRINGS, profile.children.map { it.name }),
            country = profile.country.value,
            measurementSystem = profile.system.name,
            usualHorizonDays = household.usualHorizonDays.toLong(),
            storeCategoryOrder = json.encodeToString(STRINGS, household.storeCategoryOrder.map { it.value }),
            excludedTags = json.encodeToString(STRINGS, profile.excludedTags.map { it.value }.sorted()),
            updatedAt = clock.now().toEpochMilliseconds(),
            pets = json.encodeToString(STRINGS, profile.pets.map { it.kind.name }),
            diet = profile.diet.name,
            avoid = json.encodeToString(STRINGS, profile.avoid.sortedBy { it.ordinal }.map { it.name }),
            essentialsChosen = profile.customEssentials.toLong(),
            onboardedAt = household.onboardedAt?.toEpochMilliseconds(),
        )
    }

    private fun Household_profile?.toHousehold(): Household {
        if (this == null) return Household.default(country)
        val fallback = Household.default(this@HouseholdRepository.country)
        // An unknown value falls back to the default of its own field only.
        val profile = try {
            HouseholdProfile(
                adults = adults.toInt(),
                children = strings(children).mapNotNull { name -> AgeBand.entries.firstOrNull { it.name == name } },
                pets = strings(pets).mapNotNull { name -> PetKind.entries.firstOrNull { it.name == name } }.map(::Pet),
                country = CountryCode(this.country),
                system = MeasurementSystem.entries.firstOrNull { it.name == measurement_system } ?: MeasurementSystem.METRIC,
                excludedTags = strings(excluded_tags).map(::TagId).toSet(),
                diet = Diet.entries.firstOrNull { it.name == diet } ?: Diet.OMNIVORE,
                avoid = strings(avoid).mapNotNullTo(LinkedHashSet()) { name -> Avoidance.entries.firstOrNull { it.name == name } },
                customEssentials = essentials_chosen == 1L,
            )
        } catch (_: IllegalArgumentException) {
            fallback.profile
        }
        return Household(
            profile = profile,
            usualHorizonDays = usual_horizon_days.toInt()
                .takeIf { SessionContext.isValid(MetaKey.HORIZON_DAYS, it) } ?: fallback.usualHorizonDays,
            storeCategoryOrder = strings(store_category_order).map(::CategoryId),
            onboardedAt = onboarded_at?.let(Instant::fromEpochMilliseconds),
        )
    }

    private fun strings(text: String): List<String> = try {
        json.decodeFromString(STRINGS, text)
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val STRINGS = ListSerializer(String.serializer())
    }
}
