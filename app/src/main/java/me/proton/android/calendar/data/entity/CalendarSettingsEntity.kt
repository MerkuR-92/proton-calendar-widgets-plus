package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.Duration
import com.google.gson.JsonElement
import me.proton.android.calendar.data.db.AppDatabase

// settings specific to Calendar, shared by all Calendar Members

@Entity(tableName = AppDatabase.TABLE_CALENDAR_SETTINGS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
data class CalendarSettingsEntity(
    @PrimaryKey
    val id: String,
    val calendarId: String,
    val defaultEventDuration: Int, // Default event duration in minutes
    val defaultPartDayNotifications: List<JsonElement>,
    val defaultFullDayNotifications: List<JsonElement>

) {

    data class AlarmEntity(
        val type: String, // 0: Email reminder, 1: Desktop reminder
        val trigger: String // RFC5545 encoded trigger
    ) {
        fun parseTrigger(): Trigger? {
            return try {
                Trigger(Duration.parse(trigger), Related.START)
            } catch(e: IllegalArgumentException) {
                null
            }
        }
    }

}
