package me.proton.android.calendar.presentation.settings.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_badge_layout
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_helper
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_icon
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_menu_icon
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_press
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_subtitle
import kotlinx.android.synthetic.main.item_settings_calendar.view.item_settings_calendar_title
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionStatus
import me.proton.android.calendar.domain.model.Calendar

class SettingsCalendarListAdapter(
    val listener: (Calendar) -> Unit
) : ListAdapter<Calendar, SettingsCalendarListAdapter.ViewHolder>(CalendarDiffCallback()) {

    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null
    private var defaultCalendarId: String? = null

    class CalendarDiffCallback : DiffUtil.ItemCallback<Calendar>() {
        override fun areItemsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
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

    fun setDefaultCalendarId(defaultCalendarId: String?): Boolean {
        val dataSetChanged = this.defaultCalendarId != defaultCalendarId
        this.defaultCalendarId = defaultCalendarId
        return dataSetChanged
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val calendarItemPress: View = view.item_settings_calendar_press
        private val calendarItemTitle: TextView = view.item_settings_calendar_title
        private val calendarItemSubtitle: TextView = view.item_settings_calendar_subtitle
        private val calendarItemHelper: TextView = view.item_settings_calendar_helper
        private val calendarItemMenuIcon: ImageView = view.item_settings_calendar_menu_icon
        private val calendarItemIcon: ImageView = view.item_settings_calendar_icon
        private val calendarItemBadgeLayout: LinearLayout = view.item_settings_calendar_badge_layout

        fun bind(calendar : Calendar) {
            // Calendar name
            calendarItemTitle.text = calendar.name

            // Calendar email
            calendarItemSubtitle.visibleOrGone(calendar.email.isNotEmpty())
            calendarItemSubtitle.text = calendar.email

            // Set colored calendar dot tint
            calendarItemIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))

            // Clear badges
            calendarItemBadgeLayout.removeAllViews()

            // Display default badge
            if (calendar.id == defaultCalendarId && calendar.isDisabled.not()) {
                addBadge(
                    itemView.context.getString(R.string.settings_calendar_default),
                    itemView.context.getColorFromAttr(R.attr.brand_norm)
                )
            }

            // Display disabled badge
            if (calendar.isDisabled) addBadge(itemView.context.getString(R.string.settings_calendar_disabled), itemView.context.getColor(R.color.background_secondary), R.color.text_norm)

            calendarItemHelper.visibleOrGone(false)
            if (calendar.isSubscribed) {
                val calendarSubscription = calendarSubscriptions?.firstOrNull { it.calendarId == calendar.id }

                // Display not synced badge
                if (calendarSubscription?.isSynced == false) {
                    /*
                    Priority as followed:
                    - LastUpdateTime == 0 -> Syncing
                    - isLastSyncOld -> Not synced + helper message
                    - Status == 7 -> Syncing
                    - Status > 0 -> Not synced + helper message if existing
                     */
                    addBadge(
                        itemView.context.getString(
                            if (calendarSubscription.lastUpdateTime == 0 ||
                                (calendarSubscription.status == CalendarSubscriptionStatus.SYNCING.value && calendarSubscription.isLastSyncOld.not()))
                                    R.string.settings_calendar_syncing
                            else R.string.settings_calendar_not_synced
                        ),
                        itemView.context.getColor(R.color.notification_warning)
                    )
                    val helperMessage =
                        if (calendarSubscription.isLastSyncOld)
                            itemView.context.getString(R.string.settings_calendar_subscribed_last_sync_old)
                        else {
                            when (calendarSubscription.status) {
                                CalendarSubscriptionStatus.INVALID_ICS.value -> {
                                    itemView.context.getString(R.string.settings_calendar_subscribed_invalid_ics)
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
                                    null
                                }
                            }
                        }

                    calendarItemHelper.visibleOrGone(!helperMessage.isNullOrEmpty())
                    calendarItemHelper.text = helperMessage
                }
            }

            val calendarSettingsCanBeEdited = (calendar.isSubscribed.not() && calendar.isOwner) || // my own personal calendar
                    calendar.isSubscribed || // subscribed calendar
                    calendar.isSharedWithMe // shared calendar

            // Only show menu icon when calendar can be edited
            calendarItemMenuIcon.visibleOrGone(calendarSettingsCanBeEdited)

            // Only allow item click when calendar can be edited
            calendarItemPress.visibleOrGone(calendarSettingsCanBeEdited)

            if (calendarSettingsCanBeEdited) {
                // On item click
                calendarItemPress.setOnSingleClickListener {
                    listener(calendar)
                }

                // On menu icon click
                calendarItemMenuIcon.setOnSingleClickListener {
                    listener(calendar)
                }
            }
        }

        private fun addBadge(text: String, color: Int, textColor: Int? = null) {
            val badgeView = LayoutInflater.from(itemView.context).inflate(R.layout.item_badge, calendarItemBadgeLayout, false) as TextView
            badgeView.text = text
            textColor?.let {
                badgeView.setTextColor(ContextCompat.getColor(itemView.context, it))
            }
            badgeView.backgroundTintList = ColorStateList.valueOf(color)
            calendarItemBadgeLayout.addView(badgeView)
        }
    }
}
