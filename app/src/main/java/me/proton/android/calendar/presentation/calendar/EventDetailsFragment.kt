package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.event_info.view.*
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.android.synthetic.main.item_form_section.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.text.DateFormat
import java.time.*
import java.util.*


class EventDetailsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventDetailsFragment"
    override val layoutResourceId = R.layout.fragment_event_details

    override val navigateUp = false
    override val actionMenuResourceId = R.menu.fragment_event_details

    override fun onMenuItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.action_menu_edit -> findNavController().navigate((Navigation.Deeplink.toEventEdit(navigationArguments.eventId, navigationArguments.occurrenceNumber)))
            R.id.action_menu_delete -> handleDelete()
        }
    }

    private fun handleDelete() {
        val event = eventViewModel.eventLiveData.value!!

        if (event.isRecurring()/* || event.isFromRecurring()*/) {

            AndroidUtils.displaySingleChoiceConfirmationPicker(requireContext(), getString(R.string.event_text_delete_event), listOfNotNull(
                getString(R.string.event_recurring_edit_this),
                if (navigationArguments.occurrenceNumber > 1) getString(R.string.event_recurring_edit_this_and_future) else null,
                getString(R.string.event_recurring_edit_all_events)
            ).toTypedArray(), 0) {

                lifecycleScope.launch {
                    val deleteResult = withContext(Dispatchers.IO) {
                        if (it == 0) {
                            calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.THIS_EVENT, navigationArguments.occurrenceNumber)
                        } else if (it == 1) {
                            if (navigationArguments.occurrenceNumber == 1) {
                                calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.ALL_EVENTS)
                            } else {
                                calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.THIS_EVENT_AND_FUTURE, navigationArguments.occurrenceNumber)
                            }
                        } else { // it == 2
                            calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.ALL_EVENTS)
                        }
                    }

                    if (deleteResult == UseCase.Result.Success) {
                        Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    } else {
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
                            calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.THIS_EVENT)
                        }
                        if (deleteResult == UseCase.Result.Success) {
                            Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_LONG).show()
                            findNavController().navigateUp()
                        } else {
                            Toast.makeText(requireContext(), "Error deleting event", Toast.LENGTH_LONG).show()
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
                eventViewModel.initialise(navigationArguments.eventId, if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber, null, null)
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
        section_location.image_button_action.setOnLongClickListener {
            if (eventViewModel.eventLiveData.value?.location != null) {
                mainViewModel.handleCopyToClipboard(eventViewModel.eventLiveData.value?.location as String /*TODO after get()*/)
            } else false
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


                val eventOccurrence = event.generateOccurrence(navigationArguments.occurrenceNumber, calendarViewModel.timeZoneId.id)


                // TODO HIDE YEAR WHEN IT'S THE SAME AS CURRENT

//                text_event_title.text = "SIGNATURE VERIFICATION: ${event.verificationStatus}\n\n" + event.summary + "\n"

            with (section_event_info) {

                // TODO adjust visibility in this section

                this.view_calendar_bar.background.setTint(Color.parseColor(event.calendar.color))

                this.text_status.visibleOrGone(true)
                this.text_status.text = "Cancelled TODO"

                this.text_summary.text = event.summary ?: resources.getString(R.string.default_event_summary)

                this.text_date_time.text = event.formatStartEnd(calendarViewModel.timeZoneId.id, eventOccurrence, resources)

                this.image_icon_recurrence.visibleOrGone(true)
                this.text_recurrence.visibleOrGone(true)
                this.text_recurrence.text = AndroidUtils.formatRecurrence(requireContext(), event, TODOcalendarTimeZoneId.id)




                visibleOrGone(true)
            }

            event.location?.let {
                with (section_location) {
                    text_header.text = event.location
                    image_icon.setImageResource(R.drawable.ic_map_marker)
                    image_button_action.setImageResource(R.drawable.ic_copy_clipboard)
                    image_button_action.visibleOrGone(true)
                    visibleOrGone(true)
                }
            }

            // TODO attendees dummy data
            val attendees = listOf("Participant One", "Participant Two")
            if (attendees.isNotEmpty()) {
                with (section_attendees) {
                    text_subheader.text = "4 yes, 3 maybe, 1 no, TODO"
                    text_header.text = resources.getString(R.string.event_attendee_count, attendees.size, resources.getQuantityString(R.plurals.plural_participant_uppercase, attendees.size, attendees.size))
                    image_icon.setImageResource(R.drawable.ic_contact_groups)
                    image_button_action.setImageResource(R.drawable.ic_chevron_down)
                    image_button_action.visibleOrGone(true)
                    visibleOrGone(true)
                }
                section_attendees_container.removeAllViews()
                attendees.forEach {
                    val tv = TextView(requireContext())
                    tv.text = it // TODO PROPER ITEM VIEW
                    section_attendees_container.addView(tv)
                }
            }

            with (section_calendar) {
                text_header.text = event.calendar.name
                image_icon.setImageResource(R.drawable.ic_calendar)
                visibleOrGone(true)
            }

            val alarmLabels = event.iCalEvent.alarms.mapNotNull { alarm ->
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
