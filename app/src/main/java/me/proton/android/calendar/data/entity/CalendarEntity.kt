package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import me.proton.android.calendar.data.db.AppDatabase

@Entity(
    tableName = AppDatabase.TABLE_CALENDARS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["fkUserId"],
        onDelete = CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
data class CalendarEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val color: String,
    val display: Int, // 0: hide, 1: show //CalendarDisplay, TODO maybe parse it as boolean?
    val flags: Int //
    // 0 - Inactive: the calendar keys are not accessible and the current user cannot fix it
    // 1 - Active: the calendar is all good!
    // 2 - Update passphrase: a deactivated passphrase is again accessible, you should re-encrypt the linked calendar key using the primary passphrase
    // 4 - Reset needed: the calendar needs to be reset
    // 8 - Incomplete setup: the calendar setup was not completed, need to setup the key and passphrase
    // 16 - Lost access: the user lost access to the calendar but an admin can re-invite him
    // 32 - Disabled calendar: the calendar is disabled for the current user only (all the addresses linked to the current user's members are disabled)
    // 64 - Super-owner disabled calendar: the calendar is disabled because the super-owner disabled his address. Possibility to transfer super-ownership to reactive the calendar.


    // local database fields
//    val fkUserId: String


    // @ColumnInfo, @Ignore
) {

    //@Expose(serialize = false, deserialize = false)
    lateinit var fkUserId: String

    //Functions to check all three states because it can be disabled but not inactive, or inactive but not disabled
    val isActive: Boolean get() = !isDisabled && !isInactive && (flags and 1 == 1)
    val isInactive: Boolean get() = (flags and (0 + 2 + 4 + 8 + 16) >= 1)
    val isDisabled: Boolean get() = (flags and (32 + 64) >= 1)
}
