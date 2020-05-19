package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AddressesDao : BaseDao<AddressEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM addresses WHERE fkUserId = :userId")
    abstract fun selectFlow(userId: String): Flow<List<AddressEntity>> // TODO unify Flow<List> and List<>

    @Query("SELECT * FROM addresses WHERE fkUserId = :userId AND email = :email")
    abstract fun select(userId: String, email: String): List<AddressEntity>

    @Query("SELECT * FROM addresses WHERE fkUserId = :userId")
    abstract fun select(userId: String): List<AddressEntity>

    @Query("DELETE FROM addresses WHERE id = :id")
    abstract fun deleteById(id: String)

}