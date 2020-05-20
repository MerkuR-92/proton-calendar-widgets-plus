package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.Event

@Entity(tableName = AppDatabase.TABLE_EVENTS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )])
data class EventEntity(
    @PrimaryKey
    val id: String,
    val calendarId: String,
    val calendarKeyPacket: String?, // keypackets used to decrypt Type 3 CalendarEventData, to be armored with Data packets, base64
    val createTime: Long, // unix timestamps
    val lastEditTime: Long,
    val author: String, // Address of the last member that modified the event
    val permissions: Int, // Permissions of the attendees (bitmap)
    // 1 (number) - Can invite
    //2 (number) - Can modify event
    //4 (number) - Can see attendees list
    val sharedKeyPacket: String, // base64

    // TODO all these JsonElements are probably "CalendarEventData", PersonalEvent will have memberId additionally (maybe ignore, because it's always myself, right? Valentin)
    // TODO ??? add type converters for Dao to serialize those as strings
    val sharedEvents: List<JsonElement>, // shared between all calendars // TODO nullable?
    val calendarEvents: List<JsonElement>, // specific to a calendar, shared between all calendar’s members, The data linked with the current calendar // TODO nullable?
//    @SerializedName("PersonalEvent") // TODO change this to PersonalEvents when api fixes naming
    val personalEvents: List<JsonElement> // specific to a member // TODO nullable?

    /** AttendeesEvent & Attendees: shared between all calendars
     *
     * "AttendeesEvents": {
    "Type": 3,
    "Data": "0sFQAWfo2r7cEhfZ94HhRlYsP4r...",
    "Signature": "-----BEGIN PGP SIGNATURE..."
    },
    "Attendees": [{
    "ID": "ziWi-ZOb28XR4sCGFCEpqPDMHGU699fw==",
    "Token": "f04e8fd97e117dbfb6eef8aea85890c3dc62ea9e",
    "Status": 0,
    "Permissions": 2
    }]
     */

) {






}