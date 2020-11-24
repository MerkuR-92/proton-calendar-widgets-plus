package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.item_mini_calendar_header.view.text
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.math.ceil

class MiniCalendarItemAdapter(
    private val timeZoneId: String, // TODO MOVE TO INITIALISE
    private val forDate: LocalDate,
    private val startWeekOn: DayOfWeek,
    private val calendarViewModel: CalendarViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val clickListener: ((LocalDate) -> Unit)?
) : ListAdapter<MiniCalendarItem, MiniCalendarItemAdapter.MiniCalendarViewHolder>(DiffCallback()) {

    object CalendarSettings {
        const val DAYS_IN_A_WEEK = 7 // always 7
        const val WEEKDAYS_TO_SHOW = 7 // window might be narrower than 7, TODO
        //val FIRST_WEEKDAY = DayOfWeek.MONDAY
    }

    // TODO Workaround to make sure we are not trying the modify the list concurrently in two places (submitCalendarIndicators and markDayAsSelected)
    var selectedDate: LocalDate? = null
    private var updatingList: Boolean = false

    fun initialise() {

        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

        val headerItems = (0 until WEEKDAYS_TO_SHOW).map {
            MiniCalendarItem(firstDayOfTheMonth.plusDays(-firstDayOfTheWeekOffset + it.toLong()), false,false, emptyList())
        }

        val dummyItems = (0 until firstDayOfTheWeekOffset).map {
            null
        }

        val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
            val date = firstDayOfTheMonth.plusDays(it.toLong())
            MiniCalendarItem(date, false, true, emptyList())
        }

        this.submitList(concatenate(headerItems, dummyItems, dayItems))

        calendarViewModel.calendarIndicators(firstDayOfTheMonth, firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())).observe(lifecycleOwner) {
            updatingList = true
            submitCalendarIndicators(forDate.month, it)
        }

        calendarViewModel.selectedDate.observe(lifecycleOwner) {
            // TODO Workaround to make sure we are not trying the modify the list concurrently in two places (submitCalendarIndicators and markDayAsSelected)
            selectedDate = it
            if (!updatingList) markDayAsSelected(it)
        }
    }


    sealed class MiniCalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class HeaderViewHolder(itemView: View) : MiniCalendarViewHolder(itemView) {
            fun bind(date: LocalDate) {
                itemView.text.text = date.formatDayOfWeek(short = true)
            }
        }

        class DayViewHolder(private val itemView: View, private val timeZoneId: String) : MiniCalendarViewHolder(
            itemView
        ) {

            fun bind(item: MiniCalendarItem?, selectedDate: LocalDate? = null, clickListener: ((LocalDate) -> Unit)?) {

                if (item == null) {
                    itemView.text.text = ""
                    itemView.ll_calendar_dots.visibleOrInvisible(false)
                    itemView.setBackgroundResource(0)
                } else {

                    // TODO Workaround to manually update isSelected if we couldn't go through markDayAsSelected
                    if (selectedDate == item.date) item.isSelected = true
                    when {
                        item.isSelected -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong_Inverted)
                            itemView.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                        }
                        item.date == LocalDate.now(ZoneId.of(timeZoneId)) -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong)
                            itemView.setBackgroundResource(R.drawable.ripple_mini_calendar_day_today)
                        }
                        else -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong)
                            itemView.setBackgroundResource(R.drawable.ripple_mini_calendar_day)
                        }
                    }

                    itemView.text.text = "${item.date.dayOfMonth}"

                    itemView.ll_calendar_dots.visibleOrInvisible(!item.isSelected)
                    itemView.ll_calendar_dots.apply {
                        children.forEachIndexed { index, view ->
                            if (item.indicatorColors.size - 1 >= index) {
                                (view as ImageView).drawable.setTint(Color.parseColor(item.indicatorColors[index]))
                                view.visibleOrGone(true)
                            } else {
                                view.visibleOrGone(false)
                            }
                        }
                    }
                }

                itemView.setOnClickListener {

                    if (item != null) {
                        clickListener?.invoke(item.date)
                    }
                }
            }
        }

    }

    private fun markDayAsSelected(date: LocalDate) {

        val mutableList = currentList.toMutableList()

        // select last index, because header items contain valid date for first days of the month
        val currentIndex = currentList.indexOfLast { it?.isSelected == true }
        if (currentIndex != -1) {
            mutableList[currentIndex] = mutableList[currentIndex].copy(isSelected = false)
        }
        val indexToSelect = currentList.indexOfLast { it?.date == date }
        if (indexToSelect != -1) {
            mutableList[indexToSelect] = mutableList[indexToSelect].copy(isSelected = true)
        }

        submitList(mutableList)
    }

    private fun submitCalendarIndicators(month: Month, indicators: Map<Int, List<String>>) {

        val mutableList = currentList.toMutableList()

        mutableList.forEachIndexed { index, miniCalendarItem ->
            if (index >= WEEKDAYS_TO_SHOW && miniCalendarItem != null && miniCalendarItem.date.month == month) {
                val colors = indicators.getOrDefault(miniCalendarItem.date.dayOfMonth, emptyList())

                mutableList[index] = miniCalendarItem.copy(indicatorColors = colors)
            }
        }

        submitList(mutableList)
        updatingList = false
    }

    private val ITEM_TYPE_HEADER = 0
    private val ITEM_TYPE_DAY = 1

    override fun getItemViewType(position: Int): Int {
        return if (position < CalendarSettings.WEEKDAYS_TO_SHOW) {
            ITEM_TYPE_HEADER
        } else {
            ITEM_TYPE_DAY
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int /*later when we have more view types*/
    ): MiniCalendarViewHolder {
        return if (viewType == ITEM_TYPE_HEADER) {
            MiniCalendarViewHolder.HeaderViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_mini_calendar_header,
                    parent,
                    false
                )
            )
        } else {
                MiniCalendarViewHolder.DayViewHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_mini_calendar,
                        parent,
                        false
                    ),
                    timeZoneId
                )
            }
        }

    override fun onBindViewHolder(holder: MiniCalendarViewHolder, position: Int) {

        when (holder) {
            is MiniCalendarViewHolder.HeaderViewHolder -> holder.bind(getItem(position).date)
            is MiniCalendarViewHolder.DayViewHolder -> holder.bind(
                getItem(position),
                selectedDate
            ) {
                clickListener?.invoke(it)
            }
        }

    }

    private class DiffCallback : DiffUtil.ItemCallback<MiniCalendarItem>() {
        override fun areItemsTheSame(oldItem: MiniCalendarItem, newItem: MiniCalendarItem): Boolean {
            return oldItem.date == newItem.date && oldItem.isDay == newItem.isDay //  oldItem.equals(newItem) // we are piggybacking weekday names with LocalDate so we can't compare only "date" properties
        }

        override fun areContentsTheSame(oldItem: MiniCalendarItem, newItem: MiniCalendarItem): Boolean {
            return oldItem.equals(newItem)
        }
    }

    companion object {

        /**
         * No need to measure the adapter view, we can calculate the height because item dimensions
         * are constant.
         */
        fun calculateAdapterHeight(context: Context, firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek): Int {

            val firstDayOfTheWeekNumber = firstDayOfMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val dayCellsToShow = firstDayOfTheWeekOffset + firstDayOfMonth.lengthOfMonth()

            val fullWeeksInMonth = ceil(dayCellsToShow / DAYS_IN_A_WEEK.toDouble()).toInt()

            return context.resources.getDimensionPixelSize(R.dimen.calendar_item_header_height) +
                    fullWeeksInMonth * context.resources.getDimensionPixelSize(R.dimen.calendar_item_height) +
                    fullWeeksInMonth * 2 * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_spacing) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_bottom_spacing)

        }

    }

}

