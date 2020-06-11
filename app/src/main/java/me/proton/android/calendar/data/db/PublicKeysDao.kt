package me.proton.android.calendar.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_CALENDARS
import me.proton.android.calendar.data.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.PublicKeyEntity

@Dao
abstract class PublicKeysDao : BaseDao<PublicKeyEntity> {

    @Query("SELECT * FROM public_keys WHERE email = :email")
    abstract fun select(email: String): List<PublicKeyEntity>

}
