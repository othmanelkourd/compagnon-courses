package com.compagnon.courses.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.Household_profile
import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CountryCode
import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.MeasurementSystem
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.engine.MetaKey
import com.compagnon.courses.engine.SessionContext
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The household as the app knows it.
 *
 * @property usualHorizonDays how many days a shopping usually covers (pre-fills « Courses pour combien de jours ? »).
 * @property storeCategoryOrder categories in the order of the household's store; the others follow by position.
 */
data class Household(
    val profile: HouseholdProfile,
    val usualHorizonDays: Int = SessionContext.DEFAULT_HORIZON_DAYS,
    val storeCategoryOrder: List<CategoryId> = emptyList(),
) {
    init {
        require(SessionContext.isValid(MetaKey.HORIZON_DAYS, usualHorizonDays)) {
            "usualHorizonDays out of range: $usualHorizonDays"
        }
    }

    companion object {
        /** Until onboarding exists: two adults, metric. */
        fun default(country: String): Household = Household(
            profile = HouseholdProfile(adults = 2, country = CountryCode(country), system = MeasurementSystem.METRIC),
        )
    }
}

/** The household profile (a single row); [Household.default] until it is saved once. */
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

    suspend fun household(): Household = withContext(context) { read() }

    suspend fun update(household: Household) = withContext(context) {
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
        )
    }

    /** Blocking read, for use inside a transaction. */
    internal fun read(): Household = queries.selectProfile().executeAsOneOrNull().toHousehold()

    private fun Household_profile?.toHousehold(): Household {
        if (this == null) return Household.default(country)
        return try {
            Household(
                profile = HouseholdProfile(
                    adults = adults.toInt(),
                    children = strings(children).mapNotNull { name -> AgeBand.entries.firstOrNull { it.name == name } },
                    country = CountryCode(this.country),
                    system = MeasurementSystem.entries.firstOrNull { it.name == measurement_system } ?: MeasurementSystem.METRIC,
                    excludedTags = strings(excluded_tags).map(::TagId).toSet(),
                ),
                usualHorizonDays = usual_horizon_days.toInt(),
                storeCategoryOrder = strings(store_category_order).map(::CategoryId),
            )
        } catch (_: IllegalArgumentException) {
            Household.default(this@HouseholdRepository.country)
        }
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
