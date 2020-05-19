package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import me.proton.android.calendar.data.db.AppDatabase

// TODO
// when inserting this, we need to already have event, member and calendar in local DB
//  maybe set foreign keys?

@Entity(
    tableName = AppDatabase.TABLE_EVENT_ALARMS,
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
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


