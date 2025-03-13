package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.FetchedEventsMetadataEntity

@Dao
abstract class FetchedEventsMetadataDao : BaseDao<FetchedEventsMetadataEntity> {

    @Query("DELETE FROM fetched_events_metadata WHERE userId = :userId AND calendarId = :calendarId")
    abstract suspend fun deleteAllForCalendar(userId: String, calendarId: String)

    @Query("SELECT EXISTS(SELECT * FROM fetched_events_metadata WHERE userId = :userId AND calendarId = :calendarId AND windowStartTime <= :windowStart AND windowEndTime >= :windowEnd)")
    abstract fun hasWindowFullyOverlapping(userId: String, calendarId: String, windowStart: Long, windowEnd: Long): Boolean

}
