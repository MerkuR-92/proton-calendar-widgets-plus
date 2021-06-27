package me.proton.android.calendar.presentation.calendar

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.time.LocalDate

class AgendaPagerAdapter(
    activity: FragmentActivity,
    val startingDate: LocalDate,
    private val calendarOnScrollListener: MonthFragment.CalendarOnScrollListener
) : FragmentStateAdapter(activity) {

    val startingPosition = itemCount / 2

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = ItemCalendarAgendaFragment.newInstance(
            position,
            startingDate.plusDays((position - startingPosition).toLong())
        )
        fragment.setCalendarOnScrollListener(calendarOnScrollListener)
        return fragment
    }
}
