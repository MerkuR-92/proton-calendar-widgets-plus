package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow


@Dao
abstract class SettingsDao : BaseDao<SettingsEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM settings WHERE calendarId = :calendarId")
    abstract fun select(calendarId: String): SettingsEntity?

    @Query("DELETE FROM settings WHERE id = :id")
    abstract fun deleteById(id: String)

}