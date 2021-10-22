package me.proton.android.calendar.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent

class SettingsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "SettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_settings

    override val navigateUp = true

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private lateinit var settingsUserCalendarListAdapter: SettingsCalendarListAdapter
    private lateinit var settingsSubscribedCalendarListAdapter: SettingsCalendarListAdapter

    private val subscribedCalendarsMediator = MediatorLiveData<Pair<List<CalendarEntity>, List<CalendarSubscriptionEntity>>>()
    private var subscribedCalendars: List<CalendarEntity>? = null
    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_more_settings)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings_general_press.setOnClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_general_settings)
        }

        val settingsCalendarListView = settings_calendars_list
        val settingsCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsCalendarListView.layoutManager = settingsCalendarLayoutManager
        settingsUserCalendarListAdapter = SettingsCalendarListAdapter() { calendarEntity ->
            //On Calendar click event
            // TODO
        }
        (settingsCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        settingsCalendarListView.adapter = settingsUserCalendarListAdapter

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
            userCalendars ?: return@observe

            lifecycleScope.launch {
                val calendarEmails = hashMapOf<String, String>()
                userCalendars.forEach { userCalendar ->
                    val calendarEmail = eventViewModel.getCalendarEmail(userCalendar.id)
                    calendarEmail?.let {
                        calendarEmails[userCalendar.id] = it
                    }
                }
                val defaultCalendarId = calendarViewModel.getDefaultCalendarId()
                defaultCalendarId?.let { settingsUserCalendarListAdapter.setDefaultCalendarId(defaultCalendarId) }
                settingsUserCalendarListAdapter.setCalendarEmails(calendarEmails)
                settingsUserCalendarListAdapter.submitList(
                    userCalendars.sortedBy {
                        it.isDisabled // Disabled will appear last
                    }.sortedByDescending {
                        it.id == defaultCalendarId // Default will appear first
                    }
                )
            }
        }

        val settingsSubscribedCalendarListView = settings_subscribed_calendars_list
        val settingsSubscribedCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsSubscribedCalendarListView.layoutManager = settingsSubscribedCalendarLayoutManager
        settingsSubscribedCalendarListAdapter = SettingsCalendarListAdapter() { calendarEntity ->
            //On Calendar click event
            // TODO
        }
        (settingsSubscribedCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        settingsSubscribedCalendarListView.adapter = settingsSubscribedCalendarListAdapter

        subscribedCalendarsMediator.addSource(calendarViewModel.subscribedCalendars) { value ->
            subscribedCalendars = value

            if (subscribedCalendars != null && calendarSubscriptions != null) {
                subscribedCalendarsMediator.value = Pair(subscribedCalendars!!, calendarSubscriptions!!)
            }
        }
        subscribedCalendarsMediator.addSource(calendarViewModel.calendarSubscriptions) { value ->
            calendarSubscriptions = value

            if (subscribedCalendars != null && calendarSubscriptions != null) {
                subscribedCalendarsMediator.value = Pair(subscribedCalendars!!, calendarSubscriptions!!)
            }
        }
        subscribedCalendarsMediator.observe(viewLifecycleOwner) {
            it?.let {
                lifecycleScope.launch {
                    val subscribedCalendars = it.first
                    val calendarSubscriptions = it.second

                    val dataSetChanged =
                        settingsSubscribedCalendarListAdapter.setCalendarSubscriptions(calendarSubscriptions)

                    val calendarEmails = hashMapOf<String, String>()
                    subscribedCalendars.forEach { userCalendar ->
                        val calendarEmail = eventViewModel.getCalendarEmail(userCalendar.id)
                        calendarEmail?.let {
                            calendarEmails[userCalendar.id] = it
                        }
                    }
                    settingsSubscribedCalendarListAdapter.setCalendarEmails(calendarEmails)

                    settingsSubscribedCalendarListAdapter.submitList(subscribedCalendars)
                    if (dataSetChanged) settingsSubscribedCalendarListAdapter.notifyDataSetChanged()
                    settings_subscribed_calendars.visibleOrGone(subscribedCalendars.isNotEmpty())
                }
            }
        }
    }
}
