package me.proton.android.calendar.data.db

import androidx.room.*

/**
 * Base Dao interface containing common query definitions.
 */
interface BaseDao<T> {

    // TODO maybe we should stop using this "force insert" and
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg obj: T)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntity(entity: T)

    @Update
    suspend fun update(vararg obj: T)

    @Update
    suspend fun updateEntity(entity: T): Int

    suspend fun updateOrInsert(vararg entities: T) {
        entities.forEach {
            if (updateEntity(it) == 0) {
                insertEntity(it)
            }
        }
    }

    @Delete
    suspend fun delete(vararg obj: T)

}
