package me.proton.android.calendar.presentation.calendar

import android.animation.LayoutTransition
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.android.synthetic.main.fragment_event_create_edit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.ZonedDateTime


class EventCreateEditFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventCreateEditFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_create_edit

    override val actionMenuResourceId = R.menu.fragment_event_create_edit
    override val navigateUp = false

    override fun onMenuItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.action_menu_save) {

            if (eventViewModel.validateDateTime()) {
                lifecycleScope.launch {
                    persistFormData()

                    val success = withContext(Dispatchers.IO) {
                        eventViewModel.handleSave()
                    }

                    if (success) {
                        Toast.makeText(requireContext(), "Event created", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    } else {
                        Toast.makeText(requireContext(), "Error creating event", Toast.LENGTH_SHORT).show()
                    }

                }
            } else {
                AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.event_alert_invalid_start_end_date))
            }

        }
    }

    private fun persistFormData() {
//        lifecycleScope.launch {
//
//            with (eventViewModel) {
//                summary = et_summary.text.toString().ifBlank { null }
//                location = et_location.text.toString().ifBlank { null }
//                description = et_description.text.toString().ifBlank { null }
//            }

//            withContext(Dispatchers.Default) {
//
                eventViewModel.persistRecurrenceFormData(
                    et_summary.text.toString().ifBlank { null },
                    et_location.text.toString().ifBlank { null },
                    et_description.text.toString().ifBlank { null }
                )
//            }

            // TODO CREATE EVENT WITHOUT SAVING BEFOREHAND? EXAMPLE CALL -> calendarViewModel.TEST_CREATE_EVENT_TODO()

//        }
    }

    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by sharedViewModel() //inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val viewModeInitStatus = withContext(Dispatchers.Default) {
                eventViewModel.initialise(navigationArguments.eventId, navigationArguments.initStartDate, navigationArguments.initStartTime)
            }

            if (viewModeInitStatus == UseCase.Result.Success) {
                observeEventLiveData()
                attachActionHandlers()
            } else {
                // TODO display error and close? for example when we can't decrypt event
                TimberLogger.e((viewModeInitStatus as UseCase.Result.Error).message)
                Toast.makeText(requireContext(), "Error opening event for edit", Toast.LENGTH_LONG).show()
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
//        et_location.setText(eventViewModel.location)
//        et_description.setText(eventViewModel.description)
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { event: Event ->

            event.summary?.let { et_summary.setText(it) }
            event.location?.let { et_location.setText(it) }
            event.description?.let { et_description.setText(it) }

            switch_all_day.isChecked = event.isAllDay()

            group_partial_day_event.visibleOrGone(!event.isAllDay())

            if (eventViewModel.validateDateTime()) {
                tv_start_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorSecondary))
                tv_start_time.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorSecondary))
            } else {
                tv_start_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorValidationError))
                tv_start_time.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColorValidationError))
            }

            val formattedStart = event.formatStart(eventViewModel.initialTimeZoneId)
            tv_start_date.text = formattedStart.first ?: ""
            tv_start_time.text = formattedStart.second ?: ""

            val formattedEnd = event.formatEnd(eventViewModel.initialTimeZoneId)
            tv_end_date.text = formattedEnd.first ?: ""
            tv_end_time.text = formattedEnd.second ?: ""

            tv_timezone_start.text = event.defaultTimeZone // TimeZone picked by user is saved in iCalendar's Default Timezone

            tv_calendar.text = event.calendar.name
            tv_calendar.compoundDrawables.firstOrNull()?.setTint(Color.parseColor(event.calendar.color))

            tv_recurrence.text = AndroidUtils.formatRecurrence(requireContext(), event, eventViewModel.initialTimeZoneId) ?: resources.getString(R.string.event_recurrence_none)

            displayAlarms()

        })
    }

    private fun attachActionHandlers() {

        et_summary.doAfterTextChanged { persistFormData() }
        et_location.doAfterTextChanged { persistFormData() }
        et_description.doAfterTextChanged { persistFormData() }

        switch_all_day.setOnCheckedChangeListener { _, checked ->
            eventViewModel.handleAllDaySwitch(checked)
        }

        press_timezone_start.setOnClickListener {
            val selectedIndex = allowedTimezoneIds.indexOf(eventViewModel.eventLiveData.value?.defaultTimeZone)
            AndroidUtils.displaySingleChoicePicker(requireContext(), null, allowedTimezoneIds.toTypedArray(), selectedIndex) {
                eventViewModel.handleTimeZone(allowedTimezoneIds[it])
            }
        }

        press_start_date.setOnClickListener {
            val date = eventViewModel.eventLiveData.value?.getStart(eventViewModel.initialTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(requireContext(), date) {
                eventViewModel.handleStartDate(it)
            }
        }

        press_end_date.setOnClickListener {
            val date = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.initialTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(requireContext(), date) {
                eventViewModel.handleEndDate(it)
            }
        }

        press_start_time.setOnClickListener {
            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future
            val time = eventViewModel.eventLiveData.value?.getStart(eventViewModel.initialTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleStartTime(it)
            }
        }

        press_end_time.setOnClickListener {
            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future
            val time = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.initialTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleEndTime(it)
            }
        }

        press_calendar.setOnClickListener {
            lifecycleScope.launch {
                val calendars = withContext(Dispatchers.Default) {
                    calendarViewModel.selectCalendars()
                }

                val selectedIndex = calendars.indexOfFirst { it.id == eventViewModel.eventLiveData.value!!.calendar.id }

                AndroidUtils.displayCalendarPicker(requireContext(), resources.getString(R.string.dialog_title_calendar_picker), calendars.toTypedArray(), selectedIndex) {
                    eventViewModel.handleCalendar(calendars[it])
                }
            }
        }

        press_recurrence.setOnClickListener {
            eventViewModel.initialiseForRecurrence()
            findNavController().navigate(R.id.nav_event_create_edit_recurrence)
        }











//eventViewModel.eventLiveData.value!!.iCalEvent.alarms[0]













        press_attendees.setOnClickListener {
            findNavController().navigate(R.id.nav_event_create_edit_attendees)
        }










    }

    private fun displayAlarms() {

        ll_alarms.removeAllViews()

        val event = eventViewModel.eventLiveData.value!!

        event.iCalEvent.alarms.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(R.layout.item_simple_text_button, ll_alarms, false)
            alarmView.findViewById<TextView>(R.id.tv_text).apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), calendarViewModel.timeZoneId), alarm)
                isClickable = false
            }
            alarmView.findViewById<ImageButton>(R.id.ib_cross).apply {
                setOnClickListener {
                    eventViewModel.handleAlarmDelete(index)
                }
                isClickable = true
            }
            ll_alarms.addView(alarmView)

        }

        // "add notification" button
        val addAlarmView = layoutInflater.inflate(R.layout.item_simple_text_button, ll_alarms, false)
        addAlarmView.findViewById<TextView>(R.id.tv_text).apply {
            text = resources.getString(R.string.event_text_add_alarm)
            setOnClickListener {
                eventViewModel.initialiseForAlarm()
                findNavController().navigate(R.id.nav_event_create_edit_alarm)
            }
        }
        addAlarmView.findViewById<ImageButton>(R.id.ib_cross).visibleOrGone(false)
        ll_alarms.addView(addAlarmView)

    }


}
