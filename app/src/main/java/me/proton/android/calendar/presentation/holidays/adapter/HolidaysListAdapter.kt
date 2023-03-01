package me.proton.android.calendar.presentation.holidays.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_holidays_calendar.view.item_holidays_calendar_country
import kotlinx.android.synthetic.main.item_holidays_calendar.view.item_holidays_calendar_country_flag
import kotlinx.android.synthetic.main.item_holidays_calendar.view.item_holidays_calendar_press
import kotlinx.android.synthetic.main.item_holidays_calendar_header.view.item_holidays_calendar_header_text
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.domain.model.Holidays

class HolidaysListAdapter(
    val listener: (Holidays) -> Unit
): ListAdapter<HolidaysListAdapter.HolidaysItem, HolidaysListAdapter.ViewHolder>(HolidaysDiffCallback()) {

    private var searchQuery: String = ""

    override fun getItemViewType(position: Int): Int = currentList[position].type.ordinal

    class HolidaysDiffCallback : DiffUtil.ItemCallback<HolidaysItem>() {
        override fun areItemsTheSame(oldItem: HolidaysItem, newItem: HolidaysItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: HolidaysItem, newItem: HolidaysItem): Boolean {
            return if (oldItem is HolidaysItem.Header && newItem is HolidaysItem.Header) {
                oldItem.letter == newItem.letter
            } else if (oldItem is HolidaysItem.Value && newItem is HolidaysItem.Value) {
                oldItem.holidays == newItem.holidays
            } else false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(HolidaysItemType.values()[viewType]) {
            HolidaysItemType.Header -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_holidays_calendar_header, parent, false))
            HolidaysItemType.Value -> HolidaysViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_holidays_calendar, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = currentList[position]) {
            is HolidaysItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HolidaysItem.Value -> (holder as HolidaysViewHolder).bind(item)
        }
    }

    fun setSearchQuery(searchQuery: String) {
        val queryChanged = this.searchQuery != searchQuery
        this.searchQuery = searchQuery
        if (queryChanged) notifyDataSetChanged()
    }

    enum class HolidaysItemType {
        Header,
        Value
    }

    sealed class HolidaysItem(val type: HolidaysItemType) {
        data class Header(val letter: String, val locationDefault: Boolean): HolidaysItem(HolidaysItemType.Header)
        data class Value(val holidays: Holidays): HolidaysItem(HolidaysItemType.Value)
    }

    abstract class ViewHolder(private val holidaysView: View): RecyclerView.ViewHolder(holidaysView)

    inner class HolidaysViewHolder(private val holidaysView: View): ViewHolder(holidaysView) {
        private val holidaysFlag: ImageView = holidaysView.item_holidays_calendar_country_flag
        private val holidaysCountryName: TextView = holidaysView.item_holidays_calendar_country
        private val holidaysPress: View = holidaysView.item_holidays_calendar_press

        fun bind(value: HolidaysItem.Value) {
            val holidays = value.holidays

            holidaysCountryName.text = holidays.country
            if (searchQuery.isNotBlank()) {
                holidaysCountryName.highlightSearchTokens(searchQuery.split(" ", ignoreCase = true))
            }

            holidaysFlag.setImageResource(holidays.flagDrawable)

            holidaysPress.setOnSingleClickListener {
                listener(holidays)
            }
        }
    }

    inner class HeaderViewHolder(private val headerView: View): ViewHolder(headerView){
        fun bind(headerItem: HolidaysItem.Header) {
            headerView.item_holidays_calendar_header_text.text =
                if (headerItem.locationDefault) headerView.context.getString(R.string.holidays_calendar_location_based)
                else headerItem.letter
        }
    }

}
