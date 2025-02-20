package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.EventEntityMetadata

@Dao
abstract class EventsMetadataDao : BaseDao<EventEntityMetadata> {

    /** GET By Time Window **/
    @Transaction
    @Query("SELECT * FROM events_metadata WHERE (startTime >= :windowStartTime AND startTime <= :windowEndTime) OR (endTime >= :windowStartTime AND endTime <= :windowEndTime) OR (:windowStartTime >= startTime AND :windowEndTime <= endTime) OR rRule IS NOT null")
    abstract fun flowEventsMetadataForTimeWindow(windowStartTime: Long, windowEndTime: Long): Flow<List<EventEntityMetadata>>

    /** DELETE **/
    @Query("DELETE FROM events_metadata")
    abstract suspend fun deleteAll()
    @Query("DELETE FROM events_metadata WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendarId(calendarId: String)
    @Query("DELETE FROM events_metadata WHERE id IN (:eventIds)")
    abstract suspend fun deleteByEventIds(eventIds: List<String>)

    @Query("SELECT EXISTS(SELECT * FROM events_metadata WHERE id = :eventId AND calendarId = :calendarId AND modifyTime > :modifyTime)")
    abstract suspend fun hasEventMetadataWithHigherModifyTime(eventId: String, calendarId: String, modifyTime: Long): Boolean

}
