package me.proton.android.calendar.presentation.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import kotlinx.android.synthetic.main.item_attendee.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.getInitials
import me.proton.android.calendar.common.visibleOrGone
import me.proton.android.calendar.presentation.MainActivity

class AttendeeListAdapter(
    val listener: (Attendee) -> Unit
) : ListAdapter<Attendee, AttendeeListAdapter.ViewHolder>(AttendeeDiffCallback()) {

    class AttendeeDiffCallback : DiffUtil.ItemCallback<Attendee>() {
        override fun areItemsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem.email == newItem.email
        }

        override fun areContentsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        private val attendeeItemOverlay: View = view.item_attendee_press
        private val attendeeItemTitle: TextView = view.item_attendee_title
        private val attendeeItemDescription: TextView = view.item_attendee_description
        private val attendeeItemInitials: TextView = view.item_attendee_initials
        private val attendeeItemStatus: ImageView = view.item_attendee_status

        fun bind(attendee : Attendee, position : Int) {
            val description = if (!attendee.commonName.isNullOrEmpty()) attendee.email else ""
            val title = if (description.isEmpty()) attendee.email else attendee.commonName
            attendeeItemTitle.text = title
            if (description.isNotEmpty()) attendeeItemDescription.text = description
            attendeeItemInitials.text = getInitials(title)

            /*
            android:backgroundTint="@color/notification_success"
            app:srcCompat="@drawable/ic_check"
             */
            when (attendee.participationStatus) {
                ParticipationStatus.ACCEPTED -> {
                    attendeeItemStatus.visibleOrGone(true)
                    attendeeItemStatus.backgroundTintList = ContextCompat.getColorStateList(view.context, R.color.notification_success)
                    attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(view.context, R.drawable.ic_check))
                }
                ParticipationStatus.DECLINED -> {
                    attendeeItemStatus.visibleOrGone(true)
                    attendeeItemStatus.backgroundTintList = ContextCompat.getColorStateList(view.context, R.color.notification_error)
                    attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(view.context, R.drawable.ic_close))
                }
                ParticipationStatus.NEEDS_ACTION -> {
                    attendeeItemStatus.visibleOrGone(true)
                    attendeeItemStatus.backgroundTintList = ContextCompat.getColorStateList(view.context, R.color.notification_warning)
                    attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(view.context, R.drawable.ic_question))
                }
                else ->  attendeeItemStatus.visibleOrGone(false)
            }

            attendeeItemOverlay.setOnClickListener {
                listener(attendee)
            }
        }
    }
}
