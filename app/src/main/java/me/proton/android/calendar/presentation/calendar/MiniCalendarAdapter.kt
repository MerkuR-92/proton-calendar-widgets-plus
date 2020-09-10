package me.proton.android.calendar.presentation.calendar

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.time.LocalDate

class MiniCalendarAdapter(activity: FragmentActivity, private val calendarViewModel: CalendarViewModel, val startingDate: LocalDate) : FragmentStateAdapter(activity) {

    val startingPosition = itemCount / 2

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    override fun createFragment(position: Int): Fragment {
        return ItemMiniCalendarFragment(
            calendarViewModel,
            position,
            startingDate.withDayOfMonth(1).plusMonths((position - startingPosition).toLong())
        )
    }
}
