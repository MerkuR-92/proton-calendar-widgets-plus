package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarKeysDao : BaseDao<CalendarKeyEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM calendar_keys WHERE calendarId = :calendarId")
    abstract fun select(calendarId: String): List<CalendarKeyEntity>

    @Query("SELECT * FROM calendar_keys WHERE calendarId = :calendarId AND passphraseId = :passphraseId")
    abstract fun select(calendarId: String, passphraseId: String): List<CalendarKeyEntity>

//    @Query("SELECT * FROM calendar_keys")
//    abstract fun selectAll(): List<CalendarKeyEntity>

    @Query("DELETE FROM calendar_keys WHERE id = :id")
    abstract fun deleteById(id: String)

}