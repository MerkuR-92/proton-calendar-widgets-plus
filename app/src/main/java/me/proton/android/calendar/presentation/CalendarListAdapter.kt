package me.proton.android.calendar.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_drawer_calendar.view.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.core.util.kotlin.toInt
import java.time.Duration

class CalendarListAdapter(
    val calendarViewModel: CalendarViewModel,
    val listener: (CalendarEntity) -> Unit
) : ListAdapter<CalendarEntity, CalendarListAdapter.ViewHolder>(CalendarEntityDiffCallback()) {

    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null

    class CalendarEntityDiffCallback : DiffUtil.ItemCallback<CalendarEntity>() {
        override fun areItemsTheSame(oldItem: CalendarEntity, newItem: CalendarEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalendarEntity, newItem: CalendarEntity): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_drawer_calendar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    fun setCalendarSubscriptions(calendarSubscriptions: List<CalendarSubscriptionEntity>): Boolean {
        val dataSetChanged = this.calendarSubscriptions != calendarSubscriptions
        this.calendarSubscriptions = calendarSubscriptions
        return dataSetChanged
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val calendarEntityItemOverlay: View = view.item_drawer_calendar_press
        private val calendarEntityItemTitle: TextView = view.item_drawer_calendar_title
        private val calendarEntityItemCheckBox: CheckBox = view.item_drawer_calendar_checkbox

        fun bind(calendarEntity : CalendarEntity) {
            if (calendarEntity.isDisabled) {
                // For subscribed calendars we prioritize displaying disabled label over not synced
                calendarEntityItemTitle.text = itemView.context.getString(R.string.nav_view_disabled_calendars, calendarEntity.name)
                calendarEntityItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_weak))
            } else if (calendarEntity.isSubscribed) {
                val calendarSubscription = calendarSubscriptions?.firstOrNull { it.calendarId == calendarEntity.id }
                if (calendarSubscription?.isSynced == true) {
                    calendarEntityItemTitle.text = calendarEntity.name
                    calendarEntityItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                } else {
                    calendarEntityItemTitle.text = itemView.context.getString(R.string.nav_view_not_synced_calendars, calendarEntity.name)
                    calendarEntityItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_weak))
                }
            } else {
                calendarEntityItemTitle.text = calendarEntity.name
                calendarEntityItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            }
            calendarEntityItemCheckBox.isChecked = calendarEntity.display == 1
            calendarEntityItemCheckBox.buttonTintList = ColorStateList.valueOf(Color.parseColor(calendarEntity.color))

            calendarEntityItemCheckBox.setOnSingleClickListener {
                listener(
                    calendarEntity.copy(
                        display = calendarEntityItemCheckBox.isChecked.toInt()
                    )
                )
            }

            calendarEntityItemOverlay.setOnSingleClickListener {
                calendarEntityItemCheckBox.performClick()
            }
        }
    }
}
