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

// settings specific to User

@Entity(tableName = AppDatabase.TABLE_USER_SETTINGS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["fkUserId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
data class UserSettingsEntity(

    val weekStart: Int, // 1 - Monday, 7 - Sunday
    val weekLength: Int, // 0 - 7 days, 1 - 5 days
    val displayWeekNumber: Int, // 0 off, 1 on
    val dateFormat: Int, // 0 (number) - DD/MM/YYYY, 1 (number) - DD/MM/YYYY , 2 (number) - YYYY/MM/DD
    val timeFormat: Int, // 0 (number) - 24h, 1 (number) - 12h
    val autoDetectPrimaryTimezone: Int, // 0 off, 1 on
    val primaryTimezone: String, // "Europe/Budapest", NO LONGER NULL AFTER 12/06/2020: "Can be null if AutoDetectPrimaryTimezone is 0"
    val displaySecondaryTimezone: Int, // 0 off, 1 on
    val secondaryTimezone: String?, // Can be null if DisplaySecondaryTimezone is 0
    val viewPreference: Int, /* 0 - DAILY, 1 - WEEKLY, 2 - MONTHLY, 3 - YEARLY, 4 - PLANNING */
    val defaultCalendarId: String? // TODO we still get null for old accounts (even when web says there is default calendar)

) {

//    @PrimaryKey(autoGenerate = true)
//    var _id: Int = 0

    @PrimaryKey
    lateinit var fkUserId: String

}