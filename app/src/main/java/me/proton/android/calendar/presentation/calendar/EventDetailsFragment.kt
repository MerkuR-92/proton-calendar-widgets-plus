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
import biweekly.Biweekly
import biweekly.util.Frequency
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    override fun onMenuItemClicked(menuItem: MenuItem) { // TODO maybe move this logic to viewmodel, but it's just 1 line
//        when (menuItem.itemId) {
//            R.id.action_menu_edit -> findNavController().navigate((Navigation.Deeplink.toEventEdit(navigationArguments.eventId)))
//        }
        // TODO
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
            TimberLogger.d("${it!!.iCalendar.printToString()}")




            // TODO make sure we support full-day events with DTSTART only
//            val testVCal = """
//                BEGIN:VCALENDAR
//                PRODID:-//Proton Technologies//ProtonCalendar Beta//EN
//                VERSION:2.0
//                BEGIN:VEVENT
//                DTSTAMP:20190719T130854Z
//                CREATED:20200505T173304Z
//                LAST-MODIFIED:20200505T173304Z
//                UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
//                DTSTART;VALUE=DATE:20200506
//                SEQUENCE:0
//                STATUS:CONFIRMED
//                SUMMARY:API deploy
//                END:VEVENT
//                END:VCALENDAR
//            """.trimIndent()


//            val parsed= ICalUtils.parseICalString(it!!.iCalendar)


            // LocalDate date = LocalDate.of(2000, Month.NOVEMBER, 20);
            //LocalDate nextWed = date.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
            // DayOfWeek dotw = LocalDate.of(2012, Month.JULY, 9).getDayOfWeek()

            // TODO EXTRACT DATE FORMATTING TO UTILS

            val TODOcalendarTimeZoneId = TimeZone.getDefault()

            it?.let { event -> // TODO move somewhere else?

                button_delete.setOnClickListener {

//                    text2.visibleOrGone(false)

                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.dialog_title_delete_event)
                        .setMessage(event.summary)
                        .setPositiveButton(R.string.dialog_button_delete) { dialog, which ->
                            lifecycleScope.launch { // TODO
                                val deleteResult = withContext(Dispatchers.Default) {
                                    calendarViewModel.handleDeleteEvent(event.id)
                                }
                                if (deleteResult == UseCase.Result.Success) {
                                    Toast.makeText(requireContext(), "Event deleted only from WEB", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(requireContext(), "Error deleting event", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton(R.string.dialog_button_cancel) { dialog, which ->
                        }
                        .show()

                    //
                }

                // TODO HIDE YEAR WHEN IT'S THE SAME AS CURRENT

                text_event_title.text = event.summary + "\n"

                val alarmLabels = it.iCalEvent.alarms.filter { it.trigger?.duration?.isPrior ?: false /*only show triggers "before" event*/ }.mapNotNull {

//                    TimberLogger.D()

                    val trigger = it.trigger.duration
                    if (event.isAllDay()) { // example: "1 day before at 9:00"

                        // TODO FIXME this is device timezone, use this only as a fallback for given calendar
                        val startDate = ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), calendarViewModel.timeZoneId)
                            .minus(Period.ofWeeks(trigger.weeks ?: 0))
                            .minus(Period.ofDays(trigger.days ?: 0))
                            .minus(Duration.ofHours(trigger.hours?.toLong() ?: 0L))
                            .minus(Duration.ofMinutes(trigger.minutes?.toLong() ?: 0L))

                        TimberLogger.d("trigger weeks: ${trigger.weeks}, days: ${trigger.days}")

                        val label = listOfNotNull(
                            trigger.weeks?.let { "$it ${resources.getQuantityString(R.plurals.plural_week, it, it)}" },
                            // magic number 1 is needed for days, because 5 hours before midnight will actually be "1 day before" in "human speak"
                            trigger.days?.let { "${it + 1} ${resources.getQuantityString(R.plurals.plural_day, (it + 1), (it + 1))}" } ?: "1 ${resources.getQuantityString(R.plurals.plural_day, 1, 1)}"
                        ).joinToString(separator = ", ")

                        val alarmTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(startDate.toInstant()))

                        if (label.isBlank()) {
                            null
                        } else if (it.action?.isEmail == true) {
                            getString(R.string.event_alarm_label_before_with_time_by_email, label, alarmTime)
                        } else if (it.action?.isDisplay == true) {
                            getString(R.string.event_alarm_label_before_with_time, label, alarmTime)
                        } else {
                            null
                        }

                    } else { // example: "15 minutes before"
                        val label = listOfNotNull(
                            trigger.weeks?.let { "$it ${resources.getQuantityString(R.plurals.plural_week, it, it)}" },
                            trigger.days?.let { "$it ${resources.getQuantityString(R.plurals.plural_day, it, it)}" },
                            trigger.hours?.let { "$it ${resources.getQuantityString(R.plurals.plural_hour, it, it)}" },
                            trigger.minutes?.let { "$it ${resources.getQuantityString(R.plurals.plural_minute, it, it)}" }
                        ).joinToString(separator = ", ")

                        if (label.isBlank()) {
                            null
                        } else if (it.action?.isEmail == true) {
                            getString(R.string.event_alarm_label_before_by_email, label)
                        } else if (it.action?.isDisplay == true) {
                            getString(R.string.event_alarm_label_before, label)
                        } else {
                            null
                        }
                    }

                }

                text_event_notifications.text = "NOTIFICATIONS:\n" + alarmLabels.joinToString(separator = "\n")

                    if (event.spansSingleDay()) {

                        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(event.iCalEvent.dateStart.value.rawComponents.toDate())

                        if (event.isAllDay()) { // ignoring timezones
                            text_event_date.text = formattedDate
                        } else {

                             val startDateTimeInStartTimezone = ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), ZoneId.of(TODOcalendarTimeZoneId.id))
                             val endDateTimeInStartTimezone = ZonedDateTime.ofInstant(event.iCalEvent.dateEnd.value.toInstant(), ZoneId.of(TODOcalendarTimeZoneId.id))

                             val startTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(startDateTimeInStartTimezone.toInstant()))
                             val endTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(endDateTimeInStartTimezone.toInstant()))

                             text_event_date.text = "${formattedDate}\n${getString(R.string.event_time_period_spanning_single_day, startTime, endTime)}"
                        }
                    } else {

                        if (event.isAllDay()) { // ignoring timezones
                            val startDate = DateFormat.getDateInstance(DateFormat.FULL).format(event.iCalEvent.dateStart.value.rawComponents.toDate())
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