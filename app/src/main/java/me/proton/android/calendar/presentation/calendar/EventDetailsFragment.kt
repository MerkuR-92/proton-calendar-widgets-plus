package me.proton.android.calendar.presentation.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.util.Linkify
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.parameter.ParticipationStatus
import biweekly.property.Action
import biweekly.property.Attendee
import biweekly.property.Organizer
import biweekly.property.Status
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.event_attendees_view.*
import kotlinx.android.synthetic.main.event_info.view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.android.synthetic.main.item_attendee.view.*
import kotlinx.android.synthetic.main.item_change_answer.view.*
import kotlinx.android.synthetic.main.item_change_answer_button.view.*
import kotlinx.android.synthetic.main.item_form_section.view.*
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.collapse
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.expand
import me.proton.android.calendar.common.AndroidUtils.formatSendPreferencesError
import me.proton.android.calendar.common.AndroidUtils.getInitials
import me.proton.android.calendar.common.AndroidUtils.getParticipationStatusPriorityValue
import me.proton.android.calendar.common.AndroidUtils.getText
import me.proton.android.calendar.common.AndroidUtils.rotateArrowDownward
import me.proton.android.calendar.common.AndroidUtils.rotateArrowUpward
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.EventUtilsImpl.formatStartEndForActualEndDate
import me.proton.android.calendar.common.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.EventUtilsImpl.isUserAddressAllowedSend
import me.proton.android.calendar.common.FeatureFlag.CHANGE_ANSWER
import me.proton.android.calendar.common.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainActivity
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.core.user.domain.extension.hasSubscription
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext


class EventDetailsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventDetailsFragment"
    override val layoutResourceId = R.layout.fragment_event_details

    override val navigateUp = false
    override val isScrollable = false

    private lateinit var buttonEdit: View
    private lateinit var buttonMenu: View
    private lateinit var loadingAction: View
    private lateinit var attendeeListAdapter: AttendeeListAdapter

    private val navigationArguments: EventDetailsFragmentArgs by navArgs()

    private val logger: Logger by inject()

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()
    private val accountViewModel: AccountViewModel by sharedViewModel()
    private val mainViewModel: MainViewModel by sharedViewModel()

    private fun jumpToMonthView() {
        // TODO this is a workaround for deeplinks not navigating up to direct parent, but to navigation's start destination
        //  1. see if nested graphs work when we get rid of dialogs in favor of fragments
        //  2. see if handling deeplink straight from notification (not indirectly from MainActivity and navigating manually)
        //  fixes this
        if (findNavController().previousBackStackEntry?.destination?.id != R.id.nav_calendar) {
            findNavController().navigate(
                Navigation.Deeplink.toMonth(
                    eventViewModel.eventLiveData.value?.getStart(
                        eventViewModel.displayTimeZoneId
                    )?.toLocalDate()
                )
            )
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onBackPressedCustom() {

        val immutableChangeAnswerLoading =
            eventViewModel.eventState.value is EventViewModel.EventState.Processing.ChangingAnswer
        val immutableDeletingEvent = eventViewModel.eventState.value == EventViewModel.EventState.Processing.Deleting

        if (immutableChangeAnswerLoading) {
            view?.displaySnackBar(getString(R.string.snack_event_changing_answer))
            return
        }
        if (immutableDeletingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_deleting))
            return
        }

        jumpToMonthView()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {

        buttonEdit = layoutInflater.inflate(R.layout.toolbar_action_secondary, dialog_toolbar_content, false)
        with(buttonEdit) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(
                ContextCompat.getDrawable(
                    this.context,
                    R.drawable.ic_pencil
                )
            )
            setOnSingleClickListener {
                findNavController().navigate(
                    (Navigation.Deeplink.toEventEdit(
                        navigationArguments.eventId,
                        navigationArguments.occurrenceNumber
                    ))
                )
            }
        }
        buttonMenu = layoutInflater.inflate(R.layout.toolbar_action_secondary, dialog_toolbar_content, false)
        with(buttonMenu) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(
                ContextCompat.getDrawable(
                    this.context,
                    R.drawable.ic_three_dots_vertical
                )
            )
            setOnSingleClickListener {
                AndroidUtils.displayPopupMenu(
                    view = it,
                    labels = listOf(Pair(R.string.action_delete, R.color.notification_error)),
                    icons = listOf(Pair(R.drawable.ic_trash, R.color.notification_error))
                ) {
                    lifecycleScope.launch { eventViewModel.handleDelete(navigationArguments.occurrenceNumber) }
                }
            }
        }

        loadingAction = layoutInflater.inflate(R.layout.toolbar_action_loader, dialog_toolbar_content, false)
        loadingAction.visibleOrGone(false)


        // TODO Hide buttons by default to avoid any case where edit would be possible. Remove once edit attendees is implemented
        buttonEdit.visibleOrGone(false)
        buttonMenu.visibleOrGone(false)

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            addView(
                buttonEdit, resources.getDimensionPixelSize(
                    R.dimen.action_clickable_size
                ), resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )
            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                buttonMenu, layoutParams
            )
            addView(
                loadingAction, layoutParams
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        section_answer.item_change_answer_button_yes.item_change_answer_button_title.text =
            getString(R.string.event_answer_yes)
        section_answer.item_change_answer_button_no.item_change_answer_button_title.text =
            getString(R.string.event_answer_no)
        section_answer.item_change_answer_button_maybe.item_change_answer_button_title.text =
            getString(R.string.event_answer_maybe)

        lifecycleScope.launch {

            if (!calendarViewModel.initialised) {
                //TODO Workaround since we create event details twice with current deeplink handling
                return@launch
            }

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus =
                if (userId == null) EventViewModel.Result.Error("user ID is null in EventDetailsFragment onViewCreated")
                else withContext(Dispatchers.Default) {
                    eventViewModel.initialise(
                        userId,
                        editMode = false,
                        navigationArguments.eventId,
                        if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                        null,
                        null
                    )
                }

            if (viewModeInitStatus == EventViewModel.Result.Success) {
                launch {
                    eventViewModel.getSingleEditsInfo(calendarViewModel.getUserEmails())
                }
                calendarViewModel.userAddresses.distinctUntilChanged().observe(viewLifecycleOwner) {
                    handleAttendeeAnswerViewVisibility()
                }
                observeEventLiveData(coroutineContext)
                observeEventDialogState(coroutineContext)
                observeEventSnackState(coroutineContext)
                attachActionHandlers()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.Result.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.Result.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.Result.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                    }
                }
                // Use jumpToMonthView to handle navigation when opening details from notification
                jumpToMonthView()
            }
        }
    }

    private fun observeEventSnackState(coroutineContext: CoroutineContext) {
        eventViewModel.eventSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventSnackState ->
            eventSnackState?.let {
                when (it) {
                    is EventViewModel.EventSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is EventViewModel.EventSnackState.DisplaySnackReturnToMonth -> {
                        requireActivity().displaySnackBar(it.message)

                        // Use jumpToMonthView to handle navigation when opening details from notification
                        jumpToMonthView()
                    }
                }
                eventViewModel.eventSnackState.value = null
            }
        }
    }

    private fun observeEventDialogState(coroutineContext: CoroutineContext) {
        eventViewModel.eventDialogState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventDialogState ->

            eventDialogState?.let {
                when (it) {
                    EventViewModel.EventDialogState.Delete.DisabledCalendarRecurring -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(
                                R.string.dialog_title_delete_recurring_event
                            )
                            .setMessage(
                                R.string.dialog_description_delete_recurring_event
                            )
                            .setPositiveButton(R.string.dialog_button_delete) { _, _ ->
                                lifecycleScope.launch {
                                    eventViewModel.handleDeleteDisabledCalendarRecurring()
                                }
                            }
                            .setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
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
                    EventViewModel.EventDialogState.Delete.Event -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(
                                R.string.dialog_title_delete_event
                            )
                            .setMessage(
                                R.string.dialog_description_delete_event
                            )
                            .setPositiveButton(R.string.dialog_button_delete) { _, _ ->
                                lifecycleScope.launch {
                                    eventViewModel.handleDeleteEvent(navigationArguments.occurrenceNumber)
                                }
                            }
                            .setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
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
                    is EventViewModel.EventDialogState.Delete.RecurringEvent -> {
                        var selectedItem = 0
                        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                        builder.setTitle(getString(R.string.dialog_title_delete_recurring_event))
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
                                lifecycleScope.launch {
                                    eventViewModel.handleDeleteRecurring(
                                        navigationArguments.occurrenceNumber,
                                        selectedItem,
                                        it.showThisAndFuture
                                    )
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
                    is EventViewModel.EventDialogState.Delete.AsAnOrganizer -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(
                                if (it.isPartOfChain) R.string.dialog_title_delete_recurring_event
                                else R.string.dialog_title_delete_event
                            )
                            .setMessage(
                                if (it.isPartOfChain && it.isCalendarDisabled) R.string.dialog_description_delete_recurring_event_as_organizer_disabled
                                else if (it.isPartOfChain) R.string.dialog_description_delete_recurring_event_as_organizer
                                else if (it.isCalendarDisabled) R.string.dialog_description_delete_event_as_organizer_disabled
                                else R.string.dialog_description_delete_event_as_organizer
                            )
                            .setPositiveButton(R.string.dialog_button_delete) { _, _ ->
                                lifecycleScope.launch {
                                    eventViewModel.handleDeleteAsOrganizerSendPreferences(
                                        it.isPartOfChain,
                                        it.isCalendarDisabled,
                                        calendarViewModel.timeFormatIs24Hour(requireContext())
                                    )
                                }
                            }
                            .setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
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
                    is EventViewModel.EventDialogState.Delete.AsAnAttendeeSendPreferences -> {

                        val emailsWithErrors = TextUtils.join("\n• ", it.sendPreferencesResults.emailErrors.map { entry ->
                            resources.getString(R.string.event_send_prefs_error_template, entry.key, entry.value.formatSendPreferencesError(resources))
                        })
                        val sendPrefsFailed = emailsWithErrors.isNotEmpty()
                        val displayWarning = (it.currentParticipationStatus == ParticipationStatus.ACCEPTED ||
                                it.currentParticipationStatus == ParticipationStatus.TENTATIVE) &&
                                it.isEventCanceled.not() &&
                                !it.isCalendarDisabled

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(
                                if (sendPrefsFailed && displayWarning) R.string.event_organizer_send_prefs_error_title
                                else if (it.isRecurring || (it.isSingleEdit && it.isStandaloneSingleEdit.not())) R.string.dialog_title_delete_recurring_event
                                else R.string.dialog_title_delete_event
                            )
                            .setMessage(
                                if (displayWarning.not()) {
                                    if (it.isSingleEdit && (it.isSingleEdit && it.isStandaloneSingleEdit.not()) && it.isCalendarDisabled.not()) getString(R.string.dialog_description_delete_non_standalone_single_edit_event)
                                    else if (it.isRecurring || (it.isSingleEdit && it.isStandaloneSingleEdit.not() && it.isCalendarDisabled)) getString(R.string.dialog_description_delete_recurring_event)
                                    else getString(R.string.dialog_description_delete_event)
                                } else {
                                    if (sendPrefsFailed) getString(R.string.event_delete_as_attendee_send_prefs_error_message, emailsWithErrors)
                                    else if (it.isRecurring) {
                                        val message = if (it.isAddressDisabled) getString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled)
                                        else getString(R.string.dialog_description_delete_recurring_event_as_attendee)

                                        val singleEditWarning = if (it.hasAnsweredSingleEdit) getString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee)
                                        else if (it.hasNonCancelledSingleEdit) getString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee)
                                        else ""

                                        if (singleEditWarning.isNotEmpty()) getString(R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee, message, singleEditWarning)
                                        else message
                                    }
                                    else if (it.isSingleEdit && it.isStandaloneSingleEdit.not() && it.isAddressDisabled) getString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee_disabled)
                                    else if (it.isSingleEdit && it.isStandaloneSingleEdit.not()) getString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee)
                                    else if (it.isAddressDisabled) getString(R.string.dialog_description_delete_single_event_as_attendee_disabled)
                                    else getString(R.string.dialog_description_delete_single_event_as_attendee)
                                }
                            )
                            .setPositiveButton(R.string.dialog_button_delete) { _, _ ->
                                lifecycleScope.launch {
                                    eventViewModel.handleDeleteEventAsAttendee(
                                        it.userAddress,
                                        it.sendPreferencesResults.sendPreferences,
                                        it.hasNonCancelledSingleEdit,
                                        it.hasAnsweredSingleEdit,
                                        navigationArguments.occurrenceNumber,
                                        it.isStandaloneSingleEdit
                                    )
                                }
                            }
                            .setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
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
                    is EventViewModel.EventDialogState.Delete.AsAnOrganizerSendPreferences -> {

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
                                    val attendees = eventViewModel.eventLiveData.value?.iCalEvent?.attendees
                                    if (attendees.isNullOrEmpty()) {

                                        return@launch
                                    }
                                    attendees.removeIf { attendee ->
                                        it.sendPreferencesResults.emailErrors.any { emailError ->
                                            attendee.extractEmail() == emailError.key
                                        }
                                    }

                                    eventViewModel.handleDeleteEventAsOrganizer(
                                        attendees,
                                        it.sendPreferencesResults.sendPreferences,
                                        calendarViewModel.timeFormatIs24Hour(requireContext()),
                                        it.isPartOfChain,
                                        it.isCalendarDisabled
                                    )
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
                    is EventViewModel.EventDialogState.ChangeAnswer.RecurringEvent -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.event_change_answer_recurring_title)
                            .setMessage(
                                when(it.dialogType) {
                                    EventViewModel.ChangeAnswerRecurringDialogType.OVERWRITE -> R.string.event_change_answer_recurring_overwrite_description
                                    EventViewModel.ChangeAnswerRecurringDialogType.SINGLE_EDIT -> R.string.event_change_answer_recurring_single_edit_description
                                    else -> R.string.event_change_answer_recurring_description
                                }
                            )
                            .setPositiveButton(R.string.event_change_answer_recurring_confirm) { _, _ ->
                                lifecycleScope.launch {
                                    if (!eventViewModel.handleChangeAnswerSendPreferences(it.participationStatus)) {
                                        eventViewModel.eventState.value = EventViewModel.EventState.Idle
                                        view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                                        displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
                                    }
                                }
                            }
                            .setNegativeButton(R.string.event_change_answer_recurring_cancel) { _, _ ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                                displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
                            }
                            .setOnCancelListener {
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                                displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
                            }
                            .setOnDismissListener {
                                eventViewModel.eventDialogState.value = null
                            }
                            .show()
                    }
                    is EventViewModel.EventDialogState.ChangeAnswer.SendPreferences -> {
                        // Reset change answer buttons to previous state
                        displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)

                        val errorMessage = getString(when (it.participationStatus) {
                            ParticipationStatus.ACCEPTED -> R.string.event_organizer_send_prefs_message_accepted_title
                            ParticipationStatus.DECLINED -> R.string.event_organizer_send_prefs_message_declined_title
                            ParticipationStatus.TENTATIVE -> R.string.event_organizer_send_prefs_message_tentative_title
                            else -> R.string.event_organizer_send_prefs_message_default_title
                        })

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.event_organizer_send_prefs_error_title)
                            .setMessage(resources.getString(R.string.event_send_prefs_error_template, errorMessage, it.obtainError.formatSendPreferencesError(resources)))
                            .setPositiveButton(R.string.event_organizer_send_prefs_button_title) { _, _ ->
                            }
                            .setOnCancelListener { _ ->
                                eventViewModel.eventState.value = EventViewModel.EventState.Idle
                                displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
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

    private fun attachActionHandlers() {
        section_location.text_header.setOnSingleClickListener {
            eventViewModel.eventLiveData.value?.location?.let {
                if (!mainViewModel.handleEventLocationShow(it)) {
                    logger.i("could not show location on map")
                }
            }
        }
        section_location.image_button_action.setOnSingleClickListener {
            eventViewModel.eventLiveData.value?.location?.let {
                if (mainViewModel.handleCopyToClipboard(eventViewModel.eventLiveData.value?.location as String /*TODO after get()*/)) {
                    view?.displaySnackBar(requireContext().getString(R.string.toast_copied_to_clipboard))
                } else {
                    logger.i("could not copy to clipboard")
                }
            }
        }

        section_answer.item_change_answer_button_yes.item_change_answer_button_press.setOnSingleClickListener {
            lifecycleScope.launch {
                // Ignore the result if true
                if (!eventViewModel.handleChangeAnswer(ParticipationStatus.ACCEPTED)) {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                    displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
                }
            }
        }
        section_answer.item_change_answer_button_no.item_change_answer_button_press.setOnSingleClickListener {
            lifecycleScope.launch {
                // Ignore the result if true
                if (!eventViewModel.handleChangeAnswer(ParticipationStatus.DECLINED)) {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                    displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
                }
            }
        }
        section_answer.item_change_answer_button_maybe.item_change_answer_button_press.setOnSingleClickListener {
            lifecycleScope.launch {
                // Ignore the result if true
                if (!eventViewModel.handleChangeAnswer(ParticipationStatus.TENTATIVE)) {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                    displayAttendeeAnswerState(eventViewModel.currentParticipationStatus)
                }
            }
        }
    }

    // TODO remove when the issue with invalid sender Address is fixed
    private suspend fun invalidUserAddressLogoutHack() {

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, false)) {
            logger.e("EventDetailsFragment: hack was performed but we force logout again")
        }

        with (sharedPreferences.edit()) {
            putBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, true)
            apply()
        }

        logger.i("EventDetailsFragment: hack detected invalid user address, logging out")
        accountViewModel.logoutPrimary()
        calendarViewModel.shutdown()
    }

    private fun observeEventLiveData(coroutineContext: CoroutineContext) {

        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { event: Event ->
            (requireActivity() as? MainActivity)?.displaySplashScreen(false)

            eventViewModel.eventState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventState ->
                // Update action bar buttons visibility
                val deletingEvent = eventState == EventViewModel.EventState.Processing.Deleting
                loadingAction.visibleOrGone(deletingEvent)
                // TODO Remove attendees condition once edit attendees is implemented
                buttonEdit.visibleOrGone(event.calendar.isActive && !event.isAnInvitation && !deletingEvent && !event.calendar.isSubscribed)

                val enableDeleteEvents = !deletingEvent && !event.calendar.isSubscribed
                buttonMenu.visibleOrGone(enableDeleteEvents)

                if (eventState is EventViewModel.EventState.Processing.ChangingAnswer) displayAttendeeAnswerState(eventState.participationStatus)

                if (eventState is EventViewModel.EventState.UserAddressInvalidForEncryption) {
                    lifecycleScope.launch {
                        invalidUserAddressLogoutHack()
                    }
                }
            }

            // TODO when we perform "edit this", new event is created and it won't automatically refresh here
            //  because we're still listening for the old event.id !!!

            logger.d("uid: ${event.iCalEvent.uid}")
            logger.d("id: ${event.id}")

            // TODO HIDE YEAR WHEN IT'S THE SAME AS CURRENT

            // TODO display signature verification result
//                text_event_title.text = "SIGNATURE VERIFICATION: ${event.verificationStatus}\n\n" + event.summary + "\n"

            with(section_event_info) {

                this.view_calendar_bar.background.setTint(Color.parseColor(event.calendar.color))

                if (event.status != null) {
                    if ((event.status as Status).isCancelled) {
                        this.text_status.visibleOrGone(true)
                        this.text_status.text = getString(R.string.event_status_cancelled)
                    }
                }

                this.text_summary.text =
                    event.summary?.nullIfBlank() ?: resources.getString(R.string.default_event_summary)

                this.text_date_time.text = event.formatStartEndForActualEndDate(
                    eventViewModel.displayTimeZoneId,
                    resources,
                    eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
                )

                if (event.isRecurring()) {
                    this.text_recurrence.visibleOrGone(true)
                    this.text_recurrence.text = AndroidUtils.formatRecurrence(
                        requireContext().resources,
                        event,
                        eventViewModel.eventTimeZoneId
                    )
                }

                visibleOrGone(true)
            }

            event.location?.nullIfBlank()?.let {
                with(section_location) {
                    text_header.text = event.location
                    val typedValue = TypedValue()
                    requireContext().theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        typedValue,
                        true
                    )
                    text_header.isClickable = true
                    text_header.setBackgroundResource(typedValue.resourceId)

                    image_icon.setImageResource(R.drawable.ic_map_marker)
                    image_button_action.setImageResource(R.drawable.ic_copy_clipboard)
                    image_button_action.visibleOrInvisible(true)
                    visibleOrGone(true)
                }
            }

            with(section_calendar) {
                text_header.text = if (event.calendar.isActive) {
                    event.calendar.name
                } else {
                    requireContext().getText(R.string.event_calendar_disabled, event.calendar.name)
                }
                //Set icon view to Invisible to keep the text view constraints
                image_icon.visibleOrInvisible(false)
                image_dot_icon.visibleOrGone(true)
                image_dot_icon.drawable.setTint(Color.parseColor(event.calendar.color))
                visibleOrGone(true)
            }

            val alarmLabels = event.iCalEvent.alarms.filter { it.action == Action.display() || it.action == Action.email() }
                .sortedBy { it.trigger.duration.toMillis() }
                .mapNotNull { alarm ->
                    AndroidUtils.formatAlarm(
                        resources,
                        event.isAllDay(),
                        calendarViewModel.timeFormatIs24Hour(requireContext()),
                        event.getStart(eventViewModel.displayTimeZoneId),
                        alarm
                    )
                }

            section_alarms.visibleOrGone(alarmLabels.isNotEmpty())
            if (alarmLabels.isNotEmpty()) {
                with(section_alarms) {
                    text_header.text = alarmLabels.joinToString(separator = "\n")
                    image_icon.setImageResource(R.drawable.ic_bell)
                }
            }

            event.description?.nullIfBlank()?.let {
                with(section_description) {
                    text_header.text = event.description
                    Linkify.addLinks(text_header, Linkify.ALL)
                    image_icon.setImageResource(R.drawable.ic_text_align_left)
                    visibleOrGone(true)
                }
            }

            // Make a copy of the list so we can freely remove organizer if also is an attendee
            val attendeeList = ArrayList(event.iCalEvent.attendees)
            section_attendees.visibleOrGone(event.iCalEvent.organizer != null && attendeeList.isNotEmpty())
            if (attendeeList.isNotEmpty()) {
                initParticipantsItem(attendeeList)

                // Check if organizer is also an attendee to display its status
                val organizerAttendee =
                    attendeeList.find { it.extractEmail().equals(event.iCalEvent.organizer.extractEmail(), ignoreCase = true) }
                val organizer = event.iCalEvent.organizer
                if (organizer != null) initOrganizerItem(organizer, organizerAttendee)

                initAttendeeList(attendeeList, organizerAttendee)

                handleAttendeeAnswerViewVisibility()
            }
        })
    }

    private fun handleAttendeeAnswerViewVisibility() {
        if (!CHANGE_ANSWER) {
            // TODO Remove feature flag
            section_answer.visibleOrGone(false)
            return
        }
        lifecycleScope.launch {
            val isFreeUser = eventViewModel.user.hasSubscription().not()
            val event = eventViewModel.eventLiveData.value
            val userEmails = calendarViewModel.getUserEmails()
            val userAddresses = calendarViewModel.userAddresses.value
            if (event != null && userAddresses != null && userEmails != null && !event.calendar.isSubscribed) {
                val isActive = event.calendar.isActive
                val isAddressActive = event.isUserAddressAllowedSend(userAddresses, isFreeUser)
                val participationStatus = event.getParticipationStatus(userEmails)

                if (participationStatus != null && isActive && isAddressActive && !event.isCancelled()) {
                    section_answer.visibleOrGone(true)
                    displayAttendeeAnswerState(participationStatus)
                } else section_answer.visibleOrGone(false)
            } else section_answer.visibleOrGone(false)
        }
    }

    private fun initParticipantsItem(attendeeList: List<Attendee>) {
        event_attendees_title.text = resources.getString(
            R.string.event_attendee_count,
            attendeeList.size,
            resources.getQuantityString(
                R.plurals.plural_participant_uppercase,
                attendeeList.size
            )
        )

        // Create map containing number of attendees for each participation status
        val statusMap = hashMapOf<ParticipationStatus, Int>()
        attendeeList.forEach {
            // Non handled participation status are considered as NEEDS_ACTION
            if (it.participationStatus != null
                && (it.participationStatus == ParticipationStatus.ACCEPTED ||
                        it.participationStatus == ParticipationStatus.TENTATIVE ||
                        it.participationStatus == ParticipationStatus.DECLINED ||
                        it.participationStatus == ParticipationStatus.NEEDS_ACTION)
            ) {
                statusMap[it.participationStatus] =
                    1 + (statusMap[it.participationStatus] ?: 0)
            } else {
                statusMap[ParticipationStatus.NEEDS_ACTION] =
                    1 + (statusMap[ParticipationStatus.NEEDS_ACTION] ?: 0)
            }
        }
        var attendeesStatusDescription = buildAttendeesStatusesDescription(
            statusMap[ParticipationStatus.ACCEPTED],
            R.string.event_attendee_yes,
            ""
        )
        attendeesStatusDescription = buildAttendeesStatusesDescription(
            statusMap[ParticipationStatus.TENTATIVE],
            R.string.event_attendee_maybe,
            attendeesStatusDescription
        )
        attendeesStatusDescription = buildAttendeesStatusesDescription(
            statusMap[ParticipationStatus.DECLINED],
            R.string.event_attendee_no,
            attendeesStatusDescription
        )
        attendeesStatusDescription = buildAttendeesStatusesDescription(
            statusMap[ParticipationStatus.NEEDS_ACTION],
            R.string.event_attendee_unanswered,
            attendeesStatusDescription
        )
        event_attendees_description.text = attendeesStatusDescription
    }

    private fun buildAttendeesStatusesDescription(
        statusCount: Int?,
        statusStringId: Int,
        previousStatusString: String
    ): String {
        return if (statusCount != null && previousStatusString.isNotEmpty())
            resources.getString(
                R.string.event_attendee_status_separator,
                previousStatusString,
                resources.getString(statusStringId, statusCount)
            )
        else if (statusCount != null && previousStatusString.isEmpty())
            resources.getString(statusStringId, statusCount)
        else previousStatusString
    }

    private fun initOrganizerItem(organizer: Organizer, organizerAttendee: Attendee?) {
        // TODO stop using field from Activity once we have actual user management
        val userEmails = calendarViewModel.getUserEmails()
        event_attendee_organizer_layout.item_attendee_description.visibleOrGone(true)
        val organizerEmail = organizer.extractEmail()
        if (organizerEmail != null && userEmails?.contains(canonicalizeProtonEmail(organizerEmail)) == true) {
            event_attendee_organizer_layout.item_attendee_title.text =
                resources.getString(R.string.event_attendee_is_organizer)
            event_attendee_organizer_layout.item_attendee_description.text = organizer.extractEmail()
        } else {
            event_attendee_organizer_layout.item_attendee_title.text = organizer.extractEmail()
            event_attendee_organizer_layout.item_attendee_description.text =
                resources.getString(R.string.event_attendee_organizer)
        }
        event_attendee_organizer_layout.item_attendee_initials.text = getInitials(organizer.extractEmail() ?: "")

        val organizerStatus = event_attendee_organizer_layout.item_attendee_status
        if (organizerAttendee != null && organizerAttendee.participationStatus != null) {
            initAttendeeStatus(organizerStatus, organizerAttendee.participationStatus, requireContext())
        } else organizerStatus.visibleOrGone(false)
    }

    private var attendeesListHeight: Int? = null
    private fun initAttendeeList(attendeeList: MutableList<Attendee>, organizerAttendee: Attendee?) {
        val attendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        event_attendee_list.layoutManager = attendeesLayoutManager
        attendeeListAdapter = AttendeeListAdapter()
        (event_attendee_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        event_attendee_list.adapter = attendeeListAdapter

        // Remove organizer attendee from the list if it exists to avoid duplicates
        if (organizerAttendee != null) attendeeList.remove(organizerAttendee)

        // Sort list by Participation status in following order : Accepted > Tentative > Declined > Needs action
        val sortedAttendeeList =
            attendeeList.sortedWith(compareBy { getParticipationStatusPriorityValue(it.participationStatus) })
        attendeeListAdapter.submitList(sortedAttendeeList)

        // Reset LayoutParams
        event_attendee_list.layoutParams.width = RecyclerView.LayoutParams.MATCH_PARENT
        event_attendee_list.layoutParams.height = RecyclerView.LayoutParams.WRAP_CONTENT

        if (attendeesListHeight == null) {
            if (attendeeListAdapter.itemCount <= ATTENDEE_AUTO_EXPAND_LIMIT && sortedAttendeeList.isNotEmpty()) {
                event_attendee_list.visibleOrGone(true)
                rotateArrowUpward(event_attendees_button, 0)
            } else if (sortedAttendeeList.isEmpty() && organizerAttendee != null) {
                event_attendee_list.visibleOrGone(false)
                event_attendees_button.visibleOrGone(false)
                event_attendees_press.visibleOrGone(false)
                return
            }
        }

        // Reset view height
        attendeesListHeight = null

        event_attendees_press.setOnClickListener {
            if (event_attendee_list.isVisible) {
                // Save expanded view height only once
                val height = collapse(event_attendee_list).first
                if (attendeesListHeight == null) attendeesListHeight = height
                rotateArrowDownward(event_attendees_button)
            } else {
                // TODO: Workaround for special case where desired height is not properly calculated.
                //  Passing 0 skips the animation.
                //  It means that List with more than 5 items will not have expand animation on first expand.
                expand(event_attendee_list, height = attendeesListHeight ?: 0)
                rotateArrowUpward(event_attendees_button)
            }
        }
    }

    private fun displayAttendeeAnswerState(participationStatus: ParticipationStatus?) {
        val loading = eventViewModel.eventState.value is EventViewModel.EventState.Processing.ChangingAnswer

        section_answer.item_change_answer_button_yes.item_change_answer_button_layout.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.woodsmoke))
        section_answer.item_change_answer_button_no.item_change_answer_button_layout.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.woodsmoke))
        section_answer.item_change_answer_button_maybe.item_change_answer_button_layout.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.woodsmoke))

        section_answer.item_change_answer_button_yes.item_change_answer_button_title.setTextColor(
            resources.getColor(R.color.white, null)
        )
        section_answer.item_change_answer_button_no.item_change_answer_button_title.setTextColor(
            resources.getColor(R.color.white, null)
        )
        section_answer.item_change_answer_button_maybe.item_change_answer_button_title.setTextColor(
            resources.getColor(R.color.white, null)
        )

        section_answer.item_change_answer_button_yes.item_change_answer_button_title.visibleOrInvisible(true)
        section_answer.item_change_answer_button_yes.item_change_answer_button_loader.visibleOrGone(false)
        section_answer.item_change_answer_button_no.item_change_answer_button_title.visibleOrInvisible(true)
        section_answer.item_change_answer_button_no.item_change_answer_button_loader.visibleOrGone(false)
        section_answer.item_change_answer_button_maybe.item_change_answer_button_title.visibleOrInvisible(true)
        section_answer.item_change_answer_button_maybe.item_change_answer_button_loader.visibleOrGone(false)

        when (participationStatus) {
            ParticipationStatus.ACCEPTED -> {
                section_answer.item_change_answer_button_yes.item_change_answer_button_layout.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
                section_answer.item_change_answer_button_yes.item_change_answer_button_title.setTextColor(
                    resources.getColor(R.color.woodsmoke, null)
                )
                section_answer.item_change_answer_button_yes.item_change_answer_button_title.visibleOrInvisible(!loading)
                section_answer.item_change_answer_button_yes.item_change_answer_button_loader.visibleOrGone(loading)
            }
            ParticipationStatus.DECLINED -> {
                section_answer.item_change_answer_button_no.item_change_answer_button_layout.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
                section_answer.item_change_answer_button_no.item_change_answer_button_title.setTextColor(
                    resources.getColor(R.color.woodsmoke, null)
                )
                section_answer.item_change_answer_button_no.item_change_answer_button_title.visibleOrInvisible(!loading)
                section_answer.item_change_answer_button_no.item_change_answer_button_loader.visibleOrGone(loading)
            }
            ParticipationStatus.TENTATIVE -> {
                section_answer.item_change_answer_button_maybe.item_change_answer_button_layout.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
                section_answer.item_change_answer_button_maybe.item_change_answer_button_title.setTextColor(
                    resources.getColor(R.color.woodsmoke, null)
                )
                section_answer.item_change_answer_button_maybe.item_change_answer_button_title.visibleOrInvisible(!loading)
                section_answer.item_change_answer_button_maybe.item_change_answer_button_loader.visibleOrGone(loading)
            }
        }
    }

}
