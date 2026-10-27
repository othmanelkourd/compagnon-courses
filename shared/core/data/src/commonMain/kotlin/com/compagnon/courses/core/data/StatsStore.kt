package com.compagnon.courses.core.data

import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.Household_product_pref
import com.compagnon.courses.core.database.Household_product_stats
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.PurchaseEvent
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import kotlin.time.Instant

/**
 * Blocking access to `household_product_stats` and `household_product_pref`, merged into [ProductStats]
 * (the engine's view of one product), and to `purchase_event`. Run it inside a transaction when writing.
 */
internal class StatsStore(private val database: CompagnonDatabase, private val ids: IdGenerator) {
    private val queries get() = database.householdQueries

    fun all(): Map<ProductId, ProductStats> {
        val stats = queries.selectAllStats().executeAsList().associateBy { it.product_id }
        val prefs = queries.selectAllPrefs().executeAsList().associateBy { it.product_id }
        return (stats.keys + prefs.keys).mapNotNull { id ->
            merge(stats[id], prefs[id])?.let { ProductId(id) to it }
        }.toMap()
    }

    fun get(productId: ProductId): ProductStats? = merge(
        queries.selectStats(productId.value).executeAsOneOrNull(),
        queries.selectPref(productId.value).executeAsOneOrNull(),
    )

    fun put(productId: ProductId, stats: ProductStats) {
        queries.upsertStats(
            productId = productId.value,
            nPurchases = stats.nPurchases.toLong(),
            lastAt = stats.lastAt?.toEpochMilliseconds(),
            ewmaIntervalDays = stats.ewmaIntervalDays,
            ewmaQtyBase = stats.ewmaQtyBase,
            lastQtyBase = stats.lastQtyBase,
            nextDueAt = stats.nextDueAt?.toEpochMilliseconds(),
            consecutiveNo = stats.consecutiveNo,
            lastNoAt = stats.lastNoAt?.toEpochMilliseconds(),
        )
        queries.upsertPref(
            productId = productId.value,
            usualQtyMilli = stats.usualQty?.milli,
            usualUnit = stats.usualQty?.unit?.value,
            neverAsk = stats.neverAsk.toLong(),
            snoozeUntil = stats.snoozeUntil?.toEpochMilliseconds(),
            watch = stats.watch.toLong(),
            askEveryDays = stats.userCadenceDays?.toLong(),
            householdStaple = stats.essential.toLong(),
        )
    }

    fun recordPurchase(event: PurchaseEvent) {
        queries.insertPurchase(
            id = ids.newId(),
            productId = event.productId.value,
            quantityMilli = event.quantity.milli,
            unit = event.quantity.unit.value,
            qtyBase = event.qtyBase,
            listId = event.listId?.value,
            occurredAt = event.at.toEpochMilliseconds(),
            source = event.source.wireName,
        )
    }

    private fun merge(stats: Household_product_stats?, pref: Household_product_pref?): ProductStats? {
        if (stats == null && pref == null) return null
        return try {
            ProductStats(
                nPurchases = stats?.n_purchases?.toInt() ?: 0,
                lastAt = stats?.last_at?.let(Instant::fromEpochMilliseconds),
                ewmaIntervalDays = stats?.ewma_interval_days,
                ewmaQtyBase = stats?.ewma_qty_base,
                lastQtyBase = stats?.last_qty_base,
                consecutiveNo = stats?.consecutive_no ?: 0.0,
                snoozeUntil = pref?.snooze_until?.let(Instant::fromEpochMilliseconds),
                watch = pref?.watch == 1L,
                neverAsk = pref?.never_ask == 1L,
                userCadenceDays = pref?.ask_every_days?.toInt(),
                usualQty = pref?.usual_qty_milli?.let { milli ->
                    pref.usual_unit?.let { unit -> Quantity(milli, UnitCode(unit)) }
                },
                nextDueAt = stats?.next_due_at?.let(Instant::fromEpochMilliseconds),
                lastNoAt = stats?.last_no_at?.let(Instant::fromEpochMilliseconds),
                essential = pref?.household_staple == 1L,
            )
        } catch (_: IllegalArgumentException) {
            // Out-of-range values (corrupted row): start again from no history rather than failing the session.
            null
        }
    }
}
