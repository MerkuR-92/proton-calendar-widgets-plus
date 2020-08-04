package me.proton.android.calendar.presentation.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.model.BaseModel
import me.proton.android.calendar.domain.model.Event
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventAdapter(private val clickListener: (Event) -> Unit/*TODO or just use entire item click listener from RV*/) : ListAdapter<Event, EventAdapter.EventViewHolder>(GenericDiffCallback()) {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val textView: TextView = itemView.findViewById(android.R.id.text1) // TODO

        // TODO consider databinding
        fun bind(item: Event, clickListener: (Event) -> Unit) {

            // TODO use timezone from settings

            val time = if (item.isFromRecurring()) {
                if (item.isAllDay()) "(all-day)" else "${item.getStart(ZoneId.systemDefault().id)?.format(
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME)} - ${item.getEnd(ZoneId.systemDefault().id)?.format(
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"
            } else {
                if (item.isAllDay()) "(all-day)" else "${(item.occurrence?.startDateTime ?: item.getStart(ZoneId.systemDefault().id))?.format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)} - ${(item.occurrence?.endDateTime ?: item.getEnd(ZoneId.systemDefault().id))?.format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"
            }

//            if (item.occurence == null) {
                //TimberLogger.e("binding ${item}")
//            }

            textView.setText(item.summary + "\n" + (if (item.occurrence != null) "\n(occurrence: ${item.occurrence?.occurrenceNumber})" else "") + "\n" + time) // TODO
            textView.setOnClickListener { clickListener(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int /*later when we have more view types*/): EventViewHolder {
        val textView = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return EventViewHolder(textView)

//        val inflater = LayoutInflater.from(parent.context)
//        return ViewHolder(inflater.inflate(R.layout.item_task_row, parent, false))
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) = holder.bind(getItem(position), clickListener)

    // TODO introduce necessary interface?
    private class GenericDiffCallback<T: BaseModel> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.equals(newItem) // TODO figure out generic way of determining if contents changed? timestamp?
        }
    }

}

