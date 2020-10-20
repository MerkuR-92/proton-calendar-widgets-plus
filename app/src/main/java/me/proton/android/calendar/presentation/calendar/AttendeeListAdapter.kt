package me.proton.android.calendar.presentation.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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

class AttendeeListAdapter() : ListAdapter<Attendee, AttendeeListAdapter.ViewHolder>(AttendeeDiffCallback()) {

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
        private val attendeeItemLayout: ConstraintLayout = view.item_attendee_layout
        private val attendeeItemTextLayout: LinearLayout = view.item_attendee_text_layout
        private val attendeeItemTitle: TextView = view.item_attendee_title
        private val attendeeItemDescription: TextView = view.item_attendee_description
        private val attendeeItemInitials: TextView = view.item_attendee_initials
        private val attendeeItemStatus: ImageView = view.item_attendee_status
        private val attendeeItemOptional: TextView = view.item_attendee_optional

        fun bind(attendee : Attendee, position : Int) {
            val description = if (!attendee.commonName.isNullOrEmpty()) attendee.email else ""
            val title = if (description.isEmpty()) attendee.email else attendee.commonName
            attendeeItemTitle.text = title

            if (description.isNotEmpty()) attendeeItemDescription.text = description
            else attendeeItemDescription.visibleOrGone(false)

            // TODO Handle common name and picture when contacts are implemented
            attendeeItemDescription.visibleOrGone(false)
            attendeeItemInitials.text = getInitials(title)

            if (!attendeeItemDescription.isVisible) {
                val params = attendeeItemTextLayout.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = view.context.resources.getDimensionPixelSize(R.dimen.spacing_page)
                params.bottomMargin = view.context.resources.getDimensionPixelSize(R.dimen.attendee_single_title_bottom_margin)
                attendeeItemTextLayout.layoutParams = params
            }

            if (attendee.rsvp != null && !attendee.rsvp) {
                attendeeItemOptional.visibleOrGone(true)
                // If has Optional label, we need to clear LinearLayout constraint
                //  to bottom of view to keep the same spacing
                val constraintSet = ConstraintSet()
                constraintSet.clone(attendeeItemLayout)
                constraintSet.clear(attendeeItemTextLayout.id, ConstraintSet.BOTTOM)
                constraintSet.applyTo(attendeeItemLayout)
            }

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
                ParticipationStatus.TENTATIVE -> {
                    attendeeItemStatus.visibleOrGone(true)
                    attendeeItemStatus.backgroundTintList = ContextCompat.getColorStateList(view.context, R.color.notification_warning)
                    attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(view.context, R.drawable.ic_question))
                }
                else ->  attendeeItemStatus.visibleOrGone(false)
            }
        }
    }
}
