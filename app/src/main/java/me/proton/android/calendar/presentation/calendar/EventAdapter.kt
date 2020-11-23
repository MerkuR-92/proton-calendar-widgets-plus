package me.proton.android.calendar.presentation.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationStatus
import kotlinx.android.synthetic.main.fragment_event_form.*
import kotlinx.android.synthetic.main.item_agenda_event_header.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.calendar.EventAdapter.EventViewHolder.HeaderViewHolder
import me.proton.core.util.kotlin.nullIfBlank
import java.time.LocalDate

class EventAdapter(
    private val timeZoneId: String,
    private val is24Hour: Boolean,
    private val date: LocalDate,
    private val userEmail: String?,
    private val clickListener: ((Event) -> Unit)?/*TODO or just use entire item click listener from RV*/
) : ListAdapter<Event, EventAdapter.EventViewHolder>(GenericDiffCallback()) {

    sealed class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class HeaderViewHolder(itemView: View) : EventViewHolder(itemView) {
            fun bind(date: LocalDate) {
                itemView.text_header.text = date.format(showDayOfWeek = true)
            }
        }

        class PartialDayEventViewHolder(private val itemView: View, private val timeZoneId: String, private val is24Hour: Boolean) : EventViewHolder(
            itemView
        ) {

            private val imageViewIcon: ImageView = itemView.findViewById(R.id.image_icon)
            private val textViewHeader: TextView = itemView.findViewById(R.id.text_header)
            private val textViewSubheader: TextView = itemView.findViewById(R.id.text_subheader)
            private val textViewSubheaderSide: TextView = itemView.findViewById(R.id.text_subheader_side)

            private val decryptionErrorIcon: ImageView = itemView.findViewById(R.id.decryption_error_icon)
            private val decryptionErrorView: View = itemView.findViewById(R.id.decryption_error_view)

            // TODO consider databinding
            fun bind(event: Event, date: LocalDate, userEmail: String?, clickListener: ((Event) -> Unit)?) {

                val participationStatus = if (userEmail != null) event.getParticipationStatus(userEmail) else null

                if (participationStatus == ParticipationStatus.NEEDS_ACTION) {
                    imageViewIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_event_unanswered_circle))
                } else {
                    imageViewIcon.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.shape_calendar_circle))
                }
                imageViewIcon.drawable.setTint(Color.parseColor(event.calendar.color))

                textViewHeader.text =
                    "${(event.getActualStart(
                        timeZoneId
                    ))?.formatTime(timeZoneId, is24Hour)} ‐ ${(event.getActualEnd(
                        timeZoneId
                    ))?.formatTime(timeZoneId, is24Hour)}" // TODO

                textViewSubheader.text = event.summary?.nullIfBlank() ?: itemView.resources.getString(R.string.default_event_summary)

                if (event.spansSingleDay(timeZoneId = timeZoneId)) {
                    textViewSubheaderSide.visibleOrGone(false)
                } else {

                    textViewSubheaderSide.text = event.formatFullDayCounter(date, timeZoneId)
                    textViewSubheaderSide.visibleOrGone(true)
                }

                if (event.decryptionStatus == Event.DecryptionStatus.FAILURE) {
                    decryptionErrorIcon.visibleOrGone(true)
                    decryptionErrorView.visibleOrGone(true)
                    textViewSubheader.visibleOrGone(false)
                } else {
                    decryptionErrorIcon.visibleOrGone(false)
                    decryptionErrorView.visibleOrGone(false)
                    textViewSubheader.visibleOrGone(true)
                }

                if (event.isInThePast(timeZoneId)) {
                    textViewHeader.setTextAppearance(itemView.context, R.style.Text_DefaultSmall_Weak)
                    textViewSubheader.setTextAppearance(itemView.context, R.style.Text_Default_Weak)
                    textViewSubheaderSide.setTextAppearance(itemView.context, R.style.Text_Default_Weak)
                    ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.icon_weak)))
                } else {
                    textViewHeader.setTextAppearance(itemView.context, R.style.Text_DefaultSmall)
                    textViewSubheader.setTextAppearance(itemView.context, R.style.Text_Default)
                    textViewSubheaderSide.setTextAppearance(itemView.context, R.style.Text_Default)
                }

                if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                itemView.setOnSingleClickListener { clickListener?.invoke(event) }
            }
        }

        // TODO this viewholder can actually is used also for partial-day events, that span more than one day
        class AllDayEventViewHolder(itemView: View, private val timeZoneId: String, private val is24Hour: Boolean) : EventViewHolder(itemView) {

//            private val ivBackground: ImageView = itemView.findViewById(R.id.background)

            private val viewBackground: LayerDrawable = itemView.findViewById<View>(R.id.view_background).background as LayerDrawable
            private val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
            private val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)

            private val textViewHeader: TextView = itemView.findViewById(R.id.text_header)
            private val textViewSubheader: TextView = itemView.findViewById(R.id.text_subheader)
            private val textViewSubheaderSide: TextView = itemView.findViewById(R.id.text_subheader_side)

            private val viewBackgroundStripedLayout: CardView = itemView.findViewById(R.id.view_background_striped_layout)
            private val viewBackgroundStriped: View = itemView.findViewById(R.id.view_background_striped)

            private val decryptionErrorIcon: ImageView = itemView.findViewById(R.id.decryption_error_icon)
            private val decryptionErrorView: View = itemView.findViewById(R.id.decryption_error_view)

            // TODO consider databinding
            fun bind(event: Event, date: LocalDate, userEmail: String?, clickListener: ((Event) -> Unit)?) {

                val participationStatus = if (userEmail != null) event.getParticipationStatus(userEmail) else null
                viewBackgroundStripedLayout.visibleOrGone(participationStatus == ParticipationStatus.NEEDS_ACTION)

                if (!event.isAllDay() && !event.spansSingleDay(timeZoneId = timeZoneId)) {
                    val fullDayCounter = event.calculateFullDayCounter(date, timeZoneId)
                    if (fullDayCounter.first == 1) { // this is the first day of an ongoing event
                        textViewHeader.visibleOrGone(true)
                        textViewHeader.text = "${(event.getActualStart(timeZoneId))?.formatTime(timeZoneId, is24Hour)}" // TODO
                    } else { // this is second or later day of an ongoing event
                        textViewHeader.visibleOrGone(false)
                    }
                } else {
                    textViewHeader.visibleOrGone(false)
                }

                textViewSubheader.text = event.summary?.nullIfBlank() ?: itemView.resources.getString(R.string.default_event_summary)

                if (event.spansSingleDay(timeZoneId = timeZoneId)) {
                    textViewSubheaderSide.visibleOrGone(false)
                } else {
                    textViewSubheaderSide.text = event.formatFullDayCounter(date, timeZoneId)
                    textViewSubheaderSide.visibleOrGone(true)
                }

                if (event.decryptionStatus == Event.DecryptionStatus.FAILURE) {
                    decryptionErrorIcon.visibleOrGone(true)
                    decryptionErrorView.visibleOrGone(true)
                    textViewHeader.visibleOrGone(false)
                    textViewSubheader.visibleOrGone(false)
                } else {
                    decryptionErrorIcon.visibleOrGone(false)
                    decryptionErrorView.visibleOrGone(false)
                    textViewSubheader.visibleOrGone(true)
                }

                viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))

                if (event.isInThePast(timeZoneId)) {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall_Weak)
                    textViewSubheader.setTextAppearance(R.style.Text_Default_Weak)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default_Weak)
                    ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.icon_weak)))

                    if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                    } else if (participationStatus == ParticipationStatus.NEEDS_ACTION) {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                        setStripedBackground(
                            viewBackgroundStriped,
                            itemView.context,
                            ContextCompat.getColor(itemView.context, R.color.shade_60)
                        ) // striped background with 20% opacity for unanswered all day events
                    } else {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_secondary))
                        decryptionErrorView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_norm))
                        decryptionErrorView.alpha = 0.1f
                    }
                } else {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall)
                    textViewSubheader.setTextAppearance(R.style.Text_Default)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default)

                    if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                    } else if (participationStatus == ParticipationStatus.NEEDS_ACTION) {
                        viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                        textViewHeader.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                        textViewSubheader.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                        textViewSubheaderSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                        setStripedBackground(
                            viewBackgroundStriped,
                            itemView.context,
                            Color.parseColor(event.calendar.color)
                        ) // striped background with 20% opacity for unanswered all day events
                    } else {
                        viewMainSurface.setTint(Color.parseColor(event.calendar.color))
                        textViewHeader.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                        textViewSubheader.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                        textViewSubheaderSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                        ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color)))
                        decryptionErrorView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                        decryptionErrorView.alpha = 0.2f
                    }
                }

                if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                itemView.setOnSingleClickListener { clickListener?.invoke(event) }
            }
        }
    }

    private val ITEM_TYPE_HEADER = 0
    private val ITEM_TYPE_EVENT_PARTIAL_DAY = 1
    private val ITEM_TYPE_EVENT_ALL_DAY = 2

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            ITEM_TYPE_HEADER
        } else if (getItem(position).isAllDay() || !getItem(position).spansSingleDay(timeZoneId = timeZoneId)) {
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
                timeZoneId,
                is24Hour
            )
        } else {
            EventViewHolder.AllDayEventViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_agenda_event_all_day,
                    parent,
                    false
                ),
                timeZoneId,
                is24Hour
            )
        }
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {

        when (holder) {
            is EventViewHolder.HeaderViewHolder -> holder.bind(date)
            is EventViewHolder.PartialDayEventViewHolder -> holder.bind(
                getItem(position),
                date,
                userEmail,
                clickListener
            )
            is EventViewHolder.AllDayEventViewHolder -> holder.bind(
                getItem(position),
                date,
                userEmail,
                clickListener
            )
        }

    }
}

