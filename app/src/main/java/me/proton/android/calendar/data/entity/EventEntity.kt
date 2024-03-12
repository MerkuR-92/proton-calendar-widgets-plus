package me.proton.android.calendar.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.data.db.AppDatabase

@Entity(
    tableName = AppDatabase.TABLE_EVENTS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])]
)
@Serializable
data class EventEntity(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("SharedEventID")
    val sharedEventId: String?,
    @SerialName("CalendarKeyPacket")
    val calendarKeyPacket: String?, // keypackets used to decrypt Type 3 CalendarEventData, to be armored with Data packets, base64
    @SerialName("CreateTime")
    val createTime: Long, // unix timestamps
    @SerialName("ModifyTime")
    val modifyTime: Long,
    @SerialName("Permissions")
    val permissions: Int, // Permissions of the attendees (bitmap)
    // 1 (number) - Can invite
    //2 (number) - Can modify event
    //4 (number) - Can see attendees list
    @SerialName("AddressKeyPacket")
    val addressKeyPacket: String?, // shared session key encrypted with the Address Key
    @SerialName("AddressID")
    val addressId: String?, // which Address contains the Address Key ^
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String?, // base64, shared session key encrypted with Calendar Key
    @SerialName("SharedEvents")
    val sharedEvents: List<JsonElement>, // shared between all calendars
    @SerialName("CalendarEvents")
    val calendarEvents: List<JsonElement>, // specific to a calendar, shared between all calendar’s members, The data linked with the current calendar
    @SerialName("PersonalEvents")
    val personalEvents: List<JsonElement>, // specific to a member
    @SerialName("AttendeesEvents")
    val attendeesEvents: List<JsonElement>, // shared between all calendars
    @SerialName("Attendees")
    val attendees: List<JsonElement>,
    @SerialName("IsProtonProtonInvite")
    val isProtonProtonInvite: Int?, // 1 if is proton to proton invite,
    @SerialName("IsPersonalMigrated")
    val isPersonalMigrated: Boolean? = null, // if PersonalEventContent has been moved into "Notifications" property
    @SerialName("Notifications")
    val notifications: List<JsonElement>? = null,
    @SerialName("Color")
    val color: String? = null
)
