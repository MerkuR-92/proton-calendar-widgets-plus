package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MembersDao : BaseDao<MemberEntity> {

    @Query("SELECT * FROM members WHERE calendarId = :calendarId")
    abstract suspend fun select(calendarId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE email = :address")
    abstract suspend fun selectByAddress(address: String): List<MemberEntity>

    @Query("DELETE FROM members WHERE id = :id")
    abstract suspend fun deleteById(id: String)

}
