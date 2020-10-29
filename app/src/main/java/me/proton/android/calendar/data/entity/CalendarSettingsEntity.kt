package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
@Serializable
data class CalendarSettingsEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("DefaultEventDuration")
    val defaultEventDuration: Int, // Default event duration in minutes
    @SerialName("DefaultPartDayNotifications")
    val defaultPartDayNotifications: List<JsonElement>,
    @SerialName("DefaultFullDayNotifications")
    val defaultFullDayNotifications: List<JsonElement>

) {

    @Serializable
    data class AlarmEntity(
        @SerialName("Type")
        val type: Int, // 0: Email reminder, 1: Desktop reminder
        @SerialName("Trigger")
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
