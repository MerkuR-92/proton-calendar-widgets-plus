package me.proton.android.calendar.presentation.calendar

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.property.Action
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.account.AccountViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.ZoneId
import java.util.*


class EventFormFragment() : BaseDialogFragment(), KoinComponent {

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()
    private val accountViewModel: AccountViewModel by sharedViewModel()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    override val TAG = "EventFormFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form

    override val navigateUp = false

    private val logger: Logger by inject()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    override fun onBackPressedCustom() {
        val immutableSavingEvent = eventViewModel.savingEvent.value
        if (immutableSavingEvent != null && immutableSavingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                // Reinitialise event view model data when user chooses to discard modifications
                lifecycleScope.launch {
                    val userId = accountViewModel.getPrimaryUserId()
                    val viewModeInitStatus =
                        if (userId == null) UseCase.Result.Error("user ID is null in EventDetailsFragment onViewCreated")
                        else eventViewModel.initialise(
                            userId,
                            editMode = false,
                            navigationArguments.eventId,
                            if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                            null,
                            null,
                        )
                    if (viewModeInitStatus == UseCase.Result.Success) {
                        findNavController().navigateUp()
                    } else {
                        logger.e((viewModeInitStatus as UseCase.Result.Error).message)
                        requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                        jumpToMonthView()
                    }
                }
            }
        } else findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        val immutableSavingEvent = eventViewModel.savingEvent.value
        if (immutableSavingEvent != null && immutableSavingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return true
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                jumpToMonthView()
            }
        } else jumpToMonthView()
        return true
    }

    private fun jumpToMonthView() {
        if (!findNavController().popBackStack(R.id.nav_calendar, false)) {
            // TODO this is a workaround for navigating back to month view after opening EventForm from EventDetails
            //  that was opened from system notification
            //  R.id.nav_calendar is not in the hierarchy so popping backstack will fail and we need
            //  to navigate manually
            findNavController().navigate(Navigation.Deeplink.toMonth())
        }
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.event_discard_changes_title)
            .setMessage(R.string.event_discard_changes_description)
            .setPositiveButton(R.string.event_discard_changes_confirm, callback)
            .setNegativeButton(R.string.event_discard_changes_cancel) { _, _ -> }
            .show()
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialog_toolbar_content, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(R.string.action_save)
            setOnSingleClickListener {
                onSaveClick()
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

    private fun onSaveClick() {
        if (eventViewModel.validateDateTime()) {
            lifecycleScope.launch {
                persistFormData()

                // Allow saving with no edition if creating an event
                if (navigationArguments.eventId.isNullOrEmpty() || eventViewModel.hasEventBeenEdited()) {

                    val dbEvent = eventViewModel.dbEvent
                    val shouldShowConfirmationPicker = !eventViewModel.isEventNew() &&
                            (dbEvent?.isRecurring() == true || dbEvent?.isPartOfChain() == true) &&
                            !dbEvent.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId)

                    if (shouldShowConfirmationPicker) {

                        AndroidUtils.displaySingleChoiceConfirmationPicker(
                            requireContext(), getString(R.string.event_text_edit_event), listOfNotNull(
                                getString(R.string.event_recurring_edit_this),
                                if (navigationArguments.occurrenceNumber > 1 &&
                                    (dbEvent != null && eventViewModel.eventLiveData.value?.isEventFirstOccurrence(
                                        dbEvent,
                                        eventViewModel.displayTimeZoneId
                                    ) == false)
                                ) getString(R.string.event_recurring_edit_this_and_future)
                                else null,
                                getString(R.string.event_recurring_edit_all_events)
                            ).toTypedArray(), 0
                        ) {
                            val eventEditDeleteOption =
                                if (it == 0) {
                                    EventEditDeleteOption.THIS_EVENT
                                } else if (it == 1) {
                                    if (navigationArguments.occurrenceNumber == 1) {
                                        EventEditDeleteOption.ALL_EVENTS
                                    } else {
                                        EventEditDeleteOption.THIS_EVENT_AND_FUTURE
                                    }
                                } else { // it == 2
                                    EventEditDeleteOption.ALL_EVENTS
                                }

                            // Display warning dialog for this event option if recurrence rule has been edited
                            if (eventEditDeleteOption == EventEditDeleteOption.THIS_EVENT && eventViewModel.dbEvent?.iCalEvent?.recurrenceRule != eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule) {
                                displayUpdateRecurringEventDialog(R.string.event_recurring_update_this_description) { _, _ ->
                                    handleSaveWithOption(eventEditDeleteOption)
                                }
                            }
                            // Display warning dialog for all events option if has ex dates or single edits
                            else if (eventEditDeleteOption == EventEditDeleteOption.ALL_EVENTS && (eventViewModel.hasExDates || eventViewModel.hasSingleEdit)) {
                                displayUpdateRecurringEventDialog(
                                    if (eventEditDeleteOption == EventEditDeleteOption.THIS_EVENT) R.string.event_recurring_update_this_description
                                    else R.string.event_recurring_update_all_description
                                ) { _, _ ->
                                    handleSaveWithOption(eventEditDeleteOption)
                                }
                            }
                            else {
                                handleSaveWithOption(eventEditDeleteOption)
                            }
                        }

                    } else { // TODO merge this with code above
                        val success = withContext(Dispatchers.IO) {
                            eventViewModel.handleSave(
                                editOption =
                                if (eventViewModel.dbEvent?.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId) == true)
                                    EventEditDeleteOption.ALL_EVENTS
                                else
                                    null,
                                occurrenceNumber = 1)
                        }
                        withContext(Dispatchers.Default) {
                            val userId = accountViewModel.getPrimaryUserId() ?: return@withContext logger.e("Error user id was null in EventFormFragment onSaveClick")
                            handleAlarmsUseCase.execute(userId)
                        }
                        // Post saving event value to false to stop loading state
                        eventViewModel.savingEvent.postValue(false)

                        if (eventViewModel.eventLiveData.value?.isSyncedWithApi() == true) {
                            if (success) {
                                onSuccessEventUpdateCalendarDisplay()
                                requireActivity().displaySnackBar(getString(R.string.snack_event_updated))
                                setMonthViewSelectedDay()
                                jumpToMonthView()
                            } else {
                                view?.displaySnackBar(getString(R.string.snack_event_updated_error))
                            }
                        } else {
                            if (success) {
                                onSuccessEventUpdateCalendarDisplay()
                                requireActivity().displaySnackBar(getString(R.string.snack_event_created))
                                setMonthViewSelectedDay()
                                jumpToMonthView()
                            } else {
                                view?.displaySnackBar(getString(R.string.snack_event_created_error))
                            }
                        }
                    }
                } else findNavController().navigateUp()
            }
        } else {
            AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.event_alert_invalid_start_end_date))
        }
    }

    private fun setMonthViewSelectedDay() {
        eventViewModel.eventLiveData.value?.startLocalDate?.let {
            if (calendarViewModel.selectedDate.value != it) {
                // Call default method for selection if pagers have been initialised
                if (calendarViewModel.pagersInitialised) calendarViewModel.handleDaySelected(it)
                // Set updateSelectedLocalDate for month view to initialise with event start date as selected day
                else calendarViewModel.updateSelectedLocalDate = it
            }
        }
    }

    private fun displayUpdateRecurringEventDialog(message: Int, callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.event_recurring_update_this_title)
            .setMessage(message)
            .setPositiveButton(R.string.event_recurring_update_this_confirm, callback)
            .setNegativeButton(R.string.event_recurring_update_this_cancel) { _, _ -> }
            .show()
    }

    private fun handleSaveWithOption(eventEditDeleteOption: EventEditDeleteOption) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                eventViewModel.handleSave(
                    eventEditDeleteOption,
                    navigationArguments.occurrenceNumber
                )
            }
            withContext(Dispatchers.Default) {
                val userId = accountViewModel.getPrimaryUserId() ?: return@withContext logger.e("Error user id was null in EventFormFragment onSaveClick")
                handleAlarmsUseCase.execute(userId)
            }
            // Post saving event value to false to stop loading state
            eventViewModel.savingEvent.postValue(false)

            if (success) { // TODO remove duplicated code here and below
                onSuccessEventUpdateCalendarDisplay()
                requireActivity().displaySnackBar(getString(R.string.snack_event_updated))
                setMonthViewSelectedDay()
                jumpToMonthView()
            } else {
                view?.displaySnackBar(getString(R.string.snack_event_updated_error))
            }

        }
    }

    private fun onSuccessEventUpdateCalendarDisplay() {
        val display = eventViewModel.eventLiveData.value?.calendar?.display
        if (display == null || display) return
        val calendarId = eventViewModel.eventLiveData.value?.calendar?.id
        if (calendarId != null) {
            lifecycleScope.launch {
                // 1. Update in DB
                calendarViewModel.updateCalendarVisibility(calendarId, display = 1)
                // 2. Update on Server
                calendarViewModel.updateServerCalendar(calendarId)
            }
        }
    }

    private fun persistFormData() {
        eventViewModel.persistRecurrenceFormData(
            event_form_title.text.toString().ifBlank { null },
            event_form_location.text.toString().ifBlank { null },
            event_form_description.text.toString().ifBlank { null }
        )

        // TODO CREATE EVENT WITHOUT SAVING BEFOREHAND? EXAMPLE CALL -> calendarViewModel.TEST_CREATE_EVENT_TODO()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {

            // TODO Fix transition so title hint doesn't blink on screen
            if (navigationArguments.eventId != null) event_form_title.hint = ""

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus = withContext(Dispatchers.Default) {
                if (userId == null) UseCase.Result.Error("user ID is null in EventDetailsFragment onViewCreated")
                else eventViewModel.initialise(
                    userId,
                    editMode = true,
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    navigationArguments.initStartDate,
                    navigationArguments.initStartTime,
                )
            }

            if (viewModeInitStatus == UseCase.Result.Success) {
                if (navigationArguments.eventId == null) event_form_title.requestFocus()
                observeEventLiveData()
                attachActionHandlers()
            } else {
                // TODO display error and close? for example when we can't decrypt event
                logger.e((viewModeInitStatus as UseCase.Result.Error).message)
                requireActivity().displaySnackBar(
                    if (navigationArguments.eventId != null) getString(R.string.snack_event_opening_edit_error)
                    else getString(R.string.snack_event_init_error)
                )
                findNavController().navigateUp()
            }

            eventViewModel.savingEvent.observe(viewLifecycleOwner, Observer { savingEvent: Boolean ->
                // Update action bar buttons visibility
                loadingAction.visibleOrGone(savingEvent)
                buttonSave.visibleOrGone(!savingEvent)

                // Disable/Enable all items linked to actions from our view
                event_form_title.isEnabled = !savingEvent
                event_form_location.isEnabled = !savingEvent
                event_form_description.isEnabled = !savingEvent
                event_form_all_day_press.isEnabled = !savingEvent
                event_form_all_day_switch.isClickable = !savingEvent
                event_form_all_day_switch.isFocusable = !savingEvent
                event_form_timezone_press.isEnabled = !savingEvent
                event_form_start_date_press.isEnabled = !savingEvent
                event_form_end_date_press.isEnabled = !savingEvent
                event_form_start_time_press.isEnabled = !savingEvent
                event_form_end_time_press.isEnabled = !savingEvent
                event_form_calendar_press.isEnabled = !savingEvent
                event_form_recurrence_press.isEnabled = !savingEvent
                event_form_alarm_press.isEnabled = !savingEvent
                for (i in 0 until event_form_alarm_list.childCount) {
                    // Disable the delete buttons from inside alarm items views
                    event_form_alarm_list.getChildAt(i).findViewById<View>(R.id.item_simple_text_button_delete).isEnabled = !savingEvent
                }
            })
        }
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { event: Event ->

            // TODO Fix transition so title hint doesn't blink on screen
            event_form_title.hint = resources.getString(R.string.event_hint_title)
            event.summary?.let {
                if (it.isNotEmpty()) event_form_title.setText(it)
            }
            event.location?.let { event_form_location.setText(it) }
            event.description?.let { event_form_description.setText(it) }

            if (event_form_location.text.isEmpty())
                ImageViewCompat.setImageTintList(event_form_location_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_hint)))
            if (event_form_description.text.isEmpty())
                ImageViewCompat.setImageTintList(event_form_description_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_hint)))

            event_form_all_day_switch.isChecked = event.isAllDay()
            event_form_all_day_switch.jumpDrawablesToCurrentState()

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

            val formattedStart = event.formatStart(eventViewModel.eventTimeZoneId, eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext())))
            event_form_start_date.text = formattedStart.first ?: ""
            event_form_start_time.text = formattedStart.second ?: ""

            val formattedEnd = event.formatEnd(eventViewModel.eventTimeZoneId, eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext())))
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

        event_form_location.setOnFocusChangeListener { _, hasFocus ->
            when {
                hasFocus ->
                    ImageViewCompat.setImageTintList(event_form_location_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.interaction_norm)))
                event_form_location.text.isNotEmpty() ->
                    ImageViewCompat.setImageTintList(event_form_location_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm)))
                else ->
                    ImageViewCompat.setImageTintList(event_form_location_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_hint)))
            }
        }
        event_form_description.setOnFocusChangeListener { _, hasFocus ->
            when {
                hasFocus ->
                    ImageViewCompat.setImageTintList(event_form_description_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.interaction_norm)))
                event_form_description.text.isNotEmpty() ->
                    ImageViewCompat.setImageTintList(event_form_description_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm)))
                else ->
                    ImageViewCompat.setImageTintList(event_form_description_icon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_hint)))
            }
        }

        event_form_all_day_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            event_form_all_day_switch.performClick()
        }
        event_form_all_day_switch.setOnCheckedChangeListener { _, checked ->
            eventViewModel.handleAllDaySwitch(checked)
        }

        event_form_timezone_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            val forInstant = eventViewModel.eventLiveData.value?.getStart(eventViewModel.displayTimeZoneId)?.toInstant()!!
            val formattedTimeZoneIds = allowedTimezoneIds.map {
                ICalUtils.formatTimeZoneId(it, forInstant)
            }.toTypedArray()
            formattedTimeZoneIds.sortFormattedTimeZoneIds()
            val defaultTimeZone = eventViewModel.eventLiveData.value?.defaultTimeZone
            val selectedIndex =
                if (defaultTimeZone == null) -1
                else formattedTimeZoneIds.indexOf(ICalUtils.formatTimeZoneId(defaultTimeZone, forInstant))

            AndroidUtils.displaySingleChoicePicker(requireContext(), null, formattedTimeZoneIds, selectedIndex) {
                eventViewModel.handleTimeZone(formattedTimeZoneIds[it].formattedTimeZoneToId())
            }
        }

        event_form_start_date_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val date = eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(
                context = requireContext(),
                firstDayOfWeek = eventViewModel.userSettings.weekStartDayOfWeek(),
                initialDate = date,
                minDate = FormValidation.MIN_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate(),
                maxDate = FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate()) {
                eventViewModel.handleStartDate(it)
            }
        }

        event_form_end_date_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val date = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.eventTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(
                context = requireContext(),
                firstDayOfWeek = eventViewModel.userSettings.weekStartDayOfWeek(),
                initialDate = date,
                minDate = FormValidation.MIN_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate(),
                maxDate = FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate()
            ) {
                eventViewModel.handleEndDate(it)
            }
        }

        event_form_start_time_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val is24Hour = eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            val time = eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleStartTime(it)
            }
        }

        event_form_end_time_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val is24Hour = eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            val time = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.eventTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleEndTime(it)
            }
        }

        event_form_calendar_press.setOnSingleClickListener {
            // TODO Remove once change calendar has been implemented
            if (navigationArguments.eventId != null) {
                view?.displaySnackBar(getString(R.string.snack_feature_coming_soon))
                return@setOnSingleClickListener
            }
            requireActivity().clearFocusAndHideKeyboard(view)
            lifecycleScope.launch {
                val calendars = withContext(Dispatchers.Default) {
                    calendarViewModel.getActiveCalendars()
                }

                // TODO Save active calendars in calendar VM to avoid triggering click effect when not needed
                if (calendars.size <= 1) return@launch

                val selectedIndex = calendars.indexOfFirst { it.id == eventViewModel.eventLiveData.value!!.calendar.id }

                AndroidUtils.displayCalendarPicker(requireContext(), resources.getString(R.string.dialog_title_calendar_picker), calendars.toTypedArray(), selectedIndex) {

                    lifecycleScope.launch {
                        if (!eventViewModel.handleCalendar(calendars[it])) {
                            view?.displaySnackBar(getString(R.string.snack_event_calendar_error))
                        }
                    }

                }
            }
        }

        event_form_recurrence_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForRecurrence()
            findNavController().navigate(R.id.nav_event_form_recurrence)
        }

        //TODO Attendees
        //press_attendees.setOnSingleClickListener {
        //    findNavController().navigate(R.id.nav_event_form_attendees)
        //}
    }

    private fun displayAlarms() {
        event_form_alarm_list.removeAllViews()
        event_form_alarm_icon.visibleOrGone(true)

        val event = eventViewModel.eventLiveData.value!!

        // TODO Remove filter once other type of alarms are handled
        event.iCalEvent.alarms.filter { it.action == Action.display() }.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(R.layout.item_alarm_text_button, event_form_alarm_list, false)
            alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext())), event.iCalEvent.getStart(eventViewModel.displayTimeZoneId)!!, alarm)
                isClickable = false
            }
            alarmView.findViewById<View>(R.id.item_simple_text_button_delete).apply {
                setOnSingleClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    eventViewModel.handleAlarmDelete(index)
                }
                isClickable = true
            }
            if (index == 0) event_form_alarm_icon.visibleOrGone(false)
            event_form_alarm_list.addView(alarmView)
        }

        // "add alarm" button
        event_form_alarm_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForAlarm()
            findNavController().navigate(R.id.nav_event_form_alarm)
        }
        event_form_alarm.visibleOrGone(!eventViewModel.isAlarmLimitReached())
    }
}
