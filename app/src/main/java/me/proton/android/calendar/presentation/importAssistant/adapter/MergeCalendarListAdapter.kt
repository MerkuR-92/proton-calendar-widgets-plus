package me.proton.android.calendar.presentation.importAssistant.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_calendar_import_mapping.view.item_calendar_import_mapping_icon
import kotlinx.android.synthetic.main.item_calendar_import_mapping.view.item_calendar_import_mapping_layout
import kotlinx.android.synthetic.main.item_calendar_import_mapping.view.item_calendar_import_mapping_name
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_source_title
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.data.entity.CalendarEntity

class MergeCalendarListAdapter(
    val listener: (CalendarEntity) -> Unit
): ListAdapter<CalendarEntity, MergeCalendarListAdapter.ViewHolder>(CalendarEntityDiffCallback()) {

    class CalendarEntityDiffCallback : DiffUtil.ItemCallback<CalendarEntity>() {
        override fun areItemsTheSame(oldItem: CalendarEntity, newItem: CalendarEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalendarEntity, newItem: CalendarEntity): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_import_mapping, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val calendarIcon: ImageView = view.item_calendar_import_mapping_icon
        private val calendarName: TextView = view.item_calendar_import_mapping_name
        private val calendarLayout: LinearLayout = view.item_calendar_import_mapping_layout

        fun bind(calendarEntity : CalendarEntity) {

            calendarName.text = calendarEntity.name
            calendarIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(calendarEntity.color))

            calendarLayout.setOnSingleClickListener {
                listener(calendarEntity)
            }
        }

    }
}
