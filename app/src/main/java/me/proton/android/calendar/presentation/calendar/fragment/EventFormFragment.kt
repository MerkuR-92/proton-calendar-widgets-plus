package me.proton.android.calendar.presentation.calendar.fragment

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import biweekly.property.Action
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_checkbox.view.dialog_checkbox
import kotlinx.android.synthetic.main.dialog_checkbox.view.dialog_checkbox_header
import kotlinx.android.synthetic.main.dialog_checkbox.view.dialog_checkbox_press
import kotlinx.android.synthetic.main.fragment_base_dialog.dialog_toolbar_content
import kotlinx.android.synthetic.main.fragment_event_form.event_form_alarm
import kotlinx.android.synthetic.main.fragment_event_form.event_form_alarm_icon
import kotlinx.android.synthetic.main.fragment_event_form.event_form_alarm_list
import kotlinx.android.synthetic.main.fragment_event_form.event_form_alarm_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_all_day_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_all_day_switch
import kotlinx.android.synthetic.main.fragment_event_form.event_form_calendar
import kotlinx.android.synthetic.main.fragment_event_form.event_form_calendar_icon
import kotlinx.android.synthetic.main.fragment_event_form.event_form_calendar_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_description
import kotlinx.android.synthetic.main.fragment_event_form.event_form_description_icon
import kotlinx.android.synthetic.main.fragment_event_form.event_form_end_date
import kotlinx.android.synthetic.main.fragment_event_form.event_form_end_date_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_end_time
import kotlinx.android.synthetic.main.fragment_event_form.event_form_end_time_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_location
import kotlinx.android.synthetic.main.fragment_event_form.event_form_location_icon
import kotlinx.android.synthetic.main.fragment_event_form.event_form_partial_day_end
import kotlinx.android.synthetic.main.fragment_event_form.event_form_partial_day_start
import kotlinx.android.synthetic.main.fragment_event_form.event_form_participant
import kotlinx.android.synthetic.main.fragment_event_form.event_form_participant_chip_group
import kotlinx.android.synthetic.main.fragment_event_form.event_form_participant_icon
import kotlinx.android.synthetic.main.fragment_event_form.event_form_participant_layout
import kotlinx.android.synthetic.main.fragment_event_form.event_form_participant_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_recurrence
import kotlinx.android.synthetic.main.fragment_event_form.event_form_recurrence_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_start_date
import kotlinx.android.synthetic.main.fragment_event_form.event_form_start_date_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_start_time
import kotlinx.android.synthetic.main.fragment_event_form.event_form_start_time_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_timezone
import kotlinx.android.synthetic.main.fragment_event_form.event_form_timezone_layout
import kotlinx.android.synthetic.main.fragment_event_form.event_form_timezone_press
import kotlinx.android.synthetic.main.fragment_event_form.event_form_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag.ADD_ATTENDEES
import me.proton.android.calendar.common.FormValidation
import me.proton.android.calendar.common.FormValidation.ATTENDEE_MAX_CHIP_ALLOWED
import me.proton.android.calendar.common.FragmentArguments.IS_ALL_DAY_ARG
import me.proton.android.calendar.common.FragmentArguments.IS_CALENDAR_DEFAULT_EVENT_NOTIFICATION_ARG
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.allowedTimezoneIds
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.formattedTimeZoneToId
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.sortFormattedTimeZoneIds
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTimeZoneId
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.presentation.utils.clearText
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

class EventFormFragment() : BaseDialogFragment(), KoinComponent {

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val eventViewModel: EventViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

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
        val processingEvent = eventViewModel.eventFormState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                if (navigationArguments.eventId == null) jumpToMonthView()
                else navigateBackToDetails()
            }
        } else if (navigationArguments.eventId == null) jumpToMonthView()
        else navigateBackToDetails()
    }

    private fun navigateBackToDetails() {
        // Reinitialise event view model data
        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus =
                if (userId == null) EventViewModel.InitResult.Error("user ID is null in EventDetailsFragment onViewCreated")
                else eventViewModel.initialise(
                    userId,
                    editMode = false,
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    null,
                    null
                )
            if (viewModeInitStatus == EventViewModel.InitResult.Success) {
                requireActivity().clearFocusAndHideKeyboard(view)
                findNavController().navigateUp()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.InitResult.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.InitResult.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.InitResult.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                    }
                }
                jumpToMonthView()
            }
        }
    }

    override fun onNavigationIconClicked(): Boolean {
        val processingEvent = eventViewModel.eventFormState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return true
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                jumpToMonthView()
            }
        } else {
            jumpToMonthView()
        }
        return true
    }

    private fun jumpToMonthView() {
        activity?.clearFocusAndHideKeyboard(view)
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
                persistFormData()

                // Go back to main view if no changes have been made
                if (navigationArguments.eventId?.isNotEmpty() == true && eventViewModel.hasEventBeenEdited().not()) {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    findNavController().navigateUp()
                    return@setOnSingleClickListener
                }

                lifecycleScope.launch {
                    eventViewModel.onSaveClick(
                        provideDisplayDialog(),
                        navigationArguments.eventId,
                        navigationArguments.occurrenceNumber,
                        calendarViewModel.timeFormatIs24Hour(requireContext())
                    )
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

    // TODO remove when the issue with invalid sender Address is fixed
    private suspend fun invalidUserAddressLogoutHack() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, false)) {
            logger.e("EventFormFragment: hack was performed but we force logout again")
        }

        with (sharedPreferences.edit()) {
            putBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, true)
            apply()
        }

        logger.i("EventFormFragment: hack detected invalid user address, logging out")
        accountViewModel.logoutPrimary()
        calendarViewModel.shutdown()
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
            val viewModeInitStatus = withContext(Dispatchers.Main) {
                if (userId == null) EventViewModel.InitResult.Error("user ID is null in EventDetailsFragment onViewCreated")
                else eventViewModel.initialise(
                    userId,
                    editMode = true,
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    navigationArguments.initStartDate,
                    navigationArguments.initStartTime
                )
            }

            if (viewModeInitStatus == EventViewModel.InitResult.Success) {
                if (navigationArguments.eventId == null) {
                    event_form_title.requestFocus()
                    requireContext().showKeyboard()
                }
                launch {
                    eventViewModel.getSingleEditsInfo()
                }
                observeEventLiveData()
                observeEventSnackState(coroutineContext)
                attachActionHandlers()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.InitResult.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.InitResult.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.InitResult.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(
                            if (navigationArguments.eventId != null) getString(R.string.snack_event_opening_edit_error)
                            else getString(R.string.snack_event_init_error)
                        )
                    }
                }
                if (navigationArguments.eventId == null) jumpToMonthView()
                else onBackPressedCustom()
            }

            eventViewModel.eventFormState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventState ->
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

                if (eventState is EventViewModel.EventState.UserAddressInvalidForEncryption) {
                    lifecycleScope.launch {
                        invalidUserAddressLogoutHack()
                    }
                }
            }
        }
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { nullableEvent: Event? ->

            val event = nullableEvent ?: return@Observer

            // TODO Fix transition so title hint doesn't blink on screen
            event_form_title.hint = resources.getString(R.string.event_hint_title)
            event.summary?.let {
                if (it.isNotEmpty()) event_form_title.setText(it)
            } ?: event_form_title.clearText()
            event.location?.let { event_form_location.setText(it) } ?: event_form_location.clearText()
            event.description?.let { event_form_description.setText(it) } ?: event_form_description.clearText()

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

            event_form_calendar_press.visibleOrGone(eventViewModel.isCalendarChangeAllowed())
        })
    }

    private fun observeEventSnackState(coroutineContext: CoroutineContext) {
        eventViewModel.eventFormSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventSnackState ->
            eventSnackState?.let {
                when (it) {
                    is EventViewModel.EventSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is EventViewModel.EventSnackState.DisplaySnackReturnToMonth -> {
                        requireActivity().displaySnackBar(it.message)

                        if (it.newSelectedDate != null && calendarViewModel.selectedDate.value != it.newSelectedDate) {
                            calendarViewModel.handleDaySelected(it.newSelectedDate)
                        }

                        // Use jumpToMonthView to handle navigation when opening details from notification
                        jumpToMonthView()
                    }
                }
                eventViewModel.eventFormSnackState.value = null
            }
        }
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
        event_form_title.doAfterTextChanged { if (event_form_title.hasFocus()) persistFormData() }
        event_form_location.doAfterTextChanged { if (event_form_location.hasFocus()) persistFormData() }
        event_form_description.doAfterTextChanged { if (event_form_description.hasFocus()) persistFormData() }

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

            val forInstant = eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toInstant()!!
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
            if (!eventViewModel.isCalendarChangeAllowed()) {
                view?.displaySnackBar(getString(R.string.snack_feature_coming_soon))
                return@setOnSingleClickListener
            }
            requireActivity().clearFocusAndHideKeyboard(view)
            lifecycleScope.launch {
                val calendars = calendarViewModel.getUserCalendars()?.filter { it.isActive }

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

        if (!eventViewModel.isChangingAttendeesAllowed()) {
            view?.displaySnackBar(getString(R.string.snack_event_edit_calendar_with_attendees_error))
            return
        }

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

        event.iCalEvent.alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

            val alarmView = layoutInflater.inflate(R.layout.item_alarm_text_button, event_form_alarm_list, false)
            alarmView.findViewById<TextView>(R.id.item_simple_text_button_title).apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext())), event.getStart(eventViewModel.eventTimeZoneId), alarm)
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
            val bundle = Bundle().apply {
                putBoolean(IS_ALL_DAY_ARG, eventViewModel.eventLiveData.value!!.isAllDay())
                putBoolean(IS_CALENDAR_DEFAULT_EVENT_NOTIFICATION_ARG, false)
            }
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
        event_form_alarm.visibleOrGone(!eventViewModel.isAlarmLimitReached())
    }
}
