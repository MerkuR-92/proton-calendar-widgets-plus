package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.*

// User settings for entire account

@Entity(tableName = AppDatabase.TABLE_USER_SETTINGS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["fkUserId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
@Serializable
data class UserSettingsEntity(

    @SerialName("WeekStart")
    val weekStart: Int, // 0: Locale default, 1: Monday, 6: Saturday 7: Sunday
    @SerialName("DateFormat")
    val dateFormat: Int, // 0: Locale default, 1: DD_MM_YYYY, 2: MM_DD_YYYY, 3: YYYY_MM_DD
    @SerialName("TimeFormat")
    val timeFormat: Int, // 0: Locale default, 1: 24H, 2: 12H

) {

//    @PrimaryKey(autoGenerate = true)
//    var _id: Int = 0

    @PrimaryKey
    @kotlinx.serialization.Transient
    lateinit var fkUserId: String

    fun weekStartDayOfWeek(): DayOfWeek = when (weekStart) {
        1 -> DayOfWeek.MONDAY
        6 -> DayOfWeek.SATURDAY
        7 -> DayOfWeek.SUNDAY
        else -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
    }

    fun timeFormatIs24Hour(default: Boolean): Boolean = when (timeFormat) {
        1 -> true
        2 -> false
        else -> default
    }

}
