package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity


@Dao
abstract class UserSettingsDao : BaseDao<UserSettingsEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM user_settings WHERE fkUserId = :userId")
    abstract suspend fun select(userId: String): UserSettingsEntity?

    @Query("DELETE FROM user_settings WHERE fkUserId = :userId")
    abstract suspend fun deleteByUserId(userId: String)

}
