package me.proton.android.calendar.test.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.AppDatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val dbName = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val userId = "u1"
    private val calendarA = "calA"
    private val calendarB = "calB"

    @Test
    fun migrates_from_83_and_matches_the_expected_schema() {
        seedV83().close()

        val db = helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84)

        assertEquals(listOf("id", "calendarId"), db.primaryKeyOf("events"))
        assertEquals(listOf("id", "calendarId"), db.primaryKeyOf("events_metadata"))
        db.close()
    }

    // a refetch would take minutes, so nothing may be dropped
    @Test
    fun keeps_all_rows() {
        seedV83().close()

        val db = helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84)

        assertEquals(2, db.countOf("events"))
        assertEquals(2, db.countOf("events_metadata"))
        assertEquals(2, db.countOf("events_occurrences"))
        assertEquals(2, db.countOf("event_alarms"))
        assertEquals(2, db.countOf("calendars"))
        db.close()
    }

    @Test
    fun keeps_the_event_data() {
        seedV83().close()

        val db = helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84)

        val sharedEventIds = db.query("SELECT sharedEventId FROM `events` ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(listOf("shared-e1", "shared-e2"), sharedEventIds)
        db.close()
    }

    // this alarm points at an event in another calendar, so it has no event of its own
    @Test
    fun drops_alarms_with_no_event() {
        seedV83().apply {
            // e1 only exists in calendarA
            execSQL("INSERT INTO `event_alarms` VALUES ('alarm-orphan', 100, '-PT15M', 2, 'e1', 'm1', '$calendarB')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84)

        val alarmIds = db.query("SELECT id FROM `event_alarms` ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(listOf("alarm-e1", "alarm-e2"), alarmIds)
        db.close()
    }

    // they expire by themselves, clearing them would force a refetch for nothing
    @Test
    fun keeps_the_fetched_windows() {
        seedV83().close()
        helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84).close()

        val db = openRealDatabase()
        try {
            assertEquals(2, db.openHelper.writableDatabase.countOf("fetched_events_metadata"))

            // the query shouldFetch() is built on, true means "already cached, no need to fetch"
            val stillConsideredFetched = runBlocking {
                db.fetchedEventsMetadataDao().hasWindowFullyOverlappingAt(
                    userId = userId,
                    calendarId = calendarA,
                    windowStart = WINDOW_START,
                    windowEnd = WINDOW_END,
                    nowMs = Instant.now().toEpochMilli()
                )
            }
            assertTrue("the rows are still there, so the window must stay fetched", stillConsideredFetched)
        } finally {
            db.close()
        }
    }

    // the new foreign keys are the point of all this, so check nothing is left dangling
    @Test
    fun leaves_no_broken_foreign_keys() {
        seedV83().apply {
            execSQL("INSERT INTO `event_alarms` VALUES ('alarm-orphan', 100, '-PT15M', 2, 'e1', 'm1', '$calendarB')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84)

        val violations = db.query("PRAGMA foreign_key_check").use { it.count }
        assertEquals(0, violations)
        db.close()
    }

    // _id values are copied over, so the next one must not clash with them
    @Test
    fun does_not_reuse_occurrence_ids() {
        seedV83().close()
        helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84).close()

        val db = openRealDatabase()
        try {
            val writable = db.openHelper.writableDatabase
            val copiedMax = writable.maxOccurrenceId()
            writable.execSQL(
                "INSERT INTO `events_occurrences` (userId, calendarId, eventId, eventUid, fullDay, startTime, endTime," +
                    " windowStartTime, windowEndTime, firstOccurrenceStartTime, modifyTime, startTimeZone, endTimeZone, exDates)" +
                    " VALUES ('$userId', '$calendarA', 'e1', 'uid@proton.me', 0, 300, 400, 0, 1000, 300, 0, 'UTC', 'UTC', '')"
            )
            assertTrue("the new occurrence reused an existing id", writable.maxOccurrenceId() > copiedMax)
        } finally {
            db.close()
        }
    }

    private fun openRealDatabase(): AppDatabase =
        androidx.room.Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(AppDatabaseMigrations.MIGRATION_83_84).build()

    private fun seedV83(): SupportSQLiteDatabase = helper.createDatabase(dbName, 83).apply {
        // a calendar needs a user and a user needs an account
        execSQL("INSERT INTO `AccountEntity` (`userId`, `state`) VALUES ('$userId', 'Ready')")
        execSQL(
            "INSERT INTO `UserEntity` (`userId`, `currency`, `credit`, `createdAtUtc`, `usedSpace`, " +
                "`maxSpace`, `maxUpload`, `private`, `subscribed`, `services`) " +
                "VALUES ('$userId', 'EUR', 0, 0, 0, 0, 0, 1, 0, 0)"
        )
        execSQL("INSERT INTO `calendars` (`id`, `type`, `fkUserId`) VALUES ('$calendarA', 0, '$userId')")
        execSQL("INSERT INTO `calendars` (`id`, `type`, `fkUserId`) VALUES ('$calendarB', 0, '$userId')")

        // one event per calendar, like a single-shard install would have
        insertV83Event("e1", calendarA)
        insertV83Event("e2", calendarB)
        for ((eventId, calendarId) in listOf("e1" to calendarA, "e2" to calendarB)) {
            insertV83Metadata(eventId, calendarId)
            execSQL(
                "INSERT INTO `events_occurrences` (userId, calendarId, eventId, eventUid, fullDay, startTime, endTime," +
                    " windowStartTime, windowEndTime, firstOccurrenceStartTime, modifyTime, startTimeZone, endTimeZone, exDates)" +
                    " VALUES ('$userId', '$calendarId', '$eventId', 'uid@proton.me', 0, 100, 200, 0, 1000, 100, 0, 'UTC', 'UTC', '')"
            )
            execSQL(
                "INSERT INTO `event_alarms` VALUES ('alarm-$eventId', 100, '-PT15M', 2, '$eventId', 'm1', '$calendarId')"
            )
            execSQL(
                "INSERT INTO `fetched_events_metadata` (userId, calendarId, windowStartTime, windowEndTime, validUntilMs)" +
                    " VALUES ('$userId', '$calendarId', $WINDOW_START, $WINDOW_END, ${Long.MAX_VALUE})"
            )
        }
    }

    private fun SupportSQLiteDatabase.insertV83Event(id: String, calendarId: String) = execSQL(
        "INSERT INTO `events` (id, calendarId, sharedEventId, createTime, modifyTime, permissions," +
            " sharedEvents, calendarEvents, attendeesEvents, attendees, isProtonProtonInvite)" +
            " VALUES ('$id', '$calendarId', 'shared-$id', 0, 0, 0, '[]', '[]', '[]', '[]', 0)"
    )

    private fun SupportSQLiteDatabase.insertV83Metadata(id: String, calendarId: String) = execSQL(
        "INSERT INTO `events_metadata` (id, calendarId, sharedEventId, startTime, startTimeZone, endTime," +
            " endTimeZone, fullDay, uid, exDates, createTime, modifyTime, isOrganizer, isPersonalSingleEdit)" +
            " VALUES ('$id', '$calendarId', 'shared-$id', 100, 'UTC', 200, 'UTC', 0, 'uid@proton.me', '', 0, 0, 1, 0)"
    )

    private fun SupportSQLiteDatabase.maxOccurrenceId(): Int =
        query("SELECT IFNULL(MAX(`_id`), 0) FROM `events_occurrences`").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.countOf(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.primaryKeyOf(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            val pk = cursor.getColumnIndexOrThrow("pk")
            buildList {
                while (cursor.moveToNext()) if (cursor.getInt(pk) > 0) add(cursor.getInt(pk) to cursor.getString(name))
            }
        }.sortedBy { it.first }.map { it.second }

    private companion object {
        const val WINDOW_START = 0L
        const val WINDOW_END = 5_000L
    }
}
