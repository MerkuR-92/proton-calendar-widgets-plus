package me.proton.android.calendar.presentation.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form.*
import kotlinx.android.synthetic.main.item_alarm_text_button.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.ZoneId
import java.time.ZonedDateTime


class EventFormFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventFormFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form

    override val navigateUp = false

    private val logger: Logger by inject()

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
//        findNavController().navigate(Navigation.Deeplink.toCalendar())
        findNavController().popBackStack(R.id.nav_calendar, false)
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        val buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, toolbar_content, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(R.string.action_save)
            setOnClickListener {
                onSaveClick()
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.toolbar_content)) {
            addView(
                buttonSave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun onSaveClick() {
        if (eventViewModel.validateDateTime()) {
            lifecycleScope.launch {
                persistFormData()

                if (eventViewModel.hasEventBeenEdited()) {

                    val shouldShowConfirmationPicker = !eventViewModel.isEventNew() && (
                            (eventViewModel.dbEvent?.isRecurring() == true)
                                    ||
                                    (eventViewModel.dbEvent?.isPartOfChain() == true || eventViewModel.isEventPartOfChain())
                            )

                    if (shouldShowConfirmationPicker) {

                        AndroidUtils.displaySingleChoiceConfirmationPicker(requireContext(), getString(R.string.event_text_edit_event), listOfNotNull(
                            getString(R.string.event_recurring_edit_this),
                            if (navigationArguments.occurrenceNumber > 1) getString(R.string.event_recurring_edit_this_and_future) else null,
                            getString(R.string.event_recurring_edit_all_events)
                        ).toTypedArray(), 0) {

                            lifecycleScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    if (it == 0) {
                                        eventViewModel.handleSave(EventEditDeleteOption.THIS_EVENT, navigationArguments.occurrenceNumber)
                                    } else if (it == 1) {
                                        if (navigationArguments.occurrenceNumber == 1) {
                                            eventViewModel.handleSave(EventEditDeleteOption.ALL_EVENTS, navigationArguments.occurrenceNumber)
                                        } else {
                                            eventViewModel.handleSave(EventEditDeleteOption.THIS_EVENT_AND_FUTURE, navigationArguments.occurrenceNumber)
                                        }
                                    } else { // it == 2
                                        eventViewModel.handleSave(EventEditDeleteOption.ALL_EVENTS, navigationArguments.occurrenceNumber)
                                    }
                                }

                                if (success) { // TODO remove duplicated code here and below
                                    onSuccessEventUpdateCalendarDisplay()
                                    Toast.makeText(requireContext(), "Event updated", Toast.LENGTH_SHORT).show()
                                    findNavController().popBackStack(R.id.nav_calendar, false)
                                } else {
                                    Toast.makeText(requireContext(), "Error updating event", Toast.LENGTH_LONG).show()
                                }

                            }
                        }

                    } else { // TODO merge this with code above
                        val success = withContext(Dispatchers.IO) {
                            eventViewModel.handleSave(editOption = null, occurrenceNumber = 1)
                        }

                        if (eventViewModel.eventLiveData.value?.isSyncedWithApi() == true) {
                            if (success) {
                                onSuccessEventUpdateCalendarDisplay()
                                Toast.makeText(requireContext(), "Event updated", Toast.LENGTH_SHORT).show()
//                                    findNavController().navigate(Navigation.Deeplink.toCalendar())
                                findNavController().popBackStack(R.id.nav_calendar, false)
                            } else {
                                Toast.makeText(requireContext(), "Error updating event", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            if (success) {
                                onSuccessEventUpdateCalendarDisplay()
                                Toast.makeText(requireContext(), "Event created", Toast.LENGTH_SHORT).show()
//                                    findNavController().navigate(Navigation.Deeplink.toCalendar())
                                findNavController().popBackStack(R.id.nav_calendar, false)
                            } else {
                                Toast.makeText(requireContext(), "Error creating event", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                } else {
                    findNavController().navigateUp()
                }

            }
        } else {
            AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.event_alert_invalid_start_end_date))
        }
    }

    private fun onSuccessEventUpdateCalendarDisplay() {
        val display = eventViewModel.eventLiveData.value?.calendar?.display
        if (display == null || display) return
        val calendarId = eventViewModel.eventLiveData.value?.calendar?.id
        if (calendarId != null) calendarViewModel.updateServerCalendar(calendarId, display = 1)
    }

    private fun persistFormData() {
//        lifecycleScope.launch {
//
//            with (eventViewModel) {
//                summary = et_summary.text.toString().ifBlank { null }
//                location = event_form_location.text.toString().ifBlank { null }
//                description = event_form_description.text.toString().ifBlank { null }
//            }

//            withContext(Dispatchers.Default) {
//
        eventViewModel.persistRecurrenceFormData(
            event_form_title.text.toString().ifBlank { null },
            event_form_location.text.toString().ifBlank { null },
            event_form_description.text.toString().ifBlank { null }
        )
//            }

        // TODO CREATE EVENT WITHOUT SAVING BEFOREHAND? EXAMPLE CALL -> calendarViewModel.TEST_CREATE_EVENT_TODO()

//        }
    }

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by sharedViewModel() //inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val viewModeInitStatus = withContext(Dispatchers.Default) {
                eventViewModel.initialise(
                    editMode = true,
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    navigationArguments.initStartDate,
                    navigationArguments.initStartTime,
                )
            }

            if (viewModeInitStatus == UseCase.Result.Success) {
                observeEventLiveData()
                attachActionHandlers()
            } else {
                // TODO display error and close? for example when we can't decrypt event
                logger.e((viewModeInitStatus as UseCase.Result.Error).message)
                if (navigationArguments.eventId != null) { // TODO FIXME
                    Toast.makeText(requireContext(), "Error opening event for edit", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Error initialising new event", Toast.LENGTH_LONG).show()
                }
                findNavController().navigateUp()
            }

        }







        // if we need to go back to specific destination, like escaping from editing an event
        //findNavController().popBackStack(123, true)

//        setToolbarTitle(getString(R.string.title_create_event))

//        button.setOnClickListener {
//
//
//
//        }

    }

    // TODO better name
    private fun refreshView() {
//        et_summary.setText(eventViewModel.summary)
//        event_form_location.setText(eventViewModel.location)
//        event_form_description.setText(eventViewModel.description)
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { event: Event ->

            event.summary?.let { event_form_title.setText(it) }
            event.location?.let { event_form_location.setText(it) }
            event.description?.let { event_form_description.setText(it) }

            event_form_all_day_switch.isChecked = event.isAllDay()

            event_form_partial_day_start.visibleOrGone(!event.isAllDay())
            event_form_partial_day_end.visibleOrGone(!event.isAllDay())
            event_form_timezone_layout.visibleOrGone(!event.isAllDay())

            if (eventViewModel.validateDateTime()) {
                event_form_start_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
                event_form_start_time.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
            } else {
                event_form_start_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorValidationError))
                event_form_start_time.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorValidationError))
            }

            val formattedStart = event.formatStart(eventViewModel.eventTimeZoneId)
            event_form_start_date.text = formattedStart.first ?: ""
            event_form_start_time.text = formattedStart.second ?: ""

            val formattedEnd = event.formatEnd(eventViewModel.eventTimeZoneId)
            event_form_end_date.text = formattedEnd.first ?: ""
            event_form_end_time.text = formattedEnd.second ?: ""

            event_form_timezone.text = ICalUtils.formatTimeZoneId(event.defaultTimeZone!!, eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toInstant()!!) // TimeZone picked by user is saved in iCalendar's Default Timezone

            event_form_calendar.text = event.calendar.name
            ImageViewCompat.setImageTintList(event_form_calendar_icon, ColorStateList.valueOf(Color.parseColor(event.calendar.color)))

            event_form_recurrence.text = AndroidUtils.formatRecurrence(requireContext(), event, eventViewModel.eventTimeZoneId) ?: resources.getString(R.string.event_recurrence_none)

            displayAlarms()

        })
    }

    private fun attachActionHandlers() {

        event_form_title.doAfterTextChanged { persistFormData() }
        event_form_location.doAfterTextChanged { persistFormData() }
        event_form_description.doAfterTextChanged { persistFormData() }

        event_form_location.doOnTextChanged { text, start, before, count ->
            if (text.isNullOrEmpty()) {
                ImageViewCompat.setImageTintList(event_form_location_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_weak)))
            } else if (text.length == 1) {
                //Don't update text on every change
                ImageViewCompat.setImageTintList(event_form_location_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm)))
            }
        }
        event_form_description.doOnTextChanged { text, start, before, count ->
            if (text.isNullOrEmpty()) {
                ImageViewCompat.setImageTintList(event_form_description_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_weak)))
            } else if (text.length == 1) {
                //Don't update text on every change
                ImageViewCompat.setImageTintList(event_form_description_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm)))
            }
        }

        event_form_all_day_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            event_form_all_day_switch.performClick()
        }
        event_form_all_day_switch.setOnCheckedChangeListener { _, checked ->
            eventViewModel.handleAllDaySwitch(checked)
        }

        event_form_timezone_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val selectedIndex = allowedTimezoneIds.indexOf(eventViewModel.eventLiveData.value?.defaultTimeZone)
            AndroidUtils.displaySingleChoicePicker(requireContext(), null, allowedTimezoneIds.map { ICalUtils.formatTimeZoneId(it, eventViewModel.eventLiveData.value?.getStart(eventViewModel.displayTimeZoneId)?.toInstant()!!) }.toTypedArray(), selectedIndex) {
                eventViewModel.handleTimeZone(allowedTimezoneIds[it])
            }
        }

        event_form_start_date_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val date = eventViewModel.eventLiveData.value?.getStart(eventViewModel.displayTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(
                context = requireContext(),
                initialDate = date,
                minDate = FormValidation.MIN_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate(),
                maxDate = FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate()) {
                eventViewModel.handleStartDate(it)
            }
        }

        event_form_end_date_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val date = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.displayTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(
                context = requireContext(),
                initialDate = date,
                minDate = FormValidation.MIN_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate(),
                maxDate = FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate()
            ) {
                eventViewModel.handleEndDate(it)
            }
        }

        event_form_start_time_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future
            val time = eventViewModel.eventLiveData.value?.getStart(eventViewModel.displayTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleStartTime(it)
            }
        }

        event_form_end_time_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future
            val time = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.displayTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleEndTime(it)
            }
        }

        event_form_calendar_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            lifecycleScope.launch {
                val calendars = withContext(Dispatchers.Default) {
                    calendarViewModel.getActiveCalendars()
                }

                val selectedIndex = calendars.indexOfFirst { it.id == eventViewModel.eventLiveData.value!!.calendar.id }

                AndroidUtils.displayCalendarPicker(requireContext(), resources.getString(R.string.dialog_title_calendar_picker), calendars.toTypedArray(), selectedIndex) {

                    lifecycleScope.launch {
                        if (!eventViewModel.handleCalendar(calendars[it])) {
                            Toast.makeText(requireContext(), "Error switching calendar", Toast.LENGTH_LONG).show()
                        }
                    }

                }
            }
        }

        event_form_recurrence_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForRecurrence()
            findNavController().navigate(R.id.nav_event_form_recurrence)
        }

        //TODO Attendees
        //press_attendees.setOnClickListener {
        //    findNavController().navigate(R.id.nav_event_form_attendees)
        //}

    }

    private fun displayAlarms() {

        event_form_alarm_list.removeAllViews()
        event_form_alarm_icon.visibleOrGone(true)

        val event = eventViewModel.eventLiveData.value!!

        event.iCalEvent.alarms.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(R.layout.item_alarm_text_button, event_form_alarm_list, false)
            alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), ZoneId.of(eventViewModel.displayTimeZoneId)), alarm)
                isClickable = false
            }
            alarmView.findViewById<View>(R.id.item_simple_text_button_delete).apply {
                setOnClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    eventViewModel.handleAlarmDelete(index)
                }
                isClickable = true
            }
            if (index == 0) {
                alarmView.event_form_alarm_icon.visibleOrGone(true)
                event_form_alarm_icon.visibleOrGone(false)
            }
            event_form_alarm_list.addView(alarmView)
        }

        // "add alarm" button
        event_form_alarm_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForAlarm()
            findNavController().navigate(R.id.nav_event_form_alarm)
        }
        event_form_alarm.visibleOrGone(!eventViewModel.isAlarmLimitReached())

    }


}
