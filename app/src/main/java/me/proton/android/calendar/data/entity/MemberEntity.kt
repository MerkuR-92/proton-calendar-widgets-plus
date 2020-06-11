package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
data class MemberEntity(
    @PrimaryKey
    val id: String,
    val permissions: Int, // bitmap
    val email: String, // plaintext email address
    val calendarId: String
) {
    enum class Permission(val value: Int) { // TODO see if this is even deserialized
        /** has financial responsibility. There must always be exactly one owner but it can be transferred */
        SUPEROWNER(64),
        /** can edit permissions of all other users, and can acquire super-ownership */
        OWNER(32),
        /** can edit the permissions of all other users except for admins, can add or remove members to the calendar */
        ADMIN(16),
        /** can create new events, edit calendar-specific notes, edit events owned by the calendar, delete events, share events owned by that calendar */
        WRITE(8),
        /** can view who has access to the calendar */
        READ_MEMBER_LIST(4),
        /** can read event information */
        READ(2),
        /** can see when events are, but no further event information. Every user has availability access */
        AVAILABILITY(1)
    }

    fun hasPermission(permission: Permission) { // TODO add test
        this.permissions.and(permission.value)
    }
}
