package me.proton.android.calendar.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Base Dao interface containing common query definitions.
 */
interface BaseDao<T> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg obj: T)

    @Update
    suspend fun update(vararg obj: T)

    @Delete
    suspend fun delete(vararg obj: T)

}