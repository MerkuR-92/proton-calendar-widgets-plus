package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase
import java.time.Duration

// = calendar subscription
@Entity(tableName = AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class CalendarSubscriptionEntity(
    @PrimaryKey
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("CreateTime")
    val createTime: Int,
    @SerialName("LastUpdateTime")
    val lastUpdateTime: Int, // Unix timestamp of the subscription last update time
    @SerialName("Status")
    val status: Int, // 0: OK, 1: Error, more to come
    @SerialName("URL")
    val url: String
) {
    // Calendar is in sync if status is 0 AND has been updated in the last 12 hours
    val isSynced: Boolean get() = status == 0 && ((System.currentTimeMillis() / 1000) - lastUpdateTime < Duration.ofHours(12).seconds)
}
