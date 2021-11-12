package me.proton.android.calendar.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_CALENDARS
import me.proton.android.calendar.data.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarsDao : BaseDao<CalendarEntity> {

    /** All calendars */

    @Query("SELECT * FROM calendars")
    abstract fun selectCalendars(): List<CalendarEntity>

    @Query("SELECT * FROM calendars")
    abstract fun flowCalendars(): Flow<List<CalendarEntity>>

    /** All calendars for userId */

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract suspend fun selectCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract fun flowCalendars(userId: String): Flow<List<CalendarEntity>>

    /** User calendars */

    @Query("SELECT * FROM calendars WHERE type == 0 AND fkUserId = :userId")
    abstract suspend fun selectUserCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE type == 0 AND fkUserId = :userId")
    abstract fun flowUserCalendars(userId: String): Flow<List<CalendarEntity>>

    /** Active user calendars */

    @Query("SELECT * FROM calendars WHERE flags == 1 AND type == 0 AND fkUserId = :userId")
    abstract suspend fun selectActiveUserCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE flags == 1 AND type == 0 AND fkUserId = :userId")
    abstract fun flowActiveUserCalendars(userId: String): Flow<List<CalendarEntity>>

    /** Disabled user calendars */

    @Query("SELECT * FROM calendars WHERE flags & (32 + 64) >= 32  AND type == 0 AND fkUserId = :userId")
    abstract suspend fun selectDisabledUserCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE flags & (32 + 64) >= 32  AND type == 0 AND fkUserId = :userId")
    abstract fun flowDisabledUserCalendars(userId: String): Flow<List<CalendarEntity>>

    /** Inactive user calendars */

    @Query("SELECT * FROM calendars WHERE flags & (2 + 4 + 8 + 16) >= 2  AND type == 0 AND fkUserId = :userId")
    abstract suspend fun selectInactiveUserCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE flags & (2 + 4 + 8 + 16) >= 2  AND type == 0 AND fkUserId = :userId")
    abstract fun flowInactiveUserCalendars(userId: String): Flow<List<CalendarEntity>>

    /** Subscribed calendars */

    @Query("SELECT * FROM calendars WHERE type == 1 AND fkUserId = :userId")
    abstract suspend fun selectSubscribedCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE type == 1 AND fkUserId = :userId")
    abstract fun flowSubscribedCalendars(userId: String): Flow<List<CalendarEntity>>

    /** By id */

    @Query("SELECT fkUserId FROM calendars WHERE id = :calendarId")
    abstract suspend fun selectCalendarUserId(calendarId: String): String?

    @Query("SELECT * FROM calendars WHERE id = :id")
    abstract suspend fun selectById(id: String): CalendarEntity?

    @Query("SELECT EXISTS(SELECT * FROM calendars WHERE id = :calendarId)")
    abstract suspend fun hasCalendar(calendarId: String): Boolean

    @Query("DELETE FROM calendars WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId AND calendars.display = 1")
    abstract suspend fun selectDisplayedCalendars(userId: String): List<CalendarEntity>

    @Query("UPDATE calendars SET flags = :flags WHERE id = :calendarId")
    abstract suspend fun updateCalendarFlags(calendarId: String, flags: Int)

    @Query("UPDATE calendars SET display = :display WHERE id = :calendarId")
    abstract suspend fun updateCalendarDisplay(calendarId: String, display: Int)

}
