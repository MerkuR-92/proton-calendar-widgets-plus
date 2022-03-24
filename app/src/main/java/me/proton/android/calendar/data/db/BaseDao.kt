package me.proton.android.calendar.data.db

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * Base Dao interface containing common query definitions.
 */
interface BaseDao<T> {

    @Deprecated(message = "Do not use this method directly outside of CalendarsRepository", replaceWith = ReplaceWith("updateOrInsert"))
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg obj: T)

    @Deprecated(message = "Do not use this method directly outside of CalendarsRepository", replaceWith = ReplaceWith("updateOrInsert"))
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntity(entity: T)

    @Update
    suspend fun update(vararg obj: T)

    @Deprecated(message = "Do not use this method directly outside of CalendarsRepository", replaceWith = ReplaceWith("updateOrInsert"))
    @Update
    suspend fun updateEntity(entity: T): Int

    @Deprecated(message = "Do not use this method directly outside of CalendarsRepository", replaceWith = ReplaceWith("CalendarsRepository methods"))
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
