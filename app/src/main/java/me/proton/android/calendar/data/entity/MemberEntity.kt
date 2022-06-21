package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase

/**
 * This is a Member of a Calendar.
 */
@Entity(tableName = AppDatabase.TABLE_MEMBERS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class MemberEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("Permissions")
    val permissions: Int, // bitmap
    @SerialName("Email")
    val email: String, // plaintext email address
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("Color")
    val color: String,
    @SerialName("Display")
    val display: Int, // 0: hide, 1: show
    @SerialName("Flags")
    val flags: Int
) {
    enum class Permission(val value: Int) { // TODO see if this is even deserialized
        /** has financial responsibility. There must always be exactly one owner but it can be transferred */
        SUPEROWNER(1),
        /** can edit permissions of all other users, and can acquire super-ownership */
        OWNER(2),
        /** can edit the permissions of all other users except for admins, can add or remove members to the calendar */
        ADMIN(4),
        /** can view who has access to the calendar */
        READ_MEMBER_LIST(8),
        /** can create new events, edit calendar-specific notes, edit events owned by the calendar, delete events, share events owned by that calendar */
        WRITE(16),
        /** can read event information */
        READ(32),
        /** can see when events are, but no further event information. Every user has availability access */
        AVAILABILITY(64)
    }

    fun hasPermission(permission: Permission): Boolean { // TODO add test
        return this.permissions.and(permission.value) >= 1
    }

    val hasIncompleteKeySetup: Boolean get() = flags and 8 == 8
}
