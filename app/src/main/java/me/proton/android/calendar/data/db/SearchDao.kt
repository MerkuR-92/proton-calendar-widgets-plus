package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.SearchEventEntity

@Dao
abstract class SearchDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSearchEvents(searchEvents: List<SearchEventEntity>)

    @Query("SELECT * FROM search_events")
    abstract fun selectAll(): List<SearchEventEntity>

    @Query("SELECT * FROM search_events WHERE user_id = :userId")
    abstract fun selectSearchEvents(userId: String): List<SearchEventEntity>

    @Query("SELECT * FROM search_events WHERE user_id = :userId")
    abstract fun flowSearchEvents(userId: String): Flow<List<SearchEventEntity>>

    @Query("DELETE FROM search_events")
    abstract fun deleteAllSearchEvents(): Int

    @Query("DELETE FROM search_events WHERE user_id = :userId")
    abstract fun delete(userId: String): Int

    @Query("DELETE FROM search_events WHERE user_id = :userId AND calendar_id = :calendarId")
    abstract fun delete(userId: String, calendarId: String): Int

    @Query("DELETE FROM search_events WHERE user_id = :userId AND calendar_id = :calendarId AND event_id IN (:eventIds)")
    abstract fun delete(userId: String, calendarId: String, eventIds: List<String>): Int

    @Query("SELECT EXISTS(SELECT * FROM search_events WHERE user_id = :userId AND event_id = :eventId AND calendar_id = :calendarId AND modify_time > :modifyTime)")
    abstract suspend fun hasEventWithHigherModifyTime(userId: String, calendarId: String, eventId: String, modifyTime: Long): Boolean

}
