package me.proton.android.calendar.presentation.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_general_settings.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.FeatureFlag.DELETE_CALENDAR
import me.proton.android.calendar.common.FragmentArguments.CALENDAR_ID_ARG
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
    private val calendarFormViewModel: CalendarFormViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private lateinit var settingsUserCalendarListAdapter: SettingsCalendarListAdapter
    private lateinit var settingsSubscribedCalendarListAdapter: SettingsCalendarListAdapter

    private val subscribedCalendarsMediator = MediatorLiveData<Pair<List<CalendarEntity>, List<CalendarSubscriptionEntity>>>()
    private var subscribedCalendars: List<CalendarEntity>? = null
    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null

    private var defaultCalendarId: String = ""

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

        settings_general_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_general_settings)
        }

        settings_calendars_list_add_layout_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_calendar_form)
        }
        lifecycleScope.launch {
            settings_calendars_list_add_layout.visibleOrGone(
                calendarViewModel.isUserCalendarLimitReached() == CalendarViewModel.UserCalendarLimit.NOT_REACHED
            )
        }

        val settingsCalendarListView = settings_calendars_list
        val settingsCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsCalendarListView.layoutManager = settingsCalendarLayoutManager
        settingsUserCalendarListAdapter = SettingsCalendarListAdapter() { calendarEntity ->
            //On Calendar click event
            showBottomSheetDialog(calendarEntity)
        }
        (settingsCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        settingsCalendarListView.adapter = settingsUserCalendarListAdapter

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
            userCalendars ?: return@observe

            refreshUserCalendarList(userCalendars.filter { it.isActive || it.isDisabled })
        }

        val settingsSubscribedCalendarListView = settings_subscribed_calendars_list
        val settingsSubscribedCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsSubscribedCalendarListView.layoutManager = settingsSubscribedCalendarLayoutManager
        settingsSubscribedCalendarListAdapter = SettingsCalendarListAdapter() { calendarEntity ->
            //On Calendar click event
            showBottomSheetDialog(calendarEntity)
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

        calendarViewModel.defaultCalendarId.observe(viewLifecycleOwner) { defaultCalendarId ->
            defaultCalendarId ?: return@observe

            if (this@SettingsFragment.defaultCalendarId != defaultCalendarId) {
                this@SettingsFragment.defaultCalendarId = defaultCalendarId
                calendarViewModel.userCalendars.value?.let { userCalendars ->
                    refreshUserCalendarList(userCalendars.filter { it.isActive || it.isDisabled })
                }
            }
        }

        calendarFormViewModel.calendarSettingsSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { calendarSettingsSnackState ->
            calendarSettingsSnackState?.let {
                when (it) {
                    is CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp -> {
                        view?.displaySnackBar(it.message)

                        findNavController().navigateUp()
                    }
                    else -> { } // We do not use the other values
                }
                calendarFormViewModel.calendarSettingsSnackState.value = null
            }
        }
    }

    /**
     * @param userCalendars updated user calendar list
     * Refreshes the user calendar list with the new set of data. Get the emails linked to each calendar and
     * get the current default calendar id. Sort the list by following order: default / active / disabled.
     */
    private fun refreshUserCalendarList(userCalendars: List<CalendarEntity>) {
        lifecycleScope.launch {
            val calendarEmails = hashMapOf<String, String>()
            userCalendars.forEach { userCalendar ->
                val calendarEmail = eventViewModel.getCalendarEmail(userCalendar.id)
                calendarEmail?.let {
                    calendarEmails[userCalendar.id] = it
                }
            }
            val defaultCalendarId = calendarViewModel.getDefaultCalendarId()
            var dataSetChanged = false
            defaultCalendarId?.let {
                this@SettingsFragment.defaultCalendarId = defaultCalendarId
                dataSetChanged = settingsUserCalendarListAdapter.setDefaultCalendarId(defaultCalendarId)
            }
            settingsUserCalendarListAdapter.setCalendarEmails(calendarEmails)
            settingsUserCalendarListAdapter.submitList(
                userCalendars.sortedBy {
                    it.isDisabled // Disabled will appear last
                }.sortedByDescending {
                    it.id == defaultCalendarId // Default will appear first
                }
            )
            if (dataSetChanged) settingsUserCalendarListAdapter.notifyDataSetChanged()
        }
    }

    /**
     * Displays the bottom sheet dialog with the calendar name as a header, and the following button as a content:
     * Edit, Mark as default, Delete.
     * Buttons visibility varies with the calendar type and status.
     */
    private fun showBottomSheetDialog(calendarEntity: CalendarEntity) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            val navigationBarBackgroundColor = R.color.background_navigation_bar
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        }

        bottomSheetDialog.setContentView(R.layout.dialog_calendar_settings)

        val calendarIcon = bottomSheetDialog.findViewById<ImageView>(R.id.dialog_calendar_settings_calendar_icon)
        calendarIcon?.imageTintList = ColorStateList.valueOf(Color.parseColor(calendarEntity.color))

        val calendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_settings_calendar_title)
        calendarName?.text = calendarEntity.name

        val editPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_edit_press)
        val markDefaultPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_default_press)
        val deletePress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_delete_press)

        editPress?.setOnSingleClickListener {
            val bundle = Bundle()
            bundle.putString(CALENDAR_ID_ARG, calendarEntity.id)
            findNavController().navigate(R.id.action_nav_settings_to_nav_calendar_form, bundle)
            bottomSheetDialog.dismiss()
        }

        markDefaultPress?.setOnSingleClickListener {
            lifecycleScope.launch {
                val updateDefaultCalendarId = calendarViewModel.updateDefaultCalendarId(calendarEntity.id)
                if (updateDefaultCalendarId) {
                    calendarViewModel.userCalendars.value?.let { userCalendars ->
                        refreshUserCalendarList(userCalendars.filter { it.isActive || it.isDisabled })
                    }
                    view?.displaySnackBar(requireContext().getString(R.string.snack_update_default_calendar))
                } else {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_update_default_calendar_error))
                }
            }
            bottomSheetDialog.dismiss()
        }

        deletePress?.setOnSingleClickListener {
            bottomSheetDialog.dismiss()
        }

        val deleteLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_delete)
        deleteLayout?.visibleOrGone(DELETE_CALENDAR)

        val markAsDefaultLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_default)
        markAsDefaultLayout?.visibleOrGone(calendarEntity.id != defaultCalendarId && calendarEntity.isActive && calendarEntity.isSubscribed.not())

        bottomSheetDialog.show()
    }
}
