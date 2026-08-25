package me.proton.android.calendar.test.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// two calendars on different shards can hold the same eventId, so everything here keys on (eventId, calendarId)
@RunWith(AndroidJUnit4::class)
class EventIdShardCollisionTest {

    private lateinit var db: AppDatabase

    private val userId = "user-1"
    private val calendarA = "calendar-a"
    private val calendarB = "calendar-b"
    private val sharedEventId = "1"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).build()

        // a calendar needs a user and a user needs an account, so seed those first
        db.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO `AccountEntity` (`userId`, `state`) VALUES ('$userId', 'Ready')")
            execSQL(
                "INSERT INTO `UserEntity` (`userId`, `currency`, `credit`, `createdAtUtc`, `usedSpace`, " +
                    "`maxSpace`, `maxUpload`, `private`, `subscribed`, `services`) " +
                    "VALUES ('$userId', 'EUR', 0, 0, 0, 0, 0, 1, 0, 0)"
            )
            execSQL("INSERT INTO `calendars` (`id`, `type`, `fkUserId`) VALUES ('$calendarA', 0, '$userId')")
            execSQL("INSERT INTO `calendars` (`id`, `type`, `fkUserId`) VALUES ('$calendarB', 0, '$userId')")
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun colliding_event_ids_on_different_calendars_both_persist() = runBlocking {
        db.eventsDao().insert(event(calendarA, summary = "on A"), event(calendarB, summary = "on B"))

        val onA = db.eventsDao().selectEvent(sharedEventId, calendarA)
        val onB = db.eventsDao().selectEvent(sharedEventId, calendarB)

        assertNotNull(onA)
        assertNotNull(onB)
        assertEquals(calendarA, onA!!.calendarId)
        assertEquals(calendarB, onB!!.calendarId)
        assertEquals("on A", onA.sharedEventId)
        assertEquals("on B", onB.sharedEventId)
    }

    @Test
    fun updating_the_colliding_event_does_not_overwrite_the_other_calendars_copy() = runBlocking {
        db.eventsDao().insert(event(calendarA, summary = "on A"), event(calendarB, summary = "on B"))

        db.eventsDao().update(event(calendarB, summary = "on B, edited"))

        assertEquals("on A", db.eventsDao().selectEvent(sharedEventId, calendarA)?.sharedEventId)
        assertEquals("on B, edited", db.eventsDao().selectEvent(sharedEventId, calendarB)?.sharedEventId)
    }

    @Test
    fun deleting_by_id_only_removes_the_event_in_the_given_calendar() = runBlocking {
        db.eventsDao().insert(event(calendarA), event(calendarB))

        db.eventsDao().deleteByIds(calendarA, listOf(sharedEventId))

        assertNull(db.eventsDao().selectEvent(sharedEventId, calendarA))
        assertNotNull(db.eventsDao().selectEvent(sharedEventId, calendarB))
    }

    @Test
    fun deleting_an_event_does_not_cascade_into_the_other_calendars_occurrences_and_alarms() = runBlocking {
        db.eventsDao().insert(event(calendarA), event(calendarB))
        db.eventOccurrencesDao().insert(occurrence(calendarA), occurrence(calendarB))
        db.eventAlarmsDao().insert(alarm("alarm-a", calendarA), alarm("alarm-b", calendarB))

        db.eventsDao().deleteByIds(calendarA, listOf(sharedEventId))

        val occurrences = db.eventOccurrencesDao().selectEventKeysOverlapping(
            userId = userId,
            calendarIds = listOf(calendarA, calendarB),
            fromSec = 0,
            toSec = Long.MAX_VALUE
        )
        assertEquals(listOf(calendarB), occurrences.map { it.calendarId })

        val alarms = db.eventAlarmsDao().selectAllBetweenInclusive(0, Long.MAX_VALUE)
        assertEquals(listOf("alarm-b"), alarms.map { it.id })
    }

    @Test
    fun colliding_metadata_ids_on_different_calendars_both_persist() = runBlocking {
        db.eventsMetadataDao().insert(metadata(calendarA), metadata(calendarB))

        assertEquals(2, db.eventsMetadataDao().selectEventsMetadata().size)

        db.eventsMetadataDao().deleteByEventIds(calendarA, listOf(sharedEventId))

        assertEquals(listOf(calendarB), db.eventsMetadataDao().selectEventsMetadata().map { it.calendarId })
    }

    private fun event(calendarId: String, summary: String = calendarId) = EventEntity(
        id = sharedEventId,
        calendarId = calendarId,
        sharedEventId = summary,
        calendarKeyPacket = null,
        createTime = 0,
        modifyTime = 0,
        permissions = 0,
        addressKeyPacket = null,
        addressId = null,
        sharedKeyPacket = null,
        sharedEvents = emptyList(),
        calendarEvents = emptyList(),
        attendeesEvents = emptyList(),
        attendees = emptyList(),
        attendeesInfo = emptyList(),
        isProtonProtonInvite = 0
    )

    private fun metadata(calendarId: String) = EventEntityMetadata(
        id = sharedEventId,
        calendarId = calendarId,
        sharedEventId = "shared-$calendarId",
        addressId = null,
        startTime = 100,
        startTimeZone = "UTC",
        endTime = 200,
        endTimeZone = "UTC",
        fullDay = 0,
        uid = "uid@proton.me",
        recurrenceID = null,
        exDates = emptyList(),
        rRule = null,
        createTime = 0,
        modifyTime = 0,
        isOrganizer = 0,
        sharedKeyPacket = null,
        calendarKeyPacket = null,
        addressKeyPacket = null,
        isPersonalSingleEdit = false
    )

    private fun occurrence(calendarId: String) = EventOccurrenceEntity(
        userId = userId,
        calendarId = calendarId,
        eventId = sharedEventId,
        eventUid = "uid@proton.me",
        fullDay = 0,
        startTime = 100,
        endTime = 200,
        windowStartTime = 0,
        windowEndTime = 1000,
        firstOccurrenceStartTime = 100,
        modifyTime = 0
    )

    private fun alarm(alarmId: String, calendarId: String) = EventAlarmEntity(
        id = alarmId,
        occurrence = 100,
        trigger = "-PT15M",
        action = 2,
        eventId = sharedEventId,
        memberId = "member-1",
        calendarId = calendarId
    )
}
