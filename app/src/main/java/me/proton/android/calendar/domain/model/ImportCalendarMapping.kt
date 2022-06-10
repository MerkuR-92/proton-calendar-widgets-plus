package me.proton.android.calendar.domain.model

data class ImportCalendarMapping(
    var importCalendar: Boolean,

    // Source
    val sourceId: String,
    val sourceName: String,
    val sourceEmail: String,

    // Destination
    var createDestinationCalendar: Boolean,
    var destinationId: String?, // Value is null when calendar hasn't been created yet
    var destinationName: String,
    var destinationEmail: String,
    var destinationColor: Int
)
