package me.proton.android.calendar.presentation.calendar

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_add_attendee.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Participant

class AddAttendeeListAdapter(
    private val searchList: Boolean = false,
    private val userEmails: List<String>,
    private val clickListener: (Participant) -> Unit
) : ListAdapter<Participant, AddAttendeeListAdapter.ViewHolder>(AddAttendeeDiffCallback()) {

    class AddAttendeeDiffCallback : DiffUtil.ItemCallback<Participant>() {
        override fun areItemsTheSame(oldItem: Participant, newItem: Participant): Boolean {
            return oldItem.email == newItem.email
        }

        override fun areContentsTheSame(oldItem: Participant, newItem: Participant): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_add_attendee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        private val attendeeItemTextLayout: LinearLayout = view.item_add_attendee_text_layout
        private val attendeeItemTitle: TextView = view.item_add_attendee_title
        private val attendeeItemDescription: TextView = view.item_add_attendee_description
        private val attendeeItemInitials: TextView = view.item_add_attendee_initials
        private val attendeeItemIconDelete: ImageView = view.item_add_attendee_delete_icon
        private val attendeeItemIconLoading: ProgressBar = view.item_add_attendee_loading_icon
        private val attendeeItemIconCheck: ImageView = view.item_add_attendee_check_icon
        private val attendeeItemPress: View = view.item_add_attendee_press
        private val attendeeItemSeparator: View = view.item_add_attendee_separator

        fun bind(participant : Participant, position : Int) {
            // If has common name use it, else use email and hide description field
            val title =
                if (participant.commonName.isNullOrEmpty()) participant.email ?: ""
                else participant.commonName
            val description =
                if (participant.commonName.isNullOrEmpty() ||
                    participant.commonName.equals(participant.email, ignoreCase = true)) ""
                else participant.email

            if (query.isNotEmpty() && title.contains(query)) {
                val spannableStringBuilder = SpannableStringBuilder(title)
                spannableStringBuilder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    title.indexOf(query),
                    title.indexOf(query) + query.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                attendeeItemTitle.text = spannableStringBuilder
            } else attendeeItemTitle.text = title

            attendeeItemDescription.visibleOrGone(description.isNotEmpty())
            val textLayoutParams = attendeeItemTextLayout.layoutParams as ConstraintLayout.LayoutParams
            if (description.isEmpty()) {
                // Update margins if description is hidden
                textLayoutParams.topMargin = view.context.dpToPixel(18)
                textLayoutParams.bottomMargin = view.context.dpToPixel(18)
            } else {
                // Update margins if description is visible
                textLayoutParams.topMargin = view.context.dpToPixel(14)
                textLayoutParams.bottomMargin = view.context.dpToPixel(14)
            }
            attendeeItemTextLayout.layoutParams = textLayoutParams

            if (description.isNotEmpty()) {
                if (query.isNotEmpty() && description.contains(query)) {
                    val spannableStringBuilder = SpannableStringBuilder(description)
                    spannableStringBuilder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        description.indexOf(query),
                        description.indexOf(query) + query.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    attendeeItemDescription.text = spannableStringBuilder
                } else attendeeItemDescription.text = description
            }

            attendeeItemInitials.text = getInitials(title)

            attendeeItemIconCheck.visibleOrGone(searchList && participant.added)
            attendeeItemIconLoading.visibleOrGone(false)

            attendeeItemIconDelete.visibleOrGone(!searchList)
            attendeeItemIconDelete.setOnSingleClickListener {
                if (!searchList) clickListener(participant)
            }

            attendeeItemPress.visibleOrGone(searchList && !participant.added)
            attendeeItemPress.setOnSingleClickListener {
                if (searchList) {
                    if (!userEmails.isNullOrEmpty() && userEmails.firstOrNull { it.equals(participant.email, true) } != null) {
                        view.displaySnackBar(view.context.getString(R.string.snack_add_self_as_participant))
                        return@setOnSingleClickListener
                    }
                    attendeeItemIconLoading.visibleOrGone(true)
                    clickListener(participant)
                }
            }
        }
    }

    private var query: String = ""
    fun setQuery(query: String) {
        this.query = query
    }
}
