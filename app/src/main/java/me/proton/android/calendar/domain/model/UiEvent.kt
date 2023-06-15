package me.proton.android.calendar.domain.model

import biweekly.parameter.ParticipationStatus
import biweekly.property.Status
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Lightweight representation of an Event, not containing ICS payload.
 */
data class UiEvent(
    override val id: String,
    val calendarId: String,

    val uid: String,
    val summary: String?,
    val location: String?,
    val description: String?,
    val dateStart: ZonedDateTime, // this is actual date start of single event or occurrence of recurring event
    val dateEnd: ZonedDateTime,
    val isAllDay: Boolean,
    val occurrenceNumber: Int, // TODO make it nullable or 0 is non-recurring?
    val isRecurring: Boolean, // TODO technically we could determine this from occurrenceNumber being > 0

    val calendarColor: String,

    val decryptionStatus: Event.DecryptionStatus,

    // TODO null for non-invite
    val participationStatus: ParticipationStatus?,
    val status: Status? // TODO non-nullable and make it default to what?

) : BaseModel() {

    constructor() : this(
        "",
        "",
        "",
        "",
        "",
        "",
        ZonedDateTime.now(),
        ZonedDateTime.now(),
        false,
        0,
        false,
        "#CCCCCC",
        Event.DecryptionStatus.FAILURE,
        null,
        null
    )

    /**
     * Attention: part-time Event ending at 00:00 is not considered to span the end-day.
     */
    fun spansSingleDay(actualEndDate: Boolean = false, timeZoneId: String? = null): Boolean {

        val dateTimeStart = this.dateStart.withZoneSameInstant(if (timeZoneId != null) ZoneId.of(timeZoneId) else ZoneId.systemDefault())
        val dateStart = dateTimeStart.toLocalDate()
        val dateTimeEnd = this.dateEnd.withZoneSameInstant(if (timeZoneId != null) ZoneId.of(timeZoneId) else ZoneId.systemDefault())
        val dateEnd = dateTimeEnd.toLocalDate()

        return if (this.isAllDay) {
            dateEnd == null || dateStart == dateEnd.minusDays(if (actualEndDate) 0 else 1)
        } else {

            if (dateTimeStart == dateTimeEnd) {
                true
            } else if (dateTimeEnd.toLocalTime() == LocalTime.MIDNIGHT) {
                // for part-day Event, if it ends on Midnight, we don't count it spanning that last day
                dateStart == dateEnd.minusDays(1)
            } else {
                dateStart == dateEnd
            }
        }
    }

    fun isInThePast(timeZoneId: String): Boolean = this.dateEnd.isBefore(ZonedDateTime.now(ZoneId.of(timeZoneId)))

    fun isCancelled(): Boolean = (this.status != null) && this.status.isCancelled

}


