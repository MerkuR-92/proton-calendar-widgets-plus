package me.proton.android.calendar.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class AppDatabaseMigrationSqlTest {

    private val migratedTables = setOf("events", "events_metadata", "events_occurrences", "event_alarms")

    // these two need a matching event now
    private val childTables = setOf("events_occurrences", "event_alarms")

    @Test
    fun `MIGRATION_83_84 matches the exported version 84 schema`() {
        val executed = recordStatements().map { it.normalised() }

        forEachExpectedStatement(version = 84) { table, expected ->
            assertTrue(expected.normalised() in executed) {
                "MIGRATION_83_84 has drifted from the entities for `$table`\nexpected: $expected"
            }
        }
    }

    // a refetch would take minutes, so the rows have to come along
    @Test
    fun `MIGRATION_83_84 copies the rows into the new tables`() {
        val executed = recordStatements()

        migratedTables.forEach { table ->
            assertTrue(executed.any { it.contains("ALTER TABLE $table RENAME TO ${table}_old") }) {
                "$table was not recreated"
            }
            assertTrue(executed.any { it.startsWith("INSERT INTO $table(") && it.contains("FROM ${table}_old") }) {
                "$table rows were not copied over"
            }
            assertTrue(executed.any { it.contains("DROP TABLE ${table}_old") }) { "$table left its old copy behind" }
        }
    }

    // before this, they could point at an event in another calendar
    @Test
    fun `MIGRATION_83_84 deletes rows with no event`() {
        val executed = recordStatements()

        childTables.forEach { table ->
            val delete = executed.singleOrNull { it.startsWith("DELETE FROM `$table`") }
            assertTrue(delete != null) { "$table keeps rows with no event" }
            assertTrue(delete!!.contains("`events`.`id` = `$table`.`eventId`")) { "$table is not matched on the event id" }
            assertTrue(delete.contains("`events`.`calendarId` = `$table`.`calendarId`")) {
                "$table is not matched on the calendar id"
            }
        }
    }

    // they expire by themselves, clearing them would force a refetch for nothing
    @Test
    fun `MIGRATION_83_84 does not touch the fetched windows`() {
        val executed = recordStatements()

        assertTrue(executed.none { it.contains("fetched_events_metadata") }) {
            "the fetched windows were cleared, so every upgrade pays for a full refetch"
        }
    }

    private fun recordStatements(): List<String> {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any()) } answers { statements.add(firstArg()) }
        AppDatabaseMigrations.MIGRATION_83_84.migrate(db)
        return statements
    }

    private fun forEachExpectedStatement(version: Int, assert: (table: String, sql: String) -> Unit) {
        val schema = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject
        val entities = schema.getValue("database").jsonObject.getValue("entities").jsonArray
        var checked = 0
        entities.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            if (table !in migratedTables) return@forEach
            checked++
            assert(table, entity.jsonObject.getValue("createSql").resolve(table))
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                assert(table, index.jsonObject.getValue("createSql").resolve(table))
            }
        }
        assertTrue(checked == migratedTables.size) { "expected ${migratedTables.size} tables in schema $version, found $checked" }
    }

    private fun JsonElement.resolve(table: String) = jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    private fun String.normalised() = trim().split(Regex("\\s+")).joinToString(" ")

    private fun schemaFile(version: Int): File {
        val relative = "schemas/${AppDatabase::class.java.name}/$version.json"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("exported schema $version.json not found - is room.schemaLocation wired into ksp?")
    }
}
