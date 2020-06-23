package me.proton.android.calendar.domain.model

import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.property.DateOrDateTimeProperty
import biweekly.util.Frequency
import me.proton.android.calendar.R
import me.proton.android.calendar.common.OFFLINE_EVENT_ID_PREFIX
import java.security.Signature
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class Event(
    override val id: String, // ID from API and local database
    val calendar: Calendar,
    val iCalendar: ICalendar,
    val verificationStatus: SignatureVerification? = null
) : BaseModel() {

    val iCalEvent: VEvent get() = iCalendar.events.first()

    val uid: String get() = iCalEvent.uid.value
    val summary: String? get() = iCalEvent.summary?.value // TODO maybe go back to properties?
    val location: String? get() = iCalEvent.location?.value
    val description: String? get() = iCalEvent.description?.value

    val defaultTimeZone: String? get() = iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id








//    val startTimeZoneId: String? get() = iCalendar.timezoneInfo?.getTimezone(iCalEvent.dateStart)?.timeZone?.id
//    val startTimeZoneId: String? get() = iCalendar.timezoneInfo?.getTimezone(iCalEvent.dateStart)?.timeZone?.id


    val startLocalDate: LocalDate? get() = Instant.ofEpochMilli(iCalEvent.dateStart?.value?.getTime()!!)
        .atZone(ZoneId.systemDefault())
        .toLocalDate() //LocalDate.from(iCalEvent.dateStart?.value?.toInstant())
    val endLocalDate: LocalDate? get() = Instant.ofEpochMilli(iCalEvent.dateEnd?.value?.getTime()!!)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
//    val endLocalDate: LocalDate? get() = LocalDate.from(iCalEvent.dateEnd?.value?.toInstant())






//    fun getStart(): LocalDateTime


    fun getStart(timeZoneId: String): ZonedDateTime? {
        return if (iCalEvent.dateStart?.value != null) ZonedDateTime.ofInstant(iCalEvent.dateStart.value.toInstant(), ZoneId.of(timeZoneId)) else null
    }

    fun getEnd(timeZoneId: String): ZonedDateTime? {
        return if (iCalEvent.dateEnd?.value != null) ZonedDateTime.ofInstant(iCalEvent.dateEnd.value.toInstant(), ZoneId.of(timeZoneId)) else null
    }

    fun formatStart(timeZoneId: String) = formatDateOrDateTimeProperty(iCalEvent.dateStart, timeZoneId)

    fun formatEnd(timeZoneId: String) = formatDateOrDateTimeProperty(iCalEvent.dateEnd, timeZoneId)




    /**
     * @return <formatted date?, formatted time?>
     */
    private fun formatDateOrDateTimeProperty(property: DateOrDateTimeProperty?, timeZoneId: String) : Pair<String?, String?> {
        var formattedDate: String? = null
        var formattedTime: String? = null

        if (property != null) {
            val zonedDateTime = ZonedDateTime.ofInstant(property.value.toInstant(), ZoneId.of(timeZoneId))
            formattedDate = zonedDateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            if (property.value.hasTime()) {
                formattedTime = zonedDateTime.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            }
        }
        return Pair(formattedDate, formattedTime)
    }

    //val notes: List<String> = iCalEvent.comments.map { it.value }
//        val dateStart: String = iCalEvent.dateStart.value.toString() // TODO probably won't be able to use this in GUI anyway
//        val dateEnd: String = iCalEvent.dateEnd.value.toString() // TODO probably won't be able to use this in GUI anyway
        /**
         * Timestamp when this iCalendar Event was created.
         */
//        val dateTimeStamp: String

    fun isSyncedWithApi(): Boolean = !id.startsWith(OFFLINE_EVENT_ID_PREFIX, ignoreCase = false)

    fun isAllDay(): Boolean = iCalEvent.dateStart?.value?.hasTime() == false && (if (iCalEvent.dateEnd != null) iCalEvent.dateStart?.value?.hasTime() == false else true)

    fun spansSingleDay(): Boolean {

        // DateStart and DateEnd are normalized to UTC so we can easily determine if they happen on the same day or not

        val dateStart = if (iCalEvent.dateStart == null) null else LocalDate.of(iCalEvent.dateStart.value.rawComponents.year, iCalEvent.dateStart.value.rawComponents.month, iCalEvent.dateStart.value.rawComponents.date)
        val dateEnd = if (iCalEvent.dateEnd == null) null else LocalDate.of(iCalEvent.dateEnd.value.rawComponents.year, iCalEvent.dateEnd.value.rawComponents.month, iCalEvent.dateEnd.value.rawComponents.date)

        if (dateStart == null) {
            return false
        }

        return if (isAllDay()) {
            dateEnd == null || dateStart == dateEnd.minusDays(1)
        } else {
            dateStart == dateEnd
        }
    }

    fun isRecurring(): Boolean = this.iCalEvent.recurrenceRule != null

    fun isCustomRecurring(): Boolean {

        // no Recurrence Rule
        if (this.iCalEvent.recurrenceRule == null) return false

        // custom rule exists when there is at least Interval set
        if (this.iCalEvent.recurrenceRule.value.interval != null) return true

        // or if no Interval is set, it's implicitly set to 1 and Count or Until are set
        if (this.iCalEvent.recurrenceRule.value.count != null || this.iCalEvent.recurrenceRule.value.until != null) return true

        // by default we return false which means we will ignore non-supported combinations
        return false

    }


        data class SharedEvent( // TODO maybe this could be named "SharedPart" or "SharedSplit", the same for others
            val type: Int, // 2 for SIGNED 3 for encrypted + signed
            val data: String,
            val signature: String,
            val author: String
        ) {
            val isEncrypted: Boolean get() = type and 1 > 0
//        val isSigned: Boolean get() = type and 2 > 0
        }

        data class CalendarEvent(
            val type: Int, // 2 for SIGNED 3 for encrypted + signed
            val data: String,
            val signature: String,
            val author: String
        ) {
            val isEncrypted: Boolean get() = type and 1 > 0
//        val isSigned: Boolean get() = type and 2 > 0
        }

        data class PersonalEvent(
            val type: Int, // 2 for SIGNED 3 for encrypted + signed
            val data: String,
            val signature: String,
            val author: String,
            val memberId: String
        ) {
//        val isSigned: Boolean get() = type and 2 > 0
        }

        enum class SignatureVerification {
            SUCCESS,
            FAILURE,
            NO_KEYS
        }

    }


