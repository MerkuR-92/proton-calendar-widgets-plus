package me.proton.android.calendar.data.db

import androidx.room.*
import me.proton.android.calendar.data.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventsDao : BaseDao<EventEntity> {

//    lateinit var userId: String

//    @Query("SELECT * FROM events WHERE calendarId IN (:calendarIds) AND (sharedEvents LIKE '%' || :sharedEventsFieldSubstring || '%')")

    @Query("SELECT * FROM events")
    abstract fun selectEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE calendarId IN (:calendarIds)")
    abstract fun flowEvents(calendarIds: List<String>): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE calendarId IN (:calendarIds)")
    abstract suspend fun selectEvents(calendarIds: List<String>): List<EventEntity>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId")
    abstract fun selectEvents(calendarId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    abstract fun selectByIdFlow(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    abstract suspend fun selectById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE sharedEvents LIKE '%UID:' || :uid || '%'")
    abstract suspend fun selectByUid(uid: String): List<EventEntity>

    @Query("SELECT COUNT(id) FROM events WHERE sharedEvents LIKE '%UID:' || :uid || '%'")
    abstract suspend fun countByUid(uid: String): Int

    @Query("SELECT EXISTS(SELECT * FROM events WHERE id = :eventId AND calendarId = :calendarId)")
    abstract suspend fun hasEvent(eventId: String, calendarId: String): Boolean

    @Query("SELECT COUNT(id) FROM events WHERE calendarId = :calendarId")
    abstract suspend fun count(calendarId: String): Int

    @Query("DELETE FROM events WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM events WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<String>)

    // TODO select for given timespan

}
