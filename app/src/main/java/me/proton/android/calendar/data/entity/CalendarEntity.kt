package me.proton.android.calendar.data.entity

import androidx.annotation.NonNull
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.core.user.data.entity.UserEntity

@Entity(
    tableName = AppDatabase.TABLE_CALENDARS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["userId"],
        childColumns = ["fkUserId"],
        onDelete = CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
@Serializable
data class CalendarEntity(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Color")
    val color: String,
    @SerialName("Display")
    val display: Int, // 0: hide, 1: show //CalendarDisplay, TODO maybe parse it as boolean?
    @SerialName("Flags")
    val flags: Int = 1, // Flag not returned for Update/Create calendar. Default for create is 1 but on Update we keep the previous value
    @SerialName("Type")
    val type: Int = 0, // normal calendar: 0, subscribed calendar: 1
    @NonNull
    @kotlinx.serialization.Transient
    val fkUserId: String = "" // TODO Split in two classes: One RemoteEntity and one DBEntity
) {

    // TODO remove all of the flags below and 3 props above

    //Functions to check all three states because it can be disabled but not inactive, or inactive but not disabled
    val isActive: Boolean get() = flags == 1
    val isInactive: Boolean get() = (flags and (0 + 2 + 4 + 8 + 16) >= 1)
    val isDisabled: Boolean get() = !isInactive && (flags and (32 + 64) >= 1)
    val isSuperOwnerDisabled: Boolean get() = flags and 64 == 64
    val hasIncompleteKeySetup: Boolean get() = flags and 8 == 8
    val isResetNeeded: Boolean get() = flags and 4 == 4
    val hasUpdatePassphrase: Boolean get() = flags and 2 == 2

    val isSubscribed: Boolean get() = type == 1
}

enum class CalendarFlags(val value: Int) {
    /** 0 - Inactive: the calendar keys are not accessible and the current user cannot fix it */
    INACTIVE(0),
    /** 1 - Active: the calendar is all good! */
    ACTIVE(1),
    /** 2 - Update passphrase: a deactivated passphrase is again accessible, you should re-encrypt the linked calendar key using the primary passphrase */
    UPDATE_PASSPHRASE(2),
    /** 4 - Reset needed: the calendar needs to be reset */
    RESET_NEEDED(4),
    /** 8 - Incomplete setup: the calendar setup was not completed, need to setup the key and passphrase */
    INCOMPLETE_SETUP(8),
    /** 16 - Lost access: the user lost access to the calendar but an admin can re-invite him */
    LOST_ACCESS(16),
    /** 32 - Disabled calendar: the calendar is disabled for the current user only (all the addresses linked to the current user's members are disabled) */
    DISABLED(32),
    /** 64 - Super-owner disabled calendar: the calendar is disabled because the super-owner disabled his address. Possibility to transfer super-ownership to reactive the calendar. */
    SUPER_OWNER_DISABLED(64)
}
