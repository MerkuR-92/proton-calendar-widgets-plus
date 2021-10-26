package me.proton.android.calendar.presentation.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.component.VAlarm
import biweekly.property.Action
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_calendar_color_picker.*
import kotlinx.android.synthetic.main.dialog_calendar_color_picker.view.*
import kotlinx.android.synthetic.main.dialog_checkbox.view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_calendar_form.*
import kotlinx.android.synthetic.main.fragment_event_form.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.android.synthetic.main.item_calendar_color_picker.view.*
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.CalendarForm.CALENDAR_NAME_CHARACTER_LIMIT
import me.proton.android.calendar.common.CalendarForm.DEFAULT_NOTIFICATIONS_COUNT_MAX
import me.proton.android.calendar.common.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

class CalendarFormFragment : BaseDialogFragment(), KoinComponent {

    private val navigationArguments: CalendarFormFragmentArgs by navArgs()

    override val TAG: String
        get() = "CalendarFormFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_calendar_form

    override val navigateUp = true

    private val calendarFormViewModel: CalendarFormViewModel by sharedViewModel()
    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private val logger: Logger by inject()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private var calendarId: String? = null

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialog_toolbar_content, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(
                if (navigationArguments.calendarId != null) R.string.calendar_form_update
                else R.string.calendar_form_create
            )
            setOnSingleClickListener {
                // TODO On save click
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

        calendarId = navigationArguments.calendarId

        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text =
            if (calendarId != null) getString(R.string.calendar_form_update_title)
            else getString(R.string.calendar_form_create_title)

        calendarId?.let {
            initUpdateCalendarForm(it)
        } ?: run {
            initCreateCalendarForm()
        }

        initOnClickListeners()

        calendar_form_name_input.doAfterTextChanged {
            calendar_form_name_character_limit.text = getString(R.string.calendar_form_name_character_limit, it?.length, CALENDAR_NAME_CHARACTER_LIMIT)
        }

        observeCalendarFormSnackState(coroutineContext)

        calendarFormViewModel.defaultEventDuration.observe(viewLifecycleOwner) { defaultEventDuration ->
            calendar_form_default_event_duration_value.text = getString(R.string.calendar_form_default_event_duration_value, defaultEventDuration.toString())
        }
        calendarFormViewModel.calendarColor.observe(viewLifecycleOwner) { calendarColor ->
            calendar_form_color_icon?.imageTintList = ColorStateList.valueOf(Color.parseColor(calendarColor))
        }
        calendarFormViewModel.defaultPartDayAlarms.observe(viewLifecycleOwner) { defaultPartDayAlarms ->
            calendar_form_default_event_notifications.visibleOrGone(defaultPartDayAlarms.size < DEFAULT_NOTIFICATIONS_COUNT_MAX)
            displayNotifications(
                allDay = false,
                defaultPartDayAlarms,
                calendar_form_default_event_notifications_list,
                calendar_form_default_event_notifications_icon,
                calendar_form_default_event_notifications_press
            )
        }
        calendarFormViewModel.defaultAllDayAlarms.observe(viewLifecycleOwner) { defaultAllDayAlarms ->
            calendar_form_default_all_day_event_notifications.visibleOrGone(defaultAllDayAlarms.size < DEFAULT_NOTIFICATIONS_COUNT_MAX)
            displayNotifications(
                allDay = true,
                defaultAllDayAlarms,
                calendar_form_default_all_day_event_notifications_list,
                calendar_form_default_all_day_event_notifications_icon,
                calendar_form_default_all_day_event_notifications_press
            )
        }
    }

    private fun initUpdateCalendarForm(calendarId: String) {
        lifecycleScope.launch {
            val calendarEntity = calendarViewModel.getCalendarEntity(calendarId) ?: run {
                handleInitError("CalendarEntity was null in initUpdateCalendarForm")
                return@launch
            }

            val calendarSettings = calendarViewModel.getCalendarSettings(calendarId) ?: run {
                handleInitError("CalendarSettings was null in initUpdateCalendarForm")
                return@launch
            }

            val calendarEmail = eventViewModel.getCalendarEmail(calendarId)

            // Calendar name
            calendar_form_name_input.setText(calendarEntity.name)
            calendar_form_name_character_limit.text = getString(R.string.calendar_form_name_character_limit, calendarEntity.name.length, CALENDAR_NAME_CHARACTER_LIMIT)

            // Calendar default email
            calendar_form_default_email_value.text = calendarEmail ?: getString(R.string.calendar_form_default_email_value_error)

            // Calendar color
            calendarFormViewModel.handleCalendarColor(calendarEntity.color)

            // Default event duration
            calendarFormViewModel.handleDefaultEventDuration(calendarSettings.defaultEventDuration)

            // Default part day event notifications
            calendarFormViewModel.setDefaultAlarms(calendarSettings.defaultPartDayNotifications, isAllDay = false)

            // Default all day event notifications
            calendarFormViewModel.setDefaultAlarms(calendarSettings.defaultFullDayNotifications, isAllDay = true)
        }
    }

    private fun displayNotifications(
        allDay: Boolean,
        alarms: List<VAlarm>,
        alarmsListView: ViewGroup,
        notificationIcon: View,
        itemViewPress: View
    ) {
        alarmsListView.removeAllViews()
        notificationIcon.visibleOrGone(true)

        alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(
                R.layout.item_alarm_text_button,
                alarmsListView,
                false
            )
            alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                text = AndroidUtils.formatAlarm(
                    resources,
                    allDay,
                    calendarViewModel.timeFormatIs24Hour(requireContext()),
                    LocalDate.now().toDate(ZoneId.systemDefault().id).toZonedDateTime(ZoneId.systemDefault().id, false), // TODO Simplify this
                    alarm
                )
                isClickable = false
            }
            alarmView.findViewById<View>(R.id.item_simple_text_button_delete).apply {
                setOnSingleClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    calendarFormViewModel.handleAlarmChange(alarm, allDay, isDelete = true)
                }
                isClickable = true
            }
            if (index == 0) {
                notificationIcon.visibleOrGone(false)
            }
            alarmsListView.addView(alarmView)
        }

        // "add alarm" button
        itemViewPress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val bundle = Bundle()
            bundle.putBoolean(FragmentArguments.IS_ALL_DAY_ARG, allDay)
            bundle.putBoolean(FragmentArguments.IS_CALENDAR_DEFAULT_EVENT_NOTIFICATION_ARG, true)
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
    }

    private fun initCreateCalendarForm() {
        lifecycleScope.launch {
        }
    }

    private fun initOnClickListeners() {

        // Calendar color
        calendar_form_color_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            var dialog: AlertDialog? = null

            // Get calendar color list
            val calendarColors = resources.getStringArray(R.array.calendar_colors)

            // Get dialog custom view
            val view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_calendar_color_picker, null, false)

            // Set grid view with color list
            val calendarColorPickerGridView = view.dialog_calendar_color_picker_layout
            calendarColorPickerGridView.adapter = CalendarColorListAdapter(calendarColors.toList(), calendarFormViewModel.calendarColor.value) { calendarColor ->
                calendarFormViewModel.handleCalendarColor(calendarColor)
                dialog?.dismiss()
            }

            // Display dialog
            dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .show()
        }

        // Default event duration
        calendar_form_default_event_duration_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            var dialog: AlertDialog? = null
            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
            dialog = builder.setSingleChoiceItems(
                CalendarForm.EVENT_DEFAULT_DURATION.toTypedArray(),
                calendarFormViewModel.defaultEventDuration.value?.let { CalendarForm.EVENT_DEFAULT_DURATION.indexOf(it.toString()) } ?: 0
            ) { _, item ->
                calendarFormViewModel.handleDefaultEventDuration(CalendarForm.EVENT_DEFAULT_DURATION[item].toInt())
                dialog?.dismiss()
            }.show()
        }
    }

    private fun handleInitError(message: String) {
        logger.e("Init calendar form error: $message")
        requireActivity().displaySnackBar(getString(R.string.snack_calendar_init_error))
        findNavController().navigateUp()
    }

    private fun observeCalendarFormSnackState(coroutineContext: CoroutineContext) {
        calendarFormViewModel.calendarFormSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { calendarFormSnackState ->
            calendarFormSnackState?.let {
                when (it) {
                    is CalendarFormViewModel.CalendarFormSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is CalendarFormViewModel.CalendarFormSnackState.DisplaySnackReturnToMonth -> {
                        requireActivity().displaySnackBar(it.message)

                        // Use jumpToMonthView to handle navigation when opening details from notification
                        onBackPressedCustom()
                    }
                }
                calendarFormViewModel.calendarFormSnackState.value = null
            }
        }
    }
}
