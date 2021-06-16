package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_header.view.text
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.concatenate
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatDayOfWeek
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW
import java.time.*
import java.time.temporal.WeekFields
import kotlin.math.ceil

class MiniCalendarItemAdapter(
    private val timeZoneId: String, // TODO MOVE TO INITIALISE
    private val forDate: LocalDate,
    private val startWeekOn: DayOfWeek,
    private val isMonthView: Boolean,
    private val calendarViewModel: CalendarViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val clickListener: ((LocalDate) -> Unit)?
) : ListAdapter<MiniCalendarItem, MiniCalendarItemAdapter.MiniCalendarViewHolder>(DiffCallback()) {

    object CalendarSettings {
        const val DAYS_IN_A_WEEK = 7 // always 7
        const val WEEKDAYS_TO_SHOW = 7 // window might be narrower than 7, TODO
        //val FIRST_WEEKDAY = DayOfWeek.MONDAY
    }

    private var selectedDate: LocalDate? = null
    private var indicators: Map<LocalDate, List<String>>? = null
    private var indicatorsMediator = MediatorLiveData<List<MiniCalendarItem>>()

    fun initialise(timeZoneId: String) {
        indicatorsMediator = MediatorLiveData<List<MiniCalendarItem>>()

        if (isMonthView) {
            val firstDayOfTheMonth = forDate.withDayOfMonth(1)
            val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
            val lastDayOfTheMonth = forDate.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            val lastDayOfMonthWeekValue = DayOfWeek.of(lastDayOfTheMonth.dayOfWeek.value).value
            val lastDayOfMonthOffset = 7 - (startWeekOn.value + lastDayOfMonthWeekValue - 1)

            val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
            val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(lastDayOfMonthOffset.toLong())

            val headerItems = (0 until WEEKDAYS_TO_SHOW).map {
                MiniCalendarItem(firstDayOfTheMonth.plusDays(-firstDayOfTheWeekOffset + it.toLong()), false,false, emptyList())
            }

            val previousMonthDayItems = (0 until firstDayOfTheWeekOffset).map {
                val date = firstDayOfTheMonth.minusDays(it.toLong() + 1)
                MiniCalendarItem(date, false, true, emptyList())
            }.reversed()

            val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
                val date = firstDayOfTheMonth.plusDays(it.toLong())
                MiniCalendarItem(date, false, true, emptyList())
            }

            val upcomingMonthDayItems = (0 until lastDayOfMonthOffset).map {
                val date = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(it.toLong() + 1)
                MiniCalendarItem(date, false, true, emptyList())
            }

            // submit month skeleton with only days and weekday names
            val skeletonList = concatenate(headerItems, previousMonthDayItems, dayItems, upcomingMonthDayItems)
            this.submitList(skeletonList)

            // subscribe for calendar indicators and selected date
            indicatorsMediator.addSource(calendarViewModel.calendarIndicators(
                fromDate,
                toDate,
                timeZoneId
            )) {
                indicators = it

                if (indicators != null && selectedDate != null) {
                    indicatorsMediator.value = applyIndicatorsAndSelectedDate(indicators!!, selectedDate!!, skeletonList)
                }
            }
            indicatorsMediator.addSource(calendarViewModel.selectedDate) {
                selectedDate = it

                if (indicators != null && selectedDate != null) {
                    indicatorsMediator.value = applyIndicatorsAndSelectedDate(indicators!!, selectedDate!!, skeletonList)
                }
            }
        } else {
            val firstDayOfTheMonth = forDate.withDayOfMonth(1)
            val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
            val firstDay = forDate.with(temporalField, 1)
            val headerItems = (0 until WEEKDAYS_TO_SHOW).map {
                MiniCalendarItem(firstDayOfTheMonth.plusDays(-firstDayOfTheWeekOffset + it.toLong()), false,false, emptyList())
            }

            val dayItems = (0 until 7).map {
                val date = firstDay.plusDays(it.toLong())
                MiniCalendarItem(date, false, true, emptyList())
            }

            // submit month skeleton with only days and weekday names
            val skeletonList = concatenate(headerItems, dayItems)
            this.submitList(skeletonList)

            // subscribe for calendar indicators and selected date
            indicatorsMediator.addSource(calendarViewModel.calendarIndicators(firstDay, firstDay.plusDays(6), timeZoneId)) {
                indicators = it

                if (indicators != null && selectedDate != null) {
                    indicatorsMediator.value = applyIndicatorsAndSelectedDate(indicators!!, selectedDate!!, skeletonList)
                }
            }

            indicatorsMediator.addSource(calendarViewModel.selectedDate) {
                selectedDate = it

                if (indicators != null && selectedDate != null) {
                    indicatorsMediator.value = applyIndicatorsAndSelectedDate(indicators!!, selectedDate!!, skeletonList)
                }
            }
        }

        indicatorsMediator.observe(lifecycleOwner) {
            submitList(it)
        }
    }



    private fun applyIndicatorsAndSelectedDate(indicators: Map<LocalDate, List<String>>, selectedDate: LocalDate, skeletonList: List<MiniCalendarItem?>): List<MiniCalendarItem> {

        val mutableList = if (isMonthView) {
            val tmpList = currentList.toMutableList()
            if (tmpList.size != skeletonList.size) skeletonList.toMutableList()
            else tmpList
        } else {
            skeletonList.toMutableList()
        }

        // update selected date
        // select last index, because header items contain valid date for first days of the month
        val currentIndex = currentList.indexOfLast { it?.isSelected == true }
        if (currentIndex != -1 && currentIndex < mutableList.size) {
            mutableList[currentIndex] = mutableList[currentIndex].copy(isSelected = false)
        }
        val indexToSelect = mutableList.indexOfLast { it?.date == selectedDate }
        if (indexToSelect != -1) {
            mutableList[indexToSelect] = mutableList[indexToSelect].copy(isSelected = true)
        }

        // update calendar indicators
        mutableList.forEachIndexed { index, miniCalendarItem ->
            if (index >= WEEKDAYS_TO_SHOW && miniCalendarItem != null) {
                val colors = indicators.getOrDefault(miniCalendarItem.date, emptyList())

                mutableList[index] = miniCalendarItem.copy(indicatorColors = colors)
            }
        }

        return mutableList
    }

    sealed class MiniCalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class HeaderViewHolder(itemView: View, private val timeZoneId: String) : MiniCalendarViewHolder(itemView) {
            fun bind(date: LocalDate) {
                itemView.text.text = date.formatDayOfWeek(short = true)
                if (date == LocalDate.now(ZoneId.of(timeZoneId))) itemView.text.setTextColor(ContextCompat.getColor(itemView.context, R.color.brand_norm))
                else itemView.text.setTextAppearance(itemView.context, R.style.Text_Caption_Weak)
            }
        }

        class DayViewHolder(
            itemView: View,
            private val timeZoneId: String,
            private val forDate: LocalDate,
            private val startWeekOn: DayOfWeek,
            private val isMonthView: Boolean
        ) : MiniCalendarViewHolder(
            itemView
        ) {

            fun bind(item: MiniCalendarItem?, clickListener: ((LocalDate) -> Unit)?) {

                if (item == null) {
                    itemView.text.text = ""
                    itemView.ll_calendar_dots.visibleOrInvisible(false)
                    itemView.setBackgroundResource(0)
                } else {

                    val itemLayoutParams: GridLayoutManager.LayoutParams = (itemView.item_mini_calendar_layout).layoutParams as GridLayoutManager.LayoutParams
                    itemLayoutParams.topMargin =
                        if (isMonthView && item.date.isAfter(forDate) && (item.date.weekNumber(startWeekOn) - forDate.weekNumber(startWeekOn) > 0 || item.date.month != forDate.month))
                            itemView.context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)
                        else 0

                    when {
                        item.isSelected -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong_Inverted)
                            itemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                        }
                        item.date == LocalDate.now(ZoneId.of(timeZoneId)) -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong)
                            itemView.text.setTextColor(ContextCompat.getColor(itemView.context, R.color.brand_norm))
                            itemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day)
                        }
                        isMonthView && item.date.month != forDate.month -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Weak)
                            itemView.text.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_hint))
                            itemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day)
                        }
                        else -> {
                            itemView.text.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Strong)
                            itemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day)
                        }
                    }

                    itemView.text.text = "${item.date.dayOfMonth}"

                    itemView.ll_calendar_dots.visibleOrInvisible(true)
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
                ),
                timeZoneId
            )
        } else {
            MiniCalendarViewHolder.DayViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_mini_calendar,
                    parent,
                    false
                ),
                timeZoneId,
                forDate,
                startWeekOn,
                isMonthView
            )
        }
    }

    override fun onBindViewHolder(holder: MiniCalendarViewHolder, position: Int) {

        when (holder) {
            is MiniCalendarViewHolder.HeaderViewHolder -> holder.bind(getItem(position).date)
            is MiniCalendarViewHolder.DayViewHolder -> holder.bind(getItem(position)) {
                clickListener?.invoke(it)
            }
        }

    }

    private class DiffCallback : DiffUtil.ItemCallback<MiniCalendarItem>() {
        override fun areItemsTheSame(oldItem: MiniCalendarItem, newItem: MiniCalendarItem): Boolean {
            return oldItem.date == newItem.date && oldItem.isDay == newItem.isDay //  oldItem.equals(newItem) // we are piggybacking weekday names with LocalDate so we can't compare only "date" properties
        }

        override fun areContentsTheSame(oldItem: MiniCalendarItem, newItem: MiniCalendarItem): Boolean {
            return oldItem.isSelected == newItem.isSelected && oldItem.indicatorColors.size == newItem.indicatorColors.size
                    && oldItem.indicatorColors.containsAll(newItem.indicatorColors)
        }
    }

    companion object {

        /**
         * No need to measure the adapter view, we can calculate the height because item dimensions
         * are constant.
         */
        fun calculateAdapterHeight(
            context: Context,
            firstDayOfMonth: LocalDate,
            startWeekOn: DayOfWeek,
            isMonthView: Boolean
        ): Int {
            val fullWeeksInMonth = if (isMonthView) calculateFullWeeksInMonth(firstDayOfMonth, startWeekOn) else 1

            return context.resources.getDimensionPixelSize(R.dimen.calendar_item_header_height) +
                    fullWeeksInMonth * context.resources.getDimensionPixelSize(R.dimen.calendar_item_height) +
                    (if (fullWeeksInMonth == 1) context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing) * 2
                    else (fullWeeksInMonth - 1) * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)) +
                    fullWeeksInMonth * 2 * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_spacing) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_bottom_spacing)
        }

        fun calculateFullWeeksInMonth(firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek): Int {
            val firstDayOfTheWeekNumber = firstDayOfMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val dayCellsToShow = firstDayOfTheWeekOffset + firstDayOfMonth.lengthOfMonth()

            return ceil(dayCellsToShow / DAYS_IN_A_WEEK.toDouble()).toInt()
        }

    }

}

