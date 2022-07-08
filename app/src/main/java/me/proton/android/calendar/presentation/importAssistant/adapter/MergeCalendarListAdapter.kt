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
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.domain.model.Calendar

class MergeCalendarListAdapter(
    val listener: (Calendar) -> Unit
): ListAdapter<Calendar, MergeCalendarListAdapter.ViewHolder>(CalendarDiffCallback()) {

    class CalendarDiffCallback : DiffUtil.ItemCallback<Calendar>() {
        override fun areItemsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
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

        fun bind(calendar : Calendar) {

            calendarName.text = calendar.name
            calendarIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))

            calendarLayout.setOnSingleClickListener {
                listener(calendar)
            }
        }

    }
}
