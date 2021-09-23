package me.proton.android.calendar.domain.model

import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.parameter.ParticipationStatus
import biweekly.property.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtilsImpl.sanitise
import java.time.*
import java.time.temporal.ChronoUnit
import me.proton.android.calendar.common.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.ICalUtilsImpl.clone
import me.proton.android.calendar.common.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.ICalUtilsImpl.getEnd
import me.proton.android.calendar.common.ICalUtilsImpl.getStart
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.ICalUtilsImpl.setStartTimeZone

// TODO remove nullability from signature verification and decryption statuses
data class Event private constructor(
    override val id: String, // ID from API and local database
    val calendar: Calendar,
    val iCalendar: ICalendar,
    val verificationStatus: SignatureVerification? = null,
    val decryptionStatus: DecryptionStatus? = null,
    val currentUserAttendeeId: String? = null,
    val sharedEventId: String? = null,
    val isProtonProtonInvite: Boolean? = null
) : BaseModel() {

    companion object {

        fun from(
            id: String, // ID from API and local database
            calendar: Calendar,
            iCalendar: ICalendar,
            verificationStatus: SignatureVerification? = null,
            decryptionStatus: DecryptionStatus? = null,
            currentUserAttendeeId: String? = null,
            sharedEventId: String? = null,
            isProtonProtonInvite: Boolean? = null
        ): Event? {

            val vEvent = iCalendar.events.firstOrNull()

            return if (vEvent?.sanitise() == true) {
                Event(
                    id,
                    calendar,
                    iCalendar,
                    verificationStatus,
                    decryptionStatus,
                    currentUserAttendeeId,
                    sharedEventId,
                    isProtonProtonInvite
                )
            } else null

        }

        /**
         * Makes sure we have deep copy of [ICalendar] object inside [Event].
         * Copy event with specified id / calendar / iCalendar values
         */
        fun from(event: Event, id: String? = null, calendar: Calendar? = null, iCalendar: ICalendar? = null): Event {
            val defaultTimezoneId = event.iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id
            return event.copy(
                id = id ?: event.id,
                calendar = calendar ?: event.calendar,
                iCalendar = (iCalendar ?: ICalendar(event.iCalendar)).apply {
                    setDefaultTimeZone(defaultTimezoneId)
                })
        }

        /**
         * Returns copy of an [Event] with overwritten start & end datetime with [Occurrence] values in a given [timeZoneId].
         */
        fun withOccurrence(event: Event, occurrenceNumber: Int, timeZoneId: String): Event? {

            val occurrence = event.generateOccurrence(occurrenceNumber, timeZoneId)

            return if (occurrence != null) Event.withOccurrence(event, occurrence) else null
        }

        /**
         * Returns copy of an [Event] with overwritten start & end datetime with [Occurrence] values.
         */
        fun withOccurrence(event: Event, occurrence: Occurrence): Event {
            return event.copy(iCalendar = event.iCalendar.copy() as ICalendar).apply {
                if (this.isAllDay()) {
                    this.iCalEvent.setStart(occurrence.startDateTime.toLocalDate())
                    this.iCalEvent.setEnd(occurrence.endDateTime.toLocalDate())
                } else {
                    this.iCalEvent.setStart(occurrence.startDateTime.toLocalDate(), occurrence.startDateTime.toLocalTime(), occurrence.startDateTime.zone.id)
                    this.iCalEvent.setEnd(occurrence.endDateTime.toLocalDate(), occurrence.endDateTime.toLocalTime(), occurrence.endDateTime.zone.id)
                    this.iCalendar.setStartTimeZone(occurrence.startDateTime.zone.id)
                    this.iCalendar.setEndTimeZone(occurrence.endDateTime.zone.id)
                }
                this.occurrence = occurrence
            }
        }


    }

    var occurrence: Occurrence? = null

    val iCalEvent: VEvent get() = iCalendar.events.first()

    val uid: String get() = iCalEvent.uid.value
    val summary: String? get() = iCalEvent.summary?.value
    val location: String? get() = iCalEvent.location?.value
    val description: String? get() = iCalEvent.description?.value

    val status: Status? get() = iCalEvent.status

    val hasProtonUid: Boolean get() = uid.endsWith(PROTON_UID) || uid.startsWith(PROTON_OLD_UID)

    val defaultTimeZone: String? get() = iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id

    // TODO Change this once we allow editing event with attendees
    val isAnInvitation: Boolean get() = !this.iCalEvent.attendees.isNullOrEmpty()

    val hasProtonProtonProperties: Boolean get() =
        iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID)?.value?.isNotBlank() == true &&
                iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SESSION_KEY)?.value?.isNotBlank() == true

    val isProtonProtonReply = iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_PROTON_REPLY)?.value == "TRUE"

    val hasEmailNotifications: Boolean get() = this.iCalEvent.alarms.any { it.action == Action.email() }

    fun getStart(timeZoneId: String): ZonedDateTime {
        return iCalEvent.getStart(timeZoneId)!!
    }

    fun getEnd(timeZoneId: String): ZonedDateTime {
        return iCalEvent.getEnd(timeZoneId)!!
    }

    fun getOccurrenceStart(timeZoneId: String): ZonedDateTime {
        return if (this.isSingleEdit()) {
            getStart(timeZoneId)
        } else if (this.isAllDay()) {
            occurrence?.startDateTime?.withZoneSameLocal(ZoneId.of(timeZoneId)) ?: getStart(timeZoneId)
        } else {
            occurrence?.startDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: getStart(timeZoneId)
        }
    }

    fun getOccurrenceEnd(timeZoneId: String): ZonedDateTime {
        return if (this.isSingleEdit()) {
            getEnd(timeZoneId)
        } else if (this.isAllDay()) {
            occurrence?.endDateTime?.withZoneSameLocal(ZoneId.of(timeZoneId)) ?: getEnd(timeZoneId)
        } else {
            occurrence?.endDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: getEnd(timeZoneId)
        }
    }

    fun isInThePast(timeZoneId: String): Boolean {
        return this.getOccurrenceEnd(timeZoneId).isBefore(ZonedDateTime.now(ZoneId.of(timeZoneId))) == true
    }

    fun isCancelled(): Boolean {
        return (this.status != null) && (this.status as Status).isCancelled
    }



    fun isSyncedWithApi(): Boolean = !id.startsWith(OFFLINE_EVENT_ID_PREFIX, ignoreCase = false)

    fun isAllDay(): Boolean = iCalEvent.dateStart?.value?.hasTime() == false && (if (iCalEvent.dateEnd != null) iCalEvent.dateEnd?.value?.hasTime() == false else true)

    fun spansSingleDay(actualEndDate: Boolean = false, timeZoneId: String? = null): Boolean {

        val dateStart = this.getStart(timeZoneId ?: ZoneId.systemDefault().id).toLocalDate()
        val dateEnd = this.getEnd(timeZoneId ?: ZoneId.systemDefault().id).toLocalDate()

        if (dateStart == null) {
            return false
        }

        return if (isAllDay()) {
            dateEnd == null || dateStart == dateEnd.minusDays(if (actualEndDate) 0 else 1)
        } else {
            dateStart == dateEnd
        }
    }

    /**
     * Occurrence should always be expressed in timezone we format or display the calendar with.
     */
    data class Occurrence(val startDateTime: ZonedDateTime, val endDateTime: ZonedDateTime, val occurrenceNumber: Int)



    fun isRecurring(): Boolean = this.iCalEvent.recurrenceRule != null

    fun isSingleEdit(): Boolean = this.iCalEvent.recurrenceId != null

    fun isSingleOccurrenceRecurring(timeZoneId: String): Boolean =
        isRecurring() && (iCalEvent.recurrenceRule.value.count == 1 ||
                (iCalEvent.recurrenceRule.value.count != null && iCalEvent.exceptionDates?.distinct()?.size == iCalEvent.recurrenceRule.value.count - 1) ||
                isRecurringUntilSameDay(timeZoneId) ||
                isRecurringUntilBeforeNextOccurrence(timeZoneId))

    fun isRecurringUntilSameDay(timeZoneId: String): Boolean {
        if (iCalEvent.recurrenceRule.value.until == null) return false
        val untilZonedDateTime = iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        val dateStart = iCalEvent.dateStart.value.toZonedDateTime(timeZoneId)
        return ChronoUnit.DAYS.between(dateStart, untilZonedDateTime).toInt() <= 0
    }

    fun isRecurringUntilBeforeNextOccurrence(timeZoneId: String): Boolean {
        if (iCalEvent.recurrenceRule.value.until == null) return false
        val untilZonedDateTime = iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        val occurrenceCount = this.generateOccurrencesUntil(untilZonedDateTime.toLocalDate(), timeZoneId)?.size
        return occurrenceCount == null || occurrenceCount <= 1 || occurrenceCount - 1 == iCalEvent.exceptionDates?.distinct()?.size
    }

    fun isEventFirstOccurrence(originalEvent: Event, timeZoneId: String): Boolean {

        val startDate = this.getStart(timeZoneId).toLocalDate()
        val occurrences = originalEvent.generateOccurrencesUntil(startDate, timeZoneId)

        val exZonedDateTimes =
            originalEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                exDates.values.map { exDate ->
                    exDate.toZonedDateTime(timeZoneId)
                }
            }

        return if (exZonedDateTimes.isNullOrEmpty()) {
            false
        } else {
            occurrences?.filterNot {
                it.startDateTime in exZonedDateTimes
            }?.size == 1
        }
    }

    fun isCustomRecurring(): Boolean {

        // no Recurrence Rule
        if (!this.isRecurring()) return false

        // look for any of the supported properties
        if (this.iCalEvent.recurrenceRule.value.interval != null ||
            this.iCalEvent.recurrenceRule.value.count != null ||
            this.iCalEvent.recurrenceRule.value.until != null ||
            this.iCalEvent.recurrenceRule.value.byDay?.size ?: 0 > 0
        ) return true

        // by default we return false which means we will ignore non-supported combinations
        return false

    }

    fun isUserOrganizer(userEmails: List<String>?): Boolean {
        return if (this.isAnInvitation) {
            val organizerEmail = this.iCalEvent.organizer?.extractEmail()
            organizerEmail != null && userEmails?.contains(ProtonUtilsImpl.canonicalizeProtonEmail(organizerEmail)) == true
        } else false
    }

    fun isUserAttendee(userEmails: List<String>?): Boolean {
        val canonicalUserEmails = userEmails?.map { ProtonUtilsImpl.canonicalizeProtonEmail(it, forceCanonicalization = true) }
        return if (this.isAnInvitation) {
            val attendeeEmails = this.iCalEvent.attendees?.mapNotNull { it.extractEmail() }
            attendeeEmails != null && attendeeEmails.find { canonicalUserEmails?.contains(ProtonUtilsImpl.canonicalizeProtonEmail(it, forceCanonicalization = true)) == true } != null
        } else false
    }

    // this event might be a single edit so it's technically a separate event in the database,
    //  but it's still considered as a part of a chain of events
    fun isPartOfChain(): Boolean = this.isRecurring() || this.isSingleEdit()

    sealed class EventPart {

        abstract val type: Int // 0: cleartext, 1: encrypted, 2: signed, 3: encrypted & signed
        abstract val data: String
        abstract val signature: String?
        abstract val author: String

        val isEncrypted: Boolean get() = type and 1 > 0
        val isSigned: Boolean get() = type and 2 > 0

        @Serializable
        data class Shared(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

        @Serializable
        data class Calendar(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

        @Serializable
        data class Personal(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String,
            @SerialName("MemberID")
            val memberId: String
        ): EventPart()

        @Serializable
        data class Attendee(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

    }

    @Serializable
    data class AttendeeStatusEvent(
        @SerialName("ID")
        val id: String? = null,
        @SerialName("Token")
        val token: String,
        @SerialName("Status")
        val status: Int,
        @SerialName("UpdateTime")
        val updateTime: Int? = null
    ) {
        val participationStatus: ParticipationStatus get() = when (status) {
            1 -> ParticipationStatus.TENTATIVE
            2 -> ParticipationStatus.DECLINED
            3 -> ParticipationStatus.ACCEPTED
            else -> ParticipationStatus.NEEDS_ACTION
        }
    }

    enum class SignatureVerification {
        SUCCESS,
        FAILURE,
        SIGNED_BUT_NO_KEYS,
        NOT_SIGNED
    }

    enum class DecryptionStatus {
        SUCCESS,
        FAILURE
    }
}




