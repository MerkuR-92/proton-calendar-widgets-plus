package me.proton.android.calendar.presentation.calendar

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.time.LocalDate

class MiniCalendarPagerAdapter(activity: FragmentActivity, val firstDayOfMonth: LocalDate, private val onFlingMiniCalendarListener: MonthFragment.OnFlingMiniCalendarListener) : FragmentStateAdapter(activity) {

    val startingPosition = itemCount / 2

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = ItemMiniCalendarFragment.newInstance(
            position,
            startingPosition,
            firstDayOfMonth
        )
        fragment.setOnFlingMiniCalendarListener(onFlingMiniCalendarListener)
        return fragment
    }
}
