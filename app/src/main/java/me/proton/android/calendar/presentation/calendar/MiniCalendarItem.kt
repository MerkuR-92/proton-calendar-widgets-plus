package me.proton.android.calendar.presentation.calendar

import java.time.LocalDate

data class MiniCalendarItem(
    val date: LocalDate,
    val isSelected: Boolean,
    val indicatorColors: List<String>
)
