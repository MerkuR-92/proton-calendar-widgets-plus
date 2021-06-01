package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationStatus
import kotlinx.android.synthetic.main.item_day_view_event_all_day.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.EventUtilsImpl.formatFullDayCounter
import me.proton.android.calendar.common.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.domain.model.Event
import java.time.LocalDate

class DayViewAllDayEventAdapter(
    private val userEmails: List<String>,
    private val timeZoneId: String,
    private val date: LocalDate,
    private val clickListener: (Event) -> Unit
) : ListAdapter<Event, DayViewAllDayEventAdapter.ViewHolder>(EventDiffCallback()) {

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day_view_event_all_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        private val eventItemTitle: TextView = view.text_title
        private val eventItemTitleSide: TextView = view.text_title_side

        private val viewBackground: LayerDrawable = itemView.findViewById<View>(R.id.view_background).background as LayerDrawable
        private val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
        private val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)

        fun bind(event : Event, position : Int) {

            this.view.setOnClickListener {
                clickListener(event)
            }

            initEventStatus(event.getParticipationStatus(userEmails), view.context)

            eventItemTitle.text = if (event.summary.isNullOrEmpty()) view.context.getString(R.string.default_event_summary) else event.summary
            if (event.spansSingleDay(timeZoneId = timeZoneId)) {
                eventItemTitleSide.visibleOrGone(false)
            } else {
                eventItemTitleSide.visibleOrGone(true)
                eventItemTitleSide.text = event.formatFullDayCounter(date, timeZoneId)
            }
            viewMainSurface.setTint(Color.parseColor(event.calendar.color))
            viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))
        }
    }
}

fun initEventStatus(participationStatus: ParticipationStatus?, context: Context) {
    when (participationStatus) {
        ParticipationStatus.ACCEPTED -> {
        }
        ParticipationStatus.DECLINED -> {
        }
        ParticipationStatus.TENTATIVE -> {
        }
        else -> {
        }
    }
}
