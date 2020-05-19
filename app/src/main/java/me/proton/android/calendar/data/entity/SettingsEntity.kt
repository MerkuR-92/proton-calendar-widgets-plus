package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.google.gson.JsonElement
import me.proton.android.calendar.data.db.AppDatabase

// settings specific to calendar

@Entity(tableName = AppDatabase.TABLE_SETTINGS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )])
data class SettingsEntity(
    @PrimaryKey
    val id: String,
    val calendarId: String,
    val defaultEventDuration: Int, // Default event duration in minutes
    val defaultPartDayNotifications: List<JsonElement>,
    val defaultFullDayNotifications: List<JsonElement>

) {

    // TODO map to this
    data class ReminderEntity(
        val Type: String, // 0: Email reminder, 1: Desktop reminder // TODO we'll probably only get desktop reminders from API, or maybe mobile reminders?
        val Trigger: String // RFC5545 encoded trigger
    )

}
