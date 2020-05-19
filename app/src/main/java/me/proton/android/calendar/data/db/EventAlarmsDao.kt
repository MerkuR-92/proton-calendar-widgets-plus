package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.EventAlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventAlarmsDao : BaseDao<EventAlarmEntity> {

    @Query("SELECT * FROM event_alarms WHERE eventId = :eventId")
    abstract fun select(eventId: String): Flow<List<EventAlarmEntity>>

    @Query("DELETE FROM event_alarms WHERE id = :id")
    abstract fun deleteById(id: String)

    // TODO select upcoming event to create system alarm?

}