package me.proton.android.calendar.presentation.holidayCalendar.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_holiday_calendar.view.item_holiday_calendar_country
import kotlinx.android.synthetic.main.item_holiday_calendar.view.item_holiday_calendar_country_flag
import kotlinx.android.synthetic.main.item_holiday_calendar.view.item_holiday_calendar_press
import kotlinx.android.synthetic.main.item_holiday_calendar_header.view.item_holiday_calendar_header_text
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.domain.model.Holiday

class HolidayCalendarListAdapter(
    val listener: (Holiday) -> Unit
): ListAdapter<HolidayCalendarListAdapter.HolidayItem, HolidayCalendarListAdapter.ViewHolder>(HolidayDiffCallback()) {

    private var searchQuery: String = ""

    override fun getItemViewType(position: Int): Int = currentList[position].type.ordinal

    class HolidayDiffCallback : DiffUtil.ItemCallback<HolidayItem>() {
        override fun areItemsTheSame(oldItem: HolidayItem, newItem: HolidayItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: HolidayItem, newItem: HolidayItem): Boolean {
            return if (oldItem is HolidayItem.Header && newItem is HolidayItem.Header) {
                oldItem.letter == newItem.letter
            } else if (oldItem is HolidayItem.Value && newItem is HolidayItem.Value) {
                oldItem.holiday == newItem.holiday
            } else false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(HolidayItemType.values()[viewType]) {
            HolidayItemType.Header -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_holiday_calendar_header, parent, false))
            HolidayItemType.Value -> HolidayViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_holiday_calendar, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = currentList[position]) {
            is HolidayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HolidayItem.Value -> (holder as HolidayViewHolder).bind(item)
        }
    }

    fun setSearchQuery(searchQuery: String) {
        val queryChanged = this.searchQuery != searchQuery
        this.searchQuery = searchQuery
        if (queryChanged) notifyDataSetChanged()
    }

    enum class HolidayItemType {
        Header,
        Value
    }

    sealed class HolidayItem(val type: HolidayItemType) {
        data class Header(val letter: String, val locationDefault: Boolean): HolidayItem(HolidayItemType.Header)
        data class Value(val holiday: Holiday): HolidayItem(HolidayItemType.Value)
    }

    abstract class ViewHolder(private val holidayView: View): RecyclerView.ViewHolder(holidayView)

    inner class HolidayViewHolder(private val holidayView: View): ViewHolder(holidayView) {
        private val holidayFlag: ImageView = holidayView.item_holiday_calendar_country_flag
        private val holidayCountryName: TextView = holidayView.item_holiday_calendar_country
        private val holidayPress: View = holidayView.item_holiday_calendar_press

        fun bind(value: HolidayItem.Value) {
            val holiday = value.holiday

            holidayCountryName.text = holiday.country
            if (searchQuery.isNotBlank()) {
                holidayCountryName.highlightSearchTokens(searchQuery.split(" ", ignoreCase = true))
            }

            holidayFlag.setImageResource(holiday.flagDrawable)

            holidayPress.setOnSingleClickListener {
                listener(holiday)
            }
        }
    }

    inner class HeaderViewHolder(private val headerView: View): ViewHolder(headerView){
        fun bind(headerItem: HolidayItem.Header) {
            headerView.item_holiday_calendar_header_text.text =
                if (headerItem.locationDefault) headerView.context.getString(R.string.holiday_calendar_location_based)
                else headerItem.letter
        }
    }

}
