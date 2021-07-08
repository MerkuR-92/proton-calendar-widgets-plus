package me.proton.android.calendar.presentation.calendar

import android.app.Application
import androidx.lifecycle.*
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.toInt
import me.proton.android.calendar.common.AndroidUtils.tryCast
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.DateTimeUtilsImpl.isLastDayOfWeekInMonth
import me.proton.android.calendar.common.DateTimeUtilsImpl.toBiweeklyDayOfWeek
import me.proton.android.calendar.common.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekInMonth
import me.proton.android.calendar.common.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.EventUtilsImpl.updateParticipationStatus
import me.proton.android.calendar.common.ICalUtilsImpl.adjustRRuleToStartDate
import me.proton.android.calendar.common.ICalUtilsImpl.clone
import me.proton.android.calendar.common.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.ICalUtilsImpl.setStartTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.wrapInICalendar
import me.proton.android.calendar.common.ProtonUtilsImpl.isShortDomainAddress
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.android.calendar.domain.model.User
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.util.kotlin.filterNullValues
import me.proton.core.util.kotlin.toBoolean
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList

class EventViewModel(
    application: Application,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val transformEventUseCase: TransformEventUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val sendEmailUseCase: SendEmailUseCase,
    private val logger: Logger,
    private val json: Json,
    private val getCanonicalEmailsUseCase: GetCanonicalEmailsUseCase,
    private val obtainSendPreferencesUseCase: ObtainSendPreferencesUseCase,
    private val handleSaveUseCase: HandleSaveUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase
) : AndroidViewModel(application) {

    sealed class Result {
        object Success : Result()
        object OccurrenceDoesNotExist : Result()
        object EventDoesNotExist : Result()
        class Error(val message: String) : Result()
    }

    private var coroutineScope = CoroutineScope(Dispatchers.Default)

    private lateinit var userId: UserId

    private var timeStartBackup: LocalTime? = null
    private var timeEndBackup: LocalTime? = null

    private var eventEdited = false
    private var editMode = false
    private var isCreate = false

    private lateinit var event: Event

    // original event from database, from before it has been edited
    var dbEvent: Event? = null

    private var eventCustomPartialDayAlarmsSave: ArrayList<VAlarm>? = null
    private var eventCustomAllDayAlarmsSave: ArrayList<VAlarm>? = null

    private lateinit var calendarSettings: CalendarSettingsEntity

    private var originalDbEvent: Event? = null

    private val _event = MutableLiveData<Event>() // TODO see if there's less ugly way
    val eventLiveData: LiveData<Event> = _event

    // TimeZone used when displaying event is taken from settings
    lateinit var displayTimeZoneId: String

    // TimeZone for editing event is always event's own timezone, or default
    lateinit var eventTimeZoneId: String

    lateinit var calendarUserSettings: CalendarUserSettingsEntity
    lateinit var userSettings: UserSettingsEntity
    lateinit var user: User

    var recurrenceManuallyEdited: Boolean = false
    private var singleEditsInfo: SingleEditsInfo? = null

    val eventState: MutableStateFlow<EventState> = MutableStateFlow(EventState.Idle)
    val eventDialogState: MutableStateFlow<EventDialogState?> = MutableStateFlow(null)

    var currentParticipationStatus: ParticipationStatus? = null

    // TODO Remove once we allow creating events with email notifications
    var hasEmailNotifications: Boolean = false

    sealed class EventState {

        // TODO
        object Idle: EventState()

        sealed class Processing: EventState() {
            object Saving: Processing()
            object Deleting: Processing()
            data class ChangingAnswer(val participationStatus: ParticipationStatus): Processing()
        }

    }

    sealed class EventDialogState {

        sealed class Save: EventDialogState() {

            data class SendPreferences(
                val sendPreferencesResults: SendPreferencesResults,
                val isAddParticipants: Boolean
            ): Save()
            data class AddParticipants(
                val hasExDates: Boolean,
                val hasSingleEdit: Boolean
            ): Save()
            object SendInvitation: Save()
            data class RecurringEvent(
                val sendPreferences: Map<Email, me.proton.android.calendar.domain.model.SendPreferences>,
                val showThisAndFuture: Boolean,
                val hasSingleEdit: Boolean,
                val hasFutureSingleEdit: Boolean
            ): Save()
        }

        sealed class Delete: EventDialogState() {

            object Event: Delete()
            object DisabledCalendarRecurring: Delete()
            data class RecurringEvent(
                val showThisAndFuture: Boolean
            ): Delete()
        }

        sealed class ChangeAnswer: EventDialogState() {

            data class SendPreferences(
                val participationStatus: ParticipationStatus,
                val obtainError: ObtainSendPreferencesUseCase.Result.Error
            ): ChangeAnswer()
            data class RecurringEvent(
                val participationStatus: ParticipationStatus,
                val dialogType: ChangeAnswerRecurringDialogType,
            ): ChangeAnswer()
        }

    }

    enum class ChangeAnswerRecurringDialogType {
        OVERWRITE,
        SINGLE_EDIT,
        DEFAULT
    }

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

        eventState.value = EventState.Idle

        // reset backup values
        timeStartBackup = null
        timeEndBackup = null

        eventEdited = false
        eventCustomPartialDayAlarmsSave = null
        eventCustomAllDayAlarmsSave = null
        dbEvent = null
        originalDbEvent = null
        recurrenceManuallyEdited = false
        singleEditsInfo = null
        tempRecurrenceUntilLocalDate = null
        hasEmailNotifications = false

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

        calendarUserSettings = calendarsRepository.selectCalendarUserSettings(userId.id)
            ?: return Result.Error("EventViewModel: could not get Calendar User Settings")
        userSettings = usersRepository.selectUserSettings(userId.id)
            ?: return Result.Error("EventViewModel: could not get User Settings")
        user = usersRepository.selectUserById(userId.id)
            ?: return Result.Error("EventViewModel: could not get User")

        displayTimeZoneId = calendarUserSettings.primaryTimezone

        event = if (eventId == null) {

            if (defaultCalendar == null) return Result.Error("EventViewModel: could not get default calendar")

            eventTimeZoneId = displayTimeZoneId

            val newICalendar = ICalUtilsImpl.createNewVEvent().wrapInICalendar()
            val newVEvent = newICalendar.events.first()

            // if there is no requested start date, we take today
            val startDate =
                if (initStartDate != null) LocalDate.parse(initStartDate)
                else ZonedDateTime.now(ZoneId.of(eventTimeZoneId)).toLocalDate()
            // if there is no requested start time, we calculate it according to "now"
            val startTime =
                if (initStartTime != null) LocalTime.parse(initStartTime)
                else ZonedDateTime.now(ZoneId.of(eventTimeZoneId))
                    .plusMinutes(this.calendarSettings.defaultEventDuration.toLong())
                    .truncatedTo(ChronoUnit.HOURS)
                    .toLocalTime()
            // end Zoned Date Time according to default event duration
            val endZonedDateTime = ZonedDateTime.of(
                startDate,
                startTime,
                ZoneId.of(eventTimeZoneId)
            ).plusMinutes(
                this.calendarSettings.defaultEventDuration.toLong()
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

            val newEvent = Event.from(
                ICalUtilsImpl.generateOfflineEventId(), Calendar(
                    defaultCalendar.id,
                    defaultCalendar.name,
                    defaultCalendar.color,
                    defaultCalendar.flags,
                    defaultCalendar.display == 1
                ), newICalendar
            ) ?: return Result.Error("could not create Event using factory method")

            setDefaultAlarms(newEvent, this.calendarSettings)
            newEvent

        } else {

            val dbEventEntity = calendarsRepository.selectEventEntity(eventId)
            dbEvent = if (dbEventEntity != null) transformEventUseCase.execute(dbEventEntity)
            else null

            if (dbEvent == null) return Result.EventDoesNotExist

            val eventStartTimeZone =
                dbEvent?.iCalendar?.timezoneInfo?.getTimezone(dbEvent?.iCalEvent?.dateStart)?.timeZone?.id
                    ?: displayTimeZoneId

            eventTimeZoneId = eventStartTimeZone

            val timeZoneForOccurrence = if (editMode) {
                eventTimeZoneId
            } else {
                displayTimeZoneId
            }

            // we have to generate occurrence in event's timezone, because otherwise we will overwrite it with default calendar's timezone
            val dbEventWithOccurrence = occurrenceNumber?.let {
                dbEvent?.let {
                    Event.withOccurrence(
                        it,
                        occurrenceNumber,
                        timeZoneForOccurrence
                    )
                }
            }
            // return error only if event dbEvent is recurring, if it's a single edit it's okay that occurrence can't be generated
            if (occurrenceNumber != null && (dbEvent?.isRecurring() == true) && dbEventWithOccurrence == null) return Result.OccurrenceDoesNotExist

            val adjustedEvent =
                (dbEventWithOccurrence ?: dbEvent?.copy(iCalendar = dbEvent?.iCalendar?.clone() as ICalendar))?.apply {

                    if (this.isAllDay()) { // adjust endDate to -1 day if event has no time
                        this.iCalEvent.setEnd(this.getEnd(timeZoneForOccurrence).toLocalDate().minusDays(1))
                    }

                    // default timezone in iCalendar is used for GUI
                    this.iCalendar.setDefaultTimeZone(timeZoneForOccurrence)

                    if (editMode) {
                        // Setup event time backup values
                        if (this.isAllDay()) {
                            val startTime = ICalUtilsImpl.generateEventStartTime(ZoneId.of(eventTimeZoneId))
                            timeStartBackup = startTime
                            timeEndBackup =
                                startTime.plusMinutes(this@EventViewModel.calendarSettings.defaultEventDuration.toLong())
                        } else {
                            timeStartBackup = this.getStart(timeZoneForOccurrence).toLocalTime()
                            timeEndBackup = this.getEnd(timeZoneForOccurrence).toLocalTime()
                        }

                        // Clone RRule from original event in DB if we are in edit mode
                        val eventUid = dbEvent?.uid
                        if (dbEvent?.isSingleEdit() == true && eventUid != null) {
                            // We store reference to originalDbEvent for later use
                            originalDbEvent = calendarsRepository.selectRootEventEntity(eventUid)
                                ?.let { transformEventUseCase.execute(it) }
                            this.iCalEvent.recurrenceRule = originalDbEvent?.iCalEvent?.recurrenceRule
                        }
                    }

                }

            if (adjustedEvent != null) {

                if (dbEvent != null && listOf(adjustedEvent).filterOutOccurrencesByExdates(
                        dbEvent!!,
                        timeZoneForOccurrence
                    ).isEmpty()
                ) {
                    return Result.OccurrenceDoesNotExist
                }

                adjustedEvent

            } else return Result.Error("EventViewModel: could not generate event with occurrence in EventViewModel")
        }

        hasEmailNotifications = event.hasEmailNotifications

        _event.postValue(event)

        return Result.Success
    }

    data class SingleEditsInfo(
        val hasSingleEdit: Boolean,
        val hasFutureSingleEdit: Boolean,
        val hasAnsweredSingleEdit: Map<ParticipationStatus, Boolean>
    )

    suspend fun getSingleEditsInfo(userEmails: List<String>? = null): SingleEditsInfo? {

        if (singleEditsInfo == null) {

            val event = _event.value ?: return null
            val dbEvent = dbEvent ?: return null

            var hasFutureSingleEdit = false
            val hasAnsweredSingleEdit = hashMapOf<ParticipationStatus, Boolean>()

            val occurrenceStart = event.getOccurrenceStart(eventTimeZoneId)
            val occurrence = event.occurrence
            val allowShowThisAndFuture =
                occurrence?.occurrenceNumber != null &&
                        occurrence.occurrenceNumber > 1 &&
                        !event.isEventFirstOccurrence(dbEvent, eventTimeZoneId)

            // We check for single edits only once and in initialise because it may require API calls
            val hasSingleEdit =
                if (occurrence?.occurrenceNumber == 1 &&
                    !allowShowThisAndFuture && (editMode ||
                            !event.isAnInvitation && userEmails != null)) {
                    // We don't have option "this and future" when updating first event in chain
                    // TODO Decide behavior if API call was an error and method returns null
                    dbEvent.isRecurring() && calendarsRepository.hasSingleEdits(userId, dbEvent.uid) == true
                } else {
                    // TODO Decide behavior if API call was an error and method returns null
                    val singleEdits = calendarsRepository.getSingleEdits(
                        userId,
                        dbEvent.uid,
                        if (editMode || !event.isAnInvitation && userEmails != null)
                            occurrenceStart
                        else null, // Fetch all SE when event has attendees in order to check for hasAnsweredSingleEdit
                        if (editMode || !event.isAnInvitation && userEmails != null)
                            eventTimeZoneId
                        else null
                    )
                    singleEdits?.forEach { singleEdit ->
                        if (singleEdit.getStart(eventTimeZoneId).isAfter(occurrenceStart)) {
                            hasFutureSingleEdit = true
                        }
                        // We only need hasAnsweredSingleEdit for change answer in event details view (if event has attendees)
                        if (!editMode && event.isAnInvitation && userEmails != null && !singleEdit.isCancelled()) {
                            // The only values we need are Accepted, Declined and Tentative
                            when (singleEdit.getParticipationStatus(userEmails)) {
                                ParticipationStatus.ACCEPTED -> hasAnsweredSingleEdit[ParticipationStatus.ACCEPTED] =
                                    true
                                ParticipationStatus.DECLINED -> hasAnsweredSingleEdit[ParticipationStatus.DECLINED] =
                                    true
                                ParticipationStatus.TENTATIVE -> hasAnsweredSingleEdit[ParticipationStatus.TENTATIVE] =
                                    true
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
        val defaultNotifications =
            if (isAllDay) calendarSettings.defaultFullDayNotifications else calendarSettings.defaultPartDayNotifications
        defaultNotifications.mapNotNull {
            if ((it as? JsonObject) != null) json.decodeFromJsonElement<CalendarSettingsEntity.AlarmEntity>(
                it
            ) else null
        }.forEach { alarm ->
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
            if (it.action == Action.display() || (FeatureFlag.ADD_EMAIL_NOTIFICATIONS && it.action == Action.email())) event.iCalEvent.addAlarm(it)
        }
    }

    // recurrence temp values
    var tempRecurrenceUntilLocalDate: LocalDate? = null
    var tempMonthlyRepeatOption: MonthlyRepeatOnOption = MonthlyRepeatOnOption.ON_DAY_X

    /**
     * Resets temporary values for Recurrence
     */
    fun initialiseForRecurrence() {
        this.tempMonthlyRepeatOption = MonthlyRepeatOnOption.ON_DAY_X
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
            event.iCalEvent.description?.value != description
        ) {
            markEventAsEdited()
        }

        event.iCalEvent.setSummary(summary)
        event.iCalEvent.setLocation(location)
        event.iCalEvent.setDescription(description)
    }

    /**
     * Checks if end date/time is not before start date/time
     */
    fun validateDateTime(): Boolean = !(event.getEnd(eventTimeZoneId).isBefore(event.getStart(eventTimeZoneId)))

    // alarm temp values
    private var tempAlarmSendByOption: SendByOption = SendByOption.NOTIFICATION
    var tempAlarmTime: LocalTime = LocalTime.of(9, 0)

    fun isAlarmLimitReached() = this.event.iCalEvent.alarms.size >= FormValidation.ALARM_COUNT_MAX

    /**
     * Resets temporary values for Alarm.
     */
    fun initialiseForAlarm() {
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
        timeFormatIs24Hours: Boolean,
        sendPreferences: Map<Email, SendPreferences>
    ): HandleSaveResult {

        // Post saving event value to true to trigger loading state
        eventState.value = EventState.Processing.Saving

        val eventCopy = Event.from(event)

        val handleSaveResult = handleSaveUseCase.handleSave(
            editOption,
            occurrenceNumber,
            timeFormatIs24Hours,
            sendPreferences,
            eventCopy,
            originalDbEvent,
            userSettings,
            eventTimeZoneId,
            userId,
            recurrenceManuallyEdited,
            isCreate
        )

        handleSaveResult.ifSuccessAndLogErrors(logger) {}

        // Post saving event value to false to hide loading state
        eventState.value = EventState.Idle

        if (handleSaveResult is UseCase.Result.Error) {
            return when (handleSaveResult.error) {
                UseCase.Error.EDIT_ERROR_SEND_MAIL -> HandleSaveResult.EDIT_ERROR_SEND_MAIL
                UseCase.Error.CREATE_ERROR_SEND_MAIL -> HandleSaveResult.CREATE_ERROR_SEND_MAIL
                else -> HandleSaveResult.ERROR
            }
        } else if (handleSaveResult is UseCase.Result.InvalidParams) {
            return HandleSaveResult.ERROR
        }

        return HandleSaveResult.SUCCESS
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
            event = event.copy(
                calendar = Calendar(
                    calendar.id,
                    calendar.name,
                    calendar.color,
                    calendar.flags,
                    calendar.display == 1
                )
            )
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
        val old = event.getStart(eventTimeZoneId)
        val endDate = event.getEnd(eventTimeZoneId)
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
        val old = event.getEnd(eventTimeZoneId)
        if (event.isAllDay()) {
            event.iCalEvent.setEnd(newDate)
        } else {
            event.iCalEvent.setEnd(newDate, old.toLocalTime(), eventTimeZoneId)
        }
        _event.postValue(event)
    }

    fun handleStartTime(newTime: LocalTime) {
        markEventAsEdited()
        val old = event.getStart(eventTimeZoneId)
        val endTime = event.getEnd(eventTimeZoneId)
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
        val old = event.getEnd(eventTimeZoneId)
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
            event.iCalEvent.setStart(event.getStart(eventTimeZoneId).toLocalDate())
            event.iCalEvent.setEnd(event.getEnd(eventTimeZoneId).toLocalDate())
        } else {
            // get times & timezone from backup, but date from current event date
            event.iCalEvent.setStart(
                event.getStart(eventTimeZoneId).toLocalDate(),
                timeStart,
                eventTimeZoneId
            )

            event.iCalEvent.setEnd(
                event.getEnd(eventTimeZoneId).toLocalDate(),
                timeEnd,
                eventTimeZoneId
            )
        }

        // If user choice has been saved then we don't set calendar's default alarms
        if ((isAllDay && eventCustomAllDayAlarmsSave == null) ||
            (!isAllDay && eventCustomPartialDayAlarmsSave == null)
        ) {
            setDefaultAlarms(event, calendarSettings)
        } else {
            event.iCalEvent.alarms.clear()
            // If user choice has been saved then use it even if alarm list is empty
            val savedAlarms =
                if (isAllDay) eventCustomAllDayAlarmsSave?.toList() else eventCustomPartialDayAlarmsSave?.toList()
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
    fun handleRecurrence(
        frequency: Frequency?,
        untilDate: Boolean,
        interval: Int? = null,
        count: Int? = null,
        daysOfWeek: List<DayOfWeek>? = null,
        customMonthly: Boolean = false
    ) {
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
                    ICalDate(
                        Date.from(
                            ZonedDateTime.of(
                                tempRecurrenceUntilLocalDate!!,
                                LocalTime.of(23, 59, 59),
                                ZoneId.of(eventTimeZoneId)
                            ).withZoneSameInstant(ZoneId.of(eventTimeZoneId)).toInstant()
                        ), true
                    )
                }
                builder.until(until)
            }
            daysOfWeek?.let {
                builder.byDay(daysOfWeek)
            }
            if (customMonthly) {
                val eventStartDate = event.getStart(eventTimeZoneId).toLocalDate()
                val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()
                val weekInMonth = eventStartDate.weekInMonth()

                when (tempMonthlyRepeatOption) {
                    MonthlyRepeatOnOption.ON_DAY_X -> {
                    }
                    MonthlyRepeatOnOption.ON_X_WEEKDAY -> {
                        builder.byDay(iCalDayOfWeek) // TODO be careful about using .byDay(ByDay(int, weekday)) because it uses different notation
                        builder.bySetPos(weekInMonth)
                    }
                    MonthlyRepeatOnOption.ON_LAST_WEEKDAY -> {
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

    fun handleRecurrenceUntilDate(untilLocalDate: LocalDate?) {
        markEventAsEdited()
        tempRecurrenceUntilLocalDate = untilLocalDate
    }

    enum class MonthlyRepeatOnOption {
        ON_DAY_X,
        ON_X_WEEKDAY,
        ON_LAST_WEEKDAY
    }

    /**
     * Complicated logic for displaying monthly recurrence options is calculated by ViewModel.
     */
    fun calculateMonthlyRepeatOnOptions(): List<MonthlyRepeatOnOption> {
        val eventStartDate = event.getStart(eventTimeZoneId).toLocalDate()

        val options = mutableListOf(MonthlyRepeatOnOption.ON_DAY_X)
        if (eventStartDate.weekInMonth() <= 4) options.add(MonthlyRepeatOnOption.ON_X_WEEKDAY)
        if (eventStartDate.isLastDayOfWeekInMonth()) options.add(MonthlyRepeatOnOption.ON_LAST_WEEKDAY)

        return options
    }

    /**
     * Complicated logic for determining selected recurrence option is calculated by ViewModel.
     */
    fun calculateMonthlyRepeatOnOptionIndex(): Int {

        val repeatOptions = calculateMonthlyRepeatOnOptions()

        event.iCalEvent.recurrenceRule?.value?.run {

            if (this.frequency != Frequency.MONTHLY) return 0

            val eventStartDate = event.getStart(eventTimeZoneId).toLocalDate()

            val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()

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

    fun handleRecurrenceRepeatOn(monthlyRepeatOnOption: MonthlyRepeatOnOption) {
        markEventAsEdited()
        this.tempMonthlyRepeatOption = monthlyRepeatOnOption
    }

    enum class SendByOption {
        NOTIFICATION,
        EMAIL
    }

    fun handleAlarmSendBy(option: SendByOption) {
        markEventAsEdited()
        this.tempAlarmSendByOption = option
    }

    fun handleAlarmTime(time: LocalTime) {
        markEventAsEdited()
        this.tempAlarmTime = time
    }

    fun handleAlarm(alarmTypeOption: Int, count: Int? = null, countTypeOption: Int? = null): Boolean {
        if (isAlarmLimitReached()) return false
        val duration = if (event.isAllDay()) {
            when (alarmTypeOption) {
                0 -> Duration.builder().prior(false).hours(9).build() // on the day at 9:00
                1 -> Duration.builder().prior(true).hours(6).build() // day before at 18:00
                2 -> Duration.builder().prior(true).days(6).hours(15).build() // 1 week before at 9:00, -P6DT15H
                3 -> Duration.builder().prior(true).weeks(2).days(6).hours(15)
                    .build() // 3 weeks before at 9:00, -P2W6DT15H
                4 -> null // all-day alarms have 1 fewer option
                5 -> { // custom

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

                                    val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong())
                                        .minusMinutes(tempAlarmTime.minute.toLong())

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

                                    val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong())
                                        .minusMinutes(tempAlarmTime.minute.toLong())

                                    if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                    if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
                                }
                                // On the day at x
                                4 -> {
                                    prior(false)
                                    val positiveTimeOfDay = LocalTime.of(0, 0).plusHours(tempAlarmTime.hour.toLong())
                                        .plusMinutes(tempAlarmTime.minute.toLong())

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

        duration?.apply {
            markEventAsEdited()

            val alarm = when (tempAlarmSendByOption) {
                SendByOption.NOTIFICATION -> VAlarm.display(Trigger(duration, Related.START), null)
                SendByOption.EMAIL -> VAlarm.email(Trigger(duration, Related.START), null, null, emptyList())
            }

            event.iCalEvent.addAlarm(alarm)
            saveUserEditedAlarms()
            _event.postValue(event)
        }

        return true
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

    fun handleDelete(occurrenceNumber: Int) {
        // Post deleting event value to true to display loading state
        eventState.value = EventState.Processing.Deleting

        val event = eventLiveData.value!!
        val dbEvent = this.dbEvent

        if (event.isPartOfChain() &&
            dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == false &&
            event.calendar.isActive) {

            val showThisAndFuture = occurrenceNumber > 1 &&
                    !event.isEventFirstOccurrence(dbEvent, displayTimeZoneId)

            // Display Confirmation Dialog
            eventDialogState.value = EventDialogState.Delete.RecurringEvent(showThisAndFuture)

        } else {
            // TODO Check if we need to handle inactive calendars the same way
            val disabledCalendarRecurringEvent = event.calendar.isDisabled &&
                    event.isPartOfChain() &&
                    dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == false

            // Display Confirmation Dialog
            if (disabledCalendarRecurringEvent) eventDialogState.value = EventDialogState.Delete.DisabledCalendarRecurring
            else eventDialogState.value = EventDialogState.Delete.Event
        }
    }

    suspend fun handleDeleteEvent(occurrenceNumber: Int): UseCase.Result {
        val deleteResult =
            if (dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == true) {
                deleteEventUseCase.execute(userId, event.id, EventEditDeleteOption.ALL_EVENTS, null)
            } else {
                deleteEventUseCase.execute(userId, event.id, EventEditDeleteOption.THIS_EVENT, occurrenceNumber)
            }

        // Post deleting event value to false to stop loading state
        eventState.value = EventState.Idle

        return deleteResult
    }

    suspend fun handleDeleteDisabledCalendarRecurring(): UseCase.Result {
        val deleteResult = deleteEventUseCase.execute(userId, event.id, EventEditDeleteOption.ALL_EVENTS, null)

        // Post deleting event value to false to stop loading state
        eventState.value = EventState.Idle

        return deleteResult
    }

    suspend fun handleDeleteRecurring(occurrenceNumber: Int, selectedIndex: Int, showThisAndFuture: Boolean): UseCase.Result {
        val deleteResult =
            if (selectedIndex == 0) {
                deleteEventUseCase.execute(userId, event.id, EventEditDeleteOption.THIS_EVENT, occurrenceNumber)
            } else if (selectedIndex == 1) {
                if (showThisAndFuture) {
                    deleteEventUseCase.execute(
                        userId,
                        event.id,
                        EventEditDeleteOption.THIS_EVENT_AND_FUTURE,
                        occurrenceNumber
                    )
                } else {
                    deleteEventUseCase.execute(userId, event.id, EventEditDeleteOption.ALL_EVENTS, null)
                }
            } else { // it == 2
                deleteEventUseCase.execute(userId, event.id, EventEditDeleteOption.ALL_EVENTS, null)
            }

        // Post deleting event value to false to stop loading state
        eventState.value = EventState.Idle

        return deleteResult
    }

    suspend fun handleAttendee(attendee: Attendee, canonicalEmail: String = "", addAttendee: Boolean = true) {
        markEventAsEdited()
        if (addAttendee) {
            attendee.commonName = ""
            attendee.rsvp = true
            attendee.participationLevel = ParticipationLevel.REQUIRED
            attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
            val token = ICalUtilsImpl.generateXPmToken(canonicalEmail, event.uid)
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
                // TODO Update this once we allow editing events that have attendees
                event.iCalEvent.organizer = null
            }
        }
        _event.postValue(event)
    }

    data class SendPreferencesResults(
        val sendPreferences: Map<Email, SendPreferences>,
        val emailErrors: Map<String, ObtainSendPreferencesUseCase.Result.Error>
    )

    suspend fun getSendPreferences(emails: List<String>): SendPreferencesResults {
        // get send preferences and check if attendees have disabled email addresses
        val canonicalEmails = getCanonicalEmailsUseCase.invoke(userId, emails)

        val sendPreferencesResults = obtainSendPreferencesUseCase.execute(userId, canonicalEmails.filterNullValues())

        val emailErrors = hashMapOf<String, ObtainSendPreferencesUseCase.Result.Error>()
        val sendPreferences = sendPreferencesResults.mapValues {
            when (val result = it.value) {
                is ObtainSendPreferencesUseCase.Result.Success -> result.sendPreferences
                ObtainSendPreferencesUseCase.Result.Error.AddressDisabled -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.AddressDisabled
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.GettingContactPreferences -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.GettingContactPreferences
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.NetworkError -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.NetworkError
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.TrustedKeysInvalid -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.TrustedKeysInvalid
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.PublicKeysInvalid -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.PublicKeysInvalid
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys
                    null
                }
            }
        }.filterNullValues()

        return SendPreferencesResults(sendPreferences, emailErrors)
    }

    /**
     * @return show snack with generic error
     */
    suspend fun handleChangeAnswer(newParticipationStatus: ParticipationStatus): Boolean {
        if (eventState.value is EventState.Processing) return true

        val userEmails = usersRepository.getUserAddresses(userId.id)?.map { address ->
            ProtonUtilsImpl.canonicalizeProtonEmail(address.email)
        }
        if (userEmails == null) {
            eventState.value = EventState.Idle
            return false
        }

        currentParticipationStatus = event.getParticipationStatus(userEmails) ?: ParticipationStatus.NEEDS_ACTION
        if (currentParticipationStatus != newParticipationStatus) {

            // Display Processing State
            eventState.value = EventState.Processing.ChangingAnswer(newParticipationStatus)

            if (event.isPartOfChain()) {
                val isSingleEdit = event.isSingleEdit()
                val isStandaloneSingleEdit = if (isSingleEdit) calendarsRepository.isStandaloneSingleEdit(
                    userId,
                    event.uid
                ) else false

                val hasAnsweredSingleEdit = getSingleEditsInfo(userEmails)?.hasAnsweredSingleEdit
                val overwrite =
                    if (isSingleEdit) false
                    else hasAnsweredSingleEdit != null &&
                            ((hasAnsweredSingleEdit[newParticipationStatus] == null && hasAnsweredSingleEdit.isNotEmpty())
                                    || (hasAnsweredSingleEdit[newParticipationStatus] == true && hasAnsweredSingleEdit.size > 1))

                return if (isStandaloneSingleEdit == true) {
                    handleChangeAnswerSendPreferences(
                        newParticipationStatus
                    )
                } else {
                    // Display Confirmation Dialog
                    eventDialogState.value = EventDialogState.ChangeAnswer.RecurringEvent(
                        newParticipationStatus,
                        when {
                            overwrite -> ChangeAnswerRecurringDialogType.OVERWRITE
                            isSingleEdit -> ChangeAnswerRecurringDialogType.SINGLE_EDIT
                            else -> ChangeAnswerRecurringDialogType.DEFAULT
                        }
                    )
                    true
                }
            } else {
                return handleChangeAnswerSendPreferences(
                    newParticipationStatus
                )
            }
        } else return true
    }

    suspend fun handleChangeAnswerSendPreferences(newParticipationStatus: ParticipationStatus): Boolean {
        val organizerEmail = event.iCalEvent.organizer.extractEmail()
        if (organizerEmail == null) {
            eventState.value = EventState.Idle
            return false
        }

        val sendPreferencesResults = getSendPreferences(listOf(organizerEmail))
        return if (sendPreferencesResults.emailErrors.isNotEmpty()) {

            val emailError = sendPreferencesResults.emailErrors.values.first()

            // Display Send Preferences Dialog
            eventState.value = EventState.Idle
            eventDialogState.value = EventDialogState.ChangeAnswer.SendPreferences(newParticipationStatus, emailError)

            emailError !is ObtainSendPreferencesUseCase.Result.Error.NetworkError
        } else {
            updateParticipationStatus(
                newParticipationStatus,
                sendPreferencesResults.sendPreferences
            )
        }
    }

    private suspend fun updateParticipationStatus(
        participationStatus: ParticipationStatus,
        sendPreferences: Map<Email, SendPreferences>
    ): Boolean {
        val status = participationStatus.toInt()

        val userEmails = usersRepository.getUserAddresses(userId.id)?.map { address ->
            ProtonUtilsImpl.canonicalizeProtonEmail(address.email)
        }
        if (userEmails == null) {
            eventState.value = EventState.Idle
            return false
        }

        val userAttendee = event.iCalEvent.attendees.find { attendee ->
            userEmails.firstOrNull { userEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && ProtonUtilsImpl.canonicalizeProtonEmail(attendeeEmail)
                    .equals(userEmail, ignoreCase = true)
            } != null
        }
        if (userAttendee == null) {
            eventState.value = EventState.Idle
            return false
        }
        val userParticipationStatus = userAttendee.participationStatus

        val eventCopy = event.copy(iCalendar = dbEvent?.iCalendar?.clone() as ICalendar)
        val personalPartICalString =
            if (participationStatus == ParticipationStatus.DECLINED &&
                event.iCalEvent.alarms != null && event.iCalEvent.alarms.isNotEmpty()
            ) {
                // if changes to NO, remove all notifications if there are any
                eventCopy.iCalEvent.alarms.clear()
                ""
            } else if ((userParticipationStatus == ParticipationStatus.DECLINED ||
                        userParticipationStatus == ParticipationStatus.NEEDS_ACTION) &&
                (participationStatus == ParticipationStatus.ACCEPTED || participationStatus == ParticipationStatus.TENTATIVE) &&
                event.iCalEvent.alarms.isNullOrEmpty()
            ) {
                // if changes from NO to YES/MAYBE add default calendar notifications
                if (loadSettingsForCalendar(event.calendar.id)) {
                    setDefaultAlarms(eventCopy, calendarSettings)
                    val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(eventCopy.iCalendar)
                    calendarSplit.personalPart?.printToString()
                } else null
            } else {
                // else keep notifications as it is
                null
            }

        val eventEntity = if (event.isProtonProtonInvite == null || event.isProtonProtonInvite == true) {
            val event = calendarsRepository.fetchEventById(userId, event.calendar.id, event.id).valueOrNullAndLogErrors(logger)?.event
            if (event == null) {
                eventState.value = EventState.Idle
                return false
            }
            event
        } else null

        val isProtonProtonInvite = event.isProtonProtonInvite ?: eventEntity?.isProtonProtonInvite?.toBoolean()

        if (isProtonProtonInvite == true) {
            if (!changeAnswerProtonProton(sendPreferences, eventCopy, eventEntity, userAttendee, participationStatus, status, personalPartICalString)) return false
        } else {
            if (!changeAnswer(sendPreferences, eventCopy, userAttendee, participationStatus, status, personalPartICalString)) return false
        }

        if (!event.isSingleEdit() && singleEditsInfo?.hasSingleEdit == true) {
            // If chain has single edits, update their part stat to NEEDS_ACTION
            clearSingleEditsParticipationStatus(event.calendar.id, event.uid, userEmails, participationStatus)
        }

        // Apply alarms modifications
        if (personalPartICalString?.isEmpty() == true) {
            // clear alarms
            event.iCalEvent.alarms.clear()
        } else if (personalPartICalString?.isNotEmpty() == true) {
            // add default alarms
            setDefaultAlarms(event, calendarSettings)
        }

        event.updateParticipationStatus(userEmails, participationStatus)

        if (!event.calendar.display) {
            // 1. Update in DB
            calendarsRepository.updateCalendarDisplay(event.calendar.id, 1)
            // 2. Update on Server
            updateCalendarUseCase.executeUpdate(userId, event.calendar.id)
        }

        _event.postValue(event)

        eventState.value = EventState.Idle
        return true
    }

    private suspend fun changeAnswer(
        sendPreferences: Map<Email, SendPreferences>,
        eventCopy: Event,
        userAttendee: Attendee,
        participationStatus: ParticipationStatus,
        status: Int,
        personalPartICalString: String?): Boolean {
        val updateTime = Instant.now()

        if (sendPreferences.isNotEmpty()) {
            val sendEmailUseCaseResult = sendEmailUseCase.executeToOrganizer(
                userId,
                eventCopy.iCalendar,
                dbEvent?.iCalendar?.timezoneInfo,
                userAttendee.copy(),
                event.iCalEvent.organizer.email,
                participationStatus,
                event.summary,
                sendPreferences,
                Date.from(updateTime),
                null,
                false
            )
            sendEmailUseCaseResult.ifSuccessAndLogErrors(logger) { }
            if (sendEmailUseCaseResult !is UseCase.Result.Success<*>) {
                eventState.value = EventState.Idle
                return false
            }
        }

        val attendeeId = event.currentUserAttendeeId
        if (attendeeId.isNullOrEmpty()) {
            eventState.value = EventState.Idle
            return false
        }

        val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
            userId,
            event.calendar.id,
            event.id,
            attendeeId,
            status,
            personalPartICalString,
            updateTime.epochSecond.toInt()
        )
        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
        if (updateParticipationStatusUseCaseResult !is UseCase.Result.Success<*>) {
            eventState.value = EventState.Idle
            return false
        }

        return true
    }

    private suspend fun changeAnswerProtonProton(
        sendPreferences: Map<Email, SendPreferences>,
        eventCopy: Event,
        eventEntity: EventEntity?,
        userAttendee: Attendee,
        participationStatus: ParticipationStatus,
        status: Int,
        personalPartICalString: String?): Boolean {
        if (eventEntity == null) {
            eventState.value = EventState.Idle
            return false
        }

        val updateTime = Instant.now()

        val attendeeId = event.currentUserAttendeeId
        if (attendeeId.isNullOrEmpty()) {
            eventState.value = EventState.Idle
            return false
        }

        val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
            userId,
            event.calendar.id,
            event.id,
            attendeeId,
            status,
            personalPartICalString,
            updateTime.epochSecond.toInt()
        )
        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
        if (updateParticipationStatusUseCaseResult !is UseCase.Result.Success<*>) {
            eventState.value = EventState.Idle
            return false
        }

        if (sendPreferences.isNotEmpty()) {
            updateParticipationStatusUseCaseResult.returnValue.tryCast<Int> {
                val sendEmailUseCaseResult = sendEmailUseCase.executeToOrganizer(
                    userId,
                    eventCopy.iCalendar,
                    dbEvent?.iCalendar?.timezoneInfo,
                    userAttendee.copy(),
                    event.iCalEvent.organizer.email,
                    participationStatus,
                    event.summary,
                    sendPreferences,
                    Date.from(updateTime), // Use same updateTime as for Update part stat BE call
                    eventEntity,
                    true
                )
                sendEmailUseCaseResult.ifSuccessAndLogErrors(logger) { }
                // Sending the email is optional for proton to proton so we don't care if it failed
            }
        }

        return true
    }

    private fun clearSingleEditsParticipationStatus(
        calendarId: String,
        eventUid: String,
        userEmails: List<String>,
        mainChainParticipationStatus: ParticipationStatus
    ): LiveData<Operation.State> {
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

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(
            UseCaseWorker.UniqueWorkNames.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT,
            ExistingWorkPolicy.REPLACE,
            work
        ).state
    }

    // TODO remove once we support editing with attendees
    suspend fun isApiEventAnInvitation(): Boolean? {
        val eventEntity = calendarsRepository.fetchEventById(userId, event.calendar.id, event.id).valueOrNullAndLogErrors(logger)?.event ?: return null
        return eventEntity.attendees.isNotEmpty()
    }

    suspend fun allowSendForCalendarAddress(): Boolean {
        val user =
            if (this::user.isInitialized) user
            else {
                logger.i("EventViewModel: User was null in allowSend")
                return false
            }
        val email = calendarsRepository.selectMembers(event.calendar.id).firstOrNull {
            it.hasPermission(MemberEntity.Permission.SUPEROWNER)
        }?.email
        if (email == null) {
            logger.i("EventViewModel: Email from selectMembers was null in allowSend")
            return false
        }
        return !user.isFree || !(user.isFree && isShortDomainAddress(email))
    }

    sealed class EventLinkResult {
        class Success(val occurrenceNumber: Int) : EventLinkResult()
        class DecryptionFailed(val event: Event) : EventLinkResult()
        object EventDoesNotExist : EventLinkResult()
        object Error : EventLinkResult()
    }

    suspend fun handleEventLink(userId: UserId, eventId: String, recurrenceIdTimestamp: String?): EventLinkResult {
        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return EventLinkResult.EventDoesNotExist
        val event = transformEventUseCase.execute(eventEntity) ?: return EventLinkResult.Error
        if (event.decryptionStatus == Event.DecryptionStatus.FAILURE) return EventLinkResult.DecryptionFailed(event)
        if (!event.calendar.display) {
            // 1. Update in DB
            calendarsRepository.updateCalendarDisplay(event.calendar.id, 1)
            // 2. Update on Server
            updateCalendarUseCase.executeUpdate(userId, event.calendar.id)
        }
        return if (recurrenceIdTimestamp != null) {
            val calendarUserSettings =
                calendarsRepository.selectCalendarUserSettings(userId.id) ?: return EventLinkResult.Error
            val timeZoneId = event.iCalendar.timezoneInfo?.getTimezone(event.iCalEvent.dateStart)?.timeZone?.id
                ?: calendarUserSettings.primaryTimezone
            val occurrences = event.generateOccurrencesUntil(
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(recurrenceIdTimestamp.toLong()), ZoneId.of(timeZoneId))
                    .toLocalDate(),
                timeZoneId
            )
            if (occurrences.isNullOrEmpty()) EventLinkResult.Success(0)
            else EventLinkResult.Success(occurrences.lastIndex + 1)
        } else EventLinkResult.Success(0)
    }
}
