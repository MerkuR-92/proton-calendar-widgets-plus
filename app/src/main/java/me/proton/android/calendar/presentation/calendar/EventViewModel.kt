package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.parameter.ParticipationLevel
import biweekly.parameter.ParticipationStatus
import biweekly.parameter.Related
import biweekly.property.*
import biweekly.util.*
import biweekly.util.DayOfWeek
import biweekly.util.Duration
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.ICalUtils.adjustRRuleToStartDate
import me.proton.android.calendar.common.ICalUtils.adjustToWeekStart
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.ICalUtils.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.ICalUtils.formatTimeZoneId
import me.proton.android.calendar.common.ICalUtils.iCalTimeZone
import me.proton.android.calendar.common.ICalUtils.isDateTimeTheSame
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.domain.entity.UserId
import java.time.*
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList

class EventViewModel(
    private val context: Context,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val createEventUseCase: EditCreateEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val sendEmailUseCase: SendEmailUseCase,
    private val logger: Logger,
    private val json: Json
) : ViewModel() {

    sealed class Result {
        object Success : Result()
        object OccurrenceDoesntExist : Result()
        object EventDoesntExist : Result()
        class Error(val message: String) : Result()
    }

    private lateinit var userId: UserId

    private var timeStartBackup: LocalTime? = null
    private var timeEndBackup: LocalTime? = null

    private var eventEdited = false
    private var editMode = false
    private var isCreate = false

    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private val bgScope = CoroutineScope(Dispatchers.Default + viewModelJob)

    private lateinit var event: Event
    // original event from database, from before it has been edited
    var dbEvent: Event? = null

    private var eventCustomPartialDayAlarmsSave: ArrayList<VAlarm>? = null
    private var eventCustomAllDayAlarmsSave: ArrayList<VAlarm>? = null

    private lateinit var calendarSettings: CalendarSettingsEntity
    private val _event = MutableLiveData<Event>() // TODO see if there's less ugly way

    private var originalDbEvent: Event? = null

    val eventLiveData: LiveData<Event> = _event

    // TimeZone used when displaying event is taken from settings
    lateinit var displayTimeZoneId: String
    // TimeZone for editing event is always event's own timezone, or default
    lateinit var eventTimeZoneId: String

    lateinit var calendarUserSettings: CalendarUserSettingsEntity
    lateinit var userSettings: UserSettingsEntity

    var recurrenceManuallyEdited: Boolean = false
    private var singleEditsInfo: SingleEditsInfo? = null

    var savingEvent = MutableLiveData(false)
    var deletingEvent = MutableLiveData(false)
    var changeAnswerLoading = MutableLiveData(false)

    var adjustedEndDate = false

    // TODO: Initialise is called a second time for same eventId if we open event form from event details
    //  Check if any case require us to pass through it again or if we keep the init data we had from details
    suspend fun initialise(
        userId: UserId,
        editMode: Boolean,
        eventId: String?,
        occurrenceNumber: Int?,
        initStartDate: String?,
        initStartTime: String? /*TODO in the future also endDate for multi-day events*/
    ): Result {

        // reset backup values
        timeStartBackup = null
        timeEndBackup = null
        eventEdited = false
        eventCustomPartialDayAlarmsSave = null
        eventCustomAllDayAlarmsSave = null
        savingEvent.postValue(false)
        deletingEvent.postValue(false)
        changeAnswerLoading.postValue(false)
        dbEvent = null
        originalDbEvent = null
        recurrenceManuallyEdited = false
        singleEditsInfo = null
        tempRecurrenceUntilLocalDate = null
        adjustedEndDate = false

        this.editMode = editMode

        this.userId = userId

        this.isCreate = eventId == null

        var defaultCalendar: CalendarEntity? = null
        if (editMode) {
            var defaultCalendarId = calendarsRepository.getDefaultCalendarId(userId.id)
                ?: return Result.Error("EventViewModel: could not get default calendar ID")
            defaultCalendar = calendarsRepository.selectCalendar(defaultCalendarId)
            if (defaultCalendar == null || !defaultCalendar.isActive) {
                defaultCalendar = calendarsRepository.getActiveCalendars(userId.id).firstOrNull()
                    ?: return Result.Error("EventViewModel: no active calendars for user")
                defaultCalendarId = defaultCalendar.id
            }

            if (!loadSettingsForCalendar(defaultCalendarId)) return Result.Error("EventViewModel: could not get CalendarSettings")
        }

        calendarUserSettings = calendarsRepository.selectCalendarUserSettings(userId.id) ?: return Result.Error("EventViewModel: could not get Calendar User Settings")
        userSettings = usersRepository.selectUserSettings(userId.id) ?: return Result.Error("EventViewModel: could not get User Settings")

        displayTimeZoneId = calendarUserSettings.primaryTimezone

        logger.d("displayTimezoneid = ${displayTimeZoneId}")

        logger.d("EventViewModel initialise with EventId: $eventId")
        logger.d("EventViewModel initialise with startDate: $initStartDate")
        logger.d("EventViewModel initialise with startTime: ${initStartTime}")

        event = if (eventId == null) {

            if (defaultCalendar == null) return Result.Error("EventViewModel: could not get default calendar")

            eventTimeZoneId = displayTimeZoneId

            val newICalendar = ICalUtils.createNewEvent().wrapInICalendar()
            val newVEvent = newICalendar.events.first()

            // if there is no requested start date, we take today
            val startDate =
                if (initStartDate != null) LocalDate.parse(initStartDate)
                else ZonedDateTime.now(ZoneId.of(eventTimeZoneId)).toLocalDate()
            // if there is no requested start time, we calculate it according to "now"
            val startTime =
                if (initStartTime != null) LocalTime.parse(initStartTime)
                else ZonedDateTime.now(ZoneId.of(eventTimeZoneId)).plusMinutes(
                    this.calendarSettings.defaultEventDuration.toLong()
                ).truncatedTo(ChronoUnit.HOURS).toLocalTime()
            // end Zoned Date Time according to default event duration
            val endZonedDateTime = ZonedDateTime.of(
                startDate,
                startTime,
                ZoneId.of(eventTimeZoneId)).plusMinutes(this.calendarSettings.defaultEventDuration.toLong()
            )

            timeStartBackup = startTime
            timeEndBackup = endZonedDateTime.toLocalTime() // this time can be before timeStartBackup at this point

            // TODO GUI takes timezone from iCalendar's "default timezone", maybe this should be moved to "Event" model?
            newICalendar.setDefaultTimeZone(eventTimeZoneId)

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
                newVEvent.setStart(startDate, startTime, eventTimeZoneId)
                newVEvent.setEnd(endZonedDateTime.toLocalDate(), endZonedDateTime.toLocalTime(), eventTimeZoneId)
                newICalendar.setStartTimeZone(eventTimeZoneId)
                newICalendar.setEndTimeZone(eventTimeZoneId)
            }

            logger.d("INIT: ${newICalendar.printToString()}")

            val newEvent = Event(ICalUtils.generateOfflineEventId(), Calendar(
                defaultCalendar.id,
                defaultCalendar.name,
                defaultCalendar.color,
                defaultCalendar.flags,
                defaultCalendar.display == 1
            ), newICalendar)

            setDefaultAlarms(newEvent, this.calendarSettings)
            newEvent

        } else {

            logger.v("event view model init with occurrence: $occurrenceNumber")

            val dbEventEntity = calendarsRepository.selectEventEntity(eventId)
            dbEvent = if (dbEventEntity != null) transformEventUseCase.execute(dbEventEntity)
            else null

            if (dbEvent == null) return Result.EventDoesntExist

            logger.d("timezone before generating occurrence: ${dbEvent?.iCalendar?.timezoneInfo?.getTimezone(dbEvent?.iCalEvent?.dateStart)?.timeZone?.id}")

            val eventStartTimeZone = dbEvent?.iCalendar?.timezoneInfo?.getTimezone(dbEvent?.iCalEvent?.dateStart)?.timeZone?.id ?: displayTimeZoneId

            eventTimeZoneId = eventStartTimeZone

            val timeZoneForOccurrence = if (editMode) {
                eventTimeZoneId
            } else {
                displayTimeZoneId
            }

            // we have to generate occurrence in event's timezone, because otherwise we will overwrite it with default calendar's timezone
            val dbEventWithOccurrence = occurrenceNumber?.let { dbEvent?.withOccurrence(occurrenceNumber, timeZoneForOccurrence) }
            // return error only if event dbEvent is recurring, if it's a single edit it's okay that occurrence can't be generated
            if (occurrenceNumber != null && (dbEvent?.isRecurring() == true) && dbEventWithOccurrence == null) return Result.OccurrenceDoesntExist

            val adjustedEvent = (dbEventWithOccurrence ?: dbEvent?.copy(iCalendar = dbEvent?.iCalendar?.clone() as ICalendar))?.apply {

                if (this.isAllDay()) { // adjust endDate to -1 day if event has no time
                    this.iCalEvent.setEnd(this.getEnd(timeZoneForOccurrence)!!.toLocalDate().minusDays(1))
                }

                // default timezone in iCalendar is used for GUI
                this.iCalendar.setDefaultTimeZone(timeZoneForOccurrence)

                if (editMode) {
                    // Setup event time backup values
                    if (this.isAllDay()) {
                        val startTime = ZonedDateTime.now(ZoneId.of(eventTimeZoneId))
                            .plusMinutes(this@EventViewModel.calendarSettings.defaultEventDuration.toLong())
                            .truncatedTo(ChronoUnit.HOURS).toLocalTime()
                        timeStartBackup = startTime
                        timeEndBackup =
                            startTime.plusMinutes(this@EventViewModel.calendarSettings.defaultEventDuration.toLong())
                    } else {
                        timeStartBackup = this.getStart(timeZoneForOccurrence)!!.toLocalTime()
                        timeEndBackup = this.getEnd(timeZoneForOccurrence)!!.toLocalTime()
                    }

                    // Clone RRule from original event in DB if we are in edit mode
                    val eventUid = dbEvent?.uid
                    if (dbEvent?.isSingleEdit() == true && eventUid != null) {
                        // We store reference to originalDbEvent for later use
                        originalDbEvent = calendarsRepository.selectRootEventEntity(eventUid)
                            ?.let { transformEventUseCase.execute(it) }
                        this.iCalEvent.recurrenceRule = originalDbEvent?.iCalEvent?.recurrenceRule
                    }

                    dbEvent?.let { dbEvent ->
                        hasExDates(true)
                    }
                }

            }

            if (adjustedEvent != null) {

                if (dbEvent != null && listOf(adjustedEvent).filterOutOccurrencesByExdates(dbEvent!!, timeZoneForOccurrence).isEmpty()) {
                    return Result.OccurrenceDoesntExist
                }

                adjustedEvent

            } else return Result.Error("EventViewModel: could not generate event with occurrence in EventViewModel")
        }

        _event.postValue(event)

        return Result.Success
    }

    data class SingleEditsInfo(
        val hasSingleEdit: Boolean,
        val hasFutureSingleEdit: Boolean,
        val hasAnsweredSingleEdit: Map<ParticipationStatus, Boolean>)

    suspend fun getSingleEditsInfo(userEmails: List<String>? = null): SingleEditsInfo? {

        if (singleEditsInfo == null) {

            val event = _event.value ?: return null
            val dbEvent = dbEvent ?: return null

            var hasSingleEdit: Boolean = false
            var hasFutureSingleEdit: Boolean = false
            val hasAnsweredSingleEdit = hashMapOf<ParticipationStatus, Boolean>()

            val occurrenceStart = event.getActualStart(eventTimeZoneId)
            val occurrence = event.occurrence
            val allowShowThisAndFuture =
                occurrence?.occurrenceNumber != null &&
                        occurrence.occurrenceNumber > 1 &&
                        !event.isEventFirstOccurrence(dbEvent, eventTimeZoneId)

            val hasAttendees = !event.iCalEvent.attendees.isNullOrEmpty()

            // We check for single edits only once and in initialise because it may require API calls
            hasSingleEdit =
                if (occurrence?.occurrenceNumber == 1 && !allowShowThisAndFuture && (editMode || !hasAttendees && userEmails != null)) {
                    // We don't have option "this and future" when updating first event in chain
                    // TODO Decide behavior if API call was an error and method returns null
                    dbEvent.isRecurring() && calendarsRepository.hasSingleEdits(userId, dbEvent.uid) == true
                } else {
                    // TODO Decide behavior if API call was an error and method returns null
                    val singleEdits = calendarsRepository.getSingleEdits(
                        userId,
                        dbEvent.uid,
                        if (editMode || !hasAttendees && userEmails != null)
                            occurrenceStart
                        else null, // Fetch all SE when event has attendees in order to check for hasAnsweredSingleEdit
                        if (editMode || !hasAttendees && userEmails != null)
                            eventTimeZoneId
                        else null
                    )
                    singleEdits?.forEach { singleEdit ->
                        if (singleEdit.getStart(eventTimeZoneId)?.isAfter(occurrenceStart) == true) {
                            hasFutureSingleEdit = true
                        }
                        // We only need hasAnsweredSingleEdit for change answer in event details view (if event has attendees)
                        if (!editMode && hasAttendees && userEmails != null && !singleEdit.isCancelled()) {
                            // The only values we need are Accepted, Declined and Tentative
                            when (singleEdit.getParticipationStatus(userEmails)) {
                                ParticipationStatus.ACCEPTED -> hasAnsweredSingleEdit[ParticipationStatus.ACCEPTED] = true
                                ParticipationStatus.DECLINED -> hasAnsweredSingleEdit[ParticipationStatus.DECLINED] = true
                                ParticipationStatus.TENTATIVE -> hasAnsweredSingleEdit[ParticipationStatus.TENTATIVE] = true
                            }
                        }
                    }
                    !singleEdits.isNullOrEmpty()
                }

            singleEditsInfo = SingleEditsInfo(hasSingleEdit, hasFutureSingleEdit, hasAnsweredSingleEdit)
        }

        return singleEditsInfo

    }

    private suspend fun loadSettingsForCalendar(calendarId: String): Boolean {
        return withContext(Dispatchers.Default) {
            val settings = calendarsRepository.selectCalendarSettings(calendarId)
            if (settings != null) {
                calendarSettings = settings
                true
            } else {
                false
            }
        }

    }

    private fun getDefaultAlarms(calendarSettings: CalendarSettingsEntity, isAllDay: Boolean): List<VAlarm> {
        val alarms = ArrayList<VAlarm>()
        val defaultNotifications = if (isAllDay) calendarSettings.defaultFullDayNotifications else calendarSettings.defaultPartDayNotifications
        defaultNotifications.mapNotNull { if ((it as? JsonObject) != null) json.decodeFromJsonElement<CalendarSettingsEntity.AlarmEntity>(it) else null }.forEach { alarm ->
            alarm.parseTrigger()?.let {
                if (alarm.type == 0) {
                    alarms.add(VAlarm.email(it, null, null))
                } else {
                    alarms.add(VAlarm.display(it, null))
                }
            }
        }
        return alarms
    }

    private fun setDefaultAlarms(event: Event, calendarSettings: CalendarSettingsEntity) {
        event.iCalEvent.alarms.clear()
        getDefaultAlarms(calendarSettings, event.isAllDay()).forEach {
            // TODO Remove alarm type check once other types are handled
            if (it.action == Action.display()) event.iCalEvent.addAlarm(it)
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
        val until = event.iCalEvent.recurrenceRule?.value?.until?.toZonedDateTime(eventTimeZoneId) ?: return
        this.tempRecurrenceUntilLocalDate = until.toLocalDate()
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
            !(event.getStart(eventTimeZoneId)?.isAfter(event.getEnd(eventTimeZoneId)) ?: false)
        } else {
            event.getStart(eventTimeZoneId)?.isBefore(event.getEnd(eventTimeZoneId)) ?: false
        }
    }

    // alarm temp values
    var tempAlarmSendByOption: SendByOption = SendByOption.NOTIFICATION
    var tempAlarmTime: LocalTime = LocalTime.of(9, 0)

    // TODO Remove filter once other type of alarms are handled
    fun isAlarmLimitReached() = this.event.iCalEvent.alarms.size >= FormValidation.ALARM_COUNT_MAX

    /**
     * Resets temporary values for Alarm.
     */
    fun initialiseForAlarm(/*TODO pass alarm index for edit?*/) {
        this.tempAlarmSendByOption = SendByOption.NOTIFICATION
        this.tempAlarmTime = LocalTime.of(9, 0)
    }

    enum class HandleSaveResult {
        SUCCESS,
        CREATE_ERROR_SEND_MAIL,
        EDIT_ERROR_SEND_MAIL,
        ERROR
    }

    suspend fun handleSave(
        editOption: EventEditDeleteOption? = null,
        occurrenceNumber: Int,
        resources: Resources,
        timeFormatIs24Hours: Boolean): HandleSaveResult { // create or edit
        // TODO MOVE WHATEVER WE CAN TO WORKER!!!!

        logger.d("handleSave with editOption: $editOption")
        logger.d("calendar before adjusting: " + event.iCalendar.printToString())

        // Post saving event value to true to trigger loading state
        savingEvent.postValue(true)

        if (event.isAllDay() && !adjustedEndDate) {
            event.iCalendar.adjustOutgoingAllDayEvent(event.defaultTimeZone!!)
            adjustedEndDate = true
            logger.d("calendar for all-day: " + event.iCalendar.printToString())
        } else if (!event.isAllDay()) {
            event.iCalendar.adjustStartEndTimeZones(eventTimeZoneId, event.defaultTimeZone!!)
            logger.d("calendar for part-time after adjusting timezones: " + event.iCalendar.printToString())
        }

        event.iCalEvent.recurrenceRule?.adjustToWeekStart(userSettings.weekStartDayOfWeek())

        val dbEvent = calendarsRepository.selectEventEntity(event.id)?.let { transformEventUseCase.execute(it) }
        val immutableOriginalDbEvent = originalDbEvent
        val dbEventStartDate = dbEvent?.iCalEvent?.getStart(event.defaultTimeZone!!)
        val originalDbEventStartDate = immutableOriginalDbEvent?.iCalEvent?.getStart(event.defaultTimeZone!!)
        val dbEventWithOccurrence = dbEvent?.withOccurrence(occurrenceNumber, event.defaultTimeZone!!)
        val dbEventWithOccurrenceStartDate = dbEventWithOccurrence?.iCalEvent?.getStart(event.defaultTimeZone!!)

        handleSequence(dbEventWithOccurrence)

        logger.d("db event =${dbEvent?.iCalendar?.printToString()}")
        logger.d(("dbEventStartDate : ${dbEventStartDate}"))
        logger.d(("dbEventWithOccurrence : ${dbEventWithOccurrence?.iCalendar?.printToString()}"))
        logger.d(("dbEventWithOccurrenceStartDate : ${dbEventWithOccurrenceStartDate}"))

        val newEvent = when (editOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                if (dbEvent?.isRecurring() == true) {

                    // TODO BUG:
                    // 1. event is a regular event
                    // 2. edit it and add rrule
                    // 3. we get into this nullcheck here!

                    if (dbEventWithOccurrenceStartDate == null) {
                        logger.e("dbEventWithOccurrenceStartDate == null")
                        return HandleSaveResult.ERROR
                    }

                    if (!handleOriginalEventNullSequence(dbEvent)) return HandleSaveResult.ERROR

                    val eventToCreate = event.copy( // TODO move to helper method?
                        id = ICalUtils.generateOfflineEventId(),
                        iCalendar = event.iCalendar.clone()
                    )
                    // event.uid is still the same

                    // delete all recurring properties
                    eventToCreate.iCalEvent.recurrenceRule = null
                    eventToCreate.iCalEvent.exceptionDates.clear()

                    // TODO dtstart/end is incorrect, when creating new event that is in different timezone -- we should normalize the time back to original!!!!!
                    val timeHasBeenChanged = !eventToCreate.iCalendar.isDateTimeTheSame(dbEventWithOccurrence.iCalendar)
                    if (timeHasBeenChanged) { // TODO it looks like we always use occurrence start date anyway
                        logger.d("time has been changed")
//                        eventToCreate.setRecurrenceId(dbEventWithOccurrenceStartDate, !eventToCreate.isAllDay())
                    } else {
                        logger.d("time is the same")
                    }

                    // Make sure timezone matches parent timezone
                    if (!dbEvent.isAllDay() && dbEvent.iCalendar.iCalTimeZone(dbEvent.iCalEvent.dateStart).id != event.defaultTimeZone!!) {
                        eventToCreate.setRecurrenceId(
                            dbEventWithOccurrenceStartDate.withZoneSameInstant(ZoneId.of(dbEvent.iCalendar.iCalTimeZone(dbEvent.iCalEvent.dateStart).id)),
                            !dbEvent.isAllDay()
                        )
                    } else {
                        eventToCreate.setRecurrenceId(dbEventWithOccurrenceStartDate, !dbEvent.isAllDay())
                    }

                    eventToCreate

                } else if (dbEvent?.isSingleEdit() == true) {
                    immutableOriginalDbEvent?.let {
                        if (!handleOriginalEventNullSequence(it)) return HandleSaveResult.ERROR
                    }

                    val eventToCreate = event.copy(iCalendar = event.iCalendar.clone())
                    eventToCreate.iCalEvent.recurrenceRule = null
                    eventToCreate.iCalEvent.exceptionDates.clear()

                    eventToCreate
                } else {
                    event // else no special changes for regular event, just overwrite everything
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {
                // TODO THIS NEEDS TO BE FIXED, WE PROBABLY CAN'T FIND EVENTS IN DB
                if (dbEvent == null) {
                    logger.e("dbEvent == null")
                    return HandleSaveResult.ERROR
                }
                if (!dbEvent.isSingleEdit() && dbEventWithOccurrenceStartDate == null) {
                    logger.e("dbEventWithOccurrenceStartDate == null")
                    return HandleSaveResult.ERROR
                }
                if (dbEvent.isSingleEdit() && (immutableOriginalDbEvent == null || dbEventStartDate == null)) {
                    if (immutableOriginalDbEvent == null) logger.e("originalDbEvent == null")
                    if (dbEventStartDate == null) logger.e("dbEventStartDate == null")
                    return HandleSaveResult.ERROR
                }

                // delete single edits starting with just edited occurrence / single edit
                val eventId = if (dbEvent.isSingleEdit()) immutableOriginalDbEvent!!.id else event.id
                val deleteStartDate = if (dbEvent.isSingleEdit()) dbEventStartDate!!.minusNanos(1) else dbEventWithOccurrenceStartDate!!.minusNanos(1)
                val deleteSingleEditsResult = deleteEventUseCase.execute(userId, eventId, deleteStartDate)
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) { }
                if (deleteSingleEditsResult !is UseCase.Result.Success<*>) return HandleSaveResult.ERROR

                // update original event:
                // - change COUNT to ((current occurrence number) - 1)
                // OR
                // - change UNTIL equal to (previous occurrence from just edited).endDate
                val dbEventToCopy = if (dbEvent.isSingleEdit()) immutableOriginalDbEvent else dbEvent
                val dbEventToUpdate = dbEventToCopy!!.copy(iCalendar = dbEventToCopy.iCalendar.clone())
                // Bump sequence for original event
                dbEventToUpdate.iCalEvent.setSequence((dbEventToUpdate.iCalEvent.sequence?.value ?: 0) + 1)
                val timezone = event.defaultTimeZone!!
                dbEventToUpdate.iCalEvent.recurrenceRule?.value?.let {
                    dbEventToUpdate.iCalEvent.setRecurrenceRule(
                        Recurrence.Builder(dbEventToUpdate.iCalEvent.recurrenceRule.value)
                            // count = 0 will not happen because this edit option is not available for first occurrence
                            .count(
                                if (it.count != null) occurrenceNumber - 1
                                else null
                            )
                            .until(
                                // Prioritize count over until
                                if (it.count != null) {
                                    null
                                } else if (dbEventToUpdate.isAllDay()) {
                                    ICalDate(
                                        dbEventToUpdate.generateOccurrence(
                                            occurrenceNumber,
                                            timezone
                                        )!!.startDateTime
                                            .minusDays(1)
                                            .with(ChronoField.HOUR_OF_DAY, 0)
                                            .toLocalDate()
                                            .toDate(ZoneId.systemDefault().id)
                                        , false
                                    )
                                } else {
                                    // 1 second to midnight on the start-day of previous original occurrence
                                    ICalDate(
                                        Date.from(
                                            ZonedDateTime.of(
                                                dbEventToUpdate.generateOccurrence(
                                                    occurrenceNumber,
                                                    timezone
                                                )!!.startDateTime.minusDays(1).toLocalDate(),
                                                LocalTime.of(23, 59, 59), ZoneId.of(timezone)
                                            ).toInstant()), true
                                    )
                                }
                            )
                            .build()
                    )
                }

                val editOriginalEventResult = editCreateEventUseCase.execute(userId, dbEventToUpdate.calendar.id, dbEventToUpdate)
                if (editOriginalEventResult !is UseCase.Result.Success<*>) {
                    if (editOriginalEventResult is UseCase.Result.Error) {
                        logger.e("error editing event: ${editOriginalEventResult.message}")
                    } else if (editOriginalEventResult is UseCase.Result.Error) {
                        logger.e("error editing event: ${editOriginalEventResult.message}")
                    }
                    return HandleSaveResult.ERROR
                }

                // TODO delete exdates after this occurrence?

                // --------------------------------------

                val eventToCreate = event.copy(
                    id = ICalUtils.generateOfflineEventId(),
                    iCalendar = event.iCalendar.clone().apply {
                        this.events.first().apply {
                            setUid(
                                ICalUtils.generateProtonUid(
                                    event.uid,
                                    ICalDateFormat.DATE_TIME_BASIC_WITHOUT_TZ.format(
                                        ICalUtils.eventStartZonedDateTimeToDate(if (dbEvent.isSingleEdit()) dbEventStartDate!! else dbEventWithOccurrenceStartDate!!, dbEvent.isAllDay())
                                    )
                                )
                            )
                            exceptionDates.clear()
                            val nullDate: Date? = null
                            setRecurrenceId(nullDate)
                            event.iCalEvent.recurrenceRule?.value?.let {
                                setRecurrenceRule(
                                    Recurrence.Builder(event.iCalEvent.recurrenceRule.value)
                                        .count(
                                            if (it.count != null) {
                                                val originalCount = dbEventToCopy.iCalEvent.recurrenceRule.value.count
                                                if (originalCount != null && originalCount == it.count) {
                                                    it.count - (occurrenceNumber - 1)
                                                } else {
                                                    it.count
                                                }
                                            }
                                            else null
                                        )
                                        // UNTIL is copied from event's RRULE
                                        .build()
                                )
                            }
                        }
                    }
                )
                if (dbEvent.isSingleEdit()) eventToCreate.iCalEvent.recurrenceId = null

                eventToCreate
            }
            EventEditDeleteOption.ALL_EVENTS -> {
                // All events expected behavior :
                // Single deletions and single edits are always reset when doing this operation.
                // - If Event start date : only time changed (same day), and RRule not changed
                //      -> update original event time with event value
                // - If Event start date : date changed (different day) / RRule changed
                //      -> update original event date and time with event values

                if (dbEvent?.isSingleEdit() == true && immutableOriginalDbEvent == null) {
                    logger.e("Edit all events: originalEventStartDate was null for single edit")
                    return HandleSaveResult.ERROR
                }

                // delete all single edits
                val originalEventId =
                    if (dbEvent?.isSingleEdit() == true) immutableOriginalDbEvent?.id
                    else event.id
                val originalEventStartDate =
                    if (dbEvent?.isSingleEdit() == true) originalDbEventStartDate
                    else dbEventStartDate

                if (originalEventStartDate == null) {
                    logger.e("Edit all events: originalEventStartDate was null")
                    return HandleSaveResult.ERROR
                }
                if (originalEventId == null) {
                    logger.e("Edit all events: originalEventId was null")
                    return HandleSaveResult.ERROR
                }

                val deleteSingleEditsResult =
                    deleteEventUseCase.execute(userId, originalEventId, originalEventStartDate.minusNanos(1))
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) { }
                if (deleteSingleEditsResult !is UseCase.Result.Success<*>) return HandleSaveResult.ERROR

                // delete all single deletions
                event.iCalEvent.exceptionDates.clear()

                // TWO SEPARATE THINGS:
                // - if event.isAllDay != dbEvent.isAllDay() it means there was a conversion all-day-part-day
                // - the same DAY but different TIME

                val originalEventWithOccurrence =
                    if (dbEvent?.isSingleEdit() == true) immutableOriginalDbEvent?.withOccurrence(occurrenceNumber, event.defaultTimeZone!!)
                    else dbEventWithOccurrence

                if (originalEventWithOccurrence == null) {
                    logger.e("Edit all events: originalEventWithOccurrence was null")
                    return HandleSaveResult.ERROR
                }

                val hasDayChanged =
                    (if (dbEvent?.isSingleEdit() == true) dbEventStartDate
                    else dbEventWithOccurrenceStartDate)?.truncatedTo(ChronoUnit.DAYS) != event.getStart(event.defaultTimeZone!!)?.truncatedTo(ChronoUnit.DAYS)

                if (!hasDayChanged &&
                    originalEventWithOccurrence.iCalEvent.recurrenceRule == event.iCalEvent.recurrenceRule) {

                    // update the original event's DTSTART only with new time (leave day the same)

                    // TODO Try to reduce duplicated code between single edit and occurrence logic
                    if (dbEvent?.isSingleEdit() == true) {
                        if (immutableOriginalDbEvent == null) {
                            logger.e("Edit all events: dbEvent was null")
                            return HandleSaveResult.ERROR
                        }

                        val newEvent = event.copy(
                            id = immutableOriginalDbEvent.id,
                            iCalendar = event.iCalendar.clone()
                        )
                        newEvent.iCalEvent.recurrenceId = null
                        newEvent.iCalEvent.uid = immutableOriginalDbEvent.iCalEvent.uid

                        // For single edit we need to calculate span to add days to original event date end
                        val eventSpan = ChronoUnit.DAYS.between(newEvent.getStart(event.defaultTimeZone!!), newEvent.getEnd(event.defaultTimeZone!!))
                        if (event.isAllDay()) {
                            // We use original event LocalDate
                            newEvent.also {
                                it.iCalEvent.setStart(immutableOriginalDbEvent.getStart(event.defaultTimeZone!!)!!.toLocalDate())
                                it.iCalEvent.setEnd(immutableOriginalDbEvent.getStart(event.defaultTimeZone!!)!!.plusDays(eventSpan).toLocalDate())
                            }
                        } else {
                            // We use currently edited event LocalTime but keep original event LocalDate
                            newEvent.also {
                                it.iCalEvent.setStart(
                                    immutableOriginalDbEvent.getStart(event.defaultTimeZone!!)!!.toLocalDate(),
                                    newEvent.getStart(event.defaultTimeZone!!)!!.toLocalTime(),
                                    event.defaultTimeZone
                                )
                                it.iCalEvent.setEnd(
                                    immutableOriginalDbEvent.getStart(event.defaultTimeZone!!)!!.plusDays(eventSpan).toLocalDate(),
                                    newEvent.getEnd(event.defaultTimeZone!!)!!.toLocalTime(),
                                    event.defaultTimeZone
                                )
                            }
                        }
                    } else {
                        if (dbEvent == null) {
                            logger.e("Edit all events: dbEvent was null")
                            return HandleSaveResult.ERROR
                        }
                        val eventSpan = ChronoUnit.DAYS.between(event.getStart(event.defaultTimeZone!!), event.getEnd(event.defaultTimeZone!!))
                        if (event.isAllDay()) {
                            event.also {
                                it.iCalEvent.setStart(dbEvent.getStart(event.defaultTimeZone!!)!!.toLocalDate())
                                it.iCalEvent.setEnd(dbEvent.getStart(event.defaultTimeZone!!)!!.plusDays(eventSpan).toLocalDate())
                            }
                        } else {
                            event.also {
                                it.iCalEvent.setStart(
                                    dbEvent.getStart(event.defaultTimeZone!!)!!.toLocalDate(),
                                    event.getStart(event.defaultTimeZone!!)!!.toLocalTime(),
                                    event.defaultTimeZone
                                )
                                it.iCalEvent.setEnd(
                                    dbEvent.getStart(event.defaultTimeZone!!)!!.plusDays(eventSpan).toLocalDate(),
                                    event.getEnd(event.defaultTimeZone!!)!!.toLocalTime(),
                                    event.defaultTimeZone
                                )
                            }
                        }
                    }

                } else {
                    // update the original event's DTSTART with date and time
                    //  which means no changes to just edited event, but it will overwrite the original event

                    if (dbEvent?.isSingleEdit() == true) {

                        // clear recurrenceId and use original event id since single edit will replace original event
                        val newEvent = event.copy(
                            id = originalEventId,
                            iCalendar = event.iCalendar.clone()
                        )
                        newEvent.iCalEvent.recurrenceId = null
                        newEvent.iCalEvent.exceptionDates.clear()

                        newEvent
                    } else {
                        event
                    }

                }
            }
            else -> event // else no special changes for regular event, just overwrite everything
        }

        // TODO make sure at least current day-of-week is in byDay list, when start date is changed but recurrence rule is not


        var subject: String? = null
        var body: String? = null
        if (!newEvent.iCalEvent.attendees.isNullOrEmpty()) {
            val eventCopy = newEvent.copy(iCalendar = newEvent.iCalendar.clone() as ICalendar)
            if (eventCopy.isAllDay()) {
                eventCopy.iCalEvent.setDateEnd(
                    ICalDate(
                        eventCopy.iCalEvent.getEnd(event.defaultTimeZone!!)?.toLocalDate()?.minusDays(1)?.toDate(event.defaultTimeZone!!),
                        false
                    )
                )
            }
            subject = getInviteMailSubject(eventCopy, event.defaultTimeZone!!, resources, timeFormatIs24Hours)
            body = getInviteMailBody(eventCopy, event.defaultTimeZone!!, resources, timeFormatIs24Hours)
        }

        // TODO Refactor and move into UseCase
        if (!isCreate && !newEvent.iCalEvent.attendees.isNullOrEmpty()) {
            val sendEmailResult = sendEmailUseCase.executeToAttendees(userId, newEvent.id, newEvent.iCalEvent.attendees, subject!!, body!!, isCreate, newEvent)
            sendEmailResult.ifSuccessAndLogErrors(logger) { }

            if (sendEmailResult is UseCase.Result.InvalidParams) {
                logger.e("invalid params in send email: ${sendEmailResult.message}")
                return HandleSaveResult.EDIT_ERROR_SEND_MAIL
            }
            if (sendEmailResult is UseCase.Result.Error) {
                logger.e("error in send email: ${sendEmailResult.message}")
                return HandleSaveResult.EDIT_ERROR_SEND_MAIL
            }
        }

        // TODO run work manager
        logger.d(("calling edit event use case with ${newEvent.iCalendar.printToString()}"))
        val createEventResult = viewModelScope.async(Dispatchers.IO) {
            createEventUseCase.execute(userId, newEvent.calendar.id, newEvent)
        }.await()

        if (createEventResult is UseCase.Result.InvalidParams) {
            logger.e("invalid params in create event: ${createEventResult.message}")
            return HandleSaveResult.ERROR
        }
        if (createEventResult is UseCase.Result.Error) {
            logger.e("error in create event: ${createEventResult.message}")
            return HandleSaveResult.ERROR
        }

        // TODO Refactor and move into UseCase
        if (isCreate && !newEvent.iCalEvent.attendees.isNullOrEmpty() && createEventResult is UseCase.Result.Success<*>) {
            createEventResult.returnValue.tryCast<List<String>> {
                if (this.isNullOrEmpty()) return@tryCast

                val sendEmailResult = sendEmailUseCase.executeToAttendees(userId, this.first(), newEvent.iCalEvent.attendees, subject!!, body!!, isCreate)
                // If send email fails the event without attendees remains in the calendar
                sendEmailResult.ifSuccessAndLogErrors(logger) { }

                if (sendEmailResult !is UseCase.Result.Success<*>) return HandleSaveResult.CREATE_ERROR_SEND_MAIL
            }
        }

        return HandleSaveResult.SUCCESS
    }

    private fun getInviteMailSubject(event: Event, timezone: String, resources: Resources, timeFormatIs24Hours: Boolean): String {
        // TODO Move to UseCase once we can use strings resources there
        return if (!event.isAllDay()) {
            val dateTimeStart =
                event.formatStart(timezone, timeFormatIs24Hours)
            resources.getString(
                R.string.event_send_invite_mail_subject_part_day,
                dateTimeStart.first,
                dateTimeStart.second,
                formatTimeZoneId(timezone, event.iCalEvent.dateStart.value.toInstant(), displayId = false)
            )
        } else if (!event.spansSingleDay(true, timeZoneId = timezone)) {
            resources.getString(
                R.string.event_send_invite_mail_subject_all_day_multiple,
                event.formatStart(
                    timezone,
                    timeFormatIs24Hours
                ).first
            )
        } else {
            resources.getString(
                R.string.event_send_invite_mail_subject_all_day,
                event.formatStart(
                    timezone,
                    timeFormatIs24Hours
                ).first
            )
        }
    }

    private fun getInviteMailBody(event: Event, timezone: String, resources: Resources, timeFormatIs24Hours: Boolean): String {
        // TODO Move to UseCase once we can use strings resources there
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)
        var body = resources.getString(
            R.string.event_send_invite_mail_body,
            event.summary ?: resources.getString(R.string.default_event_summary),
            if (event.isAllDay() && !event.spansSingleDay(true, timeZoneId = timezone)) {
                resources.getString(
                    R.string.event_send_invite_mail_body_all_day_multiple,
                    formattedDateStart.first,
                    formattedDateEnd.first
                )
            } else if (event.isAllDay()) {
                resources.getString(
                    R.string.event_send_invite_mail_body_all_day_single,
                    formattedDateStart.first
                )
            } else {
                resources.getString(
                    R.string.event_send_invite_mail_body_part_day,
                    formattedDateStart.first,
                    formattedDateStart.second,
                    formatTimeZoneId(timezone, event.iCalEvent.dateStart.value.toInstant(), displayId = false),
                    formattedDateEnd.first,
                    formattedDateEnd.second,
                    formatTimeZoneId(timezone, event.iCalEvent.dateEnd.value.toInstant(), displayId = false)
                )
            }
        )
        if (event.location != null) body += resources.getString(
            R.string.event_send_invite_mail_body_where,
            event.location
        )
        if (event.description != null) body += resources.getString(
            R.string.event_send_invite_mail_body_description,
            event.description
        )
        return body
    }

    private fun handleSequence(dbEventWithOccurrence: Event? = null) {
        // Bump sequence when event is new or following changes :
        // - Status
        // - Start / End time in UTC
        // - Recurrence ID
        // - Recurrence Rule
        // Note that when editing a single edits we ignore the recurrence rule changes
        val dbEventStart =
            if (event.isRecurring()) dbEventWithOccurrence?.getStart(eventTimeZoneId)
            else dbEvent?.getStart(eventTimeZoneId)
        val dbEventEnd =
            if (event.isRecurring()) dbEventWithOccurrence?.getEnd(eventTimeZoneId)
            else dbEvent?.getEnd(eventTimeZoneId)

        val bumpSequence = dbEvent == null ||
                dbEvent?.status != event.status ||
                dbEventStart != event.getStart(eventTimeZoneId) ||
                dbEventEnd != event.getEnd(eventTimeZoneId) ||
                (dbEvent?.isSingleEdit() == false && dbEvent?.iCalEvent?.recurrenceRule != event.iCalEvent.recurrenceRule)

        if (bumpSequence || event.iCalEvent.sequence?.value == null) {
            if (event.isSyncedWithApi()) {
                event.iCalEvent.setSequence((event.iCalEvent.sequence?.value ?: 0) + 1) // TODO conflict resolution
            }
        }
    }

    private suspend fun handleOriginalEventNullSequence(dbEvent: Event): Boolean {
        // Update the sequence of parent if it didn't have a value before
        if (dbEvent.iCalEvent.sequence?.value == null) {
            dbEvent.iCalEvent.setSequence(0)
            val editOriginalEventResult = editCreateEventUseCase.execute(userId, dbEvent.calendar.id, dbEvent)
            if (editOriginalEventResult !is UseCase.Result.Success<*>) {
                if (editOriginalEventResult is UseCase.Result.Error) {
                    logger.e("error editing event: ${editOriginalEventResult.message}")
                } else if (editOriginalEventResult is UseCase.Result.Error) {
                    logger.e("error editing event: ${editOriginalEventResult.message}")
                }
                return false
            }
        }
        return true
    }

    suspend fun handleCalendar(calendar: CalendarEntity): Boolean {
        // If user choice has been saved then we don't set calendar's default alarms
        val alarmsEdited = (event.isAllDay() && eventCustomAllDayAlarmsSave != null) ||
                (!event.isAllDay() && eventCustomPartialDayAlarmsSave != null)
        return if (loadSettingsForCalendar(calendar.id)) {
            markEventAsEdited()
            if (event.iCalEvent.organizer != null) {
                val organizerEmail = calendarsRepository.selectMembers(calendar.id).firstOrNull {
                    it.hasPermission(MemberEntity.Permission.SUPEROWNER)
                }?.email
                event.iCalEvent.organizer = Organizer(organizerEmail, organizerEmail)
            }
            event = event.copy(calendar = Calendar(calendar.id, calendar.name, calendar.color, calendar.flags, calendar.display == 1))
            if (!alarmsEdited) setDefaultAlarms(event, calendarSettings)
            _event.postValue(event)
            true
        } else {
            false
        }
    }

    fun handleTimeZone(timeZoneId: String) {
        markEventAsEdited()
        event.iCalendar.setDefaultTimeZone(timeZoneId)
        eventTimeZoneId = timeZoneId
        _event.postValue(event)
    }

    fun handleStartDate(newDate: LocalDate) {
        markEventAsEdited()
        val old = event.getStart(eventTimeZoneId)!!
        val endDate = event.getEnd(eventTimeZoneId)!!
        val unit = ChronoUnit.DAYS
        val diff = unit.between(old, endDate)
        when {
            diff > 0 -> handleEndDate(newDate.plusDays(diff))
            diff < 0 -> handleEndDate(newDate.minusDays(diff))
            else -> handleEndDate(newDate)
        }
        if (event.isAllDay()) {
            event.iCalEvent.setStart(newDate)
        } else {
            event.iCalEvent.setStart(newDate, old.toLocalTime(), eventTimeZoneId)
        }
        event.iCalendar.adjustRRuleToStartDate(old)
        _event.postValue(event)
    }

    fun handleEndDate(newDate: LocalDate) {
        markEventAsEdited()
        adjustedEndDate = false
        val old = event.getEnd(eventTimeZoneId)!!
        if (event.isAllDay()) {
            event.iCalEvent.setEnd(newDate)
        } else {
            event.iCalEvent.setEnd(newDate, old.toLocalTime(), eventTimeZoneId)
        }
        _event.postValue(event)
    }

    fun handleStartTime(newTime: LocalTime) {
        markEventAsEdited()
        val old = event.getStart(eventTimeZoneId)!!
        val endTime = event.getEnd(eventTimeZoneId)!!
        val newDate = LocalDateTime.of(old.toLocalDate(), newTime.truncatedTo(ChronoUnit.MINUTES))
        val unit = ChronoUnit.MINUTES
        val diff = unit.between(old, endTime)
        val newEndTime = newDate.plusMinutes(diff)
        if (newEndTime.dayOfWeek != endTime.dayOfWeek)
            handleEndDate(newEndTime.toLocalDate())
        handleEndTime(newEndTime.toLocalTime())
        event.iCalEvent.setStart(old.toLocalDate(), newTime, eventTimeZoneId)
        timeStartBackup = newTime
        _event.postValue(event)
    }

    fun handleEndTime(newTime: LocalTime) {
        markEventAsEdited()
        val old = event.getEnd(eventTimeZoneId)!!
        event.iCalEvent.setEnd(old.toLocalDate(), newTime, eventTimeZoneId)
        timeEndBackup = newTime
        _event.postValue(event)
    }

    fun handleAllDaySwitch(isAllDay: Boolean) {
        val timeStart = timeStartBackup
        val timeEnd = timeEndBackup
        if (timeStart == null || timeEnd == null) return
        markEventAsEdited()
        if (isAllDay) {
            // remove time part and timezone from start/end
            event.iCalEvent.setStart(event.getStart(eventTimeZoneId)!!.toLocalDate())
            event.iCalEvent.setEnd(event.getEnd(eventTimeZoneId)!!.toLocalDate())
        } else {
            // get times & timezone from backup, but date from current event date
            event.iCalEvent.setStart(
                event.getStart(eventTimeZoneId)!!.toLocalDate(),
                timeStart,
                eventTimeZoneId
            )

            event.iCalEvent.setEnd(
                event.getEnd(eventTimeZoneId)!!.toLocalDate(),
                timeEnd,
                eventTimeZoneId
            )
        }

        // If user choice has been saved then we don't set calendar's default alarms
        if ((isAllDay && eventCustomAllDayAlarmsSave == null) ||
            (!isAllDay && eventCustomPartialDayAlarmsSave == null)) {
            setDefaultAlarms(event, calendarSettings)
        } else {
            event.iCalEvent.alarms.clear()
            // If user choice has been saved then use it even if alarm list is empty
            val savedAlarms = if (isAllDay) eventCustomAllDayAlarmsSave?.toList() else eventCustomPartialDayAlarmsSave?.toList()
            savedAlarms?.forEach {
                event.iCalEvent.addAlarm(it)
            }
        }

        event.iCalendar.adjustRRuleToStartDate()

        _event.postValue(event)
    }

    /**
     * Handles simple Recurrence Rule like weekly or monthly.
     *
     * @param frequency if null, removes entire recurrence rule
     */
    fun handleRecurrence(frequency: Frequency?, untilDate: Boolean, interval: Int? = null, count: Int? = null, daysOfWeek: List<DayOfWeek>? = null, customMonthly: Boolean = false) {
        markEventAsEdited()
        recurrenceManuallyEdited = true
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
                    ICalDate(tempRecurrenceUntilLocalDate!!.toDate(ZoneId.systemDefault().id), false)
                } else {
                    ICalDate(Date.from(ZonedDateTime.of(tempRecurrenceUntilLocalDate!!, LocalTime.of(23, 59, 59), ZoneId.of(eventTimeZoneId)).withZoneSameInstant(ZoneId.of(eventTimeZoneId)).toInstant()), true)
                }
                builder.until(until)
            }
            daysOfWeek?.let {
                builder.byDay(daysOfWeek)
            }
            if (customMonthly) {
                val eventStartDate = event.getStart(eventTimeZoneId)!!.toLocalDate()
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

    private fun markEventAsEdited() {
        eventEdited = true
    }

    fun hasEventBeenEdited(): Boolean {
        return eventEdited
    }

    fun isEventNew() = !event.isSyncedWithApi()

    fun isEventRecurring() = event.isRecurring()

    fun isEventPartOfChain() = event.isPartOfChain()

    fun isEventFirstOccurrence() = event.isFirstOccurrence()

    fun handleRecurrenceUntilDate(untilLocalDate: LocalDate?) {
        markEventAsEdited()
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
        val eventStartDate = event.getStart(eventTimeZoneId)!!.toLocalDate()

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

            val eventStartDate = event.getStart(eventTimeZoneId)!!.toLocalDate()

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

    fun handleRecurrenceRepeatOn(monthlyRepeatOnOption: MonthlyRepatOnOption) {
        markEventAsEdited()
        this.tempMonthlyRepeatOption = monthlyRepeatOnOption
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
                    logger.d("alarm time = ${tempAlarmTime}") // TODO

                    // -P6DT15H 1 week before at 9
                    // -P6DT23H59M 1 week before at 00:01

                    if (count != null && countTypeOption != null) {
                        Duration.builder().apply {

                            val alarmAtMidnight = tempAlarmTime == LocalTime.MIDNIGHT

                            // We hide minutes and hours buttons and use same radio group so days and weeks have id 2 & 3
                            when (countTypeOption) {
                                2 -> {
                                    prior(true)
                                    val adjustedDays = count - 1 + (if (alarmAtMidnight) 1 else 0)
                                    if (adjustedDays > 0) days(adjustedDays)
                                    if (count == 0) days(count)

                                    val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong()).minusMinutes(tempAlarmTime.minute.toLong())

                                    if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                    if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
                                }
                                3 -> {
                                    prior(true)
                                    val adjustedWeeks = count - 1 + (if (alarmAtMidnight) 1 else 0)
                                    if (adjustedWeeks > 0) weeks(adjustedWeeks)

                                    if (!alarmAtMidnight) {
                                        days(7 - 1)
                                    }

                                    val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong()).minusMinutes(tempAlarmTime.minute.toLong())

                                    if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                    if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
                                }
                                // On the day at x
                                4 -> {
                                    prior(false)
                                    val positiveTimeOfDay = LocalTime.of(0, 0).plusHours(tempAlarmTime.hour.toLong()).plusMinutes(tempAlarmTime.minute.toLong())

                                    if (positiveTimeOfDay.hour > 0) hours(positiveTimeOfDay.hour)
                                    if (positiveTimeOfDay.minute > 0) minutes(positiveTimeOfDay.minute)

                                    // on the same day at 00:00 which means "at the time of the event"
                                    if (tempAlarmTime == LocalTime.MIDNIGHT) seconds(0)
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

        logger.d("duration: ${duration}")

        duration?.apply {

            val alarm = when (tempAlarmSendByOption) {
                SendByOption.NOTIFICATION -> VAlarm.display(Trigger(duration, Related.START), null)
                SendByOption.EMAIL -> VAlarm.email(Trigger(duration, Related.START), null, null, emptyList())
            }

            event.iCalEvent.addAlarm(alarm)
            saveUserEditedAlarms()
            _event.postValue(event)
        }
    }

    fun handleAlarmDelete(index: Int) {
        markEventAsEdited()
        event.iCalEvent.alarms.removeAt(index)
        saveUserEditedAlarms()
        _event.postValue(event)
    }

    private fun saveUserEditedAlarms() {
        // If an action is done on alarms we go into edited alarm mode and save the user choice over default alarms
        if (event.isAllDay()) eventCustomAllDayAlarmsSave = ArrayList(event.iCalEvent.alarms)
        else eventCustomPartialDayAlarmsSave = ArrayList(event.iCalEvent.alarms)
    }

    // Returns timezone id if it has been initialized
    fun getDisplayTimeZone(): ZoneId? {
        return if (this::displayTimeZoneId.isInitialized) ZoneId.of(displayTimeZoneId) else null
    }

    fun hasExDates(afterSelectedEvent: Boolean = false): Boolean {
        val immutableOriginalEvent =
            if (event.isSingleEdit()) originalDbEvent
            else dbEvent

        immutableOriginalEvent?.let { dbEvent ->
            return if (afterSelectedEvent) {
                val exZonedDateTimes =
                    dbEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                        exDates.values.map { exDate ->
                            exDate.toZonedDateTime(eventTimeZoneId)
                        }
                    }
                exZonedDateTimes.firstOrNull {
                    it.isAfter(event.getStart(eventTimeZoneId))
                } != null
            } else {
                dbEvent.isRecurring() && !dbEvent.iCalEvent.exceptionDates.isNullOrEmpty()
            }
        }
        return false
    }

    fun hasRecurrenceRuleBeenEdited(): Boolean {
        val immutableOriginalEvent =
            if (event.isSingleEdit()) originalDbEvent
            else dbEvent
        return immutableOriginalEvent?.iCalEvent?.recurrenceRule != event.iCalEvent.recurrenceRule
    }

    suspend fun handleAttendee(attendee: Attendee, canonicalEmail: String = "", addAttendee: Boolean = true) {
        markEventAsEdited()
        if (addAttendee) {
            attendee.rsvp = true
            attendee.participationLevel = ParticipationLevel.REQUIRED
            attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
            val token = ICalUtils.generateXPmToken(canonicalEmail, event.uid)
            attendee.addParameter(X_PM_TOKEN, token)
            event.iCalEvent.addAttendee(
                attendee
            )
            if (event.iCalEvent.organizer == null) {
                val organizerEmail = calendarsRepository.selectMembers(event.calendar.id).firstOrNull {
                    it.hasPermission(MemberEntity.Permission.SUPEROWNER)
                }?.email
                event.iCalEvent.organizer = Organizer(organizerEmail, organizerEmail)
            }
        } else {
            event.iCalEvent.attendees.remove(attendee)
            if (event.iCalEvent.organizer != null && event.iCalEvent.attendees.isNullOrEmpty()) {
                event.iCalEvent.organizer = null
            }
        }
        _event.postValue(event)
    }

    fun handleParticipationStatus(userEmails: List<String>, participationStatus: ParticipationStatus) {
        event.updateParticipationStatus(userEmails, participationStatus)
        _event.postValue(event)
    }

    suspend fun updateParticipationStatus(
        calendarId: String,
        eventId: String,
        attendeeId: String,
        participationStatus: ParticipationStatus,
        userAttendee: Attendee,
        userEmails: List<String>,
        subject: String,
        body: String
    ) : Boolean {
        val status = participationStatus.toInt()

        val userParticipationStatus = userAttendee.participationStatus

        val eventCopy = event.copy(iCalendar = dbEvent?.iCalendar?.clone() as ICalendar)
        val personalPartICalString =
            if (participationStatus == ParticipationStatus.DECLINED &&
                event.iCalEvent.alarms != null && event.iCalEvent.alarms.isNotEmpty()) {
                // if changes to NO, remove all notifications if there are any
                eventCopy.iCalEvent.alarms.clear()
                ""
            } else if ((userParticipationStatus == ParticipationStatus.DECLINED ||
                        userParticipationStatus == ParticipationStatus.NEEDS_ACTION) &&
                (participationStatus == ParticipationStatus.ACCEPTED || participationStatus == ParticipationStatus.TENTATIVE) &&
                event.iCalEvent.alarms.isNullOrEmpty()) {
                // if changes from NO to YES/MAYBE add default calendar notifications
                if (loadSettingsForCalendar(calendarId)) {
                    setDefaultAlarms(eventCopy, calendarSettings)
                    val calendarSplit = ICalUtils.splitICalendarIntoParts(eventCopy.iCalendar)
                    calendarSplit.personalPart?.printToString()
                } else null
            } else {
                // else keep notifications as it is
                null
            }

        val sendEmailUseCaseResult = sendEmailUseCase.executeToOrganizer(
            userId,
            eventCopy.iCalendar,
            dbEvent?.iCalendar?.timezoneInfo,
            userAttendee.copy(),
            event.iCalEvent.organizer.email,
            participationStatus,
            subject,
            body
        )
        sendEmailUseCaseResult.ifSuccessAndLogErrors(logger) { }
        if (sendEmailUseCaseResult !is UseCase.Result.Success<*>) {
            return false
        }

        val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
            userId,
            calendarId,
            eventId,
            attendeeId,
            status,
            personalPartICalString
        )
        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
        if (updateParticipationStatusUseCaseResult !is UseCase.Result.Success<*>) {
            return false
        }

        if (!event.isSingleEdit() && singleEditsInfo?.hasSingleEdit == true) {
            // If chain has single edits, update their part stat to NEEDS_ACTION
            clearSingleEditsParticipationStatus(calendarId, event.uid, userEmails, participationStatus)
        }

        // Apply alarms modifications
        if (personalPartICalString?.isEmpty() == true) {
            // clear alarms
            event.iCalEvent.alarms.clear()
            _event.postValue(event)
        } else if (personalPartICalString?.isNotEmpty() == true) {
            // add default alarms
            setDefaultAlarms(event, calendarSettings)
            _event.postValue(event)
        }
        return true
    }

    private fun clearSingleEditsParticipationStatus(
        calendarId: String,
        eventUid: String,
        userEmails: List<String>,
        mainChainParticipationStatus: ParticipationStatus
    ) : LiveData<Operation.State> {
        val status = mainChainParticipationStatus.toInt()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT,
                    UseCaseWorker.INPUT_USER_ID to userId.id,
                    UseCaseWorker.INPUT_CALENDAR_ID to calendarId,
                    UseCaseWorker.INPUT_EVENT_UID to eventUid,
                    UseCaseWorker.INPUT_USER_EMAILS to userEmails.toTypedArray(),
                    UseCaseWorker.INPUT_PARTICIPATION_STATUS to status
                )
            )
            .build()

        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT, ExistingWorkPolicy.REPLACE, work).state
    }

    suspend fun isStandaloneSingleEdit(): Boolean {
        return calendarsRepository.isStandaloneSingleEdit(userId, event.uid) == true
    }
}
