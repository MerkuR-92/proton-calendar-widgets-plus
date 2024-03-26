package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase


@Entity(
    tableName = AppDatabase.TABLE_EVENTS_METADATA,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])]
)
@Serializable
data class EventEntityMetadata(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("SharedEventID")
    val sharedEventId: String,
    @SerialName("AddressID")
    val addressId: String?,
    @SerialName("StartTime")
    val startTime: Long,
    @SerialName("StartTimezone")
    val startTimeZone: String,
    @SerialName("EndTime")
    val endTime: Long,
    @SerialName("EndTimezone")
    val endTimeZone: String,
    @SerialName("FullDay")
    val fullDay: Int,
    @SerialName("UID")
    val uid: String,
    @SerialName("RecurrenceID")
    val recurrenceID: Long?,
    @SerialName("Exdates")
    val exDates: List<Long>,
    @SerialName("RRule")
    val rRule: String?,
    @SerialName("CreateTime")
    val createTime: Long,
    @SerialName("ModifyTime")
    val modifyTime: Long,
    @SerialName("IsOrganizer")
    val isOrganizer: Int,
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String?,
    @SerialName("CalendarKeyPacket")
    val calendarKeyPacket: String?,
    @SerialName("AddressKeyPacket")
    val addressKeyPacket: String?,
    @SerialName("IsPersonalSingleEdit")
    val isPersonalSingleEdit: Boolean
)
