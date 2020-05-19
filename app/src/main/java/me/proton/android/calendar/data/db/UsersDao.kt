package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsersDao : BaseDao<UserEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM users")
    abstract fun selectUsers(): Flow<List<UserEntity>>

}