package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.domain.usecase.UseCase
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
            R.id.action_menu_edit -> findNavController().navigate((Navigation.Deeplink.toEventEdit(navigationArguments.eventId)))
        }
    }

    private val navigationArguments: EventDetailsFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val mainViewModel: MainViewModel by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val a = arguments



        calendarViewModel.observeEvent(
            navigationArguments.eventId
        ).observe(viewLifecycleOwner, Observer {



            TimberLogger.d("GOT EVENT IN DETAILS FRAGMENT: $it")
            TimberLogger.d("navigation occurrence number: ${navigationArguments.occurrenceNumber}")
            TimberLogger.d("${it?.iCalendar?.printToString()}")
            TimberLogger.d("uid: ${it?.iCalEvent?.uid}")
            TimberLogger.d("id: ${it?.id}")


            // TODO EXTRACT DATE FORMATTING TO UTILS

            val TODOcalendarTimeZoneId = TimeZone.getDefault()

            it?.let { event -> // TODO move somewhere else?

                button_delete.setOnClickListener {



                    if (event.isRecurring()) {

                        AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.event_text_edit_event), listOfNotNull(
                            getString(R.string.event_recurring_edit_this),
                            if (navigationArguments.occurrenceNumber > 1) getString(R.string.event_recurring_edit_this_and_following) else null,
                            getString(R.string.event_recurring_edit_all_events)
                        ).toTypedArray(), -1) {

                            lifecycleScope.launch {
                                val deleteResult = withContext(Dispatchers.IO) {
                                    if (it == 0) {
                                        calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.THIS_EVENT, navigationArguments.occurrenceNumber)
                                    } else if (it == 1) {
                                        if (navigationArguments.occurrenceNumber == 1) {
                                            calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.ALL_EVENTS)
                                        } else {
                                            calendarViewModel.handleDeleteEvent(event.id, EventEditDeleteOption.THIS_EVENT_AND_FOLLOWING, navigationArguments.occurrenceNumber)
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
                        MaterialAlertDialogBuilder(context)
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

                // TODO HIDE YEAR WHEN IT'S THE SAME AS CURRENT

                text_event_title.text = "SIGNATURE VERIFICATION: ${event.verificationStatus}\n\n" + event.summary + "\n"

                val alarmLabels = it.iCalEvent.alarms./*filter { it.trigger?.duration?.isPrior ?: false /*only show triggers "before" event*/ }*/mapNotNull { alarm ->
                    AndroidUtils.formatAlarm(resources, it.isAllDay(), ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), calendarViewModel.timeZoneId), alarm)
                }

                text_event_notifications.text = "NOTIFICATIONS:\n" + alarmLabels.joinToString(separator = "\n")


                    if (event.spansSingleDay()) {

                        val formattedDate = event.formatStart(calendarViewModel.timeZoneId.id)// DateFormat.getDateInstance(DateFormat.FULL).format(event.iCalEvent.dateStart.value.rawComponents.toDate())

                        if (event.isAllDay()) { // ignoring timezones
                            text_event_date.text = formattedDate.first
                        } else {

                             val startDateTimeInStartTimezone = ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), ZoneId.of(TODOcalendarTimeZoneId.id))
                             val endDateTimeInStartTimezone = ZonedDateTime.ofInstant(event.iCalEvent.dateEnd.value.toInstant(), ZoneId.of(TODOcalendarTimeZoneId.id))

                             val startTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(startDateTimeInStartTimezone.toInstant()))
                             val endTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(endDateTimeInStartTimezone.toInstant()))

                             text_event_date.text = "${formattedDate}\n${getString(R.string.event_time_period_spanning_single_day, startTime, endTime)}"
                        }
                    } else {

                        if (event.isAllDay()) { // ignoring timezones
                            val startDate = event.formatStart(calendarViewModel.timeZoneId.id) //DateFormat.getDateInstance(DateFormat.FULL).format(event.iCalEvent.dateStart.value.rawComponents.toDate())
                            // TODO maybe extract this to Event

                            val endDateMinus1Day = ZonedDateTime.ofInstant(event.iCalEvent.dateEnd.value.toInstant(), calendarViewModel.timeZoneId).minusDays(1)
                            val endDate = DateFormat.getDateInstance(DateFormat.FULL).format(Date.from(endDateMinus1Day.toInstant()))

                            text_event_date.text = getString(R.string.event_time_period_spanning_many_days, startDate, endDate)
                        } else {

                                val startDateTimeInStartTimezone = ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), ZoneId.of(TODOcalendarTimeZoneId.id))
                                val endDateTimeInStartTimezone = ZonedDateTime.ofInstant(event.iCalEvent.dateEnd.value.toInstant(), ZoneId.of(TODOcalendarTimeZoneId.id))

                                val startDateTime = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date.from(startDateTimeInStartTimezone.toInstant()))
                                val endDateTime = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date.from(endDateTimeInStartTimezone.toInstant()))

                                text_event_date.text = getString(R.string.event_time_period_spanning_many_days, startDateTime, endDateTime)
                        }
                    }

                text_event_all_day_pill.visibleOrGone(event.isAllDay())
                text_event_all_day_pill.background.setTint(Color.parseColor(event.calendar.color))

                // TODO this will be used when displaying recurring events on grid
                //event.iCalEvent.exceptionDates
                // The exception dates, if specified, are used in
                //      computing the recurrence set.  The recurrence set is the complete
                //      set of recurrence instances for a calendar component.  The
                //      recurrence set is generated by considering the initial "DTSTART"
                //      property along with the "RRULE", "RDATE", and "EXDATE" properties
                //      contained within the recurring component.  The "DTSTART" property
                //      defines the first instance in the recurrence set.  The "DTSTART"
                //      property value SHOULD match the pattern of the recurrence rule, if
                //      specified.  The recurrence set generated with a "DTSTART" property
                //      value that doesn't match the pattern of the rule is undefined.
                //      The final recurrence set is generated by gathering all of the
                //      start DATE-TIME values generated by any of the specified "RRULE"
                //      and "RDATE" properties, and then excluding any start DATE-TIME
                //      values specified by "EXDATE" properties.  This implies that start
                //      DATE-TIME values specified by "EXDATE" properties take precedence
                //      over those specified by inclusion properties (i.e., "RDATE" and
                //      "RRULE").  When duplicate instances are generated by the "RRULE"
                //      and "RDATE" properties, only one recurrence is considered.
                //      Duplicate instances are ignored.
                //
                //      The "EXDATE" property can be used to exclude the value specified
                //      in "DTSTART".  However, in such cases, the original "DTSTART" date
                //      MUST still be maintained by the calendaring and scheduling system
                //      because the original "DTSTART" value has inherent usage
                //      dependencies by other properties such as the "RECURRENCE-ID".


                val recurrenceLabel = AndroidUtils.formatRecurrence(requireContext(), it, TODOcalendarTimeZoneId.id)

                text_event_recurrence.text = "REPEAT: $recurrenceLabel"

                text_event_location.text = "LOCATION: " + event.location
                text_event_calendar.text = event.calendar.name
                text_event_calendar.compoundDrawables.firstOrNull()?.setTint(Color.parseColor(event.calendar.color))

                // TODO example of trigger 5minutes after END date (it's "RELATED" parameter) -> TRIGGER;RELATED=END:P5M
                // absolute time: TRIGGER;VALUE=DATE-TIME:19980101T050000Z

                text_event_description.text = "DESCRIPTION: " + event.description
//                text_event_notes.text = "NOTES:" + event.notes.joinToString()

                text_event_location.setOnClickListener {
                    event.location?.let { mainViewModel.handleEventLocationShow(event.location!!) }
                }
                text_event_location.setOnLongClickListener {
                    if (event.location != null) {
                        mainViewModel.handleCopyToClipboard(event.location!! /*TODO after get()*/)
                    } else false
                }


            }
        })



    }


}
