package me.proton.android.calendar.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_drawer_calendar.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.data.entity.CalendarEntity

class CalendarListAdapter(
    val listener: (CalendarEntity) -> Unit
) : ListAdapter<CalendarEntity, CalendarListAdapter.ViewHolder>(CalendarEntityDiffCallback()) {

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
        holder.bind(item, position)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val calendarEntityItemLayout: ConstraintLayout = view.item_drawer_calendar_layout
        private val calendarEntityItemTitle: TextView = view.item_drawer_calendar_title
        private val calendarEntityItemCheckBox: CheckBox = view.item_drawer_calendar_checkbox

        fun bind(calendarEntity : CalendarEntity, position : Int) {
            calendarEntityItemTitle.text = calendarEntity.name
            calendarEntityItemCheckBox.isChecked = calendarEntity.display == 1
            calendarEntityItemCheckBox.buttonTintList = ColorStateList.valueOf(Color.parseColor(calendarEntity.color))

            calendarEntityItemLayout.setOnClickListener {
                calendarEntityItemCheckBox.isChecked = !calendarEntityItemCheckBox.isChecked
                listener(calendarEntity)
            }
            calendarEntityItemCheckBox.setOnClickListener {
                listener(calendarEntity)
            }
        }
    }
}