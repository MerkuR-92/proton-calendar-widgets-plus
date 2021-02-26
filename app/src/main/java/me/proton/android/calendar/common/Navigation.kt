package me.proton.android.calendar.common

import android.net.Uri
import me.proton.android.calendar.domain.Logger
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Navigation {

    object Deeplink {
        fun toRoot() = Uri.parse("proton-calendar://protonmail.com/root")
        fun toMonth(selectedDate: LocalDate? = null): Uri {
            return Uri.parse(
                "proton-calendar://protonmail.com/month" +
                        if (selectedDate != null) "/details?date=${selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                        else "")
        }
        fun toLogin() = Uri.parse("proton-calendar://protonmail.com/login")
        fun toEventDetails(eventId: String, occurrenceNumber: Int? = 0) = Uri.parse("proton-calendar://protonmail.com/event/details?eventId=$eventId&occurrenceNumber=${occurrenceNumber}")
        fun toMainActivityWithEventId(eventId: String, occurrenceNumber: Int? = 0) = Uri.parse("proton-calendar://protonmail.com/main?eventId=$eventId&occurrenceNumber=${occurrenceNumber}")
        fun toEventEdit(eventId: String? = null, occurrenceNumber: Int? = 0) = Uri.parse("proton-calendar://protonmail.com/event/edit?eventId=$eventId&occurrenceNumber=${occurrenceNumber}")
        fun toEventCreate(initStartDate: LocalDate, initStartTime: LocalTime? = null) = Uri.parse("proton-calendar://protonmail.com/event/create?initStartDate=${initStartDate}&initStartTime=${initStartTime ?: ""}")
    }

}
