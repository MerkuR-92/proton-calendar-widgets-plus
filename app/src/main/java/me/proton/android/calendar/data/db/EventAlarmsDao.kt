package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.EventAlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventAlarmsDao : BaseDao<EventAlarmEntity> {

    @Query("SELECT * FROM event_alarms WHERE eventId = :eventId")
    abstract fun select(eventId: String): Flow<List<EventAlarmEntity>>

    @Query("SELECT * FROM event_alarms WHERE occurrence = (SELECT MIN(occurrence) FROM event_alarms WHERE occurrence >= :timestampSeconds)")
    abstract suspend fun selectUpcoming(timestampSeconds: Long): List<EventAlarmEntity>

    @Query("SELECT * FROM event_alarms WHERE occurrence >= :timestampSecondsStart AND occurrence <= :timestampSecondsEnd")
    abstract suspend fun select(timestampSecondsStart: Long, timestampSecondsEnd: Long): List<EventAlarmEntity>

    @Query("DELETE FROM event_alarms WHERE id = :id")
    abstract suspend fun deleteById(id: String)

}
