package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_header.view.text
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class MiniCalendarItemAdapter(
    private val timeZoneId: String, // TODO MOVE TO INITIALISE
    private val forDate: LocalDate,
    private val startWeekOn: DayOfWeek,
    private val clickListener: ((LocalDate) -> Unit)?
) : ListAdapter<MiniCalendarItem, MiniCalendarItemAdapter.MiniCalendarViewHolder>(DiffCallback()) {

    object CalendarSettings {
        const val DAYS_IN_A_WEEK = 7 // always 7
        const val WEEKDAYS_TO_SHOW = 7 // window might be narrower than 7, TODO
        //val FIRST_WEEKDAY = DayOfWeek.MONDAY
    }

    private val selectedDate: LocalDate? = null

    fun initialise() {

        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

        val headerItems = (0 until WEEKDAYS_TO_SHOW).map {
            MiniCalendarItem(firstDayOfTheMonth.plusDays(-firstDayOfTheWeekOffset + it.toLong()), false, emptyList())
        }

        val dummyItems = (0 until firstDayOfTheWeekOffset).map {
            null
        }

        val dayItems = (0 until firstDayOfTheMonth.month.length(firstDayOfTheMonth.isLeapYear)).map {
            MiniCalendarItem(firstDayOfTheMonth.plusDays(it.toLong()), it % 2 == 0, listOf("#000000", "#E6984C"))
        }

        this.submitList(concatenate(headerItems, dummyItems, dayItems))

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

            fun bind(item: MiniCalendarItem?, clickListener: ((LocalDate) -> Unit)?) {

                if (item == null) {
                    itemView.text.text = ""
                    itemView.ll_calendar_dots.visibleOrInvisible(false)
                    itemView.setBackgroundResource(0)
                } else {

                    if (item.isSelected) {
                        itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong_Inverted)
                        itemView.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                    } else if (item.date == LocalDate.now(ZoneId.of(timeZoneId))) {
                        itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong)
                        itemView.setBackgroundResource(R.drawable.ripple_mini_calendar_day_today)
                    } else {
                        itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong)
                        itemView.setBackgroundResource(R.drawable.ripple_mini_calendar_day)
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

                itemView.setOnClickListener { if (item != null) clickListener?.invoke(item.date) }
            }
        }

    }



    fun markAsSelected(date: LocalDate) {

//        if (selectedDate != null) {
            val currentIndex = currentList.indexOfFirst { it.date == selectedDate }
            notifyItemChanged(currentIndex)

//        }




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
                clickListener
            )
        }

    }

    private class DiffCallback : DiffUtil.ItemCallback<MiniCalendarItem>() {
        override fun areItemsTheSame(oldItem: MiniCalendarItem, newItem: MiniCalendarItem): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: MiniCalendarItem, newItem: MiniCalendarItem): Boolean {
            return oldItem.equals(newItem)
        }
    }

}

