package me.proton.android.calendar.presentation.import.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_drawer_calendar.view.item_drawer_calendar_press
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_checkbox
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_press
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_source_email
import kotlinx.android.synthetic.main.item_import_calendar.view.item_import_calendar_source_title
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.data.api.ExternalCalendarEntity

class ExternalCalendarListAdapter(
    val accountEmail: String,
    val listener: (ExternalCalendarEntity) -> Unit
) : ListAdapter<ExternalCalendarEntity, ExternalCalendarListAdapter.ViewHolder>(ExternalCalendarEntityDiffCallback()) {

    class ExternalCalendarEntityDiffCallback : DiffUtil.ItemCallback<ExternalCalendarEntity>() {
        override fun areItemsTheSame(oldItem: ExternalCalendarEntity, newItem: ExternalCalendarEntity): Boolean {
            return oldItem.source == newItem.source
        }

        override fun areContentsTheSame(oldItem: ExternalCalendarEntity, newItem: ExternalCalendarEntity): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_calendar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val externalCalendarEntityItemTitle: TextView = view.item_import_calendar_source_title
        private val externalCalendarEntityItemDescription: TextView = view.item_import_calendar_source_email
        private val externalCalendarEntityItemCheckBox: CheckBox = view.item_import_calendar_checkbox
        private val externalCalendarEntityItemOverlay: View = view.item_import_calendar_press

        fun bind(externalCalendarEntity : ExternalCalendarEntity) {

            externalCalendarEntityItemTitle.text = externalCalendarEntity.source
            externalCalendarEntityItemDescription.text = accountEmail

            externalCalendarEntityItemCheckBox.setOnSingleClickListener {
                listener(externalCalendarEntity)
            }

            externalCalendarEntityItemOverlay.setOnSingleClickListener {
                externalCalendarEntityItemCheckBox.performClick()
            }
        }

    }
}
