package me.proton.android.calendar.presentation.calendar

import androidx.lifecycle.*
import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.*
import biweekly.util.DayOfWeek
import biweekly.util.Duration
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.adjustRRuleToStartDate
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.ICalUtils.isDateTimeTheSame
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.*
import java.time.*
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*

class EventViewModel(
    private val calendarsRepository: CalendarsRepository,
    private val createEventUseCase: EditCreateEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val gson: Gson
) : ViewModel() {


    private var timeStartBackup: LocalTime? = null
    private var timeEndBackup: LocalTime? = null

    private var eventEdited = false
    private var eventBumpSeqId = false

    // TODO get this from preferences/settings
    val startWeekOnMonday = true

    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private val bgScope = CoroutineScope(Dispatchers.Default + viewModelJob)

//    val userLiveData: LiveData<User> = liveData {
//        val data = database.loadUser() // loadUser is a suspend function.
//        emit(data)
//    }

    private lateinit var event: Event
    private lateinit var calendarSettings: CalendarSettingsEntity
    private val _event = MutableLiveData<Event>() // TODO see if there's less ugly way

    val eventLiveData: LiveData<Event> = _event

    /**
     * Initial TimeZoneId for this event, default from Calendar Settings or taken from Event.
     */
    lateinit var displayTimeZoneId: String

    suspend fun initialise(eventId: String?, occurrenceNumber: Int?, initStartDate: String?, initStartTime: String? /*TODO in the future also endDate for multi-day events*/): UseCase.Result /* TODO maybe use separate Result class */ {

        // reset backup values
        timeStartBackup = null
        timeEndBackup = null
        eventEdited = false
        eventBumpSeqId = false

//            calendarUserSettings.defaultCalendarId // TODO we still can't rely on this, it can be null in API!!!
        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//        val todoDefaultCalendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")!!
        // TODO get those values from somewhere
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO
        val userId = TODOuserID//"IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="

        val defaultCalendarId = calendarsRepository.getDefaultCalendarId(userId) ?: return UseCase.Result.Error("could not get default calendar ID")

//        val userSettings = calendarsRepository.selectUserSettings(userId) ?: return UseCase.Result.Error("could not get User Settings")

//        val calendarSettings = calendarsRepository.selectCalendarSettings(defaultCalendarId) ?: return UseCase.Result.Error("could not get Calendar Settings")

        displayTimeZoneId = TimeZone.getDefault().id // TODO get it from settings

        TimberLogger.d("EventViewModel initialise with EventId: $eventId")
        TimberLogger.d("EventViewModel initialise with startDate: $initStartDate")
        TimberLogger.d("EventViewModel initialise with startTime: ${initStartTime}")

        val defaultCalendar = calendarsRepository.selectCalendar(defaultCalendarId) ?: return UseCase.Result.Error("could not get default Calendar from DB")

        this.calendarSettings = calendarsRepository.selectCalendarSettings(defaultCalendar.id) ?: return UseCase.Result.Error("could not get CalendarSettings")

        event = if (eventId == null) {

            val newICalendar = ICalUtils.createNewEvent().wrapInICalendar()
            val newVEvent = newICalendar.events.first()

            // if there is no requested start date, we take today
            val startDate = if (initStartDate != null) LocalDate.parse(initStartDate) else LocalDate.now()
            // if there is no requested start time, we calculate it according to "now"
            val startTime = if (initStartTime != null) LocalTime.parse(initStartTime) else LocalTime.now().plusMinutes(
                this.calendarSettings.defaultEventDuration.toLong()).truncatedTo(ChronoUnit.HOURS)
            // end Zoned Date Time according to default event duration
            val endZonedDateTime = ZonedDateTime.of(startDate, startTime, ZoneId.of(displayTimeZoneId)).plusMinutes(
                this.calendarSettings.defaultEventDuration.toLong())

            timeStartBackup = startTime
            timeEndBackup = endZonedDateTime.toLocalTime() // this time can be before timeStartBackup at this point

            // TODO GUI takes timezone from iCalendar's "default timezone", maybe this should be moved to "Event" model?
            newICalendar.setDefaultTimeZone(displayTimeZoneId)

            if (initStartTime == null) { // create new all-day event

                newVEvent.setStart(startDate)

                // event end time goes over midnight
                if (endZonedDateTime.dayOfYear != startDate.dayOfYear) {
                    newVEvent.setEnd(endZonedDateTime.toLocalDate().minusDays(1))
                    timeEndBackup = timeStartBackup // workaround TODO we could force-change date-end to next-day
                } else {
                    newVEvent.setEnd(endZonedDateTime.toLocalDate())
                }

            } else { // create new all-day event
                newVEvent.setStart(startDate, startTime, displayTimeZoneId)
                newVEvent.setEnd(endZonedDateTime.toLocalDate(), endZonedDateTime.toLocalTime(), displayTimeZoneId)
                newICalendar.setStartTimeZone(displayTimeZoneId)
                newICalendar.setEndTimeZone(displayTimeZoneId)
            }



//            if (initStartDate == null) { // TODO this will be only used when we create new event from outside of the app
//                newVEvent.setStart(defaultStartDate)
//                newVEvent.setEnd(defaultStartDate)
//            } else {
//

//                if (initStartTime == null) { // create new all-day event
//                    newVEvent.setStart(requestedStartDate)
//                    newVEvent.setEnd(requestedStartDate.plusDays(1))
//                } else { // create new partial-day event

//                }

//                if (initStartTime == null) { // create new all-day event

//                }

//            }


            TimberLogger.d("INIT: ${newICalendar.printToString()}")

            val newEvent = Event(ICalUtils.generateOfflineEventId(), Calendar(
                defaultCalendar.id,
                defaultCalendar.name,
                defaultCalendar.color,
                defaultCalendar.isActive
            ), newICalendar)

            setDefaultAlarms(newEvent, this.calendarSettings)
            newEvent

        } else {

            TimberLogger.v("event view model init with occurrence: $occurrenceNumber")

            val dbEvent = viewModelScope.async(Dispatchers.IO) {
                calendarsRepository.eventFlow(eventId).first()
            }.await()

            TimberLogger.d("timezone before generating occurrence: ${dbEvent?.iCalendar?.timezoneInfo?.getTimezone(dbEvent?.iCalEvent?.dateStart)?.timeZone?.id}")

            val eventStartTimeZone = dbEvent?.iCalendar?.timezoneInfo?.getTimezone(dbEvent?.iCalEvent?.dateStart)?.timeZone?.id ?: displayTimeZoneId

            // we have to generate occurrence in event's timezone, because otherwise we will overwrite it with default calendar's timezone
            (dbEvent?.withOccurrence(occurrenceNumber ?: 0, eventStartTimeZone) ?: dbEvent)?.apply {

                if (this.isAllDay()) { // adjust endDate to -1 day if event has no time
                    this.iCalEvent.setEnd(this.endLocalDate!!.minusDays(1)) // TODO NPE FIXME REMOVE THIS PROPERTY!!!!

                    timeStartBackup = LocalTime.now()
                    timeEndBackup = LocalTime.now().plusMinutes(this@EventViewModel.calendarSettings.defaultEventDuration.toLong())//.truncatedTo(ChronoUnit.HOURS)
                } else {
                    timeStartBackup = this.getStart(eventStartTimeZone)!!.toLocalTime()
                    timeEndBackup = this.getEnd(eventStartTimeZone)!!.toLocalTime()
                }

                // default timezone in iCalendar is used for GUI
                this.iCalendar.setDefaultTimeZone(eventStartTimeZone)

            } ?: return UseCase.Result.Error("could not find event ${eventId}")
        }

        _event.postValue(event)

        return UseCase.Result.Success
    }

    private fun setDefaultAlarms(event: Event, calendarSettings: CalendarSettingsEntity) {
        event.iCalEvent.alarms.clear()

        if (event.isAllDay()) {
            if (calendarSettings.defaultFullDayNotifications.isNotEmpty()) {
                calendarSettings.defaultFullDayNotifications.mapNotNull { if (it.isJsonObject) gson.fromJson(it, CalendarSettingsEntity.AlarmEntity::class.java) else null }.forEach { alarm ->
                    alarm.parseTrigger()?.let {
                        if (alarm.type == "0") {
                            event.iCalEvent.addAlarm(VAlarm.email(it, null, null))
                        } else {
                            event.iCalEvent.addAlarm(VAlarm.display(it, null))
                        }
                    }
                }
            } else {

            }
        } else {
            if (calendarSettings.defaultPartDayNotifications.isNotEmpty()) {
                calendarSettings.defaultPartDayNotifications.mapNotNull { if (it.isJsonObject) gson.fromJson(it, CalendarSettingsEntity.AlarmEntity::class.java) else null }.forEach { alarm ->
                    alarm.parseTrigger()?.let {
                        if (alarm.type == "0") {
                            event.iCalEvent.addAlarm(VAlarm.email(it, null, null))
                        } else {
                            event.iCalEvent.addAlarm(VAlarm.display(it, null))
                        }
                    }
                }
            } else {
                val duration = Duration.builder().prior(true).minutes(15).build()
                event.iCalEvent.addAlarm(VAlarm.display(Trigger(duration, Related.START), null))
            }
        }

    }

    // recurrence temp values
    var tempRecurrenceUntilLocalDate: LocalDate? = null
    var tempMonthlyRepeatOption: MonthlyRepatOnOption = MonthlyRepatOnOption.ON_DAY_X

    /**
     * Resets temporary values for Recurrence
     */
    fun initialiseForRecurrence() {
        this.tempMonthlyRepeatOption = MonthlyRepatOnOption.ON_DAY_X
        this.tempRecurrenceUntilLocalDate = null
    }

    /**
     * Saves form data in iCalendar, but doesn't emit new LiveData
     * because the changes are already there in user interface.
     */
    fun persistRecurrenceFormData(summary: String?, location: String?, description: String?) {

        if (event.iCalEvent.summary?.value != summary ||
            event.iCalEvent.location?.value != location ||
            event.iCalEvent.description?.value != description) {

            markEventAsEdited()
        }

        event.iCalEvent.setSummary(summary)
        event.iCalEvent.setLocation(location)
        event.iCalEvent.setDescription(description)
    }

    /**
     * Checks if start date/time is before end date/time
     */
    fun validateDateTime(): Boolean {
        // TODO this works only as long as we have the same timezone for start and end
        return if (event.isAllDay()) {
            !(event.getStart(displayTimeZoneId)?.isAfter(event.getEnd(displayTimeZoneId)) ?: false)
        } else {
            event.getStart(displayTimeZoneId)?.isBefore(event.getEnd(displayTimeZoneId)) ?: false
        }
    }

    // alarm temp values
    var tempAlarmSendByOption: SendByOption = SendByOption.NOTIFICATION
    var tempAlarmTime: LocalTime = LocalTime.of(9, 0)

    fun isAlarmLimitReached() = this.event.iCalEvent.alarms.size >= FormValidation.ALARM_COUNT_MAX

    /**
     * Resets temporary values for Alarm.
     */
    fun initialiseForAlarm(/*TODO pass alarm index for edit?*/) {
        this.tempAlarmSendByOption = SendByOption.NOTIFICATION
        this.tempAlarmTime = LocalTime.of(9, 0)
    }



    suspend fun handleSave(editOption: EventEditDeleteOption? = null, occurrenceNumber: Int): Boolean { // create or edit

//        val calendarToSave = event.iCalendar

        // TODO MOVE WHATEVER WE CAN TO WORKER!!!!

        TimberLogger.d("handleSave with editOption: $editOption")
        TimberLogger.d("calendar before adjusting: " + event.iCalendar.printToString())

        if (event.isAllDay()) {
            event.iCalendar.adjustOutgoingAllDayEvent(event.defaultTimeZone!!)
            displayTimeZoneId = event.defaultTimeZone!!
            TimberLogger.d("calendar for all-day: " + event.iCalendar.printToString())
        } else {
            event.iCalendar.adjustStartEndTimeZones(displayTimeZoneId, event.defaultTimeZone!!)
            displayTimeZoneId = event.defaultTimeZone!!
            TimberLogger.d("calendar for part-time after adjusting timezones: " + event.iCalendar.printToString())
        }

        if (eventBumpSeqId) {
//            event.iCalEvent.setSequence((event.iCalEvent.sequence?.value ?: 0) + 1) // TODO FIXME
        }


        // TODO FIXME THIS HAS TO COUNT FROM db-EVENT START, NOT MODIFIED CURRENT EVENT!
        //val occurrence = event.generateOccurrence(occurrenceNumber ?: 0, event.defaultTimeZone!!)


        val dbEvent = calendarsRepository.selectEventEntity(event.id)?.let { transformEventUseCase.execute(it) }
        val dbEventStartDate = dbEvent?.iCalEvent?.getStart(event.defaultTimeZone!!)
        val dbEventWithOccurrence = dbEvent?.withOccurrence(occurrenceNumber, event.defaultTimeZone!!)
        val dbEventWithOccurrenceStartDate = dbEventWithOccurrence?.iCalEvent?.getStart(event.defaultTimeZone!!)

        TimberLogger.d("db event =${dbEvent?.iCalendar?.printToString()}")
        TimberLogger.d(("dbEventStartDate : ${dbEventStartDate}"))
        TimberLogger.d(("dbEventWithOccurrence : ${dbEventWithOccurrence?.iCalendar?.printToString()}"))
        TimberLogger.d(("dbEventWithOccurrenceStartDate : ${dbEventWithOccurrenceStartDate}"))

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        val newEvent = when (editOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                // TODO FIXME isRecurring OR isInChain?????????
                if (event.isRecurring() || event.isFromRecurring()) {

                    if (dbEventWithOccurrenceStartDate == null) return false

                    val eventToCreate = event.copy( // TODO move to helper method?
                        id = ICalUtils.generateOfflineEventId(),
                        iCalendar = event.iCalendar.clone()
                    )
                    // event.uid is still the same

                    // delete all recurring properties
                    eventToCreate.iCalEvent.recurrenceRule = null
                    eventToCreate.iCalEvent.exceptionDates.clear()

//                    TODO dtstart/end is incorrect, when creating new event that is in different timezone -- we should normalize the time back to original!!!!!
                    val timeHasBeenChanged = !eventToCreate.iCalendar.isDateTimeTheSame(dbEventWithOccurrence.iCalendar)
                    if (timeHasBeenChanged) { // TODO it looks like we always use occurrence start date anyway
                        TimberLogger.d("time has been changed")
//                        eventToCreate.setRecurrenceId(dbEventWithOccurrenceStartDate, !eventToCreate.isAllDay())
                    } else {
                        TimberLogger.d("time is the same")
                    }
                    eventToCreate.setRecurrenceId(dbEventWithOccurrenceStartDate, !dbEvent.isAllDay()) // RecurrenceId has to be in original event's format

                    eventToCreate

                } else {
                    event // else no special changes for regular event, just overwrite everything
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {

                // TODO THIS NEEDS TO BE FIXED, WE PROBABLY CAN'T FIND EVENTS IN DB
                if (dbEvent == null) return false
                if (dbEventWithOccurrenceStartDate == null) return false

                // delete single edits starting with just edited occurrence
                val deleteSingleEditsResult = deleteEventUseCase.execute(TODOuserID, event.id, dbEventWithOccurrenceStartDate!!.minusNanos(1))
                if (deleteSingleEditsResult != UseCase.Result.Success ) return false

                // update original event:
                // - change COUNT to ((current occurrence number) - 1)
                // OR
                // - change UNTIL equal to (previous occurrence from just edited).endDate
                val dbEventToUpdate = dbEvent.copy(iCalendar = dbEvent.iCalendar.clone())
                dbEventToUpdate.iCalEvent.recurrenceRule?.value?.let {
                    dbEventToUpdate.iCalEvent.setRecurrenceRule(Recurrence.Builder(dbEventToUpdate.iCalEvent.recurrenceRule.value)
                        // TODO count = 0 will not happen because this edit option is not available for first occurrence
                        .count(if (it.count != null) occurrenceNumber - 1 else null)
                        // 1 second to midnight on the end-day of previous original occurrence
                        .until(Date.from(dbEvent.generateOccurrence(occurrenceNumber - 1, event.defaultTimeZone!!)!!.endDateTime.plusDays(1).with(ChronoField.HOUR_OF_DAY, 0).minusSeconds(1).toInstant()), true)
                        .build())

                    val editOriginalEventResult = editCreateEventUseCase.execute(TODOuserID, dbEventToUpdate.calendar.id, dbEventToUpdate)
                    if (editOriginalEventResult != UseCase.Result.Success ) {
                        if (editOriginalEventResult is UseCase.Result.Error) {
                            TimberLogger.e("error editing event: ${editOriginalEventResult.message}")
                        } else if (editOriginalEventResult is UseCase.Result.Error) {
                            TimberLogger.e("error editing event: ${editOriginalEventResult.message}")
                        }
                        return false
                    }
                }

                // TODO delete exdates after this occurrence?

                // --------------------------------------

                val eventToCreate = event.copy(
                    id = ICalUtils.generateOfflineEventId(),
                    iCalendar = event.iCalendar.clone().apply {
                        this.events.first().apply {
                            setUid(ICalUtils.generateProtonUid(event.uid, ICalDateFormat.DATE_TIME_BASIC_WITHOUT_TZ.format(Date.from(dbEventWithOccurrenceStartDate.toInstant()))))
                            exceptionDates.clear()
                            val nullDate: Date? = null
                            setRecurrenceId(nullDate)
                            event.iCalEvent.recurrenceRule?.value?.let {
                                setRecurrenceRule(Recurrence.Builder(event.iCalEvent.recurrenceRule.value)
                                    .count(if (it.count != null) it.count - occurrenceNumber + 1 else null)
                                    // UNTIL is copied from event's RRULE
                                    .build())
                            }
                        }
                    }
                )

                //
//            if (this.recurrenceRule?.value?.until != null) {
//                this.recurrenceRule = RecurrenceRule(Recurrence.Builder(this.recurrenceRule.value).until(this.recurrenceRule?.value?.until, false).build())
//            }

                eventToCreate

            }
            EventEditDeleteOption.ALL_EVENTS -> {

                // delete all single edits
                val deleteSingleEditsResult = deleteEventUseCase.execute(TODOuserID, event.id, dbEventStartDate!!.minusNanos(1))
                if (deleteSingleEditsResult != UseCase.Result.Success ) return false

                // delete all single deletions
                event.iCalEvent.exceptionDates.clear()

//                TWO SEPARATE THINGS:
//                - if event.isAllDay != dbEvent.isAllDay() it means there was a conversion all-day-part-day
//                - the same DAY but different TIME

                if (dbEventWithOccurrenceStartDate!!.truncatedTo(ChronoUnit.DAYS) == event.getStart(event.defaultTimeZone)?.truncatedTo(ChronoUnit.DAYS) &&
                    dbEventWithOccurrence.iCalEvent.recurrenceRule == event.iCalEvent.recurrenceRule) {

                    // update the original event's DTSTART only with new time (leave day the same)

                    if (event.isAllDay()) {
                        event.also {
                            TimberLogger.d("all day, updating only new time: ${dbEvent.getStart(event.defaultTimeZone)!!.toLocalDate()}/${dbEvent.getEnd(event.defaultTimeZone)!!.toLocalDate()}")
                            it.iCalEvent.setStart(dbEvent.getStart(event.defaultTimeZone)!!.toLocalDate())
                            it.iCalEvent.setEnd(dbEvent.getEnd(event.defaultTimeZone)!!.toLocalDate())
                        }
                    } else {
                        TimberLogger.d("part day, updating datetime: ${dbEvent.getStart(event.defaultTimeZone)!!}/${dbEvent.getEnd(event.defaultTimeZone)!!}")
                        event.also {
                            it.iCalEvent.setStart(dbEvent.getStart(event.defaultTimeZone)!!.toLocalDate(), event.getStart(event.defaultTimeZone)!!.toLocalTime(), event.defaultTimeZone)
                            it.iCalEvent.setEnd(dbEvent.getEnd(event.defaultTimeZone)!!.toLocalDate(), event.getEnd(event.defaultTimeZone)!!.toLocalTime(), event.defaultTimeZone)
                        }
                    }

                } else {

                    // update the original event's DTSTART with date and time
                    //  which means no changes to just edited event, but it will overwrite the original event

                    event

                }
            }
            else -> event // else no special changes for regular event, just overwrite everything
        }

        // TODO make sure at least current day-of-week is in byDay list, when start date is changed but recurrence rule is not



        // TODO run work manager
        TimberLogger.d(("calling edit event use case with ${newEvent.iCalendar.printToString()}"))
        val createEventResult = viewModelScope.async(Dispatchers.IO) {
            createEventUseCase.execute(TODOuserID, newEvent.calendar.id, newEvent)
        }

        return createEventResult.await() == UseCase.Result.Success

    }

    fun handleCalendar(calendar: CalendarEntity) {
        markEventAsEdited()
        event = event.copy(calendar = Calendar(calendar.id, calendar.name, calendar.color, calendar.isActive))
        _event.postValue(event)
    }

    fun handleTimeZone(timeZoneId: String) {
        markEventAsEdited(bumpSequenceId = true)
        event.iCalendar.setDefaultTimeZone(timeZoneId)
        _event.postValue(event)
    }

    fun handleStartDate(newDate: LocalDate) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getStart(displayTimeZoneId)!!
        if (event.isAllDay()) {
            event.iCalEvent.setStart(newDate)
        } else {
            event.iCalEvent.setStart(newDate, old.toLocalTime(), displayTimeZoneId)
        }
        event.iCalendar.adjustRRuleToStartDate()
        _event.postValue(event)
    }

    fun handleEndDate(newDate: LocalDate) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getEnd(displayTimeZoneId)!!
        if (event.isAllDay()) {
            event.iCalEvent.setEnd(newDate)
        } else {
            event.iCalEvent.setEnd(newDate, old.toLocalTime(), displayTimeZoneId)
        }
        _event.postValue(event)
    }

    fun handleStartTime(newTime: LocalTime) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getStart(displayTimeZoneId)!!
        event.iCalEvent.setStart(old.toLocalDate(), newTime, displayTimeZoneId)
        timeStartBackup = newTime
        _event.postValue(event)
    }

    fun handleEndTime(newTime: LocalTime) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getEnd(displayTimeZoneId)!!
        event.iCalEvent.setEnd(old.toLocalDate(), newTime, displayTimeZoneId)
        timeEndBackup = newTime
        _event.postValue(event)
    }

    fun handleAllDaySwitch(isAllDay: Boolean) {
        markEventAsEdited()

        if (isAllDay) {
            // persist backup of timezone & start/end times
            //eventStartTimeZoneIdBackup = event.startTimeZoneId
//            timeStartBackup = ZonedDateTime.ofInstant(event.iCalEvent.dateStart.value.toInstant(), ZoneId.of(initialTimeZoneId)).toLocalTime()
//            timeEndBackup = ZonedDateTime.ofInstant(event.iCalEvent.dateEnd.value.toInstant(), ZoneId.of(initialTimeZoneId)).toLocalTime()
            //TimberLogger.d("saving backup: $timeStartBackup, ${timeEndBackup}")

            // remove time part and timezone from start/end
            event.iCalEvent.setStart(event.getStart(displayTimeZoneId)!!.toLocalDate())
            event.iCalEvent.setEnd(event.getEnd(displayTimeZoneId)!!.toLocalDate())

//            event.iCalendar.setStartTimeZone(null)
//            event.iCalendar.setEndTimeZone(null)
        } else {
            // get times & timezone from backup, but date from current event date
            event.iCalEvent.setStart(
                event.getStart(displayTimeZoneId)!!.toLocalDate(),
                timeStartBackup!!,
                displayTimeZoneId
            )

            event.iCalEvent.setEnd(
                event.getEnd(displayTimeZoneId)!!.toLocalDate(),
                timeEndBackup!!,
                displayTimeZoneId
            )

//            event.iCalendar.setStartTimeZone(defaultTimeZoneId)
//            event.iCalendar.setEndTimeZone(eventStartTimeZoneIdBackup)
        }

        setDefaultAlarms(event, calendarSettings)

        event.iCalendar.adjustRRuleToStartDate()

        _event.postValue(event)
    }

    /**
     * Handles simple Recurrence Rule like weekly or monthly.
     *
     * @param frequency if null, removes entire recurrence rule
     */
    fun handleRecurrence(frequency: Frequency?, untilDate: Boolean, interval: Int? = null, count: Int? = null, daysOfWeek: List<DayOfWeek>? = null, customMonthly: Boolean = false) {

        markEventAsEdited(bumpSequenceId = true)

        val builder = Recurrence.Builder(frequency)

        if (frequency != null) {
            interval?.let {
                if (it > 1) {
                    builder.interval(it)
                }
            }
            count?.let {
                builder.count(it)
            }
            if (untilDate && tempRecurrenceUntilLocalDate != null) {
                val until = if (event.isAllDay()) {
                    ICalDate(tempRecurrenceUntilLocalDate!!.toDate("UTC"), false)
                } else {
                    ICalDate(Date.from(ZonedDateTime.of(tempRecurrenceUntilLocalDate!!, LocalTime.of(23, 59, 59), ZoneId.of(displayTimeZoneId)).withZoneSameInstant(ZoneId.of("UTC")).toInstant()), true)
                }
                builder.until(until)
            }
            daysOfWeek?.let {
                builder.byDay(daysOfWeek)
            }
            if (customMonthly) {
                val eventStartDate = event.getStart(displayTimeZoneId)!!.toLocalDate()
                val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()
                val weekInMonth = eventStartDate.weekInMonth()

                when (tempMonthlyRepeatOption) {
                    MonthlyRepatOnOption.ON_DAY_X -> { }
                    MonthlyRepatOnOption.ON_X_WEEKDAY -> {
                        builder.byDay(iCalDayOfWeek) // TODO be careful about using .byDay(ByDay(int, weekday)) because it uses different notation
                        builder.bySetPos(weekInMonth)
                    }
                    MonthlyRepatOnOption.ON_LAST_WEEKDAY -> {
                        builder.byDay(iCalDayOfWeek)
                        builder.bySetPos(-1)
                    }
                }
            }
        }

        event.iCalEvent.setRecurrenceRule(if (frequency != null) builder.build() else null)
        _event.postValue(event)
    }

    private fun markEventAsEdited(bumpSequenceId: Boolean = false) {
        TimberLogger.d("markEventAsEdited, bump seqid = $bumpSequenceId")
        eventEdited = true
        if (bumpSequenceId) eventBumpSeqId = true
    }

    fun hasEventBeenEdited() = eventEdited

    fun hasEventTimeBeenEdited() = eventEdited

    fun isEventNew() = !event.isSyncedWithApi()

    fun isEventRecurring() = event.isRecurring()

    fun isEventFirstOccurrence() = event.isFirstOccurrence()

    fun handleRecurrenceUntilDate(untilLocalDate: LocalDate?) {
        markEventAsEdited(bumpSequenceId = true)
        tempRecurrenceUntilLocalDate = untilLocalDate
    }

    enum class MonthlyRepatOnOption {
        ON_DAY_X,
        ON_X_WEEKDAY,
        ON_LAST_WEEKDAY
    }

    /**
     * Complicated logic for displaying monthly recurrence options is calculated by ViewModel.
     */
    fun calculateMonthlyRepeatOnOptions(): List<MonthlyRepatOnOption> {
        val eventStartDate = event.getStart(displayTimeZoneId)!!.toLocalDate()

        val options = mutableListOf(MonthlyRepatOnOption.ON_DAY_X)
        if (eventStartDate.weekInMonth() <= 4) options.add(MonthlyRepatOnOption.ON_X_WEEKDAY)
        if (eventStartDate.isLastDayOfWeekInMonth()) options.add(MonthlyRepatOnOption.ON_LAST_WEEKDAY)

        return options
    }

    /**
     * Complicated logic for determining selected recurrence option is calculated by ViewModel.
     */
    fun calculateMonthlyRepeatOnOptionIndex(): Int {

        val repeatOptions = calculateMonthlyRepeatOnOptions()

            event.iCalEvent.recurrenceRule?.value?.run {

            if (this.frequency != Frequency.MONTHLY) return 0

                val eventStartDate = event.getStart(displayTimeZoneId)!!.toLocalDate()

                val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()
                val weekInMonth = eventStartDate.weekInMonth()

                val eventDaySetPos = this.bySetPos.getOrNull(this.byDay.indexOfFirst { it.day == iCalDayOfWeek })

                if (eventDaySetPos != null) {
                    if (eventDaySetPos in 1..4) {
                        return 1
                    } else if (eventDaySetPos == -1) {
                        return repeatOptions.lastIndex
                    }
                }
        }

        return 0 // default: Recurrence Rule never ends
    }

    fun handleRecurrenceRepeatOn(selectedIndex: Int) {
        markEventAsEdited(bumpSequenceId = true)
        this.tempMonthlyRepeatOption = calculateMonthlyRepeatOnOptions()[selectedIndex]
    }

    enum class SendByOption {
        NOTIFICATION,
        EMAIL
    }

    enum class RelativeNotificationTrigger {

    }

    fun handleAlarmSendBy(option: SendByOption) {
        markEventAsEdited()
        this.tempAlarmSendByOption = option
    }

    fun handleAlarmTime(time: LocalTime) {
        markEventAsEdited()
        this.tempAlarmTime = time
    }

    fun handleAlarm(alarmTypeOption: Int, count: Int? = null, countTypeOption: Int? = null) {
        markEventAsEdited()

        val duration = if (event.isAllDay()) {
            when (alarmTypeOption) {
                0 -> Duration.builder().prior(false).hours(9).build() // on the day at 9:00
                1 -> Duration.builder().prior(true).hours(6).build() // day before at 18:00
                2 -> Duration.builder().prior(true).days(6).hours(15).build() // 1 week before at 9:00, -P6DT15H
                3 -> Duration.builder().prior(true).weeks(2).days(6).hours(15).build() // 3 weeks before at 9:00, -P2W6DT15H
                4 -> null // all-day alarms have 1 fewer option
                5 -> { // custom
                    TimberLogger.d(" time = ${tempAlarmTime}") // TODO

                    // -P6DT15H 1 week before at 9
                    // -P6DT23H59M 1 week before at 00:01

                    if (count != null && countTypeOption != null) {
                        Duration.builder().apply {
                            prior(true)
                            when (countTypeOption) {
                                0 -> {
//                                    if (tempAlarmTime.hour == 0 && tempAlarmTime.minute == 0) {
//                                        days(count)
//                                    } else {
                                        //if (count > 1)
                                        val adjustedDays = count - 1
                                        if (adjustedDays > 0) days(adjustedDays)

                                        val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong()).minusMinutes(tempAlarmTime.minute.toLong())

                                        if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                        if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
//                                    }
                                }
                                1 -> {
//                                    if (tempAlarmTime.hour == 0 && tempAlarmTime.minute == 0) {
//                                        weeks(count)
//                                    } else {
                                        //if (count > 1)
                                        val adjustedWeeks = count - 1
                                        if (adjustedWeeks > 0) weeks(adjustedWeeks)
                                        days(7 - 1)

                                        val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong()).minusMinutes(tempAlarmTime.minute.toLong())

                                        if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                        if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
//                                    }


                                    // TODO

                                }
                            }
                        }.build()
                    } else null
                }
                else -> null
            }
        } else { // partial-day trigger can contain only one component
            when (alarmTypeOption) {
                0 -> Duration.builder().prior(false).seconds(0).build() // at the time of event
                1 -> Duration.builder().prior(true).minutes(10).build()
                2 -> Duration.builder().prior(true).minutes(30).build()
                3 -> Duration.builder().prior(true).hours(1).build()
                4 -> Duration.builder().prior(true).weeks(1).build()
                5 -> { // custom
                    Duration.builder().apply {
                        prior(true)
                        when (countTypeOption) {
                            0 -> minutes(count)
                            1 -> hours(count)
                            2 -> days(count)
                            3 -> weeks(count)
                        }
                    }.build()
                }
                else -> null
            }
        }

        TimberLogger.d("duration: ${duration}")

        duration?.apply {

            val alarm = when (tempAlarmSendByOption) {
                SendByOption.NOTIFICATION -> VAlarm.display(Trigger(duration, Related.START), null)
                SendByOption.EMAIL -> VAlarm.email(Trigger(duration, Related.START), null, null, emptyList())
            }

            event.iCalEvent.addAlarm(alarm)
            _event.postValue(event)
        }

    }

    fun handleAlarmDelete(index: Int) {
        markEventAsEdited()
        event.iCalEvent.alarms.removeAt(index)
        _event.postValue(event)
    }



}
