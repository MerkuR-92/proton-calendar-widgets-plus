package me.proton.android.calendar.presentation.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.android.calendar.domain.model.BaseModel
import me.proton.android.calendar.domain.model.Event
import java.time.ZoneId

class EventAdapter(private val clickListener: (Event) -> Unit/*TODO or just use entire item click listener from RV*/) : ListAdapter<Event, EventAdapter.EventViewHolder>(GenericDiffCallback()) {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val textView: TextView = itemView.findViewById(android.R.id.text1) // TODO

        // TODO consider databinding
        fun bind(item: Event, clickListener: (Event) -> Unit) {

            val time = if (item.isAllDay()) "(all-day)" else "${item.formatStart(ZoneId.systemDefault().id)} - ${item.formatEnd(ZoneId.systemDefault().id)}"

            textView.setText(time + "\n" + item.summary) // TODO
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

