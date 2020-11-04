package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity


@Dao
abstract class CalendarUserSettingsDao : BaseDao<CalendarUserSettingsEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun select(userId: String): CalendarUserSettingsEntity?

    @Query("DELETE FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun deleteByUserId(userId: String)

}
