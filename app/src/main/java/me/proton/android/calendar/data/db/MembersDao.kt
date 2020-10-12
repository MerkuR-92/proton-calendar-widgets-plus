package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MembersDao : BaseDao<MemberEntity> {

    @Query("SELECT * FROM members WHERE calendarId = :calendarId")
    abstract fun select(calendarId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE email = :address")
    abstract fun selectByAddress(address: String): List<MemberEntity>

    @Query("DELETE FROM members WHERE id = :id")
    abstract fun deleteById(id: String)

}
