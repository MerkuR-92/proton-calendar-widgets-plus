package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.EventEntityMetadata

@Dao
abstract class EventsMetadataDao : BaseDao<EventEntityMetadata> {

    /** GET All **/
    @Query("SELECT * FROM events_metadata")
    abstract suspend fun selectEventsMetadata(): List<EventEntityMetadata>
    @Query("SELECT * FROM events_metadata")
    abstract fun flowEventsMetadata(): Flow<List<EventEntityMetadata>>

    /** GET By Time Window **/
    @Transaction
    @Query("SELECT * FROM events_metadata WHERE calendarId IN (:calendarIds) AND ((startTime >= :windowStartTime AND startTime <= :windowEndTime) OR (endTime >= :windowStartTime AND endTime <= :windowEndTime) OR (:windowStartTime >= startTime AND :windowEndTime <= endTime) OR rRule IS NOT null)")
    abstract suspend fun selectEventsMetadataForTimeWindow(calendarIds: List<String>, windowStartTime: Long, windowEndTime: Long): List<EventEntityMetadata>
    @Transaction
    @Query("SELECT * FROM events_metadata WHERE (startTime >= :windowStartTime AND startTime <= :windowEndTime) OR (endTime >= :windowStartTime AND endTime <= :windowEndTime) OR (:windowStartTime >= startTime AND :windowEndTime <= endTime) OR rRule IS NOT null")
    abstract fun flowEventsMetadataForTimeWindow(windowStartTime: Long, windowEndTime: Long): Flow<List<EventEntityMetadata>>

    /** GET By Calendar IDs **/
    @Query("SELECT * FROM events_metadata WHERE calendarId IN (:calendarIds)")
    abstract fun selectEventsMetadataByCalendarIds(calendarIds: List<String>): List<EventEntityMetadata>
    @Query("SELECT * FROM events_metadata WHERE calendarId IN (:calendarIds)")
    abstract fun flowEventsMetadataByCalendarIds(calendarIds: List<String>): Flow<List<EventEntityMetadata>>

    /** GET By Event ID **/
    @Query("SELECT * FROM events_metadata WHERE id = :eventId")
    abstract suspend fun selectEventMetadataByEventId(eventId: String): EventEntityMetadata?
    @Query("SELECT * FROM events_metadata WHERE id IN (:eventIds)")
    abstract suspend fun selectAllEventsMetadataByEventId(eventIds: List<String>): List<EventEntityMetadata>
    @Query("SELECT * FROM events_metadata WHERE id = :eventId")
    abstract fun flowEventMetadataByEventId(eventId: String): Flow<EventEntityMetadata?>

    /** GET By Event and Calendar ID **/
    @Transaction
    @Query("SELECT * FROM events_metadata WHERE id = :eventId AND calendarId = :calendarId")
    abstract suspend fun selectEventMetadata(eventId: String, calendarId: String): EventEntityMetadata?

    /** DELETE **/
    @Query("DELETE FROM events_metadata")
    abstract suspend fun deleteAll()
    @Query("DELETE FROM events_metadata WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendarId(calendarId: String)
    @Query("DELETE FROM events_metadata WHERE id IN (:eventIds)")
    abstract suspend fun deleteByEventIds(eventIds: List<String>)

    @Query("SELECT EXISTS(SELECT * FROM events_metadata WHERE id = :eventId AND calendarId = :calendarId AND modifyTime > :modifyTime)")
    abstract suspend fun hasEventMetadataWithHigherModifyTime(eventId: String, calendarId: String, modifyTime: Long): Boolean

    @Query("SELECT COUNT(ID) FROM events_metadata")
    abstract fun flowEventsMetadataCount(): Flow<Int>
}
