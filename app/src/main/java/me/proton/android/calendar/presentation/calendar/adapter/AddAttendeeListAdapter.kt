package me.proton.android.calendar.presentation.calendar.adapter

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
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
import biweekly.property.Attendee
import kotlinx.android.synthetic.main.item_add_attendee.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.getInitials
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail

class AddAttendeeListAdapter(
    private val searchList: Boolean = false,
    private val clickListener: (Attendee) -> Unit
) : ListAdapter<Attendee, AddAttendeeListAdapter.ViewHolder>(AddAttendeeDiffCallback()) {

    private var attendeeList: List<Attendee> = arrayListOf()
    private var query: String = ""
    private var organizerEmail: String = ""

    class AddAttendeeDiffCallback : DiffUtil.ItemCallback<Attendee>() {
        override fun areItemsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem.extractEmail() == newItem.extractEmail()
        }

        override fun areContentsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
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

        fun bind(attendee : Attendee, position : Int) {
            val isOrganizer = organizerEmail.equals(attendee.extractEmail(), true)
            // If has common name use it, else use email and hide description field
            val title =
                when {
                    !searchList && isOrganizer -> {
                        view.context.getString(R.string.event_current_user_organizer)
                    }
                    attendee.commonName.isNullOrEmpty() -> attendee.extractEmail() ?: ""
                    else -> attendee.commonName
                }
            val description =
                if (!isOrganizer && (attendee.commonName.isNullOrEmpty() ||
                            attendee.commonName.equals(attendee.extractEmail(), ignoreCase = true))) ""
                else attendee.extractEmail() ?: ""

            attendeeItemTitle.text = title
            attendeeItemTitle.highlightSearchTokens(listOf(query))

            attendeeItemDescription.visibleOrGone(description.isNotEmpty())
            val textLayoutParams = attendeeItemTextLayout.layoutParams as ConstraintLayout.LayoutParams
            if (description.isEmpty()) {
                // Update margins if description is hidden
                textLayoutParams.topMargin = view.context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin_large)
                textLayoutParams.bottomMargin = view.context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin_large)
            } else {
                // Update margins if description is visible
                textLayoutParams.topMargin = view.context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin)
                textLayoutParams.bottomMargin = view.context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin)
            }
            attendeeItemTextLayout.layoutParams = textLayoutParams

            if (description.isNotEmpty()) {
                if (query.isNotEmpty() && description.contains(query, true)) {
                    val spannableStringBuilder = SpannableStringBuilder(description)
                    spannableStringBuilder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        description.indexOf(query, ignoreCase = true),
                        description.indexOf(query, ignoreCase = true) + query.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    attendeeItemDescription.text = spannableStringBuilder
                } else attendeeItemDescription.text = description
            }

            attendeeItemInitials.text =
                if (!searchList && isOrganizer) getInitials(attendee.commonName)
                else getInitials(title)

            val added = attendeeList.firstOrNull { it.extractEmail().equals(attendee.extractEmail(), true) && !isOrganizer } != null

            attendeeItemIconCheck.visibleOrGone(searchList && added)
            attendeeItemIconLoading.visibleOrGone(false)

            attendeeItemIconDelete.visibleOrGone(!searchList && !isOrganizer)
            attendeeItemIconDelete.setOnSingleClickListener {
                if (!searchList) clickListener(attendee)
            }

            attendeeItemPress.visibleOrGone(searchList && !added)
            attendeeItemPress.setOnSingleClickListener {
                if (searchList) {
                    attendeeItemIconLoading.visibleOrGone(true)
                    clickListener(attendee)
                }
            }
        }
    }

    fun setAttendeeList(attendeeList: List<Attendee>) {
        this.attendeeList = attendeeList
    }

    fun setQuery(query: String) {
        this.query = query
    }

    fun setOrganizerEmail(organizerEmail: String) {
        this.organizerEmail = organizerEmail
    }
}
