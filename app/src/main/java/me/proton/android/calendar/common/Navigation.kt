package me.proton.android.calendar.common

import android.net.Uri
import me.proton.android.calendar.domain.Logger
import java.time.LocalDate
import java.time.LocalTime

object Navigation {

    object Deeplink {
        fun toCalendar() = Uri.parse("proton-calendar://protonmail.com/calendar")
        fun toEventDetails(eventId: String, occurrenceNumber: Int? = 0) = Uri.parse("proton-calendar://protonmail.com/event/details?eventId=$eventId&occurrenceNumber=${occurrenceNumber}")
        fun toEventEdit(eventId: String, occurrenceNumber: Int? = 0) = Uri.parse("proton-calendar://protonmail.com/event/edit?eventId=$eventId&occurrenceNumber=${occurrenceNumber}")
        fun toEventCreate(initStartDate: LocalDate, initStartTime: LocalTime? = null) = Uri.parse("proton-calendar://protonmail.com/event/create?initStartDate=${initStartDate}&initStartTime=${initStartTime ?: ""}")
    }

}