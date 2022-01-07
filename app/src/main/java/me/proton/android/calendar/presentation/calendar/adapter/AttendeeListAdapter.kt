package me.proton.android.calendar.presentation.calendar.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationLevel
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import kotlinx.android.synthetic.main.item_attendee.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.getInitials
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl

class AttendeeListAdapter(val canonicalUserEmails: List<String>?) : ListAdapter<Attendee, AttendeeListAdapter.ViewHolder>(AttendeeDiffCallback()) {

    class AttendeeDiffCallback : DiffUtil.ItemCallback<Attendee>() {
        override fun areItemsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem.extractEmail() == newItem.extractEmail()
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
        private val attendeeItemLayout: ConstraintLayout = view.item_attendee_layout
        private val attendeeItemTextLayout: LinearLayout = view.item_attendee_text_layout
        private val attendeeItemTitle: TextView = view.item_attendee_title
        private val attendeeItemDescription: TextView = view.item_attendee_description
        private val attendeeItemInitials: TextView = view.item_attendee_initials
        private val attendeeItemStatus: ImageView = view.item_attendee_status
        private val attendeeItemOptional: TextView = view.item_attendee_optional

        fun bind(attendee : Attendee, position : Int) {
            // If has common name use it, else use email and hide description field
            val attendeeEmail = attendee.extractEmail()
            val title =
                if (attendee.commonName.isNullOrEmpty()) attendeeEmail ?: ""
                else attendee.commonName
            val description =
                if (attendee.commonName.isNullOrEmpty() ||
                    attendee.commonName.equals(attendeeEmail, ignoreCase = true)) ""
                else attendeeEmail ?: ""

            val attendeeIsCurrentUser = attendeeEmail?.let {
                canonicalUserEmails?.contains(
                    ProtonUtilsImpl.canonicalizeProtonEmail(
                        attendeeEmail,
                        forceCanonicalization = true
                    )
                ) == true
            } ?: false
            attendeeItemTitle.text =
                if (attendeeIsCurrentUser) view.context.getString(R.string.event_attendee_is_current_user)
                else title
            attendeeItemDescription.visibleOrGone(description.isNotEmpty() || attendeeIsCurrentUser)
            if (description.isNotEmpty() || attendeeIsCurrentUser) {
                attendeeItemDescription.text =
                    if (attendeeIsCurrentUser) attendeeEmail
                    else description
            }

            attendeeItemInitials.text = getInitials(title)

            if (attendee.participationLevel != null && attendee.participationLevel == ParticipationLevel.OPTIONAL) {
                if (attendeeItemDescription.visibility != View.VISIBLE) {
                    // Use description to display optional label if we only have the title,
                    //  in order to keep the correct alignment
                    attendeeItemOptional.visibleOrGone(false)
                    attendeeItemDescription.visibleOrGone(true)
                    attendeeItemDescription.text = view.context.getString(R.string.event_attendee_optional)
                } else {
                    attendeeItemOptional.visibleOrGone(true)
                    // If has Optional label, we need to clear LinearLayout constraint
                    //  to bottom of view to keep the same spacing
                    val constraintSet = ConstraintSet()
                    constraintSet.clone(attendeeItemLayout)
                    constraintSet.clear(attendeeItemTextLayout.id, ConstraintSet.BOTTOM)
                    constraintSet.applyTo(attendeeItemLayout)
                }
            } else {
                attendeeItemOptional.visibleOrGone(false)
            }

            initAttendeeStatus(attendeeItemStatus, attendee.participationStatus ?: ParticipationStatus.NEEDS_ACTION, view.context)
        }
    }
}

fun initAttendeeStatus(attendeeItemStatus: ImageView, participationStatus: ParticipationStatus, context: Context) {
    attendeeItemStatus.visibleOrGone(true)
    when (participationStatus) {
        ParticipationStatus.ACCEPTED -> {
            attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check_circle_filled))
        }
        ParticipationStatus.DECLINED -> {
            attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_clear_circle_filled))
        }
        ParticipationStatus.TENTATIVE -> {
            attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_question_circle_filled))
        }
        else ->  attendeeItemStatus.visibleOrGone(false)
    }
}
