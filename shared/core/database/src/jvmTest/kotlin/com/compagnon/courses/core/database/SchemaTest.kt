package com.compagnon.courses.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaTest {
    private val driver = createJvmDriver()
    private val database = createDatabase(driver)

    @Test
    fun theSchemaIsVersionThreeWithEveryTable() {
        assertEquals(3L, CompagnonDatabase.Schema.version)
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
                "purchase_event", "question_answer", "question_session", "question_session_event",
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
        assertFailsWith<Exception> {
            database.shoppingListQueries.insertItem("c", "l", "p2", "both", 1, "piece", 3, "manual", 0)
        }
        assertFailsWith<Exception> { database.shoppingListQueries.insertItem("d", "l", "p3", null, 1000, null, 4, "manual", 0) }
    }

    @Test
    fun onlyOneOpenLinePerProduct() {
        database.shoppingListQueries.insertList("l", null, 0)
        database.shoppingListQueries.insertItem("a", "l", "p", null, 1000, "kilogram", 1, "manual", 0)
        assertFailsWith<Exception> {
            database.shoppingListQueries.insertItem("b", "l", "p", null, 1000, "kilogram", 2, "manual", 0)
        }
        // A checked (or deleted) line no longer blocks a new one.
        database.shoppingListQueries.setChecked(5, "a")
        database.shoppingListQueries.insertItem("c", "l", "p", null, 1000, "kilogram", 3, "manual", 0)
        assertEquals(2, database.shoppingListQueries.selectItems("l").executeAsList().size)
    }

    @Test
    fun theProfileHasASingleRow() {
        val queries = database.householdQueries
        queries.upsertProfile(2, "[]", "FR", "METRIC", 7, "[]", "[]", 0, "[]", "OMNIVORE", "[]", 0, null)
        queries.upsertProfile(3, "[]", "FR", "METRIC", 7, "[]", "[]", 1, "[\"CAT\"]", "VEGETARIAN", "[]", 1, 1)
        val row = queries.selectProfile().executeAsOne()
        assertEquals(3L, row.adults)
        assertEquals("VEGETARIAN", row.diet)
        assertEquals(1L, row.onboarded_at)
    }

    @Test
    fun essentialsAndCadencesAreSetWithoutTouchingTheOtherPreferences() {
        val queries = database.householdQueries
        queries.upsertPref("milk", 2000, "liter", 0, null, 1, null, 0)
        queries.insertPrefIfMissing("milk")
        queries.insertPrefIfMissing("bread")
        queries.setEssential("milk")
        queries.setEssential("bread")
        queries.setCadence(7, "bread")
        assertEquals(listOf("bread", "milk"), queries.selectEssentialIds().executeAsList())
        val milk = queries.selectPref("milk").executeAsOne()
        assertEquals(2000L, milk.usual_qty_milli)
        assertEquals(1L, milk.watch)
        assertEquals(7L, queries.selectCadences().executeAsOne().ask_every_days)

        queries.clearEssentials()
        queries.clearCadences()
        assertTrue(queries.selectEssentialIds().executeAsList().isEmpty())
        assertTrue(queries.selectCadences().executeAsList().isEmpty())
        assertEquals(1L, queries.selectPref("milk").executeAsOne().watch)
    }

    @Test
    fun aVersionOneDatabaseMigratesWithoutLosingItsData() {
        val file = File.createTempFile("compagnon-v1", ".db").apply { deleteOnExit() }
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties())
        driver.execute(
            null,
            "INSERT INTO household_profile (id, adults, children, country, measurement_system, usual_horizon_days, " +
                "store_category_order, excluded_tags, updated_at) VALUES (1, 3, '[\"BABY\"]', 'FR', 'METRIC', 14, '[]', " +
                "'[\"contains-pork\"]', 5)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO household_product_pref (product_id, never_ask, watch, ask_every_days) VALUES ('milk', 1, 0, 10)",
            0,
        )

        CompagnonDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

        val migrated = createDatabase(driver).householdQueries
        val profile = migrated.selectProfile().executeAsOne()
        assertEquals(3L, profile.adults)
        assertEquals(14L, profile.usual_horizon_days)
        assertEquals("[\"contains-pork\"]", profile.excluded_tags)
        assertEquals("OMNIVORE", profile.diet)
        assertEquals("[]", profile.pets)
        assertEquals(0L, profile.essentials_chosen)
        assertNull(profile.onboarded_at)
        val pref = migrated.selectPref("milk").executeAsOne()
        assertEquals(1L, pref.never_ask)
        assertEquals(10L, pref.ask_every_days)
        assertEquals(0L, pref.household_staple)
        driver.close()
    }
}
