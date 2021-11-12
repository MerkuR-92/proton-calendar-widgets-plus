package me.proton.android.calendar.presentation.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.item_settings_calendar.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionStatus

class SettingsCalendarListAdapter(
    val listener: (CalendarEntity) -> Unit
) : ListAdapter<CalendarEntity, SettingsCalendarListAdapter.ViewHolder>(CalendarEntityDiffCallback()) {

    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null
    private var calendarEmails: Map<String, String>? = null
    private var defaultCalendarId: String? = null

    class CalendarEntityDiffCallback : DiffUtil.ItemCallback<CalendarEntity>() {
        override fun areItemsTheSame(oldItem: CalendarEntity, newItem: CalendarEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalendarEntity, newItem: CalendarEntity): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings_calendar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    fun setCalendarSubscriptions(calendarSubscriptions: List<CalendarSubscriptionEntity>): Boolean {
        val dataSetChanged = this.calendarSubscriptions != calendarSubscriptions
        this.calendarSubscriptions = calendarSubscriptions
        return dataSetChanged
    }

    fun setCalendarEmails(calendarEmails: Map<String, String>): Boolean {
        val dataSetChanged = this.calendarEmails != calendarEmails
        this.calendarEmails = calendarEmails
        return dataSetChanged
    }

    fun setDefaultCalendarId(defaultCalendarId: String?): Boolean {
        val dataSetChanged = this.defaultCalendarId != defaultCalendarId
        this.defaultCalendarId = defaultCalendarId
        return dataSetChanged
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val calendarEntityItemPress: View = view.item_settings_calendar_press
        private val calendarEntityItemTitle: TextView = view.item_settings_calendar_title
        private val calendarEntityItemSubtitle: TextView = view.item_settings_calendar_subtitle
        private val calendarEntityItemHelper: TextView = view.item_settings_calendar_helper
        private val calendarEntityItemMenuIcon: ImageView = view.item_settings_calendar_menu_icon
        private val calendarEntityItemIcon: ImageView = view.item_settings_calendar_icon
        private val calendarEntityItemBadgeLayout: LinearLayout = view.item_settings_calendar_badge_layout

        fun bind(calendarEntity : CalendarEntity) {
            // Calendar name
            calendarEntityItemTitle.text = calendarEntity.name

            // Calendar email
            val calendarEmail = calendarEmails?.get(calendarEntity.id)
            calendarEntityItemSubtitle.visibleOrGone(calendarEmail?.isNotEmpty() == true)
            calendarEntityItemSubtitle.text = calendarEmail

            // Set colored calendar dot tint
            calendarEntityItemIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(calendarEntity.color))

            // Clear badges
            calendarEntityItemBadgeLayout.removeAllViews()

            // Display default badge
            if (calendarEntity.id == defaultCalendarId) addBadge(itemView.context.getString(R.string.settings_calendar_default), R.color.brand_norm)

            // Display disabled badge
            if (calendarEntity.isDisabled) addBadge(itemView.context.getString(R.string.settings_calendar_disabled), R.color.notification_warning)

            calendarEntityItemHelper.visibleOrGone(false)
            if (calendarEntity.isSubscribed) {
                val calendarSubscription = calendarSubscriptions?.firstOrNull { it.calendarId == calendarEntity.id }

                // Display not synced badge
                if (calendarSubscription?.isSynced == false) {
                    addBadge(
                        itemView.context.getString(
                            if (calendarSubscription.isSyncing) R.string.settings_calendar_syncing
                            else R.string.settings_calendar_not_synced
                        ),
                        R.color.notification_warning
                    )
                    val helperMessage = when (calendarSubscription.status) {
                        CalendarSubscriptionStatus.INVALID_ICS.value -> {
                            itemView.context.getString(R.string.settings_calendar_subscribed_wrong_link)
                        }
                        CalendarSubscriptionStatus.SIZE_EXCEED_LIMIT.value -> {
                            itemView.context.getString(R.string.settings_calendar_subscribed_too_big)
                        }
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_BAD_REQUEST.value,
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_UNAUTHORIZED.value,
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_FORBIDDEN.value,
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_NOT_FOUND.value,
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_TEST.value -> {
                            itemView.context.getString(R.string.settings_calendar_subscribed_not_accessible)
                        }
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_GENERIC_ERROR.value,
                        CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_INTERNAL_SERVER_ERROR.value -> {
                            itemView.context.getString(R.string.settings_calendar_subscribed_tmp_not_accessible)
                        }
                        CalendarSubscriptionStatus.P2P_LINK_NOT_FOUND.value,
                        CalendarSubscriptionStatus.UNABLE_TO_DECRYPT.value -> {
                            itemView.context.getString(R.string.settings_calendar_subscribed_not_decrypted)
                        }
                        else -> {
                            if (calendarSubscription.isLastSyncOld)
                                itemView.context.getString(R.string.settings_calendar_subscribed_last_sync_old)
                            else null
                        }
                    }

                    calendarEntityItemHelper.visibleOrGone(!helperMessage.isNullOrEmpty())
                    calendarEntityItemHelper.text = helperMessage
                }
            }

            // Only show menu icon when calendar can be edited
            calendarEntityItemMenuIcon.visibleOrGone(calendarEntity.isSubscribed.not())

            // Only allow item click when calendar can be edited
            calendarEntityItemPress.visibleOrGone(calendarEntity.isSubscribed.not())

            if (calendarEntity.isSubscribed.not()) {
                // On item click
                calendarEntityItemPress.setOnSingleClickListener {
                    listener(calendarEntity)
                }

                // On menu icon click
                calendarEntityItemMenuIcon.setOnSingleClickListener {
                    listener(calendarEntity)
                }
            }
        }

        private fun addBadge(text: String, colorId: Int) {
            val badgeView = LayoutInflater.from(itemView.context).inflate(R.layout.item_badge, calendarEntityItemBadgeLayout, false) as TextView
            badgeView.text = text
            badgeView.backgroundTintList = ColorStateList.valueOf(itemView.context.getColor(colorId))
            calendarEntityItemBadgeLayout.addView(badgeView)
        }
    }
}
