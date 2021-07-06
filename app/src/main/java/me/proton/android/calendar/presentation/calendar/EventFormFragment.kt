package me.proton.android.calendar.presentation.calendar

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import biweekly.property.Action
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_checkbox.view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form.*
import kotlinx.android.synthetic.main.fragment_event_form_attendees.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.formatSendPreferencesError
import me.proton.android.calendar.common.AndroidUtils.formattedTimeZoneToId
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.AndroidUtils.sortFormattedTimeZoneIds
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatTimeZoneId
import me.proton.android.calendar.common.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.FeatureFlag.ADD_ATTENDEES
import me.proton.android.calendar.common.FormValidation.ATTENDEE_MAX_CHIP_ALLOWED
import me.proton.android.calendar.common.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.core.mailmessage.domain.entity.Email
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.ZoneId
import java.util.*
import kotlin.coroutines.CoroutineContext


class EventFormFragment() : BaseDialogFragment(), KoinComponent {

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()
    private val accountViewModel: AccountViewModel by sharedViewModel()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    override val TAG = "EventFormFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form
    override val isScrollable = false

    override val navigateUp = false

    private val logger: Logger by inject()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // We navigate even though contacts permission is not granted
            findNavController().navigate(R.id.nav_event_form_attendees)
        }

    override fun onBackPressedCustom() {
        val processingEvent = eventViewModel.eventState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                if (navigationArguments.eventId == null) {
                    jumpToMonthView()
                } else {
                    // Reinitialise event view model data when user chooses to discard modifications
                    lifecycleScope.launch {
                        val userId = accountViewModel.getPrimaryUserId()
                        val viewModeInitStatus =
                            if (userId == null) EventViewModel.Result.Error("user ID is null in EventDetailsFragment onViewCreated")
                            else eventViewModel.initialise(
                                userId,
                                editMode = false,
                                navigationArguments.eventId,
                                if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                                null,
                                null
                            )
                        if (viewModeInitStatus == EventViewModel.Result.Success) {
                            findNavController().navigateUp()
                        } else {
                            when (viewModeInitStatus) {
                                EventViewModel.Result.OccurrenceDoesNotExist -> {
                                    AndroidUtils.displaySimpleOkAlert(
                                        requireContext(),
                                        getString(R.string.error_occurrence_doesnt_exist)
                                    )
                                }
                                EventViewModel.Result.EventDoesNotExist -> {
                                    AndroidUtils.displaySimpleOkAlert(
                                        requireContext(),
                                        getString(R.string.error_event_doesnt_exist)
                                    )
                                }
                                is EventViewModel.Result.Error -> {
                                    logger.e(viewModeInitStatus.message)
                                    requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                                }
                            }
                            jumpToMonthView()
                        }
                    }
                }
            }
        } else findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        val processingEvent = eventViewModel.eventState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return true
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                requireActivity().clearFocusAndHideKeyboard(view)
                jumpToMonthView()
            }
        } else {
            requireActivity().clearFocusAndHideKeyboard(view)
            jumpToMonthView()
        }
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
            persistFormData()

            // Allow saving with no edition if creating an event
            if (navigationArguments.eventId.isNullOrEmpty() || eventViewModel.hasEventBeenEdited()) {

                lifecycleScope.launch {

                    // check if event wasn't changed to invitation shortly before saving
                    if (!eventViewModel.isEventNew() && eventViewModel.isApiEventAnInvitation() != false) {
                        AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.snack_event_edit_with_attendees_error))
                        return@launch
                    }

                    eventViewModel.eventState.value = EventViewModel.EventState.Processing.Saving

                    val dbEvent = eventViewModel.dbEvent
                    val shouldShowConfirmationPicker = !eventViewModel.isEventNew() &&
                            (dbEvent?.isRecurring() == true || dbEvent?.isPartOfChain() == true) &&
                            !dbEvent.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId)
                    if (!eventViewModel.eventLiveData.value?.iCalEvent?.attendees.isNullOrEmpty()) {
                        if (shouldShowConfirmationPicker && !navigationArguments.eventId.isNullOrEmpty()) {
                            val singleEditsInfo = eventViewModel.getSingleEditsInfo()
                            // Display Add Participants Dialog
                            eventViewModel.eventDialogState.value = EventViewModel.EventDialogState.Save.AddParticipants(eventViewModel.hasExDates(), singleEditsInfo?.hasSingleEdit == true)
                        } else {
                            // Display Send Invitation Dialog
                            eventViewModel.eventDialogState.value = EventViewModel.EventDialogState.Save.SendInvitation
                        }
                    } else saveEvent(mapOf())
                }

            } else findNavController().navigateUp()

        } else {
            AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.event_alert_invalid_start_end_date))
        }
    }

    private suspend fun handleSaveAttendeesSendPreferences(isAddParticipants: Boolean) {
        // TODO try to move the logic to VM
        val attendeesEmails = eventViewModel.eventLiveData.value?.iCalEvent?.attendees?.mapNotNull { it.extractEmail() }
        if (!attendeesEmails.isNullOrEmpty()) {

            val sendPreferencesResults = eventViewModel.getSendPreferences(attendeesEmails)

            if (sendPreferencesResults.emailErrors.isNotEmpty()) {

                if (sendPreferencesResults.emailErrors.any { it.value == ObtainSendPreferencesUseCase.Result.Error.NetworkError }) {
                    this@EventFormFragment.view?.displaySnackBar(getString(R.string.snack_network_error))
                    eventViewModel.eventState.value = EventViewModel.EventState.Idle
                } else {
                    // Display Send Preferences Dialog
                    eventViewModel.eventDialogState.value = EventViewModel.EventDialogState.Save.SendPreferences(sendPreferencesResults, isAddParticipants)
                }
            } else {
                if (isAddParticipants) handleSaveWithOption(EventEditDeleteOption.ALL_EVENTS, sendPreferencesResults.sendPreferences)
                else saveEvent(sendPreferencesResults.sendPreferences)
            }
        } else {
            saveEvent(mapOf())
        }
    }

    private suspend fun saveEvent(sendPreferences: Map<Email, SendPreferences>) {

        val dbEvent = eventViewModel.dbEvent
        val shouldShowConfirmationPicker = !eventViewModel.isEventNew() &&
                (dbEvent?.isRecurring() == true || dbEvent?.isPartOfChain() == true) &&
                !dbEvent.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId)
        val singleEditsInfo = eventViewModel.getSingleEditsInfo()
        if (shouldShowConfirmationPicker) {
            val showThisAndFuture = navigationArguments.occurrenceNumber > 1 &&
                    (dbEvent != null && eventViewModel.eventLiveData.value?.isEventFirstOccurrence(dbEvent,
                        eventViewModel.displayTimeZoneId) == false)

            // Display Recurring Options Dialog
            eventViewModel.eventDialogState.value = EventViewModel.EventDialogState.Save.RecurringEvent(
                sendPreferences,
                showThisAndFuture,
                singleEditsInfo?.hasSingleEdit == true,
                singleEditsInfo?.hasFutureSingleEdit == true
            )

        } else { // TODO merge this with code above
            val handleSaveResult = withContext(Dispatchers.IO) {
                eventViewModel.handleSave(
                    editOption =
                    if (eventViewModel.dbEvent?.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId) == true)
                        EventEditDeleteOption.ALL_EVENTS
                    else
                        null,
                    occurrenceNumber = 1,
                    calendarViewModel.timeFormatIs24Hour(requireContext()),
                    sendPreferences
                )
            }
            withContext(Dispatchers.Default) {
                val userId = accountViewModel.getPrimaryUserId() ?: return@withContext logger.e("Error user id was null in EventFormFragment onSaveClick")
                handleAlarmsUseCase.execute(userId)
            }
            // stop loading state
            eventViewModel.eventState.value = EventViewModel.EventState.Idle

            if (eventViewModel.eventLiveData.value?.isSyncedWithApi() == true) {
                if (handleSaveResult == EventViewModel.HandleSaveResult.SUCCESS) {
                    onSuccessEventUpdateCalendarDisplay()
                    requireActivity().displaySnackBar(getString(R.string.snack_event_updated))
                    setMonthViewSelectedDay()
                    jumpToMonthView()
                } else if (handleSaveResult == EventViewModel.HandleSaveResult.EDIT_ERROR_SEND_MAIL) {
                    view?.displaySnackBar(getString(R.string.snack_event_updated_error_failed_mail))
                } else {
                    view?.displaySnackBar(getString(R.string.snack_event_updated_error))
                }
            } else {
                if (handleSaveResult == EventViewModel.HandleSaveResult.SUCCESS || handleSaveResult == EventViewModel.HandleSaveResult.CREATE_ERROR_SEND_MAIL) {
                    onSuccessEventUpdateCalendarDisplay()
                    requireActivity().displaySnackBar(getString(
                        if (handleSaveResult == EventViewModel.HandleSaveResult.SUCCESS) R.string.snack_event_created
                        else R.string.snack_event_created_failed_mail))
                    setMonthViewSelectedDay()
                    jumpToMonthView()
                } else {
                    view?.displaySnackBar(getString(R.string.snack_event_created_error))
                }
            }
        }
    }

    private fun setMonthViewSelectedDay() {
        eventViewModel.eventLiveData.value?.getStart(eventViewModel.displayTimeZoneId)?.toLocalDate()?.let {
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
            .setNegativeButton(R.string.event_recurring_update_this_cancel) { _, _ ->
                eventViewModel.eventState.value = EventViewModel.EventState.Idle
            }
            .setOnCancelListener { _ ->
                eventViewModel.eventState.value = EventViewModel.EventState.Idle
            }
            .show()
    }

    private fun handleSaveWithOption(eventEditDeleteOption: EventEditDeleteOption, sendPreferences: Map<Email, SendPreferences>) {
        lifecycleScope.launch {
            val handleSaveResult = withContext(Dispatchers.IO) {
                eventViewModel.handleSave(
                    eventEditDeleteOption,
                    navigationArguments.occurrenceNumber,
                    calendarViewModel.timeFormatIs24Hour(requireContext()),
                    sendPreferences
                )
            }
            withContext(Dispatchers.Default) {
                val userId = accountViewModel.getPrimaryUserId() ?: return@withContext logger.e("Error user id was null in EventFormFragment onSaveClick")
                handleAlarmsUseCase.execute(userId)
            }
            // stop loading state
            eventViewModel.eventState.value = EventViewModel.EventState.Idle

            if (handleSaveResult == EventViewModel.HandleSaveResult.SUCCESS) { // TODO remove duplicated code here and below
                onSuccessEventUpdateCalendarDisplay()
                requireActivity().displaySnackBar(getString(R.string.snack_event_updated))
                setMonthViewSelectedDay()
                jumpToMonthView()
            } else if (handleSaveResult == EventViewModel.HandleSaveResult.EDIT_ERROR_SEND_MAIL) {
                view?.displaySnackBar(getString(R.string.snack_event_updated_error_failed_mail))
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
                if (userId == null) EventViewModel.Result.Error("user ID is null in EventDetailsFragment onViewCreated")
                else eventViewModel.initialise(
                    userId,
                    editMode = true,
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    navigationArguments.initStartDate,
                    navigationArguments.initStartTime
                )
            }

            if (viewModeInitStatus == EventViewModel.Result.Success) {
                if (navigationArguments.eventId == null) {
                    event_form_title.requestFocus()
                    requireContext().showKeyboard()
                }
                launch {
                    eventViewModel.getSingleEditsInfo()
                }
                observeEventLiveData()
                observeEventDialogState(coroutineContext)
                attachActionHandlers()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.Result.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.error_occurrence_doesnt_exist))
                    }
                    EventViewModel.Result.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.error_event_doesnt_exist))
                    }
                    is EventViewModel.Result.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(
                            if (navigationArguments.eventId != null) getString(R.string.snack_event_opening_edit_error)
                            else getString(R.string.snack_event_init_error)
                        )
                    }
                }
                findNavController().navigateUp()
            }

            eventViewModel.eventState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventState ->
                val processingEvent = eventState is EventViewModel.EventState.Processing

                // Update action bar buttons visibility
                loadingAction.visibleOrGone(processingEvent)
                buttonSave.visibleOrGone(!processingEvent)

                // Disable/Enable all items linked to actions from our view
                event_form_title.isEnabled = !processingEvent
                event_form_location.isEnabled = !processingEvent
                event_form_description.isEnabled = !processingEvent
                event_form_all_day_press.isEnabled = !processingEvent
                event_form_all_day_switch.isClickable = !processingEvent
                event_form_all_day_switch.isFocusable = !processingEvent
                event_form_timezone_press.isEnabled = !processingEvent
                event_form_start_date_press.isEnabled = !processingEvent
                event_form_end_date_press.isEnabled = !processingEvent
                event_form_start_time_press.isEnabled = !processingEvent
                event_form_end_time_press.isEnabled = !processingEvent
                event_form_calendar_press.isEnabled = !processingEvent
                event_form_recurrence_press.isEnabled = !processingEvent
                event_form_alarm_press.isEnabled = !processingEvent

                for (i in 0 until event_form_alarm_list.childCount) {
                    // Disable the delete buttons from inside alarm items views
                    event_form_alarm_list.getChildAt(i)
                        .findViewById<View>(R.id.item_simple_text_button_delete).isEnabled = !processingEvent
                }

                event_form_participant_press.isEnabled = !processingEvent
                for (i in 0 until event_form_participant_chip_group.childCount) {
                    // Disable the chips from inside attendees items views
                    event_form_participant_chip_group.getChildAt(i).isEnabled = !processingEvent
                }
            }
        }
    }

    private fun observeEventDialogState(coroutineContext: CoroutineContext) {
        eventViewModel.eventDialogState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventDialogState ->

            eventDialogState?.let {
                when (it) {
                    is EventViewModel.EventDialogState.Save.SendPreferences -> {

                        val emailsWithErrors = TextUtils.join("\n• ", it.sendPreferencesResults.emailErrors.map { entry ->
                            resources.getString(R.string.event_send_prefs_error_template, entry.key, entry.value.formatSendPreferencesError(resources))
                        })

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.event_attendees_send_prefs_error_title)
                            .setMessage(
                                if (it.sendPreferencesResults.sendPreferences.isEmpty()) getString(
                                    R.string.event_attendees_send_prefs_error_none_message,
                                    emailsWithErrors
                                )
                                else getString(
                                    R.string.event_attendees_send_prefs_error_some_message,
                                    emailsWithErrors
                                )
                            )
                            .setPositiveButton(R.string.event_attendees_send_prefs_error_confirm) { _, _ ->
                                lifecycleScope.launch {
                                    // Remove attendees whom emails were invalid
                                    eventViewModel.eventLiveData.value?.iCalEvent?.attendees?.removeIf { attendee ->
                                        it.sendPreferencesResults.emailErrors.any { emailError ->
                                            attendee.extractEmail() == emailError.key
                                        }
                                    }

                                    if (it.isAddParticipants) handleSaveWithOption(EventEditDeleteOption.ALL_EVENTS, it.sendPreferencesResults.sendPreferences)
                                    else saveEvent(it.sendPreferencesResults.sendPreferences)
                                }
                            }
                            .setNegativeButton(R.string.event_attendees_send_prefs_error_cancel) { _, _, ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnCancelListener {
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnDismissListener {
                                eventViewModel.eventDialogState.value = null
                            }
                            .show()
                    }
                    is EventViewModel.EventDialogState.Save.AddParticipants -> {
                        val message =
                            if (it.hasExDates || it.hasSingleEdit) {
                                R.string.event_add_participants_overwrite_dialog_description
                            } else {
                                R.string.event_add_participants_dialog_description
                            }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.event_add_participants_dialog_title)
                            .setMessage(message)
                            .setPositiveButton(R.string.event_add_participants_dialog_confirm) { _, _ ->
                                lifecycleScope.launch {
                                    handleSaveAttendeesSendPreferences(true)
                                }
                            }
                            .setNegativeButton(R.string.event_add_participants_dialog_cancel) { _, _ ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnCancelListener {
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnDismissListener {
                                eventViewModel.eventDialogState.value = null
                            }
                            .show()
                    }
                    is EventViewModel.EventDialogState.Save.SendInvitation -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.event_send_invite_dialog_title)
                            .setMessage(R.string.event_send_invite_dialog_description)
                            .setPositiveButton(R.string.event_send_invite_dialog_confirm) { _, _ ->
                                lifecycleScope.launch {
                                    handleSaveAttendeesSendPreferences(false)
                                }
                            }
                            .setNegativeButton(R.string.event_send_invite_dialog_cancel) { _, _ ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnCancelListener {
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnDismissListener {
                                eventViewModel.eventDialogState.value = null
                            }
                            .show()
                    }
                    is EventViewModel.EventDialogState.Save.RecurringEvent -> {
                        var selectedItem = 0
                        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                        builder.setTitle(getString(R.string.event_text_edit_event))
                            .setSingleChoiceItems(
                                listOfNotNull(
                                    getString(R.string.event_recurring_edit_this),
                                    if (it.showThisAndFuture) getString(R.string.event_recurring_edit_this_and_future)
                                    else null,
                                    getString(R.string.event_recurring_edit_all_events)
                                ).toTypedArray(),
                                0
                            ) { _, item ->
                                selectedItem = item
                            }
                            .setPositiveButton(R.string.dialog_button_ok) { dialog, _ ->

                                val eventEditDeleteOption =
                                    if (selectedItem == 0) {
                                        EventEditDeleteOption.THIS_EVENT
                                    } else if (selectedItem == 1) {
                                        if (it.showThisAndFuture) {
                                            EventEditDeleteOption.THIS_EVENT_AND_FUTURE
                                        } else {
                                            EventEditDeleteOption.ALL_EVENTS
                                        }
                                    } else { // it == 2
                                        EventEditDeleteOption.ALL_EVENTS
                                    }

                                // Display warning dialog for this event option if recurrence rule has been edited
                                if (eventEditDeleteOption == EventEditDeleteOption.THIS_EVENT && eventViewModel.recurrenceManuallyEdited && eventViewModel.hasRecurrenceRuleBeenEdited()) {
                                    displayUpdateRecurringEventDialog(R.string.event_recurring_update_this_description) { _, _ ->
                                        handleSaveWithOption(eventEditDeleteOption, it.sendPreferences)
                                    }
                                }
                                // Display warning dialog for all events option if has ex dates or single edits
                                else if (eventEditDeleteOption == EventEditDeleteOption.ALL_EVENTS && (eventViewModel.hasExDates() || it.hasSingleEdit)) {
                                    displayUpdateRecurringEventDialog(R.string.event_recurring_update_all_description) { _, _ ->
                                        handleSaveWithOption(eventEditDeleteOption, it.sendPreferences)
                                    }
                                }
                                // Display warning dialog for all events option if has ex dates or single edits
                                else if (eventEditDeleteOption == EventEditDeleteOption.THIS_EVENT_AND_FUTURE && (eventViewModel.hasExDates(true) || it.hasFutureSingleEdit)) {
                                    displayUpdateRecurringEventDialog(R.string.event_recurring_update_all_description) { _, _ ->
                                        handleSaveWithOption(eventEditDeleteOption, it.sendPreferences)
                                    }
                                }
                                else {
                                    handleSaveWithOption(eventEditDeleteOption, it.sendPreferences)
                                }

                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnCancelListener { _ ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                            }
                            .setOnDismissListener {
                                eventViewModel.eventDialogState.value = null
                            }
                            .show()
                    }
                }
            }

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

            ImageViewCompat.setImageTintList(
                event_form_location_icon,
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (event_form_location.text.isEmpty()) R.color.icon_hint else R.color.icon_norm
                    )
                )
            )
            ImageViewCompat.setImageTintList(
                event_form_description_icon,
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (event_form_description.text.isEmpty()) R.color.icon_hint else R.color.icon_norm
                    )
                )
            )

            event_form_all_day_switch.isChecked = event.isAllDay()
            event_form_all_day_switch.jumpDrawablesToCurrentState()

            event_form_partial_day_start.visibleOrGone(!event.isAllDay())
            event_form_partial_day_end.visibleOrGone(!event.isAllDay())
            event_form_timezone_layout.visibleOrGone(!event.isAllDay())

            if (eventViewModel.validateDateTime()) {
                event_form_start_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
                event_form_start_time.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
            } else {
                event_form_start_date.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.textColorValidationError
                    )
                )
                event_form_start_time.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.textColorValidationError
                    )
                )
            }

            val formattedStart = event.formatStart(
                eventViewModel.eventTimeZoneId,
                eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            )
            event_form_start_date.text = formattedStart.first ?: ""
            event_form_start_time.text = formattedStart.second ?: ""

            val formattedEnd = event.formatEnd(
                eventViewModel.eventTimeZoneId,
                eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            )
            event_form_end_date.text = formattedEnd.first ?: ""
            event_form_end_time.text = formattedEnd.second ?: ""

            event_form_timezone.text = formatTimeZoneId(
                event.defaultTimeZone!!,
                eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toInstant()!!
            ) // TimeZone picked by user is saved in iCalendar's Default Timezone

            event_form_calendar.text = event.calendar.name
            ImageViewCompat.setImageTintList(
                event_form_calendar_icon,
                ColorStateList.valueOf(Color.parseColor(event.calendar.color))
            )

            event_form_recurrence.text =
                AndroidUtils.formatRecurrence(requireContext().resources, event, eventViewModel.eventTimeZoneId)
                    ?: resources.getString(R.string.event_recurrence_none)

            displayAlarms()

            event_form_participant_chip_group.removeAllViews()
            event.iCalEvent.attendees.take(ATTENDEE_MAX_CHIP_ALLOWED).forEach { attendee ->
                val chipTitle = if (attendee.commonName.isNotEmpty()) attendee.commonName else attendee.extractEmail()
                chipTitle?.let {
                    addAttendeeChip(chipTitle)
                }
            }
            if (!event.iCalEvent.attendees.isNullOrEmpty()) {
                addAttendeeChip(getString(R.string.event_current_user_organizer))

                if (event.iCalEvent.attendees.size > ATTENDEE_MAX_CHIP_ALLOWED) {
                    addAttendeeChip(
                        getString(
                            R.string.event_max_attendee_chip,
                            event.iCalEvent.attendees.size - ATTENDEE_MAX_CHIP_ALLOWED
                        )
                    )
                }
            }
            ImageViewCompat.setImageTintList(
                event_form_participant_icon, ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (event.iCalEvent.attendees.isNullOrEmpty()) R.color.icon_hint
                        else R.color.icon_norm
                    )
                )
            )

            lifecycleScope.launch {
                event_form_participant_layout.visibleOrGone(ADD_ATTENDEES && eventViewModel.allowSendForCalendarAddress() && event.hasProtonUid) // TODO Remove feature flag
            }

            event_form_participant.visibleOrGone(event.hasProtonUid && event.iCalEvent.attendees.isNullOrEmpty())
            event_form_participant_chip_group.visibleOrGone(!event.iCalEvent.attendees.isNullOrEmpty())

            // TODO Replace this with !event.isAnInvitation once we check if event is an invite by checking organizer field
            event_form_calendar_press.visibleOrGone(event.iCalEvent.organizer == null || navigationArguments.eventId.isNullOrEmpty())
        })
    }

    private fun addAttendeeChip(title: String) {
        val chip = layoutInflater.inflate(R.layout.item_attendee_chip, event_form_participant_chip_group, false) as Chip
        chip.text = title
        chip.setOnSingleClickListener {
            navigateToAttendees()
        }
        event_form_participant_chip_group.addView(chip)
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
                formatTimeZoneId(it, forInstant)
            }.toTypedArray()
            formattedTimeZoneIds.sortFormattedTimeZoneIds()
            val defaultTimeZone = eventViewModel.eventLiveData.value?.defaultTimeZone
            val selectedIndex =
                if (defaultTimeZone == null) -1
                else formattedTimeZoneIds.indexOf(formatTimeZoneId(defaultTimeZone, forInstant))

            AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.settings_timezone_title), formattedTimeZoneIds, selectedIndex) {
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
                val calendars = calendarViewModel.activeCalendars.value

                // TODO Save active calendars in calendar VM to avoid triggering click effect when not needed
                if (calendars == null || calendars.size <= 1) return@launch

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

        event_form_participant_press.setOnSingleClickListener {
            navigateToAttendees()
        }
    }

    private fun shouldShowContactsPermissionsDialog(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(SharedPreferencesKeys.SHOW_CONTACTS_PERMISSIONS_DIALOG, true)
    }

    private fun changeContactsPermissionsPreferences(showDialog: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val editor = sharedPreferences.edit()
        editor.putBoolean(SharedPreferencesKeys.SHOW_CONTACTS_PERMISSIONS_DIALOG, showDialog)
        editor.apply()
    }

    private fun navigateToAttendees() {
        requireActivity().clearFocusAndHideKeyboard(view)

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                findNavController().navigate(R.id.nav_event_form_attendees)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
                    && shouldShowContactsPermissionsDialog() -> {
                displayCustomPermissionDialog(true)
            }
            shouldShowContactsPermissionsDialog() -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.READ_CONTACTS)
            }
            else -> {
                findNavController().navigate(R.id.nav_event_form_attendees)
            }
        }
    }

    private fun displayCustomPermissionDialog(openSettings: Boolean) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_checkbox, null, false)

        view.dialog_checkbox_header.text = getString(R.string.contacts_permission_dialog_message)
        view.dialog_checkbox_press.setOnClickListener {
            view.dialog_checkbox.performClick()
        }
        val positiveButtonText =
            if (openSettings) R.string.contacts_permission_dialog_open_settings
            else R.string.contacts_permission_dialog_allow

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.contacts_permission_dialog_title)
            .setView(view)
            .setPositiveButton(positiveButtonText) { _, _ ->
                if (openSettings) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.data = Uri.fromParts("package", requireContext().packageName, null)
                    startActivity(intent)
                } else {
                    requestPermissionLauncher.launch(
                        Manifest.permission.READ_CONTACTS)
                }
            }
            .setNegativeButton(R.string.contacts_permission_dialog_cancel) { _, _ ->
                findNavController().navigate(R.id.nav_event_form_attendees)
            }
            .setOnCancelListener {
                findNavController().navigate(R.id.nav_event_form_attendees)
            }
            .setOnDismissListener {
                if (view.dialog_checkbox.isChecked) {
                    changeContactsPermissionsPreferences(false)
                }
            }
            .show()
    }

    private fun displayAlarms() {
        event_form_alarm_list.removeAllViews()
        event_form_alarm_icon.visibleOrGone(true)

        val event = eventViewModel.eventLiveData.value!!

        // TODO Remove filter once other type of alarms are handled
        event.iCalEvent.alarms.filter { it.action == Action.display() }.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(R.layout.item_alarm_text_button, event_form_alarm_list, false)
            alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext())), event.getStart(eventViewModel.displayTimeZoneId), alarm)
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
