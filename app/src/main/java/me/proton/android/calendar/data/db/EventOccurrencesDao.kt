package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.EventOccurrenceEntity

@Dao
abstract class EventOccurrencesDao : BaseDao<EventOccurrenceEntity> {

    @Query("DELETE FROM events_occurrences WHERE userId = :userId AND calendarId = :calendarId AND eventId = :eventId")
    abstract suspend fun deleteAllForEvent(userId: String, calendarId: String, eventId: String)

    @Query("SELECT * FROM events_occurrences WHERE userId = :userId AND calendarId IN (:calendarIds) AND rRule IS null AND (startTime <= :timestampSecondsTo AND endTime >= :timestampSecondsFrom)")
    abstract fun selectNonRecurringBetweenInclusive(userId: String, calendarIds: List<String>, timestampSecondsFrom: Long, timestampSecondsTo: Long): Flow<List<EventOccurrenceEntity>>

    @Query("SELECT * FROM events_occurrences WHERE userId = :userId AND calendarId IN (:calendarIds) AND rRule IS NOT null AND (startTime <= :timestampSecondsTo AND lastOccurrenceEndTime >= :timestampSecondsFrom)")
    abstract fun selectFiniteRecurring(userId: String, calendarIds: List<String>, timestampSecondsFrom: Long, timestampSecondsTo: Long): Flow<List<EventOccurrenceEntity>>

    @Query("SELECT * FROM events_occurrences WHERE userId = :userId AND calendarId IN (:calendarIds) AND rRule IS NOT null AND lastOccurrenceEndTime IS null")
    abstract fun selectInfiniteRecurring(userId: String, calendarIds: List<String>): Flow<List<EventOccurrenceEntity>>

}
