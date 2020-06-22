package me.proton.android.calendar.data.db

import androidx.room.*
import me.proton.android.calendar.data.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventsDao : BaseDao<EventEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM events WHERE calendarId = :calendarId ORDER BY lastEditTime DESC") // TODO order just for testing
    abstract fun selectEvents(calendarId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    abstract fun selectByIdFlow(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    abstract suspend fun selectById(id: String): EventEntity?

    @Query("DELETE FROM events WHERE id = :id")
    abstract fun deleteById(id: String)

    // TODO select for given timespan

}
