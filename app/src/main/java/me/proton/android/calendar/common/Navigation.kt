package me.proton.android.calendar.common

import android.net.Uri
import me.proton.android.calendar.domain.Logger
import java.time.LocalDate
import java.time.LocalTime

object Navigation {

    object Deeplink {
        fun toEventDetails(eventId: String) = Uri.parse("proton-calendar://protonmail.com/event/details?eventId=$eventId")
        fun toEventEdit(eventId: String) = Uri.parse("proton-calendar://protonmail.com/event/edit?eventId=$eventId")
        fun toEventCreate(initStartDate: LocalDate, initStartTime: LocalTime? = null) = Uri.parse("proton-calendar://protonmail.com/event/create?initStartDate=${initStartDate}&initStartTime=${initStartTime ?: ""}")
    }

}