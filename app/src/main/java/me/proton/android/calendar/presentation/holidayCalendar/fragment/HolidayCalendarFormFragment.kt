package me.proton.android.calendar.presentation.holidayCalendar.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.component.VAlarm
import biweekly.property.Action
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.dialog_calendar_color_picker.view.dialog_calendar_color_picker_layout
import kotlinx.android.synthetic.main.fragment_base_dialog.dialog_toolbar_content
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_color_icon
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_color_press
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_country_flag
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_country_search_disclaimer
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_country_value
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_country_value_press
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_default_all_day_event_notifications
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_default_all_day_event_notifications_icon
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_default_all_day_event_notifications_list
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_default_all_day_event_notifications_press
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_language_press
import kotlinx.android.synthetic.main.fragment_holiday_calendar_form.holiday_calendar_form_language_value
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.holidayCalendar.viewModel.HolidayCalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.settings.adapter.CalendarColorListAdapter
import me.proton.core.presentation.utils.currentLocale
import org.koin.core.KoinComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class HolidayCalendarFormFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "HolidayCalendarFormFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_holiday_calendar_form

    override val navigateUp = false
    override val isScrollable = false

    private val navigationArguments: HolidayCalendarFormFragmentArgs by navArgs()

    private val mainViewModel: MainViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val holidayCalendarViewModel: HolidayCalendarViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private var calendarId: String? = null

    override fun onBackPressedCustom() {
        requireActivity().clearFocusAndHideKeyboard(view)

        // Display snack and return if we're saving the calendar changes
        val processingCalendar = holidayCalendarViewModel.holidayCalendarState.value is HolidayCalendarViewModel.HolidayCalendarState.Processing
        if (processingCalendar) {
            view?.displaySnackBar(getString(R.string.snack_calendar_saving))
            return
        }

        // Check if we need to display discard changes dialog
        if (holidayCalendarViewModel.hasBeenEdited()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.event_discard_changes_title)
                .setMessage(R.string.event_discard_changes_description)
                .setPositiveButton(R.string.event_discard_changes_confirm) { _, _ ->
                    findNavController().navigateUp()
                }
                .setNegativeButton(R.string.event_discard_changes_cancel) { _, _ -> }
                .show()
        } else findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = getString(R.string.holiday_calendar_title)

        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialog_toolbar_content, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(
                R.string.action_save
            )
            setOnSingleClickListener {

                requireActivity().clearFocusAndHideKeyboard(view)

                if (!mainViewModel.isConnectedToNetwork) {
                    view?.displaySnackBar(getString(R.string.snack_network_error))
                    return@setOnSingleClickListener
                }

                lifecycleScope.launch {
                    if (holidayCalendarViewModel.hasBeenEdited() || calendarId.isNullOrEmpty()) {
                        val selectedDate = calendarViewModel.selectedDateTime.value
                        // Save new form values
                        val returnToSettings = findNavController().previousBackStackEntry?.destination?.id == R.id.nav_settings || calendarId != null
                        if (holidayCalendarViewModel.handleSaveHolidayCalendar(returnToSettings, selectedDate?.first)) {
                            view?.displaySnackBar(resources.getString(R.string.snack_create_calendar_success))
                            findNavController().navigateUp()
                        } else {
                            view?.displaySnackBar(resources.getString(R.string.snack_create_calendar_error))
                        }
                    } else findNavController().navigateUp()
                }
            }
        }

        loadingAction = layoutInflater.inflate(R.layout.toolbar_action_loader, dialog_toolbar_content, false)
        loadingAction.visibleOrGone(false)

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            addView(
                buttonSave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                loadingAction, layoutParams
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        holidayCalendarViewModel.resetValues()

        calendarId = navigationArguments.calendarId

        lifecycleScope.launch {
            calendarId?.let {
                // Hide disclaimer based on location
                holiday_calendar_form_country_search_disclaimer.visibleOrGone(false)
                // Init form for existing calendar
                holidayCalendarViewModel.initUpdateHolidayCalendar(it)
            } ?: run {
                // Display disclaimer based on location
                holiday_calendar_form_country_search_disclaimer.visibleOrGone(true)
                // Use random color from array as calendar color
                val calendarColors = resources.getIntArray(R.array.accent_colors_base)
                // Init form for new calendar
                holidayCalendarViewModel.initCreateHolidayCalendar(
                    calendarColors[(0..calendarColors.lastIndex).random()],
                    requireContext().resources.configuration.currentLocale().language.lowercase()
                )
            }
        }

        initOnClickListeners()

        observeHolidayCalendarFormValues()

        observeHolidayCalendarSnackState(lifecycleScope.coroutineContext)
    }

    private fun observeHolidayCalendarFormValues() {

        holidayCalendarViewModel.country.observe(viewLifecycleOwner) { country ->
            holiday_calendar_form_country_value.text = country
            if (holidayCalendarViewModel.hasBeenEdited()) {
                // Hide disclaimer based on location
                holiday_calendar_form_country_search_disclaimer.visibleOrGone(false)
            }
            val countryCode = holidayCalendarViewModel.holidayCalendars.value?.firstOrNull { it.country == country }?.countryCode
            holiday_calendar_form_country_flag.setImageResource(
                resources.getIdentifier(
                    "${requireContext().packageName}:drawable/flag_$countryCode",
                    "drawable",
                    requireContext().packageName
                )
            )
        }

        holidayCalendarViewModel.language.observe(viewLifecycleOwner) { language ->
            if (language.isNullOrEmpty()) return@observe
            holiday_calendar_form_language_value.text = language
        }

        holidayCalendarViewModel.calendarColor.observe(viewLifecycleOwner) { calendarColor ->
            if (calendarColor == 0) {
                // Value was reset. Set to background_norm to avoid seeing the color being refreshed
                holiday_calendar_form_color_icon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.background_norm))
                return@observe
            }

            holiday_calendar_form_color_icon?.imageTintList = ColorStateList.valueOf(calendarColor)
        }

        holidayCalendarViewModel.defaultAllDayAlarms.observe(viewLifecycleOwner) { defaultAllDayAlarms ->
            holiday_calendar_form_default_all_day_event_notifications.visibleOrGone(defaultAllDayAlarms.size < CalendarForm.DEFAULT_NOTIFICATIONS_COUNT_MAX)
            displayNotifications(
                defaultAllDayAlarms,
                holiday_calendar_form_default_all_day_event_notifications_list,
                holiday_calendar_form_default_all_day_event_notifications_icon,
                holiday_calendar_form_default_all_day_event_notifications_press
            )
        }

        holidayCalendarViewModel.holidayCalendarState.asLiveData(lifecycleScope.coroutineContext).observe(viewLifecycleOwner) { eventState ->
            val processingEvent = eventState is HolidayCalendarViewModel.HolidayCalendarState.Processing

            // Update action bar buttons visibility
            loadingAction.visibleOrGone(processingEvent)
            buttonSave.visibleOrGone(!processingEvent)

            // Disable/Enable all items linked to actions from our view
            holiday_calendar_form_country_value_press.isEnabled = !processingEvent
            holiday_calendar_form_color_press.isEnabled = !processingEvent

            holiday_calendar_form_default_all_day_event_notifications_press.isEnabled = !processingEvent
            for (i in 0 until holiday_calendar_form_default_all_day_event_notifications_list.childCount) {
                // Disable the delete buttons from inside alarm items views
                holiday_calendar_form_default_all_day_event_notifications_list.getChildAt(i)
                    .findViewById<View>(R.id.item_simple_text_button_delete).isEnabled = !processingEvent
            }
        }
    }

    /**
     * Display notifications list and register listeners for add / remove notifications.
     */
    private fun displayNotifications(
        alarms: List<VAlarm>,
        alarmsListView: ViewGroup,
        notificationIcon: View,
        itemViewPress: View
    ) {
        alarmsListView.removeAllViews()
        notificationIcon.visibleOrGone(true)

        lifecycleScope.launch {
            alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

                val alarmView = layoutInflater.inflate(
                    R.layout.item_alarm_text_button,
                    alarmsListView,
                    false
                )
                alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                    text = AndroidUtils.formatAlarm(
                        resources,
                        true,
                        calendarViewModel.timeFormatIs24Hour(requireContext()),
                        LocalDate.now().toDate(ZoneId.systemDefault().id).toZonedDateTime(ZoneId.systemDefault().id, false), // TODO Simplify this
                        alarm
                    )
                    isClickable = false
                }
                alarmView.findViewById<View>(R.id.item_simple_text_button_delete).apply {
                    // Remove notification listener
                    setOnSingleClickListener {
                        requireActivity().clearFocusAndHideKeyboard(view)
                        holidayCalendarViewModel.handleAlarmChange(alarm, isDelete = true)
                    }
                    isClickable = true
                }
                if (index == 0) notificationIcon.visibleOrGone(false)
                alarmsListView.addView(alarmView)
            }
        }

        // Add notification listener
        itemViewPress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val bundle = Bundle().apply {
                putBoolean(FragmentArguments.IS_ALL_DAY_ARG, true)
                putInt(FragmentArguments.DEFAULT_NOTIFICATIONS_TYPE_ARG, 2)
            }
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
    }

    private fun initOnClickListeners() {

        // Country
        holiday_calendar_form_country_value_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_holiday_calendar_form_to_nav_holiday_calendar_search)
        }

        // Calendar language
        holiday_calendar_form_language_press.setOnSingleClickListener {
            val languages = holidayCalendarViewModel.getLanguages()
            AndroidUtils.displayPickerDialog(
                requireContext(),
                null,
                languages.toTypedArray(),
                holidayCalendarViewModel.language.value?.let { language ->
                    languages.indexOf(language)
                } ?: 0
            ) {
                holidayCalendarViewModel.handleLanguage(languages[it])
            }
        }

        // Calendar color
        holiday_calendar_form_color_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            var dialog: AlertDialog? = null

            // Get calendar color list
            val calendarColors = resources.getIntArray(R.array.accent_colors_base)

            // Get dialog custom view
            val view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_calendar_color_picker, null, false)

            // Set grid view with color list
            val calendarColorPickerGridView = view.dialog_calendar_color_picker_layout
            calendarColorPickerGridView.adapter = CalendarColorListAdapter(calendarColors.toList(), holidayCalendarViewModel.calendarColor.value) { calendarColor ->
                holidayCalendarViewModel.handleCalendarColor(calendarColor)
                dialog?.dismiss()
            }

            // Display dialog
            dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setPositiveButton(getString(R.string.dialog_button_close)) { _, _ -> }
                .show()
        }
    }

    private fun observeHolidayCalendarSnackState(coroutineContext: CoroutineContext) {
        holidayCalendarViewModel.holidayCalendarSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { holidayCalendarSnackState ->
            holidayCalendarSnackState?.let {
                when (it) {
                    is HolidayCalendarViewModel.HolidayCalendarSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is HolidayCalendarViewModel.HolidayCalendarSnackState.DisplaySnackNavigateUp -> {
                        requireActivity().displaySnackBar(it.message)

                        findNavController().navigateUp()
                    }
                }
                holidayCalendarViewModel.holidayCalendarSnackState.value = null
            }
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
