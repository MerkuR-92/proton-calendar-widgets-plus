package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
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
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.format
import me.proton.android.calendar.common.formatTime
import me.proton.android.calendar.common.visibleOrGone
import me.proton.android.calendar.domain.model.BaseModel
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.calendar.EventAdapter.EventViewHolder.HeaderViewHolder
import java.time.LocalDate

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
            private val textViewSubheaderSide: TextView = itemView.findViewById(R.id.text_subheader_side)

            // TODO consider databinding
            fun bind(event: Event, date: LocalDate, clickListener: ((Event) -> Unit)?) {

//                if (/*TODO if event is unanswered*/ true) {
//                    imageViewIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_event_unanswered_circle))
//                } else {
                    imageViewIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.shape_calendar_circle))
//                }

                imageViewIcon.drawable.setTint(Color.parseColor(event.calendar.color))

                textViewHeader.text =
                    "${(event.getActualStart(
                        timeZoneId
                    ))?.formatTime(timeZoneId)} ‐ ${(event.getActualEnd(
                        timeZoneId
                    ))?.formatTime(timeZoneId)}" // TODO

                textViewSubheader.text = event.summary ?: itemView.resources.getString(R.string.default_event_summary)

                if (event.spansSingleDay()) {
                    textViewSubheaderSide.visibleOrGone(false)
                } else {

                    textViewSubheaderSide.text = event.formatFullDayCounter(date, timeZoneId)
                    textViewSubheaderSide.visibleOrGone(true)
                }

                if (event.isInThePast(timeZoneId)) {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall_Weak)
                    textViewSubheader.setTextAppearance(R.style.Text_Default_Weak)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default_Weak)
                } else {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall)
                    textViewSubheader.setTextAppearance(R.style.Text_Default)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default)
                }

                if (event.status != null) {
                    if ((event.status as Status).isCancelled) {
                        textViewHeader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        textViewHeader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                }

                itemView.setOnClickListener { clickListener?.invoke(event) }
            }
        }

        // TODO this viewholder can actually is used also for partial-day events, that span more than one day
        class AllDayEventViewHolder(itemView: View, private val timeZoneId: String) : EventViewHolder(itemView) {

//            private val ivBackground: ImageView = itemView.findViewById(R.id.background)

            private val viewBackground: LayerDrawable = itemView.findViewById<View>(R.id.view_background).background as LayerDrawable
            private val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
            private val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)

            private val textViewHeader: TextView = itemView.findViewById(R.id.text_header)
            private val textViewSubheader: TextView = itemView.findViewById(R.id.text_subheader)
            private val textViewSubheaderSide: TextView = itemView.findViewById(R.id.text_subheader_side)

            // TODO consider databinding
            fun bind(event: Event, date: LocalDate, clickListener: ((Event) -> Unit)?) {

//                TODO if event is unanswered, add tiled backgrounds

                // TODO ADD MULTI-DAY INDICATORS

                if (!event.isAllDay() && !event.spansSingleDay()) {
                    textViewHeader.visibleOrGone(true)
                    textViewHeader.text = "${(event.getActualStart(timeZoneId))?.formatTime(timeZoneId)}" // TODO
                } else {
                    textViewHeader.visibleOrGone(false)
                }

                textViewSubheader.text = event.summary ?: itemView.resources.getString(R.string.default_event_summary)

                if (event.spansSingleDay()) {
                    textViewSubheaderSide.visibleOrGone(false)
                } else {
                    textViewSubheaderSide.text = event.formatFullDayCounter(date, timeZoneId)
                    textViewSubheaderSide.visibleOrGone(true)
                }

                viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))

                if (event.isInThePast(timeZoneId)) {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall_Weak)
                    textViewSubheader.setTextAppearance(R.style.Text_Default_Weak)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default_Weak)

                    if (event.isCancelled()) {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_of_cancelled_event))
                    } else {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_of_past_event))
                    }
                } else {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall)
                    textViewHeader.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                    textViewSubheader.setTextAppearance(R.style.Text_Default)
                    textViewSubheader.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default)
                    textViewSubheaderSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))

                    if (event.isCancelled()) {
                        textViewHeader.setTextAppearance(R.style.Text_DefaultSmall)
                        textViewSubheader.setTextAppearance(R.style.Text_Default)
                        textViewSubheaderSide.setTextAppearance(R.style.Text_Default)

                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_of_cancelled_event))
                    } else {
                        viewMainSurface.setTint(Color.parseColor(event.calendar.color))
                    }

                }

                if (event.isCancelled()) {
                        textViewHeader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        textViewHeader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                itemView.setOnClickListener { clickListener?.invoke(event) }
            }
        }
    }

    private val ITEM_TYPE_HEADER = 0
    private val ITEM_TYPE_EVENT_PARTIAL_DAY = 1
    private val ITEM_TYPE_EVENT_ALL_DAY = 2

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            ITEM_TYPE_HEADER
        } else if (getItem(position).isAllDay() || !getItem(position).spansSingleDay()) {
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
                    ),
                    timeZoneId
                )
            }
        }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {

        when (holder) {
            is EventViewHolder.HeaderViewHolder -> holder.bind(date)
            is EventViewHolder.PartialDayEventViewHolder -> holder.bind(
                getItem(position),
                date,
                clickListener
            )
            is EventViewHolder.AllDayEventViewHolder -> holder.bind(
                getItem(position),
                date,
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

