package me.proton.android.calendar.presentation.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.format.DateFormat
import android.text.util.Linkify
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
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
import me.proton.android.calendar.common.FeatureFlag.CHANGE_ANSWER
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainActivity
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.collections.ArrayList


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

    override fun onBackPressedCustom() {

        // TODO this is a workaround for deeplinks not navigating up to direct parent, but to navigation's start destination
        //  1. see if nested graphs work when we get rid of dialogs in favor of fragments
        //  2. see if handling deeplink straight from notification (not indirectly from MainActivity and navigating manually)
        //  fixes this
        if (findNavController().previousBackStackEntry?.destination?.id != R.id.nav_calendar) {
            findNavController().navigate(Navigation.Deeplink.toMonth(eventViewModel.eventLiveData.value?.startLocalDate))
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {

        buttonEdit = layoutInflater.inflate(R.layout.toolbar_action_secondary, dialog_toolbar_content, false)
        with (buttonEdit) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_pencil))
            setOnSingleClickListener {
                findNavController().navigate(
                    (Navigation.Deeplink.toEventEdit(
                        navigationArguments.eventId,
                        navigationArguments.occurrenceNumber
                    )))
            }
        }
        buttonMenu = layoutInflater.inflate(R.layout.toolbar_action_secondary, dialog_toolbar_content, false)
        with (buttonMenu) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_three_dots_vertical))
            setOnSingleClickListener {
                AndroidUtils.displayPopupMenu(
                    view = it,
                    labels = listOf(Pair(R.string.action_delete, R.color.notification_error)),
                    icons = listOf(Pair(R.drawable.ic_trash, R.color.notification_error))
                ) {
                    handleDelete()
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
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                buttonMenu, layoutParams
            )
            addView(
                loadingAction, layoutParams
            )
        }
    }

    private fun handleDelete() {
        val event = eventViewModel.eventLiveData.value!!
        val dbEvent = eventViewModel.dbEvent

        if (event.isPartOfChain() &&
            dbEvent?.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId) == false &&
            event.calendar.isActive) {

            val showThisAndFuture = navigationArguments.occurrenceNumber > 1 &&
                    !event.isEventFirstOccurrence(dbEvent, eventViewModel.displayTimeZoneId)
            AndroidUtils.displaySingleChoiceConfirmationPicker(
                requireContext(), getString(R.string.dialog_title_delete_recurring_event), listOfNotNull(
                    getString(R.string.event_recurring_edit_this),
                    if (showThisAndFuture) getString(R.string.event_recurring_edit_this_and_future)
                    else null,
                    getString(R.string.event_recurring_edit_all_events)
                ).toTypedArray(), 0
            ) {

                lifecycleScope.launch {

                    // Post deleting event value to true to display loading state
                    eventViewModel.savingEvent.postValue(true)

                    val deleteResult = withContext(Dispatchers.IO) {
                        if (it == 0) {
                            calendarViewModel.handleDeleteEvent(
                                event.id,
                                EventEditDeleteOption.THIS_EVENT,
                                navigationArguments.occurrenceNumber
                            )
                        } else if (it == 1) {
                            if (showThisAndFuture) {
                                calendarViewModel.handleDeleteEvent(
                                    event.id,
                                    EventEditDeleteOption.THIS_EVENT_AND_FUTURE,
                                    navigationArguments.occurrenceNumber
                                )
                            } else {
                                calendarViewModel.handleDeleteEvent(
                                    event.id,
                                    EventEditDeleteOption.ALL_EVENTS
                                )
                            }
                        } else { // it == 2
                            calendarViewModel.handleDeleteEvent(
                                event.id,
                                EventEditDeleteOption.ALL_EVENTS
                            )
                        }
                    }

                    // Post deleting event value to false to stop loading state
                    eventViewModel.savingEvent.postValue(false)

                    if (deleteResult is UseCase.Result.Success<*>) {
                        requireActivity().displaySnackBar(getString(R.string.snack_event_deleted))
                        // Use onBackPressedCustom to handle navigation when opening details from notification
                        onBackPressedCustom()
                    } else {

                        if (deleteResult is UseCase.Result.Error) {
                            logger.e("Error deleting event: ${deleteResult.message}")
                        } else if (deleteResult is UseCase.Result.InvalidParams) {
                            logger.e("InvalidParams deleting event: ${deleteResult.message}")
                        }

                        view?.displaySnackBar(getString(R.string.snack_event_deleted_error))
                    }

                }
            }

        } else { // TODO unify showing dialog
            // TODO Check if we need to handle inactive calendars the same way
            val disabledCalendarRecurringEvent = event.calendar.isDisabled &&
                    event.isPartOfChain() &&
                    eventViewModel.dbEvent?.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId) == false
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(
                    if (disabledCalendarRecurringEvent) R.string.dialog_title_delete_recurring_event
                    else R.string.dialog_title_delete_event)
                .setMessage(
                    if (disabledCalendarRecurringEvent) R.string.dialog_description_delete_recurring_event
                    else R.string.dialog_description_delete_event)
                .setPositiveButton(R.string.dialog_button_delete) { dialog, which ->
                    lifecycleScope.launch { // TODO

                        // Post deleting event value to true to display loading state
                        eventViewModel.savingEvent.postValue(true)

                        val deleteResult = withContext(Dispatchers.Default) {
                            if (eventViewModel.dbEvent?.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId) == true
                                || disabledCalendarRecurringEvent) {
                                calendarViewModel.handleDeleteEvent(
                                    event.id,
                                    EventEditDeleteOption.ALL_EVENTS
                                )
                            } else {
                                calendarViewModel.handleDeleteEvent(
                                    event.id,
                                    EventEditDeleteOption.THIS_EVENT,
                                    navigationArguments.occurrenceNumber
                                )
                            }
                        }

                        // Post deleting event value to false to stop loading state
                        eventViewModel.savingEvent.postValue(false)

                        if (deleteResult is UseCase.Result.Success<*>) {
                            requireActivity().displaySnackBar(getString(R.string.snack_event_deleted))
                            // Use onBackPressedCustom to handle navigation when opening details from notification
                            onBackPressedCustom()
                        } else {

                            if (deleteResult is UseCase.Result.Error) {
                                logger.e("Error deleting event: ${deleteResult.message}")
                            } else if (deleteResult is UseCase.Result.InvalidParams) {
                                logger.e("InvalidParams deleting event: ${deleteResult.message}")
                            }

                            view?.displaySnackBar(getString(R.string.snack_event_deleted_error))
                        }
                    }
                }
                .setNegativeButton(R.string.dialog_button_cancel) { dialog, which ->
                }
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        section_answer.item_change_answer_button_yes.item_change_answer_button_title.text = getString(R.string.event_answer_yes)
        section_answer.item_change_answer_button_no.item_change_answer_button_title.text = getString(R.string.event_answer_no)
        section_answer.item_change_answer_button_maybe.item_change_answer_button_title.text = getString(R.string.event_answer_maybe)

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
                        null,
                    )
                }

            if (viewModeInitStatus == EventViewModel.Result.Success) {
                launch {
                    eventViewModel.getSingleEditsInfo(calendarViewModel.getUserEmails())
                }
                calendarViewModel.userAddresses.observe(viewLifecycleOwner) {
                    handleAttendeeAnswerViewVisibility()
                }
                observeEventLiveData()
                attachActionHandlers()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.Result.OccurrenceDoesntExist -> {
                        AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.error_occurrence_doesnt_exist))
                    }
                    EventViewModel.Result.EventDoesntExist -> {
                        AndroidUtils.displaySimpleOkAlert(requireContext(), getString(R.string.error_event_doesnt_exist))
                    }
                    is EventViewModel.Result.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                    }
                }
                // Use onBackPressedCustom to handle navigation when opening details from notification
                onBackPressedCustom()
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
            onChangeAnswerClick(ParticipationStatus.ACCEPTED)
        }
        section_answer.item_change_answer_button_no.item_change_answer_button_press.setOnSingleClickListener {
            onChangeAnswerClick(ParticipationStatus.DECLINED)
        }
        section_answer.item_change_answer_button_maybe.item_change_answer_button_press.setOnSingleClickListener {
            onChangeAnswerClick(ParticipationStatus.TENTATIVE)
        }
    }

    private fun onChangeAnswerClick(newParticipationStatus: ParticipationStatus) {
        if (eventViewModel.changeAnswerLoading.value == true) return
        val userEmails = calendarViewModel.getUserEmails()
        userEmails?.let {
            val participationStatus = eventViewModel.eventLiveData.value?.getParticipationStatus(it)
            if (participationStatus != newParticipationStatus) {
                handleChangeAnswer(
                    newParticipationStatus,
                    participationStatus ?: ParticipationStatus.NEEDS_ACTION,
                    userEmails
                )
            }
        }
    }

    private fun handleChangeAnswer(
        participationStatus: ParticipationStatus,
        currentParticipationStatus: ParticipationStatus,
        userEmails: List<String>
    ) {
        lifecycleScope.launch {
            // Display loading state for new value
            displayAttendeeAnswerState(participationStatus, true)
            if (eventViewModel.eventLiveData.value?.isPartOfChain() == true) {
                val isSingleEdit = eventViewModel.eventLiveData.value?.isSingleEdit() ?: false
                val isStandaloneSingleEdit = if (isSingleEdit) eventViewModel.isStandaloneSingleEdit() else false
                val overwrite =
                    if (isSingleEdit) false
                    else eventViewModel.getSingleEditsInfo(userEmails)?.hasAnsweredSingleEdit == true

                if (isStandaloneSingleEdit) {
                    updateAttendeeParticipationStatus(participationStatus, currentParticipationStatus, userEmails)
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.event_change_answer_recurring_title)
                        .setMessage(
                            when {
                                overwrite -> R.string.event_change_answer_recurring_overwrite_description
                                isSingleEdit -> R.string.event_change_answer_recurring_single_edit_description
                                else -> R.string.event_change_answer_recurring_description
                            }
                        )
                        .setPositiveButton(R.string.event_change_answer_recurring_confirm) { _, _ ->
                            updateAttendeeParticipationStatus(participationStatus, currentParticipationStatus, userEmails)
                        }
                        .setNegativeButton(R.string.event_change_answer_recurring_cancel) { _, _ ->
                            displayAttendeeAnswerState(currentParticipationStatus, false)
                        }
                        .setOnCancelListener {
                            displayAttendeeAnswerState(currentParticipationStatus, false)
                        }
                        .show()
                }
            } else {
                updateAttendeeParticipationStatus(participationStatus, currentParticipationStatus, userEmails)
            }
        }
    }

    private fun updateAttendeeParticipationStatus(
        participationStatus: ParticipationStatus,
        currentParticipationStatus: ParticipationStatus,
        userEmails: List<String>
    ) {
        lifecycleScope.launch {
            val event = eventViewModel.eventLiveData.value
            if (event == null) {
                displayAttendeeAnswerState(currentParticipationStatus, false)
                view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                return@launch
            }
            val calendarId = event.calendar.id
            val eventId = event.id
            val attendeeId = event.currentUserAttendeeId
            if (attendeeId.isNullOrEmpty()) {
                displayAttendeeAnswerState(currentParticipationStatus, false)
                view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                return@launch
            }

            if (eventViewModel.updateParticipationStatus(
                    calendarId,
                    eventId,
                    attendeeId,
                    participationStatus,
                    userEmails
                )) {
                eventViewModel.handleParticipationStatus(userEmails, participationStatus)
            } else {
                // TODO Use custom error messages depending on error ("Cannot send to organizer: ${sendPreferenceErrorMessage}")
                view?.displaySnackBar(requireContext().getString(R.string.snack_change_attendee_answer_error))
                displayAttendeeAnswerState(currentParticipationStatus, false)
            }
        }
    }

    private fun observeEventLiveData() {

        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { event: Event ->
            (requireActivity() as? MainActivity)?.displaySplashScreen(false)

            eventViewModel.savingEvent.observe(viewLifecycleOwner, Observer { savingEvent: Boolean ->
                // Update action bar buttons visibility
                loadingAction.visibleOrGone(savingEvent)
                // TODO Remove attendees condition once edit attendees is implemented
                buttonEdit.visibleOrGone(event.calendar.isActive && event.iCalEvent.attendees.isNullOrEmpty() && !savingEvent)
                buttonMenu.visibleOrGone(event.iCalEvent.attendees.isNullOrEmpty() && !savingEvent)
            })

            // TODO when we perform "edit this", new event is created and it won't automatically refresh here
            //  because we're still listening for the old event.id !!!

            logger.d("GOT EVENT IN DETAILS FRAGMENT: $event")
            logger.d("navigation occurrence number: ${navigationArguments.occurrenceNumber}")
            logger.d("${event?.iCalendar?.printToString()}")
            logger.d("uid: ${event?.iCalEvent?.uid}")
            logger.d("id: ${event?.id}")

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
                        requireContext(),
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

            // TODO Remove filter once other type of alarms are handled
            val alarmLabels = event.iCalEvent.alarms.filter { it.action == Action.display() }.sortedBy { it.trigger.duration.toMillis() }
                .mapNotNull { alarm ->
                    AndroidUtils.formatAlarm(
                        resources,
                        event.isAllDay(),
                        calendarViewModel.timeFormatIs24Hour(requireContext()),
                        event.iCalEvent.getStart(eventViewModel.displayTimeZoneId)!!,
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        text_header.text = Html.fromHtml(event.description, Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        text_header.text = Html.fromHtml(event.description)
                    }
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
                val organizerAttendee = attendeeList.find { it.email.equals(event.iCalEvent.organizer.extractEmail(), ignoreCase = true) }
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
        val event = eventViewModel.eventLiveData.value
        val userEmails = calendarViewModel.getUserEmails()
        val userAddresses = calendarViewModel.userAddresses.value
        if (event != null && userAddresses != null && userEmails != null) {
            lifecycleScope.launch {
                val isActive = event.calendar.isActive
                val isAddressActive = event.isUserInvitedAddressEnabled(userAddresses)
                val participationStatus = event.getParticipationStatus(userEmails)

                if (participationStatus != null && isActive && isAddressActive && !event.isCancelled()) {
                    section_answer.visibleOrGone(true)
                    displayAttendeeAnswerState(participationStatus)
                } else section_answer.visibleOrGone(false)
            }
        } else section_answer.visibleOrGone(false)
    }

    private fun initParticipantsItem(attendeeList: List<Attendee>) {
        event_attendees_title.text = resources.getString(
            R.string.event_attendee_count,
            attendeeList.size,
            resources.getQuantityString(
                R.plurals.plural_participant_uppercase,
                attendeeList.size
            ))

        // Create map containing number of attendees for each participation status
        val statusMap = hashMapOf<ParticipationStatus, Int>()
        attendeeList.forEach {
            // Non handled participation status are considered as NEEDS_ACTION
            if (it.participationStatus != null
                && (it.participationStatus == ParticipationStatus.ACCEPTED ||
                        it.participationStatus == ParticipationStatus.TENTATIVE ||
                        it.participationStatus == ParticipationStatus.DECLINED ||
                        it.participationStatus == ParticipationStatus.NEEDS_ACTION)) {
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

    private fun buildAttendeesStatusesDescription(statusCount: Int?, statusStringId: Int, previousStatusString: String): String {
        return if (statusCount != null && previousStatusString.isNotEmpty())
            resources.getString(R.string.event_attendee_status_separator,
                previousStatusString,
                resources.getString(statusStringId, statusCount))
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
        val sortedAttendeeList = attendeeList.sortedWith(compareBy { getParticipationStatusPriorityValue(it.participationStatus) })
        attendeeListAdapter.submitList(sortedAttendeeList)
        // Reset view height
        attendeesListHeight = null

        if (attendeeListAdapter.itemCount <= ATTENDEE_AUTO_EXPAND_LIMIT && sortedAttendeeList.isNotEmpty()) {
            event_attendee_list.visibleOrGone(true)
            rotateArrowUpward(event_attendees_button, 0)
        } else if (sortedAttendeeList.isEmpty() && organizerAttendee != null) {
            event_attendee_list.visibleOrGone(false)
            event_attendees_button.visibleOrGone(false)
            event_attendees_press.visibleOrGone(false)
            return
        }

        event_attendees_press.setOnClickListener {
            if (event_attendee_list.isVisible) {
                // Save expanded view height only once
                val height = collapse(event_attendee_list)
                if (attendeesListHeight == null) attendeesListHeight = height
                rotateArrowDownward(event_attendees_button)
            } else {
                // TODO: Workaround for special case where desired height is not properly calculated.
                //  Passing 0 skips the animation.
                //  It means that List with more than 5 items will not have expand animation on first expand.
                expand(event_attendee_list, height = attendeesListHeight?: 0)
                rotateArrowUpward(event_attendees_button)
            }
        }
    }

    private fun displayAttendeeAnswerState(participationStatus: ParticipationStatus?, loading: Boolean = false) {
        eventViewModel.changeAnswerLoading.postValue(loading)

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
