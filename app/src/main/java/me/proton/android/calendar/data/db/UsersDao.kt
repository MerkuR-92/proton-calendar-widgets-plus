package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsersDao : BaseDao<UserEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM users")
    abstract fun usersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    abstract fun select(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId")
    abstract suspend fun selectUserById(userId: String): UserEntity?

    @Query("DELETE FROM users WHERE id = :userId")
    abstract suspend  fun deleteById(userId: String)

}
