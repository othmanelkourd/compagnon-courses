package com.compagnon.courses.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaTest {
    private val driver = createJvmDriver()
    private val database = createDatabase(driver)

    @Test
    fun theSchemaIsVersionOneWithEveryTable() {
        assertEquals(1L, CompagnonDatabase.Schema.version)
        val tables = driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            mapper = { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) names += cursor.getString(0)!!
                app.cash.sqldelight.db.QueryResult.Value(names)
            },
            parameters = 0,
        ).value
        assertEquals(
            listOf(
                "catalog_category", "catalog_product", "catalog_product_tag", "catalog_state", "catalog_storage_zone",
                "catalog_unit", "household_product_pref", "household_product_stats", "household_profile", "list_item",
                "outbox", "purchase_event", "question_answer", "question_session", "question_session_event",
                "shopping_list",
            ),
            tables,
        )
    }

    @Test
    fun aListItemIsEitherAProductOrAFreeText() {
        database.shoppingListQueries.insertList("l", null, 0)
        database.shoppingListQueries.insertItem("a", "l", "p", null, 1000, "kilogram", 1, "manual", 0)
        database.shoppingListQueries.insertItem("b", "l", null, "piles", null, null, 2, "manual", 0)
        assertFailsWith<Exception> { database.shoppingListQueries.insertItem("c", "l", "p2", "both", 1, "piece", 3, "manual", 0) }
        assertFailsWith<Exception> { database.shoppingListQueries.insertItem("d", "l", "p3", null, 1000, null, 4, "manual", 0) }
    }

    @Test
    fun onlyOneOpenLinePerProduct() {
        database.shoppingListQueries.insertList("l", null, 0)
        database.shoppingListQueries.insertItem("a", "l", "p", null, 1000, "kilogram", 1, "manual", 0)
        assertFailsWith<Exception> { database.shoppingListQueries.insertItem("b", "l", "p", null, 1000, "kilogram", 2, "manual", 0) }
        // A checked (or deleted) line no longer blocks a new one.
        database.shoppingListQueries.setChecked(5, "a")
        database.shoppingListQueries.insertItem("c", "l", "p", null, 1000, "kilogram", 3, "manual", 0)
        assertEquals(2, database.shoppingListQueries.selectItems("l").executeAsList().size)
    }

    @Test
    fun theProfileHasASingleRow() {
        val queries = database.householdQueries
        queries.upsertProfile(2, "[]", "FR", "METRIC", 7, "[]", "[]", 0)
        queries.upsertProfile(3, "[]", "FR", "METRIC", 7, "[]", "[]", 1)
        assertEquals(3L, queries.selectProfile().executeAsOne().adults)
        assertTrue(database.outboxQueries.countPending().executeAsOne() == 0L)
    }
}
