package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.text.util.Linkify
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.Status
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.event_attendees_view.*
import kotlinx.android.synthetic.main.event_info.view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.android.synthetic.main.item_attendee.view.*
import kotlinx.android.synthetic.main.item_form_section.view.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.CalendarListAdapter
import me.proton.android.calendar.presentation.MainViewModel
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.ZoneId
import java.time.ZonedDateTime


class EventDetailsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventDetailsFragment"
    override val layoutResourceId = R.layout.fragment_event_details

    override val navigateUp = false

    override fun onBackPressedCustom() {

        // TODO this is a workaround for deeplinks not navigating up to direct parent, but to navigation's start destination
        //  1. see if nested graphs work when we get rid of dialogs in favor of fragments
        //  2. see if handling deeplink straight from notification (not indirectly from MainActivity and navigating manually)
        //  fixes this
        if (findNavController().previousBackStackEntry?.destination?.id != R.id.nav_calendar) {
            findNavController().navigate(Navigation.Deeplink.toMonth())
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    private lateinit var buttonEdit: View
    private lateinit var attendeeListAdapter: AttendeeListAdapter

    override fun onToolbarCreated(toolbar: Toolbar) {

        buttonEdit = layoutInflater.inflate(R.layout.toolbar_action_secondary, toolbar_content, false)
        with (buttonEdit) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_pencil))
            setOnClickListener {
                findNavController().navigate(
                    (Navigation.Deeplink.toEventEdit(
                        navigationArguments.eventId,
                        navigationArguments.occurrenceNumber
                    )))
            }
        }
        val buttonMenu = layoutInflater.inflate(R.layout.toolbar_action_secondary, toolbar_content, false)
        with (buttonMenu) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_three_dots_vertical))
            setOnClickListener {
                AndroidUtils.displayPopupMenu(
                    view = it,
                    labels = listOf(Pair(R.string.action_delete, R.color.notification_error)),
                    icons = listOf(Pair(R.drawable.ic_trash, R.color.notification_error))
                ) {
                    handleDelete()
                }
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.toolbar_content)) {
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
        }
    }

    private fun handleDelete() {
        val event = eventViewModel.eventLiveData.value!!

        if (event.isPartOfChain() && !event.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId)) {

            AndroidUtils.displaySingleChoiceConfirmationPicker(
                requireContext(), getString(R.string.event_text_delete_event), listOfNotNull(
                    getString(R.string.event_recurring_edit_this),
                    if (navigationArguments.occurrenceNumber > 1) getString(R.string.event_recurring_edit_this_and_future) else null,
                    getString(R.string.event_recurring_edit_all_events)
                ).toTypedArray(), 0
            ) {

                lifecycleScope.launch {
                    val deleteResult = withContext(Dispatchers.IO) {
                        if (it == 0) {
                            calendarViewModel.handleDeleteEvent(
                                event.id,
                                EventEditDeleteOption.THIS_EVENT,
                                navigationArguments.occurrenceNumber
                            )
                        } else if (it == 1) {
                            if (navigationArguments.occurrenceNumber == 1) {
                                calendarViewModel.handleDeleteEvent(
                                    event.id,
                                    EventEditDeleteOption.ALL_EVENTS
                                )
                            } else {
                                calendarViewModel.handleDeleteEvent(
                                    event.id,
                                    EventEditDeleteOption.THIS_EVENT_AND_FUTURE,
                                    navigationArguments.occurrenceNumber
                                )
                            }
                        } else { // it == 2
                            calendarViewModel.handleDeleteEvent(
                                event.id,
                                EventEditDeleteOption.ALL_EVENTS
                            )
                        }
                    }

                    if (deleteResult == UseCase.Result.Success) {
                        Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    } else {

                        if (deleteResult is UseCase.Result.Error) {
                            TimberLogger.e("Error deleting event: ${deleteResult.message}")
                        } else if (deleteResult is UseCase.Result.InvalidParams) {
                            TimberLogger.e("InvalidParams deleting event: ${deleteResult.message}")
                        }

                        Toast.makeText(requireContext(), "Error deleting event", Toast.LENGTH_LONG).show()
                    }

                }
            }

        } else { // TODO unify showing dialog
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_title_delete_event)
                .setMessage(R.string.dialog_description_delete_event)
                .setPositiveButton(R.string.dialog_button_delete) { dialog, which ->
                    lifecycleScope.launch { // TODO
                        val deleteResult = withContext(Dispatchers.Default) {
                            if (event.isSingleOccurrenceRecurring(eventViewModel.displayTimeZoneId)) {
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
                        if (deleteResult == UseCase.Result.Success) {
                            Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_LONG).show()
                            findNavController().navigateUp()
                        } else {

                            if (deleteResult is UseCase.Result.Error) {
                                TimberLogger.e("Error deleting event: ${deleteResult.message}")
                            } else if (deleteResult is UseCase.Result.InvalidParams) {
                                TimberLogger.e("InvalidParams deleting event: ${deleteResult.message}")
                            }

                            Toast.makeText(
                                requireContext(),
                                "Error deleting event",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .setNegativeButton(R.string.dialog_button_cancel) { dialog, which ->
                }
                .show()
        }
    }

    private val navigationArguments: EventDetailsFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val eventViewModel: EventViewModel by sharedViewModel()
    private val mainViewModel: MainViewModel by sharedViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val a = arguments

        lifecycleScope.launch {

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val viewModeInitStatus = withContext(Dispatchers.Default) {
                eventViewModel.initialise(
                    editMode = false,
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    null,
                    null,
                )
            }

            if (viewModeInitStatus == UseCase.Result.Success) {
                observeEventLiveData()
                attachActionHandlers()
            } else {
                // TODO display error and close? for example when we can't decrypt event
                TimberLogger.e((viewModeInitStatus as UseCase.Result.Error).message)
                Toast.makeText(requireContext(), "Error opening event", Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            }

        }


    }

    private fun attachActionHandlers() {
        section_location.text_header.setOnClickListener {
            eventViewModel.eventLiveData.value?.location?.let {
                mainViewModel.handleEventLocationShow(it)
            }
        }
        section_location.image_button_action.setOnClickListener {
            eventViewModel.eventLiveData.value?.location?.let {
                mainViewModel.handleCopyToClipboard(eventViewModel.eventLiveData.value?.location as String /*TODO after get()*/)
            }
        }
    }

    private fun observeEventLiveData() {

        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { event: Event ->

            // TODO when we perform "edit this", new event is created and it won't automatically refresh here
            //  because we're still listening for the old event.id !!!

            TimberLogger.d("GOT EVENT IN DETAILS FRAGMENT: $event")
            TimberLogger.d("navigation occurrence number: ${navigationArguments.occurrenceNumber}")
            TimberLogger.d("${event?.iCalendar?.printToString()}")
            TimberLogger.d("uid: ${event?.iCalEvent?.uid}")
            TimberLogger.d("id: ${event?.id}")


            // TODO EXTRACT DATE FORMATTING TO UTILS




            //val eventOccurrence = event.generateOccurrence(navigationArguments.occurrenceNumber, calendarViewModel.timeZoneId.id)
//            TimberLogger.d("event occurrence generated: ${eventOccurrence}")


            // TODO HIDE YEAR WHEN IT'S THE SAME AS CURRENT

//                text_event_title.text = "SIGNATURE VERIFICATION: ${event.verificationStatus}\n\n" + event.summary + "\n"

            // TODO Remove attendees condition once edit attendees is implemented
            buttonEdit.visibleOrGone(event.calendar.isActive && event.iCalEvent.attendees.isNullOrEmpty())

            with(section_event_info) {

                this.view_calendar_bar.background.setTint(Color.parseColor(event.calendar.color))

                if (event.status != null) {
                    if ((event.status as Status).isCancelled) {
                        this.text_status.visibleOrGone(true)
                        this.text_status.text = getString(R.string.event_status_cancelled)
                    }
                }

                this.text_summary.text =
                    event.summary ?: resources.getString(R.string.default_event_summary)

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

            event.location?.let {
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

            val alarmLabels = event.iCalEvent.alarms.sortedBy { it.trigger.duration.toMillis() }
                .mapNotNull { alarm ->
                    AndroidUtils.formatAlarm(
                        resources,
                        event.isAllDay(),
                        calendarViewModel.timeFormatIs24Hour,
                        ZonedDateTime.ofInstant(
                            event.iCalEvent.dateStart.value.toInstant(),
                            ZoneId.of(eventViewModel.displayTimeZoneId)
                        ),
                        alarm
                    )
                }

            if (alarmLabels.isNotEmpty()) {
                with(section_alarms) {
                    text_header.text = alarmLabels.joinToString(separator = "\n")
                    image_icon.setImageResource(R.drawable.ic_bell)
                    visibleOrGone(true)
                }
            }

            event.description?.let {
                with(section_description) {
                    text_header.text = event.description
                    Linkify.addLinks(text_header, Linkify.ALL)
                    image_icon.setImageResource(R.drawable.ic_text_align_left)
                    visibleOrGone(true)
                }
            }

            val attendeeList = event.iCalEvent.attendees
            section_attendees.visibleOrGone(event.iCalEvent.organizer != null && attendeeList.isNotEmpty())
            if (attendeeList.isNotEmpty()) {
                event_attendees_title.text =
                    resources.getString(R.string.event_attendee_title, event.iCalEvent.attendees.size)
                val statusMap = hashMapOf<ParticipationStatus, Int>()
                attendeeList.forEach {
                    TimberLogger.d("Attendees : attendee ${it.email} /// ${it.commonName}")
                    if (it.participationStatus != null) {
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

                val organizer = event.iCalEvent.organizer
                if (organizer != null) {
                    lifecycleScope.launch {
                        val user = withContext(Dispatchers.Default) {
                            calendarViewModel.selectUser()
                        }
                        if (user?.email == organizer.email) {
                            event_attendee_organizer_layout.item_attendee_title.text =
                                resources.getString(R.string.event_attendee_is_organizer)
                            event_attendee_organizer_layout.item_attendee_description.text = organizer.email
                        } else {
                            event_attendee_organizer_layout.item_attendee_title.text = organizer.email
                            event_attendee_organizer_layout.item_attendee_description.text =
                                resources.getString(R.string.event_attendee_organizer)
                        }
                        event_attendee_organizer_layout.item_attendee_initials.text = getInitials(organizer.email)
                    }
                }

                val attendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
                event_attendee_list.layoutManager = attendeesLayoutManager
                attendeeListAdapter = AttendeeListAdapter()
                (event_attendee_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
                event_attendee_list.adapter = attendeeListAdapter
                attendeeListAdapter.submitList(attendeeList)

                event_attendees_press.setOnClickListener {
                    if (event_attendee_list.isVisible) {
                        collapse(event_attendee_list)
                        rotateArrowDownward(event_attendees_button)
                    } else {
                        expand(event_attendee_list)
                        rotateArrowUpward(event_attendees_button)
                    }
                }
            }
        })
    }

    private fun buildAttendeesStatusesDescription(statusCount: Int?, statusStringId: Int, previousStatusString: String): String {
        return if (statusCount != null && previousStatusString.isNotEmpty())
            resources.getString(R.string.event_attendee_status_separator,
                previousStatusString,
                resources.getString(statusStringId, statusCount))
        else if (statusCount != null && previousStatusString.isEmpty())
            resources.getString(statusStringId, statusCount)
        else ""
    }




}
