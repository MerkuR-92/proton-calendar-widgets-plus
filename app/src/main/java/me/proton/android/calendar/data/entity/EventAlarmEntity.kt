package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.proton.android.calendar.data.db.AppDatabase

@Entity(
    tableName = AppDatabase.TABLE_EVENT_ALARMS,
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["eventId"])]
)
data class EventAlarmEntity(
    @PrimaryKey
    val id: String,
    val occurrence: Long, // next alarm occurrence timestamp
    val trigger: String, //"-PT15H"
    val action: Int, // 1 -- email, 2 -- push
    val eventId: String,
    val memberId: String,
    val calendarId: String
)


