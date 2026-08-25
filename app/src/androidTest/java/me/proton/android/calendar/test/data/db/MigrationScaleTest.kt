package me.proton.android.calendar.test.data.db

import android.util.Log
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.AppDatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// the migration copies every row, so make sure that stays quick on a big account
@RunWith(AndroidJUnit4::class)
class MigrationScaleTest {

    private val dbName = "migration-scale-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrates_a_large_account() {
        val userId = "u1"
        val calendars = (1..5).map { "cal$it" }
        val eventsPerCalendar = 1000
        val blob = "\"" + "x".repeat(2000) + "\"" // stand-in for the encrypted payload

        helper.createDatabase(dbName, 83).apply {
            execSQL("INSERT INTO `AccountEntity` (`userId`, `state`) VALUES ('$userId', 'Ready')")
            execSQL(
                "INSERT INTO `UserEntity` (`userId`, `currency`, `credit`, `createdAtUtc`, `usedSpace`, " +
                    "`maxSpace`, `maxUpload`, `private`, `subscribed`, `services`) " +
                    "VALUES ('$userId', 'EUR', 0, 0, 0, 0, 0, 1, 0, 0)"
            )
            beginTransaction()
            calendars.forEach { execSQL("INSERT INTO `calendars` (`id`, `type`, `fkUserId`) VALUES ('$it', 0, '$userId')") }
            calendars.forEach { calendarId ->
                repeat(eventsPerCalendar) { i ->
                    val id = "e-$calendarId-$i"
                    execSQL(
                        "INSERT INTO `events` (id, calendarId, sharedEventId, createTime, modifyTime, permissions," +
                            " sharedEvents, calendarEvents, attendeesEvents, attendees, isProtonProtonInvite)" +
                            " VALUES ('$id', '$calendarId', 'shared-$id', 0, 0, 0, '[$blob]', '[$blob]', '[]', '[]', 0)"
                    )
                    execSQL(
                        "INSERT INTO `events_metadata` (id, calendarId, sharedEventId, startTime, startTimeZone, endTime," +
                            " endTimeZone, fullDay, uid, exDates, createTime, modifyTime, isOrganizer, isPersonalSingleEdit)" +
                            " VALUES ('$id', '$calendarId', 'shared-$id', 100, 'UTC', 200, 'UTC', 0, 'uid$i@proton.me', '', 0, 0, 1, 0)"
                    )
                    execSQL(
                        "INSERT INTO `events_occurrences` (userId, calendarId, eventId, eventUid, fullDay, startTime," +
                            " endTime, windowStartTime, windowEndTime, firstOccurrenceStartTime, modifyTime," +
                            " startTimeZone, endTimeZone, exDates)" +
                            " VALUES ('$userId', '$calendarId', '$id', 'uid$i@proton.me', 0, 100, 200, 0, 1000, 100, 0, 'UTC', 'UTC', '')"
                    )
                    execSQL(
                        "INSERT INTO `event_alarms` VALUES ('alarm-$calendarId-$id', 100, '-PT15M', 2, '$id', 'm1', '$calendarId')"
                    )
                }
            }
            setTransactionSuccessful()
            endTransaction()
            close()
        }

        val sizeMb = dbFile().length() / (1024.0 * 1024.0)
        val startedAt = System.currentTimeMillis()
        val db = helper.runMigrationsAndValidate(dbName, 84, true, AppDatabaseMigrations.MIGRATION_83_84)
        val tookMs = System.currentTimeMillis() - startedAt

        val total = calendars.size * eventsPerCalendar
        assertEquals(total, db.countOf("events"))
        assertEquals(total, db.countOf("events_occurrences"))
        assertEquals(total, db.countOf("event_alarms"))
        assertEquals(0, db.query("PRAGMA foreign_key_check").use { it.count })
        Log.w("MigrationScale", "db=%.1fMB events=%d migration took %dms".format(sizeMb, total, tookMs))
        db.close()
    }

    private fun dbFile(): File =
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(dbName)

    private fun androidx.sqlite.db.SupportSQLiteDatabase.countOf(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }
}
