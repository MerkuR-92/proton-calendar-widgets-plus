package me.proton.android.calendar.presentation.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.CalendarForm.CALENDAR_NAME_CHARACTER_LIMIT
import me.proton.android.calendar.common.CalendarForm.DEFAULT_NOTIFICATIONS_COUNT_MAX
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.util.kotlin.all
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.ZonedDateTime

class CalendarFormFragment : BaseDialogFragment(), KoinComponent {

    private val navigationArguments: CalendarFormFragmentArgs by navArgs()

    override val TAG: String
        get() = "CalendarFormFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_calendar_form

    override val navigateUp = true

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private val logger: Logger by inject()
    private val json: Json by inject()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private var calendarId: String? = null

    private var selectedColor: String? = null // TODO Maybe turn into livedata in VM and observe to update form icon
    private var selectedDefaultEventDuration: String? = null // TODO Maybe turn into livedata in VM and observe to update form icon
    private var defaultPartDayAlarms: ArrayList<VAlarm> = arrayListOf()
    private var defaultAllDayAlarms: ArrayList<VAlarm> = arrayListOf()

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
            calendar_form_color_icon?.imageTintList = ColorStateList.valueOf(Color.parseColor(calendarEntity.color))
            selectedColor = calendarEntity.color // TODO Use LiveData ?

            // Default event duration
            calendar_form_default_event_duration_value.text = getString(R.string.calendar_form_default_event_duration_value, calendarSettings.defaultEventDuration.toString())
            selectedDefaultEventDuration = calendarSettings.defaultEventDuration.toString()

            // Default part day event notifications
            defaultPartDayAlarms = setDefaultAlarms(calendarSettings.defaultPartDayNotifications)
            displayNotifications(allDay = false)

            // Default all day event notifications
            defaultAllDayAlarms = setDefaultAlarms(calendarSettings.defaultFullDayNotifications)
            displayNotifications(allDay = true)
        }
    }

    private fun setDefaultAlarms(defaultNotifications: List<JsonElement>): ArrayList<VAlarm> {
        val alarms = ArrayList<VAlarm>()
        defaultNotifications.mapNotNull {
            if ((it as? JsonObject) != null) json.decodeFromJsonElement<CalendarSettingsEntity.AlarmEntity>(
                it
            ) else null
        }.forEach { alarm ->
            alarm.parseTrigger()?.let {
                if (alarm.type == 0) {
                    alarms.add(VAlarm.email(it, null, null))
                } else {
                    alarms.add(VAlarm.display(it, null))
                }
            }
        }
        return alarms
    }

    private fun displayNotifications(allDay: Boolean) {
        (if (allDay) calendar_form_default_all_day_event_notifications_list else calendar_form_default_event_notifications_list)
            .removeAllViews()
        (if (allDay) calendar_form_default_all_day_event_notifications_icon else calendar_form_default_event_notifications_icon)
            .visibleOrGone(true)

        val alarms = if (allDay) defaultAllDayAlarms else defaultPartDayAlarms
        alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(
                R.layout.item_alarm_text_button,
                (if (allDay) calendar_form_default_all_day_event_notifications_list else calendar_form_default_event_notifications_list),
                false
            )
            alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                text = AndroidUtils.formatAlarm(
                    resources,
                    allDay,
                    calendarViewModel.timeFormatIs24Hour(requireContext()),
                    ZonedDateTime.now(),
                    alarm
                )
                isClickable = false
            }
            alarmView.findViewById<View>(R.id.item_simple_text_button_delete).apply {
                setOnSingleClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    (if (allDay) defaultAllDayAlarms else defaultPartDayAlarms).removeAt(index)
                    (if (allDay) calendar_form_default_all_day_event_notifications_list else calendar_form_default_event_notifications_list)
                        .removeView(alarmView)
                }
                isClickable = true
            }
            if (index == 0) {
                (if (allDay) calendar_form_default_all_day_event_notifications_icon else calendar_form_default_event_notifications_icon)
                    .visibleOrGone(false)
            }
            (if (allDay) calendar_form_default_all_day_event_notifications_list else calendar_form_default_event_notifications_list)
                .addView(alarmView)
        }

        // "add alarm" button
        (if (allDay) calendar_form_default_all_day_event_notifications_press else calendar_form_default_event_notifications_press)
            .setOnSingleClickListener {
                requireActivity().clearFocusAndHideKeyboard(view)

                // TODO Navigate to alarm form
            }
        (if (allDay) calendar_form_default_all_day_event_notifications else calendar_form_default_event_notifications)
            .visibleOrGone(
                (if (allDay) defaultAllDayAlarms else defaultPartDayAlarms).size < DEFAULT_NOTIFICATIONS_COUNT_MAX
            )
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
            calendarColorPickerGridView.adapter = CalendarColorListAdapter(calendarColors.toList(), selectedColor) { calendarColor ->
                selectedColor = calendarColor // TODO Remove once selectedColor is livedata
                calendar_form_color_icon?.imageTintList = ColorStateList.valueOf(Color.parseColor(calendarColor)) // TODO Remove once selectedColor is livedata
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
                CalendarForm.EVENT_DEFAULT_DURATION.indexOf(selectedDefaultEventDuration)
            ) { _, item ->
                selectedDefaultEventDuration = CalendarForm.EVENT_DEFAULT_DURATION[item] // TODO Remove once selectedDefaultEventDuration is livedata
                calendar_form_default_event_duration_value.text = getString(
                    R.string.calendar_form_default_event_duration_value, CalendarForm.EVENT_DEFAULT_DURATION[item] // TODO Remove once selectedDefaultEventDuration is livedata
                )
                dialog?.dismiss()
            }.show()
        }
    }

    private fun handleInitError(message: String) {
        logger.e("Init calendar form error: $message")
        requireActivity().displaySnackBar(getString(R.string.snack_calendar_init_error))
        findNavController().navigateUp()
    }
}
