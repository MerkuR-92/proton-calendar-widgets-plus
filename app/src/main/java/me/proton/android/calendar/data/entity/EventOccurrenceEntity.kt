package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.EventKey

@Entity(
    tableName = AppDatabase.TABLE_EVENTS_OCCURRENCES,
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id", "calendarId"],
        childColumns = ["eventId", "calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["eventId", "calendarId"]), Index(value = ["eventUid"])]
)
@Serializable
data class EventOccurrenceEntity(
    val userId: String,
    val calendarId: String,
    val eventId: String,
    val eventUid: String,
    val fullDay: Int,
    val startTime: Long? = null, // if these are null then event doesn't happen in this window
    val endTime: Long? = null,
    val windowStartTime: Long,
    val windowEndTime: Long,
    val firstOccurrenceStartTime: Long,
    val lastOccurrenceEndTime: Long? = null, // if non-null it means we generated last occurrence in one of the windows
    val rRule: String? = null,
    val modifyTime: Long,
    // metadata from EventEntityMetadata so we can expand RRULEs and apply EXDATE/RECURRENCE-ID masking
    val startTimeZone: String = "",
    val endTimeZone: String = "",
    val exDates: List<Long> = emptyList(),
    val recurrenceID: Long? = null,

    @PrimaryKey(autoGenerate = true)
    var _id: Int = 0
)

val EventOccurrenceEntity.key get() = EventKey(eventId, calendarId)

fun List<EventOccurrenceEntity>.distinct(): List<EventOccurrenceEntity> =
    this.distinctBy { (Triple(it.userId, it.calendarId, it.eventId)) }

