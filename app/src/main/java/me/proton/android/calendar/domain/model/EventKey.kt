package me.proton.android.calendar.domain.model

data class EventKey(
    val eventId: String,
    val calendarId: String
)

val Event.key get() = EventKey(id, calendar.id)
