package me.proton.android.calendar.presentation.calendar

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import me.proton.android.calendar.common.TimberLogger
import java.time.LocalDate

class MiniCalendarPagerAdapter(activity: FragmentActivity, val firstDayOfMonth: LocalDate) : FragmentStateAdapter(activity) {

    val startingPosition = itemCount / 2
    val miniCalendarPositionListeners: HashMap<Int, ItemMiniCalendarFragment.MiniCalendarPositionListener> = hashMapOf()

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = ItemMiniCalendarFragment.newInstance(
            position,
            startingPosition,
            firstDayOfMonth
        )
        fragment.setAdapter(this)
        miniCalendarPositionListeners[position] = fragment.getMiniCalendarPositionListener()
        return fragment
    }

    fun miniCalendarsScrollUp(position: Int, scrollValue: Float, sliderTop: Int) {
        miniCalendarPositionListeners[position]?.scrollUp(scrollValue, sliderTop)
    }

    fun miniCalendarsScrollDown(position: Int, scrollValue: Float, sliderTop: Int) {
        miniCalendarPositionListeners[position]?.scrollDown(scrollValue, sliderTop)
    }

    fun resetMiniCalendarsPosition(position: Int) {
        miniCalendarPositionListeners[position]?.resetPosition()
    }
}
