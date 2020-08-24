package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.os.Bundle
import android.text.util.Linkify
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.property.Status
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.event_info.view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.android.synthetic.main.item_form_section.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.ZonedDateTime
import java.util.*


class EventDetailsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventDetailsFragment"
    override val layoutResourceId = R.layout.fragment_event_details

    override val navigateUp = false
    //override val actionMenuResourceId = R.menu.fragment_event_details

    override fun onMenuItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
//            R.id.action_menu_edit ->
//            )
//            R.id.action_menu_delete -> {




//                handleDelete()
//            }
        }
    }


    override fun onToolbarCreated(toolbar: Toolbar) {

        val buttonEdit = layoutInflater.inflate(R.layout.toolbar_action_primary, toolbar_content, false)
        with (buttonEdit) {
            (this as ImageButton).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_pen))
            setOnClickListener {
                findNavController().navigate(
                    (Navigation.Deeplink.toEventEdit(
                        navigationArguments.eventId,
                        navigationArguments.occurrenceNumber
                    )))
            }
        }
        val buttonMenu = layoutInflater.inflate(R.layout.toolbar_action_navigation, toolbar_content, false)
        with (buttonMenu) {
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
                    R.dimen.icon_size
                ), resources.getDimensionPixelSize(R.dimen.icon_size)
            )
            addView(
                buttonMenu, resources.getDimensionPixelSize(
                    R.dimen.icon_size
                ), resources.getDimensionPixelSize(R.dimen.icon_size)
            )

        }


    }

    private fun handleDelete() {
        val event = eventViewModel.eventLiveData.value!!

        if (event.isRecurring()/* || event.isFromRecurring()*/) {

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
                .setMessage(event.summary)
                .setPositiveButton(R.string.dialog_button_delete) { dialog, which ->
                    lifecycleScope.launch { // TODO
                        val deleteResult = withContext(Dispatchers.Default) {
                            calendarViewModel.handleDeleteEvent(
                                event.id,
                                EventEditDeleteOption.THIS_EVENT
                            )
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

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by inject()
    private val mainViewModel: MainViewModel by sharedViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val a = arguments

        lifecycleScope.launch {

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val viewModeInitStatus = withContext(Dispatchers.Default) {
                eventViewModel.initialise(
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    null,
                    null
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
        section_attendees.image_button_action.setOnClickListener {
            section_attendees_container.visibleOrGone(!section_attendees_container.isVisible)
            if (section_attendees_container.isVisible) {
                section_attendees.image_button_action.setImageResource(R.drawable.ic_chevron_up)
            } else {
                section_attendees.image_button_action.setImageResource(R.drawable.ic_chevron_down)
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

            val TODOcalendarTimeZoneId = TimeZone.getDefault()


            //val eventOccurrence = event.generateOccurrence(navigationArguments.occurrenceNumber, calendarViewModel.timeZoneId.id)
//            TimberLogger.d("event occurrence generated: ${eventOccurrence}")


            // TODO HIDE YEAR WHEN IT'S THE SAME AS CURRENT

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
                    event.summary ?: resources.getString(R.string.default_event_summary)

                this.text_date_time.text = event.formatStartEnd(
                    calendarViewModel.timeZoneId.id,
                    null,
                    resources
                )

                if (event.isRecurring()) {
                    this.text_recurrence.visibleOrGone(true)
                    this.text_recurrence.text = AndroidUtils.formatRecurrence(
                        requireContext(),
                        event,
                        TODOcalendarTimeZoneId.id
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
                    image_button_action.visibleOrGone(true)
                    visibleOrGone(true)
                }
            }

            // TODO attendees dummy data
            val attendees = mutableListOf<String>()
            val attendeeCount = (-1..10).random()
            if (attendeeCount > 0) {
                for (count in 0..attendeeCount) {
                    attendees.add("Attendee ${count + 1}")
                }
            }
            if (attendees.isNotEmpty()) {
                with(section_attendees) {
                    text_subheader.text = "4 yes, 3 maybe, 1 no, TODO"
                    text_header.text = resources.getString(
                        R.string.event_attendee_count, attendees.size, resources.getQuantityString(
                            R.plurals.plural_participant_uppercase,
                            attendees.size,
                            attendees.size
                        )
                    )
                    image_icon.setImageResource(R.drawable.ic_contact_groups)
                    visibleOrGone(true)
                }
                section_attendees_container.removeAllViews()
                attendees.forEach {
                    val tv = TextView(requireContext())
                    tv.text = it // TODO PROPER ITEM VIEW
                    section_attendees_container.addView(tv)
                }
                section_attendees.image_button_action.visibleOrGone(true)
                if (attendees.size > FormValidation.ATTENDEE_SHOW_TRESHOLD) {
                    section_attendees.image_button_action.setImageResource(R.drawable.ic_chevron_down)
                    section_attendees_container.visibleOrGone(false)
                } else {
                    section_attendees.image_button_action.setImageResource(R.drawable.ic_chevron_up)
                    section_attendees_container.visibleOrGone(true)
                }

            }

            with(section_calendar) {
                text_header.text = if (event.calendar.isActive) {
                    event.calendar.name
                } else {
                    requireContext().getText(R.string.event_calendar_disabled, event.calendar.name)
                }
                image_icon.setImageResource(R.drawable.ic_calendar)
                visibleOrGone(true)
            }

            val alarmLabels = event.iCalEvent.alarms.sortedBy { it.trigger.duration.toMillis() }
                .mapNotNull { alarm ->
                    AndroidUtils.formatAlarm(
                        resources,
                        event.isAllDay(),
                        ZonedDateTime.ofInstant(
                            event.iCalEvent.dateStart.value.toInstant(),
                            calendarViewModel.timeZoneId
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


//                text_event_all_day_pill.visibleOrGone(event.isAllDay())
//

//                text_event_recurrence.text = "REPEAT: $recurrenceLabel"


//                text_event_calendar.compoundDrawables.firstOrNull()?.setTint(Color.parseColor(event.calendar.color))


//                text_event_notes.text = "NOTES:" + event.notes.joinToString()


        })
    }




}
