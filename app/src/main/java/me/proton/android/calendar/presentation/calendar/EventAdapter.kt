package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.property.Status
import kotlinx.android.synthetic.main.item_agenda_event_header.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.format
import me.proton.android.calendar.common.formatTime
import me.proton.android.calendar.domain.model.BaseModel
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.calendar.EventAdapter.EventViewHolder.HeaderViewHolder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventAdapter(
    private val timeZoneId: String,
    private val date: LocalDate,
    private val clickListener: ((Event) -> Unit)?/*TODO or just use entire item click listener from RV*/
) : ListAdapter<Event, EventAdapter.EventViewHolder>(GenericDiffCallback()) {

    sealed class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class HeaderViewHolder(itemView: View) : EventViewHolder(itemView) {
            fun bind(date: LocalDate) {
                itemView.text_header.text = date.format(showDayOfWeek = true)
            }
        }

        class PartialDayEventViewHolder(private val itemView: View, private val timeZoneId: String) : EventViewHolder(
            itemView
        ) {

            private val imageViewIcon: ImageView = itemView.findViewById(R.id.image_icon)
            private val textViewHeader: TextView = itemView.findViewById(R.id.text_header)
            private val textViewSubheader: TextView = itemView.findViewById(R.id.text_subheader)

            // TODO consider databinding
            fun bind(event: Event, clickListener: ((Event) -> Unit)?) {

//                if (/*TODO if event is unanswered*/ true) {
//                    imageViewIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_event_unanswered_circle))
//                } else {
                    imageViewIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.shape_calendar_circle))
//                }

                imageViewIcon.drawable.setTint(Color.parseColor(event.calendar.color))

                textViewHeader.text = "${(event.occurrence?.startDateTime ?: event.getStart(
                    timeZoneId
                ))?.formatTime(timeZoneId)} ‐ ${(event.occurrence?.endDateTime ?: event.getEnd(
                    timeZoneId
                ))?.formatTime(timeZoneId)}" // TODO
                textViewSubheader.text = event.summary

                if (event.status != null) {
                    if ((event.status as Status).isCancelled) {
                        textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                }

                itemView.setOnClickListener { clickListener?.invoke(event) }
            }
        }

        class AllDayEventViewHolder(itemView: View) : EventViewHolder(itemView) {

            private val textView: TextView = itemView.findViewById(R.id.text_header) // TODO

            // TODO consider databinding
            fun bind(item: Event, clickListener: ((Event) -> Unit)?) {

                // TODO use timezone from settings

                val time =
                    (if (item.isAllDay()) "(all-day)" else "") + "${(item.occurrence?.startDateTime ?: item.getStart(
                        //timeZoneId
                        ZoneId.systemDefault().id
                    ))?.format(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    )} - ${(item.occurrence?.endDateTime ?: item.getEnd(
                        //timeZoneId
                        ZoneId.systemDefault().id
                    ))?.format(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    )}"
                //}

                textView.setText(item.summary + "\n" + (if (item.occurrence != null) "\n(occurrence: ${item.occurrence?.occurrenceNumber})" else "") + "\n" + time) // TODO
                textView.setOnClickListener { clickListener?.invoke(item) }
            }
        }
    }

    private val ITEM_TYPE_HEADER = 0
    private val ITEM_TYPE_EVENT_PARTIAL_DAY = 1
    private val ITEM_TYPE_EVENT_ALL_DAY = 2

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            ITEM_TYPE_HEADER
        } else if (getItem(position).isAllDay()) {
            ITEM_TYPE_EVENT_ALL_DAY
        } else {
            ITEM_TYPE_EVENT_PARTIAL_DAY
        }
    }

    // accommodating header view
//    override fun getItemCount(): Int {
//        return super.getItemCount() + 1
//    }
//
//    override fun getItem(position: Int): Event {
//        return super.getItem(position - 1)
//    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int /*later when we have more view types*/
    ): EventViewHolder {
        return if (viewType == ITEM_TYPE_HEADER) {
            HeaderViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_agenda_event_header,
                    parent,
                    false
                )
            )
        } else if (viewType == ITEM_TYPE_EVENT_PARTIAL_DAY) {
                EventViewHolder.PartialDayEventViewHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_agenda_event_partial_day,
                        parent,
                        false
                    ),
                    timeZoneId
                )
            } else {
                EventViewHolder.AllDayEventViewHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_agenda_event_all_day,
                        parent,
                        false
                    )
                )
            }
        }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {

        when (holder) {
            is EventViewHolder.HeaderViewHolder -> holder.bind(date)
            is EventViewHolder.PartialDayEventViewHolder -> holder.bind(
                getItem(position),
                clickListener
            )
            is EventViewHolder.AllDayEventViewHolder -> holder.bind(
                getItem(position),
                clickListener
            )
        }

    }

    // TODO introduce necessary interface?
    private class GenericDiffCallback<T : BaseModel> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.equals(newItem) // TODO figure out generic way of determining if contents changed? timestamp?
        }
    }

}

