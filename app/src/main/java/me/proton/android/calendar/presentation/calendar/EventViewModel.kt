package me.proton.android.calendar.presentation.calendar

import androidx.lifecycle.*
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.RecurrenceId
import biweekly.property.Trigger
import biweekly.util.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import me.proton.android.calendar.common.*
import me.proton.android.calendar.data.api.CalendarUserSettingsApiEntity
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.DeleteEventUseCase
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class EventViewModel(
    private val calendarsRepository: CalendarsRepository,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val valueStoreProvider: ValueStoreProvider,
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
    private val _event = MutableLiveData<Event>() // TODO see if there's less ugly way

    val eventLiveData: LiveData<Event> = _event

    /**
     * Initial TimeZoneId for this event, default from Calendar Settings or taken from Event.
     */
    lateinit var initialTimeZoneId: String

    suspend fun initialise(eventId: String?, initStartDate: String?, initStartTime: String? /*TODO in the future also endDate for multi-day events*/): UseCase.Result /* TODO maybe use separate Result class */ {

        // reset backup values
        timeStartBackup = null
        timeEndBackup = null
        eventEdited = false
        eventBumpSeqId = false

//            calendarUserSettings.defaultCalendarId // TODO we still can't rely on this, it can be null in API!!!
        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
        val todoDefaultCalendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")!!
        // TODO get those values from somewhere
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO
        val userId = TODOuserID//"IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="
        val calendarUserSettings = gson.fromJson(valueStoreProvider.provideValueStore(userId).getString(ValueKey.USER_CALENDAR_SETTINGS), CalendarUserSettingsApiEntity::class.java) ?: return UseCase.Result.Error("could not get User CalendarSettings")

        initialTimeZoneId = "Europe/Zurich" // TODO

        TimberLogger.d("EventViewModel initialise with EventId: $eventId")
        TimberLogger.d("EventViewModel initialise with startDate: $initStartDate")
        TimberLogger.d("EventViewModel initialise with startTime: ${initStartTime}")



        val defaultCalendar = calendarsRepository.selectCalendar(todoDefaultCalendarId) ?: return UseCase.Result.Error("could not get default Calendar from DB")
        val calendarSettings = calendarsRepository.selectSettings(defaultCalendar.id) ?: return UseCase.Result.Error("could not get CalendarSettings")

        event = if (eventId == null) {

            val newICalendar = ICalUtils.createNewEvent().wrapInICalendar()
            val newVEvent = newICalendar.events.first()





            // if there is no requested start date, we take today
            val startDate = if (initStartDate != null) LocalDate.parse(initStartDate) else LocalDate.now()
            // if there is no requested start time, we calculate it according to "now"
            val startTime = if (initStartTime != null) LocalTime.parse(initStartTime) else LocalTime.now().plusMinutes(calendarSettings.defaultEventDuration.toLong()).truncatedTo(ChronoUnit.HOURS)
            // end Zoned Date Time according to default event duration
            val endZonedDateTime = ZonedDateTime.of(startDate, startTime, ZoneId.of(initialTimeZoneId)).plusMinutes(calendarSettings.defaultEventDuration.toLong())

            timeStartBackup = startTime
            timeEndBackup = endZonedDateTime.toLocalTime() // this time can be before timeStartBackup at this point

            // TODO GUI takes timezone from iCalendar's "default timezone", maybe this should be moved to "Event" model?
            newICalendar.setDefaultTimeZone(initialTimeZoneId)

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
                newVEvent.setStart(startDate, startTime, initialTimeZoneId)
                newVEvent.setEnd(endZonedDateTime.toLocalDate(), endZonedDateTime.toLocalTime(), initialTimeZoneId)
                newICalendar.setStartTimeZone(initialTimeZoneId)
                newICalendar.setEndTimeZone(initialTimeZoneId)
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

            val newEvent = Event(ICalUtils.generateOfflineEventId(), Calendar(defaultCalendar.id, defaultCalendar.name, defaultCalendar.color), newICalendar)

            setDefaultAlarms(newEvent)
            newEvent

        } else {

            val dbEvent = viewModelScope.async(Dispatchers.IO) {
                calendarsRepository.eventFlow(eventId).first()
            }.await()


            dbEvent?.apply {

                if (dbEvent.isAllDay()) { // adjust endDate to -1 day if event has no time
                    dbEvent.iCalEvent.setEnd(dbEvent.endLocalDate!!.minusDays(1)) // TODO NPE

                    timeStartBackup = LocalTime.now()
                    timeEndBackup = LocalTime.now().plusMinutes(calendarSettings.defaultEventDuration.toLong())//.truncatedTo(ChronoUnit.HOURS)
                } else {
                    timeStartBackup = dbEvent.getStart(initialTimeZoneId)!!.toLocalTime()
                    timeEndBackup = dbEvent.getEnd(initialTimeZoneId)!!.toLocalTime()
                }

                // default timezone in iCalendar is used for GUI
                dbEvent.iCalendar.setDefaultTimeZone(dbEvent.iCalendar.timezoneInfo.getTimezone(dbEvent.iCalEvent.dateStart)?.timeZone?.id ?: initialTimeZoneId)

            } ?: return UseCase.Result.Error("could not find event ${eventId}")

                // TODO
                //handleAllDaySwitch(initStartTime == null)


        }

        _event.postValue(event)

        return UseCase.Result.Success
    }

    private fun setDefaultAlarms(event: Event) {
        event.iCalEvent.alarms.clear()

        val duration = if (event.isAllDay()) {
            Duration.builder().prior(true).hours(15).build() // 1 day before at 9:00
        } else {
            Duration.builder().prior(true).minutes(15).build()
        }

        event.iCalEvent.addAlarm(VAlarm.display(Trigger(duration, Related.START), null))
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
            !(event.getStart(initialTimeZoneId)?.isAfter(event.getEnd(initialTimeZoneId)) ?: false)
        } else {
            event.getStart(initialTimeZoneId)?.isBefore(event.getEnd(initialTimeZoneId)) ?: false
        }
    }

    // alarm temp values
    var tempAlarmSendByOption: SendByOption = SendByOption.NOTIFICATION
    var tempAlarmTime: LocalTime = LocalTime.of(9, 0)

    /**
     * Resets temporary values for Alarm and optionally provides Alarm for editing.
     */
    fun initialiseForAlarm(/*TODO pass alarm index or sth?*/) {
        this.tempAlarmSendByOption = SendByOption.NOTIFICATION
        this.tempAlarmTime = LocalTime.of(9, 0)
    }



    suspend fun handleSave(editOption: EventEditDeleteOption? = null, occurrenceNumber: Int? = null): Boolean { // create or edit

//        val calendarToSave = event.iCalendar

        // TODO MOVE WHATEVER WE CAN TO WORKER!!!!

        TimberLogger.d("handleSave with editOption: $editOption")
        TimberLogger.d("calendar before adjusting: " + event.iCalendar.printToString())

        if (event.isAllDay()) {
            event.iCalendar.adjustOutgoingAllDayEvent(event.defaultTimeZone!!)
            initialTimeZoneId = event.defaultTimeZone!!
            TimberLogger.d("calendar for all-day: " + event.iCalendar.printToString())
        } else {
            event.iCalendar.adjustStartEndTimeZones(initialTimeZoneId, event.defaultTimeZone!!)
            initialTimeZoneId = event.defaultTimeZone!!
            TimberLogger.d("calendar for part-time after adjusting timezones: " + event.iCalendar.printToString())
        }

        if (eventBumpSeqId) {
            event.iCalEvent.setSequence((event.iCalEvent.sequence?.value ?: 0) + 1)
        }

        val occurrence = event.generateOccurrence(occurrenceNumber ?: 0, event.defaultTimeZone!!)

        //TimberLogger.d(("occurence generated: ${occurrence}"))

        val dbEvent = calendarsRepository.selectEventEntity(event.id)?.let { transformEventUseCase.execute(it) }
//        val dbEventOccurrence = dbEvent?.let {
//            dbEventgenerateOccurrence(occurrenceNumber ?: 0, event.defaultTimeZone!!)
//        }

        //TimberLogger.d(("dbEventOccurrence generated: ${dbEventOccurrence}"))



        when (editOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                if (event.isRecurring()) { // TODO this is not completely correct, because singly-edited events with recurrence-id and UID are still linked with original event

                    if (occurrence == null) return false

                    event = event.copy( // TODO move to helper method?
                        id = ICalUtils.generateOfflineEventId()
                        //,
                        //iCalendar = ICalendar(calendarToSave)
                    )
                    // event.uid is still the same


                    val recurrenceStartDate = Date.from(occurrence.startDateTime.toInstant())
                    val recurrenceEndDate = Date.from(occurrence.endDateTime.toInstant())

                    event.iCalEvent.setDateStart(recurrenceStartDate, !event.isAllDay())
                    event.iCalEvent.setDateEnd(recurrenceEndDate, !event.isAllDay())

                    event.iCalEvent.recurrenceRule = null
                    event.iCalEvent.setRecurrenceId(RecurrenceId(recurrenceStartDate, !event.isAllDay()))

                } // else no special changes for regular event, just overwrite everything

            }
            EventEditDeleteOption.THIS_EVENT_AND_FOLLOWING -> TODO()
            EventEditDeleteOption.ALL_EVENTS -> TODO()
            null -> {
                // CURRENT EDIT FOR ORIGINAL EVENT EDIT
            }
        }

        // TODO figure out if there was time-change

        // TODO make sure at least current day-of-week is in byDay list, when start date is changed but recurrence rule is not

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        // TODO run work manager
        TimberLogger.d(("calling use case with ${event.iCalendar.printToString()}"))
        val createEventResult = viewModelScope.async(Dispatchers.IO) {
            //createEventUseCase.execute(TODOuserID, event.calendar.id, event)
        }

        return false//createEventResult.await() == UseCase.Result.Success

    }

    fun handleCalendar(calendar: CalendarEntity) {
        markEventAsEdited()
        event = event.copy(calendar = Calendar(calendar.id, calendar.name, calendar.color))
        _event.postValue(event)
    }

    fun handleTimeZone(timeZoneId: String) {
        markEventAsEdited(bumpSequenceId = true)
        event.iCalendar.setDefaultTimeZone(timeZoneId)
        _event.postValue(event)
    }

    fun handleStartDate(newDate: LocalDate) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getStart(initialTimeZoneId)!!
        if (event.isAllDay()) {
            event.iCalEvent.setStart(newDate)
        } else {
            event.iCalEvent.setStart(newDate, old.toLocalTime(), initialTimeZoneId)
        }
        _event.postValue(event)
    }

    fun handleEndDate(newDate: LocalDate) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getEnd(initialTimeZoneId)!!
        if (event.isAllDay()) {
            event.iCalEvent.setEnd(newDate)
        } else {
            event.iCalEvent.setEnd(newDate, old.toLocalTime(), initialTimeZoneId)
        }
        _event.postValue(event)
    }

    fun handleStartTime(newTime: LocalTime) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getStart(initialTimeZoneId)!!
        event.iCalEvent.setStart(old.toLocalDate(), newTime, initialTimeZoneId)
        timeStartBackup = newTime
        _event.postValue(event)
    }

    fun handleEndTime(newTime: LocalTime) {
        markEventAsEdited(bumpSequenceId = true)
        val old = event.getEnd(initialTimeZoneId)!!
        event.iCalEvent.setEnd(old.toLocalDate(), newTime, initialTimeZoneId)
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
            event.iCalEvent.setStart(event.getStart(initialTimeZoneId)!!.toLocalDate())
            event.iCalEvent.setEnd(event.getEnd(initialTimeZoneId)!!.toLocalDate())

//            event.iCalendar.setStartTimeZone(null)
//            event.iCalendar.setEndTimeZone(null)
        } else {
            // get times & timezone from backup, but date from current event date
            event.iCalEvent.setStart(
                event.getStart(initialTimeZoneId)!!.toLocalDate(),
                timeStartBackup!!,
                initialTimeZoneId
            )

            event.iCalEvent.setEnd(
                event.getEnd(initialTimeZoneId)!!.toLocalDate(),
                timeEndBackup!!,
                initialTimeZoneId
            )

//            event.iCalendar.setStartTimeZone(defaultTimeZoneId)
//            event.iCalendar.setEndTimeZone(eventStartTimeZoneIdBackup)
        }

        setDefaultAlarms(event)

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
                builder.interval(it)
            }
            count?.let {
                builder.count(it)
            }
            if (untilDate && tempRecurrenceUntilLocalDate != null) {
                builder.until(tempRecurrenceUntilLocalDate!!.toDate(initialTimeZoneId))
            }
            daysOfWeek?.let {
                builder.byDay(daysOfWeek)
            }
            if (customMonthly) {
                val eventStartDate = event.getStart(initialTimeZoneId)!!.toLocalDate()
                val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()
                val weekInMonth = eventStartDate.weekInMonth()

                builder.byDay(iCalDayOfWeek)

                when (tempMonthlyRepeatOption) {
                    MonthlyRepatOnOption.ON_X_WEEKDAY -> builder.bySetPos(weekInMonth)
                    MonthlyRepatOnOption.ON_LAST_WEEKDAY -> builder.bySetPos(-1)
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
        val eventStartDate = event.getStart(initialTimeZoneId)!!.toLocalDate()

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

                val eventStartDate = event.getStart(initialTimeZoneId)!!.toLocalDate()

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
