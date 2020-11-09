package me.proton.android.calendar.presentation.calendar

import java.time.LocalDate

data class MiniCalendarItem(
    val date: LocalDate,
    var isSelected: Boolean,
    /**
     * Item is an actual day indicator and not dummy helper
     */
    val isDay: Boolean,
    val indicatorColors: List<String>
)
