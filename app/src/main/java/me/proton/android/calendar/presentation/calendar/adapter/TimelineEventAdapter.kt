package me.proton.android.calendar.presentation.calendar.adapter

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_timeline.view.decryption_error_icon
import kotlinx.android.synthetic.main.item_timeline.view.decryption_error_view
import kotlinx.android.synthetic.main.item_timeline.view.iv_calendar_bar
import kotlinx.android.synthetic.main.item_timeline.view.rl_event
import kotlinx.android.synthetic.main.item_timeline.view.rl_event_day_container
import kotlinx.android.synthetic.main.item_timeline.view.tv_event_date_header
import kotlinx.android.synthetic.main.item_timeline.view.tv_event_date_text
import kotlinx.android.synthetic.main.item_timeline.view.tv_event_header
import kotlinx.android.synthetic.main.item_timeline.view.tv_event_header_day_indicator
import kotlinx.android.synthetic.main.item_timeline.view.tv_event_subheader
import kotlinx.android.synthetic.main.item_timeline.view.tv_event_subheader_side_text
import kotlinx.android.synthetic.main.item_timeline.view.v_event_spacing
import kotlinx.android.synthetic.main.item_timeline_header.view.text_header
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatShort
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TimelineEventAdapter(
    private val clickListener: (TimelineEvent) -> Unit
) : ListAdapter<TimelineEventAdapter.TimelineItem, TimelineEventAdapter.ViewHolder>(TimelineEventDiffCallback()) {

    override fun getItemViewType(position: Int): Int = getCurrentList()[position].type.ordinal

    class TimelineEventDiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return if (oldItem is TimelineItem.Header && newItem is TimelineItem.Header) {
                oldItem.year == newItem.year
            } else if (oldItem is TimelineItem.Event && newItem is TimelineItem.Event) {
                oldItem.event == newItem.event
            } else false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(TimelineItemType.values()[viewType]) {
            TimelineItemType.Header -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_timeline_header, parent, false))
            TimelineItemType.Event -> EventViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getCurrentList()[position]) {
            is TimelineItem.Header -> (holder as HeaderViewHolder).bind(item)
            is TimelineItem.Event -> (holder as EventViewHolder).bind(item)
        }
    }

    enum class TimelineItemType {
        Header,
        Event
    }

    sealed class TimelineItem(val type: TimelineItemType) {
        data class Header(val year: Int): TimelineItem(TimelineItemType.Header)
        data class Event(val event: TimelineEvent): TimelineItem(TimelineItemType.Event)
    }

    abstract class ViewHolder(private val eventView: View): RecyclerView.ViewHolder(eventView)

    inner class EventViewHolder(private val eventView: View): ViewHolder(eventView) {
        fun bind(eventItem: TimelineItem.Event) {

            val event = eventItem.event
            with(eventView) {
                // show or hide date column
                rl_event_day_container.visibleOrInvisible(event.showDateColumn)
                tv_event_date_header.text = if (event.showDateColumn) event.happensOn.month.formatShort() else ""
                tv_event_date_text.text = if (event.showDateColumn) "${event.happensOn.dayOfMonth}" else ""

                // strikethrough if event is cancelled
                if (event.isCancelledOrDeclined) {
                    tv_event_header.paintFlags = tv_event_header.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tv_event_subheader.paintFlags = tv_event_subheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    tv_event_header.paintFlags = tv_event_header.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tv_event_subheader.paintFlags = tv_event_subheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                // set summary and time
                tv_event_header.text = event.summary
                tv_event_header_day_indicator.text = if (event.fullDayCounter != null) " " + event.fullDayCounter else ""
                tv_event_subheader.text = event.dateContent
                tv_event_subheader_side_text.text = event.location

                if (event.searchTerm.isNotBlank()) {
                    tv_event_header.highlightSearchTokens(event.searchTerm.split(" ", ignoreCase = true))
                    tv_event_subheader_side_text.highlightSearchTokens(event.searchTerm.split(" ", ignoreCase = true))
                }

                // clear summary and show views for event that failed decryption
                if (event.isEncrypted) tv_event_header.text = ""
                decryption_error_view.visibleOrInvisible(event.isEncrypted)
                decryption_error_icon.visibleOrInvisible(event.isEncrypted)

                // add spacing after last event in a day
                v_event_spacing.visibleOrGone(event.showBottomSpacing)

                // set style of calendar bar
                if (event.needsAction) {
                    iv_calendar_bar.setBackgroundResource(R.drawable.ic_calendar_bar_unanswered)
                } else {
                    iv_calendar_bar.setBackgroundResource(R.drawable.shape_calendar_bar)
                }

                // tint calendar bar
                iv_calendar_bar.background.setTint(Color.parseColor(event.calendarColor))

            }

            eventView.rl_event.setOnSingleClickListener {
                clickListener(eventItem.event)
            }
        }
    }

    inner class HeaderViewHolder(private val headerView: View): ViewHolder(headerView){
        fun bind(headerItem: TimelineItem.Header) {
            headerView.text_header.text = headerItem.year.toString()
        }
    }

    data class TimelineEvent(
        val id: String,
        val summary: String,
        val dateContent: String,
        val location: String,
        val isCancelledOrDeclined: Boolean,
        val needsAction: Boolean,
        val isEncrypted: Boolean,
        // LocalDate that this Event spans, not necessarily the same as dateStart
        val happensOn: LocalDate,
        val showDateColumn: Boolean,
        val showBottomSpacing: Boolean,
        val fullDayCounter: String?,
        val occurrenceNumber: Int,
        val calendarColor: String,
        val searchTerm: String
    )

}

/**
 * Finds index of first element happening today.
 * @param [zoneId] used to determine what "today" is
 */
fun List<TimelineEventAdapter.TimelineItem>.findIndexToScrollTo(zoneId: ZoneId): Int {

    // we need to adjust list position relative to "today"
    val today = LocalDate.now(zoneId)

    val binaryIndex = this.binarySearchBy(0) {
        if (it is TimelineEventAdapter.TimelineItem.Event) {
            ChronoUnit.DAYS.between(today, it.event.happensOn).toInt()
        } else if (it is TimelineEventAdapter.TimelineItem.Header) {
            ChronoUnit.DAYS.between(today, LocalDate.of(it.year, 1, 1)).toInt()
        } else {
            Int.MAX_VALUE
        }
    }
    val nonNegativeBinaryIndex = if (binaryIndex < 0) {
        (binaryIndex + 1) * -1
    } else binaryIndex

    var positionToScrollTo = if (nonNegativeBinaryIndex > this.size - 1) this.size - 1 else nonNegativeBinaryIndex

    val binaryItemToScrollTo = if (positionToScrollTo >= 0 && positionToScrollTo <= this.lastIndex) this[positionToScrollTo] else null
    val binaryItemToScrollToLocalDate = if (binaryItemToScrollTo is TimelineEventAdapter.TimelineItem.Event) {
        binaryItemToScrollTo.event.happensOn
    } else if (binaryItemToScrollTo is TimelineEventAdapter.TimelineItem.Header) {
        LocalDate.of(binaryItemToScrollTo.year, 1, 1)
    } else {
        today // can only happen if list was empty, not valid anyway
    }

    // item on the list found with binary search might not be the first today, we need to traverse up,
    // until we find the first item on that day
    while (positionToScrollTo > 0) {
        val listItem = this[positionToScrollTo]
        if (listItem is TimelineEventAdapter.TimelineItem.Event) {
            if (listItem.event.happensOn.isBefore(binaryItemToScrollToLocalDate)) {
                positionToScrollTo++
                break
            } else {
                positionToScrollTo--
            }
        } else break
    }

    return positionToScrollTo
}
