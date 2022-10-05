package me.proton.android.calendar.presentation.settings.fragment

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_settings.settings_calendars_list
import kotlinx.android.synthetic.main.fragment_settings.settings_calendars_list_add_layout
import kotlinx.android.synthetic.main.fragment_settings.settings_calendars_list_add_layout_press
import kotlinx.android.synthetic.main.fragment_settings.settings_general_info
import kotlinx.android.synthetic.main.fragment_settings.settings_general_press
import kotlinx.android.synthetic.main.fragment_settings.settings_import
import kotlinx.android.synthetic.main.fragment_settings.settings_import_press
import kotlinx.android.synthetic.main.fragment_settings.settings_import_separator
import kotlinx.android.synthetic.main.fragment_settings.settings_subscribed_calendars
import kotlinx.android.synthetic.main.fragment_settings.settings_subscribed_calendars_list
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.FeatureFlag.CHANGE_LANGUAGE
import me.proton.android.calendar.common.FeatureFlag.DELETE_CALENDAR
import me.proton.android.calendar.common.FragmentArguments.CALENDAR_ID_ARG
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.usecase.DeleteCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.settings.adapter.SettingsCalendarListAdapter
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject

@AndroidEntryPoint
class SettingsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "SettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_settings

    override val navigateUp = true

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val calendarFormViewModel: CalendarFormViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val resourceProvider: ResourceProvider by inject()

    private lateinit var settingsUserCalendarListAdapter: SettingsCalendarListAdapter
    private lateinit var settingsSubscribedCalendarListAdapter: SettingsCalendarListAdapter

    private val subscribedCalendarsMediator = MediatorLiveData<Pair<List<Calendar>, List<CalendarSubscriptionEntity>>>()
    private var subscribedCalendars: List<Calendar>? = null
    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null

    private var defaultCalendarId: String? = null

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_settings)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings_general_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_general_settings)
        }

        lifecycleScope.launch {
            val displayImport = calendarViewModel.displayImport()
            settings_import.visibleOrGone(displayImport)
            settings_import_separator.visibleOrGone(displayImport)
        }
        settings_import_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_import_assistant_guide)
        }

        // Build the general settings description
        var generalSettingsDescription = getString(
            R.string.settings_general_info_separator,
            getString(R.string.settings_general_info_time_zone),
            getString(R.string.settings_general_info_calendar_layout)
        )
        if (CHANGE_LANGUAGE) {
            generalSettingsDescription = getString(
                R.string.settings_general_info_separator,
                getString(R.string.settings_general_info_language),
                generalSettingsDescription
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            generalSettingsDescription = getString(
                R.string.settings_general_info_separator,
                getString(R.string.settings_general_info_theme),
                generalSettingsDescription
            )
        }
        settings_general_info.text = getString(R.string.settings_general_info, generalSettingsDescription).replaceFirstChar {
            it.titlecase(DateTimeUtilsImpl.getLocaleForFormatting())
        }

        settings_calendars_list_add_layout_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_calendar_form)
        }

        val settingsCalendarListView = settings_calendars_list
        val settingsCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsCalendarListView.layoutManager = settingsCalendarLayoutManager
        settingsUserCalendarListAdapter = SettingsCalendarListAdapter() { calendar ->
            //On Calendar click event
            showBottomSheetDialog(calendar)
        }
        (settingsCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        settingsCalendarListView.adapter = settingsUserCalendarListAdapter

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
            userCalendars ?: return@observe

            lifecycleScope.launch {
                settings_calendars_list_add_layout.visibleOrGone(
                    calendarViewModel.isUserCalendarLimitReached(userCalendars) == CalendarViewModel.UserCalendarLimit.NOT_REACHED
                )
            }
            refreshUserCalendarList(userCalendars.filter { it.isActive || it.isDisabled })
        }

        val settingsSubscribedCalendarListView = settings_subscribed_calendars_list
        val settingsSubscribedCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsSubscribedCalendarListView.layoutManager = settingsSubscribedCalendarLayoutManager
        settingsSubscribedCalendarListAdapter = SettingsCalendarListAdapter() { calendar ->
            //On Calendar click event
            showBottomSheetDialog(calendar)
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

                    settingsSubscribedCalendarListAdapter.submitList(subscribedCalendars)
                    if (dataSetChanged) settingsSubscribedCalendarListAdapter.notifyDataSetChanged()
                    settings_subscribed_calendars.visibleOrGone(subscribedCalendars.isNotEmpty())
                }
            }
        }

        calendarViewModel.defaultCalendarId.observe(viewLifecycleOwner) { defaultCalendarId ->

            if (this@SettingsFragment.defaultCalendarId != defaultCalendarId) {
                lifecycleScope.launch {
                    calendarViewModel.getUserCalendars()?.let { userCalendars ->
                        refreshUserCalendarList(userCalendars.filter { it.isActive || it.isDisabled })
                    }
                }
            }
        }

        calendarFormViewModel.calendarSettingsSnackState.asLiveData(lifecycleScope.coroutineContext).observe(viewLifecycleOwner) { calendarSettingsSnackState ->
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
    private fun refreshUserCalendarList(userCalendars: List<Calendar>) {
        lifecycleScope.launch {
            var defaultCalendarId = calendarViewModel.getDefaultCalendarId()
            val defaultCalendar = userCalendars.firstOrNull { it.id == defaultCalendarId }
            if (defaultCalendar == null || !defaultCalendar.isActive || !defaultCalendar.isOwner) {
                defaultCalendarId = userCalendars.firstOrNull { it.isActive && it.isOwner }?.id
            }
            this@SettingsFragment.defaultCalendarId = defaultCalendarId
            val dataSetChanged: Boolean = settingsUserCalendarListAdapter.setDefaultCalendarId(defaultCalendarId)
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
    private fun showBottomSheetDialog(calendar: Calendar) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window?.navigationBarColor = requireContext().getColorFromAttr(
                R.attr.proton_background_norm
            )
        }

        bottomSheetDialog.setContentView(R.layout.dialog_calendar_settings)

        val calendarIcon = bottomSheetDialog.findViewById<ImageView>(R.id.dialog_calendar_settings_calendar_icon)
        calendarIcon?.imageTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))

        val calendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_settings_calendar_title)
        calendarName?.text = calendar.name

        val editPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_edit_press)
        val markDefaultPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_default_press)
        val deletePress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_delete_press)

        editPress?.setOnSingleClickListener {
            val bundle = Bundle().apply {
                putString(CALENDAR_ID_ARG, calendar.id)
            }
            findNavController().navigate(R.id.action_nav_settings_to_nav_calendar_form, bundle)
            bottomSheetDialog.dismiss()
        }

        markDefaultPress?.setOnSingleClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                bottomSheetDialog.dismiss()
                view?.displaySnackBar(requireContext().getString(R.string.snack_network_error))
                return@setOnSingleClickListener
            }

            lifecycleScope.launch {
                val updateDefaultCalendarId = calendarViewModel.updateDefaultCalendarId(calendar.id)
                if (updateDefaultCalendarId) {
                    calendarViewModel.getUserCalendars()?.let { userCalendars ->
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
            lifecycleScope.launch {
                val prepareOption = calendarViewModel.prepareDeleteCalendar(calendar.id)

                val dialogMessage = when (prepareOption) {
                    is DeleteCalendarUseCase.DeleteCalendarOption.Delete.DefaultLastActive -> resourceProvider.provideString(R.string.delete_calendar_dialog_message)
                    is DeleteCalendarUseCase.DeleteCalendarOption.Delete.DefaultNextActive -> resourceProvider.provideString(R.string.delete_default_calendar_dialog_message, prepareOption.nextDefaultName)
                    is DeleteCalendarUseCase.DeleteCalendarOption.Error -> null
                    is DeleteCalendarUseCase.DeleteCalendarOption.Delete.NonDefault -> resourceProvider.provideString(R.string.delete_calendar_dialog_message)
                }

                if (prepareOption is DeleteCalendarUseCase.DeleteCalendarOption.Error) {
                    bottomSheetDialog.dismiss()
                    view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error))
                } else {
                    bottomSheetDialog.dismiss()
                    with (MaterialAlertDialogBuilder(requireContext())) {
                        setTitle(resourceProvider.provideString(R.string.delete_calendar_dialog_title))
                        setMessage(dialogMessage)
                        setPositiveButton(R.string.dialog_button_delete, object : DialogInterface.OnClickListener {
                            override fun onClick(p0: DialogInterface?, p1: Int) {
                                lifecycleScope.launch {
                                    val deleteResult = calendarViewModel.deleteCalendar(prepareOption)
                                    when (deleteResult) {
                                        is UseCase.Result.Error -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error))
                                        is UseCase.Result.InvalidParams -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error_password_confirmation))
                                        is UseCase.Result.Success<*> -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_deleted))
                                    }
                                    bottomSheetDialog.dismiss()
                                }
                            }
                        })
                        setNegativeButton(R.string.dialog_button_cancel, null)
                    }.create().show()
                }
            }
        }

        val deleteLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_delete)
        deleteLayout?.visibleOrGone(DELETE_CALENDAR && calendar.isSubscribed.not() && calendar.isSharedWithMe.not())

        val markAsDefaultLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_default)
        markAsDefaultLayout?.visibleOrGone(calendar.id != defaultCalendarId && calendar.isActive && calendar.isSubscribed.not() && calendar.isSharedWithMe.not())

        bottomSheetDialog.show()
    }
}
