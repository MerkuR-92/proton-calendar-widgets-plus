package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.model.EventKey

@Dao
abstract class EventOccurrencesDao : BaseDao<EventOccurrenceEntity> {

    @Query("DELETE FROM events_occurrences WHERE userId = :userId AND calendarId = :calendarId AND eventId = :eventId")
    abstract suspend fun deleteAllForEvent(userId: String, calendarId: String, eventId: String)

    @Query("SELECT * FROM events_occurrences WHERE userId = :userId AND calendarId IN (:calendarIds) AND rRule IS null AND (startTime <= :timestampSecondsTo AND endTime >= :timestampSecondsFrom)")
    abstract fun selectNonRecurringBetweenInclusive(userId: String, calendarIds: List<String>, timestampSecondsFrom: Long, timestampSecondsTo: Long): Flow<List<EventOccurrenceEntity>>

    @Query("SELECT * FROM events_occurrences WHERE userId = :userId AND calendarId IN (:calendarIds) AND rRule IS NOT null AND firstOccurrenceStartTime <= :timestampSecondsTo AND lastOccurrenceEndTime >= :timestampSecondsFrom")
    abstract fun selectFiniteRecurring(userId: String, calendarIds: List<String>, timestampSecondsFrom: Long, timestampSecondsTo: Long): Flow<List<EventOccurrenceEntity>>

    @Query("SELECT * FROM events_occurrences WHERE userId = :userId AND calendarId IN (:calendarIds) AND rRule IS NOT null AND lastOccurrenceEndTime IS null AND firstOccurrenceStartTime <= :timestampSecondsTo")
    abstract fun selectInfiniteRecurring(userId: String, calendarIds: List<String>, timestampSecondsTo: Long): Flow<List<EventOccurrenceEntity>>

    @Query("SELECT EXISTS(SELECT * FROM events_occurrences WHERE userId = :userId AND eventId = :eventId AND calendarId = :calendarId AND modifyTime >= :modifyTime)")
    abstract fun hasOccurrenceWithEqualOrHigherModifyTime(userId: String, calendarId: String, eventId: String, modifyTime: Long): Boolean

    // cheap count for widget to respond to post-login insertions
    @Query("SELECT COUNT(*) FROM events_occurrences WHERE userId = :userId")
    abstract fun countForUser(userId: String): Flow<Int>

    @Query("""
        SELECT DISTINCT eventId, calendarId
        FROM events_occurrences
        WHERE userId = :userId
          AND calendarId IN (:calendarIds)
          AND startTime IS NOT NULL
          AND endTime IS NOT NULL
          AND startTime <= :toSec
          AND endTime >= :fromSec
    """)
    abstract suspend fun selectEventKeysOverlapping(
        userId: String,
        calendarIds: List<String>,
        fromSec: Long,
        toSec: Long
    ): List<EventKey>

    // resolve event keys for uids via the indexed eventUid column
    @Query("SELECT DISTINCT eventId, calendarId, eventUid FROM events_occurrences WHERE eventUid IN (:uids)")
    abstract suspend fun selectEventRefsByUids(uids: Set<String>): List<EventUidRef>

    data class EventUidRef(val eventId: String, val calendarId: String, val eventUid: String) {
        val key get() = EventKey(eventId, calendarId)
    }
}
