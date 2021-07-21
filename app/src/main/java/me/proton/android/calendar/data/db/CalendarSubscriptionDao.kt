package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity

@Dao
abstract class CalendarSubscriptionDao : BaseDao<CalendarSubscriptionEntity> {

    @Query("SELECT * FROM calendar_subscription WHERE calendarId = :calendarId")
    abstract suspend fun select(calendarId: String): CalendarSubscriptionEntity?

    @Query("DELETE FROM calendar_subscription WHERE calendarId = :calendarId")
    abstract suspend fun deleteById(calendarId: String)

}