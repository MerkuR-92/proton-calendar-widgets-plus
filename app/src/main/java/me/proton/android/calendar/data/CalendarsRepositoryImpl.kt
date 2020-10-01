package me.proton.android.calendar.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.filterOutOccurrencesByExdates
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.ext.getScopeId
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId

@FlowPreview
@ExperimentalCoroutinesApi
class CalendarsRepositoryImpl(
    private val gson: Gson,
    private val database: AppDatabase,
    private val transformEventUseCase: TransformEventUseCase,
    private val logger: Logger,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val valueStoreProvider: ValueStoreProvider) : CalendarsRepository {

    private val daysToEvents = mutableMapOf<LocalDate, Event>()
    private val eventFlows = mutableMapOf<Pair<LocalDate, LocalDate>, Flow<List<Event>>>()

    // root events from database
    //private val dbEvents = MutableStateFlow<List<Event>>(emptyList())
    private val dbEvents = mutableListOf<Event>()
    // all events including expanded
    private val events = MutableStateFlow<List<Event>>(emptyList())

    private var eventsExpandedUntil: LocalDate? = null
    private var prefetchedFrom: LocalDate? = null
    private var prefetchedTo: LocalDate? = null

    override val fetchingState = MutableStateFlow<CalendarsRepository.FetchingState>(CalendarsRepository.FetchingState.NotNeeded)

    private lateinit var selectedCalendarIds: List<String>

    override suspend fun init(calendarIds: List<String>, userId: String, toDate: LocalDate, timeZoneId: String) {
        selectedCalendarIds = calendarIds
        logger.v("zzz CalendarsRepository init()")

        database.eventsDao().flowEvents(calendarIds).distinctUntilChanged().debounce(DB_FLOW_DEBOUNCE_MS).collect { eventEntities ->
            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            val displayedCalendarsIds = database.calendarsDao().selectDisplayedCalendars(userId).map { it.id }.toList()
            val filteredEventEntities = eventEntities.filter { displayedCalendarsIds.contains(it.calendarId) }

            logger.v("xxx db events flow collect")

            addEventsToDb(filteredEventEntities, toDate, timeZoneId)

            //events.value = transformedEvents /*TODO*/
            /*.flatMap { event ->

                if (event.isRecurring()) {

                    val expandedOccurrences = ICalUtils.expandOccurrencesWithSingleEdits(event, dbEvents.filter { it.uid == event.uid }, toDate, timeZoneId)!!
                    val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event)

                    filteredByExdates

                } else if (event.isFromRecurring()) {
                    // Event that is "from recurring" has already been created when expading ^
                    emptyList()
                } else {
                    listOf(event)
                }

            }*/

            //eventsExpandedUntil = toDate

            fetchingState.value = CalendarsRepository.FetchingState.Finished
        }

        // TODO launchIn coroutine scope?
        //}.flowOn(Dispatchers.Default).collect()*/

    }

    override suspend fun refreshEvents(calendarIds: List<String>?) {
        fetchingState.value = CalendarsRepository.FetchingState.Fetching

        logger.v("xxx call to refresh events")

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        // TODO When optimizing : Only refresh a specific list of calendars and their events using calendarIds parameter
        val selectedCalendarIds = getActiveCalendars(TODOuserID).filter { it.display == 1 }.map { it.id }.toList()

        val events = database.eventsDao().selectEvents(selectedCalendarIds)

        // TODO get rid of this date calculation by guaranteeing `eventsExpandedUntil` is non-empty
        val timeZoneId = ZoneId.of(selectUserSettings(TODOuserID)?.primaryTimezone!!)
        val firstDayOfTheMonth = LocalDate.now(timeZoneId).withDayOfMonth(1).plusMonths(1)
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())

        addEventsToDb(events, toDate, timeZoneId.id)

        fetchingState.value = CalendarsRepository.FetchingState.Finished
    }

    private suspend fun addEventsToDb(events: List<EventEntity>, toDate: LocalDate, timeZoneId: String) {
        val transformedEvents = events.mapNotNull { transformEventUseCase.execute(it) }

        dbEvents.clear()
        dbEvents.addAll(transformedEvents)

        logger.v("xxx db events transformed")

        expandDbEventsUntil(eventsExpandedUntil ?: toDate, timeZoneId, force = true)
    }

    // TODO add fetch(from, to)

    override suspend fun selectCalendar(calendarId: String): CalendarEntity? {
        return database.calendarsDao().selectById(calendarId)
    }

    override suspend fun selectCalendars(userId: String): List<CalendarEntity> {
        return database.calendarsDao().selectCalendars(userId)
    }

    override fun flowCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowCalendars(userId)
    }

    override suspend fun persistCalendar(userId: String, calendar: CalendarEntity) {
        calendar.fkUserId = userId
        //  TODO make sure we have "flags" set!!!!!
        database.calendarsDao().insert(calendar)
    }

    override suspend fun updateCalendar(userId: String, calendar: CalendarEntity) {
        calendar.fkUserId = userId
        database.calendarsDao().update(calendar)
    }

    override suspend fun deleteCalendarById(id: String) {
        database.calendarsDao().deleteById(id)
    }

    override suspend fun getActiveCalendars(userId: String): List<CalendarEntity> {
        return selectCalendars(userId).filter { it.isActive }
    }

    override suspend fun getDisabledCalendars(userId: String): List<CalendarEntity> {
        return selectCalendars(userId).filter { it.isDisabled }
    }

    override suspend fun calendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean {
        val calendar = selectCalendar(calendarId)
        return if (calendar != null) calendar.display == newDisplay else false
    }

    override fun eventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<List<Event>> {

        fun isAllDayPrio(a: Event, b: Event): Boolean {
            // If a is an all day event,
            // b is a part day event,
            // and the all day event starts on the same day that b ends and (b does not span multiple days)
            // The last check is needed because a part day event can span on 2 days without being seen
            // as an all day event
            return a.isAllDay() &&
                    !b.isAllDay() &&
                    ((a.getActualStart(ZoneId.systemDefault().id))?.toLocalDate())?.isEqual((b.getActualEnd(ZoneId.systemDefault().id))?.toLocalDate()) == true &&
                    b.spansSingleDay()
        }

        val comparator = Comparator<Event> { a, b ->
            return@Comparator when {
                isAllDayPrio(a, b) -> {
                    -1
                }
                isAllDayPrio(b, a) -> {
                    1
                }
                else -> {
                    val coeficcient1 = ((a.getActualStart(ZoneId.systemDefault().id))?.toEpochSecond() ?: 0) - ((b.getActualStart(ZoneId.systemDefault().id))?.toEpochSecond() ?: 0)
                    val coeficcient2 = ((b.getActualEnd(ZoneId.systemDefault().id))?.toEpochSecond() ?: 0) - ((a.getActualEnd(ZoneId.systemDefault().id))?.toEpochSecond() ?: 0)

                    coeficcient1.toInt() or coeficcient2.toInt()
                }
            }
        }

        TimberLogger.v("zzz createEventsFlow: ${fromDate} - ${toDate}: ${timeZoneId}")

        return events.map {

            TimberLogger.v("xxx flow filtering for full day range: ${fromDate} - ${toDate}: ${timeZoneId}, ${it.size}")

            val filtered = it.filter {
                    it.overlapsWithFullDayRange(fromDate, toDate, timeZoneId)
            }.groupBy { it.isAllDay() || !it.spansSingleDay() }

//            (filtered.get(true)?.sortedWith(comparator) ?: emptyList())

            val result = mutableListOf<Event>()
                result.addAll(filtered.get(true)?.sortedWith(compareBy({ it.getActualStart(timeZoneId) }, { it.summary })) ?: emptyList())
                result.addAll(filtered.get(false)?.sortedWith(compareBy({ it.getActualStart(timeZoneId) }, { it.summary })) ?: emptyList())
            result

            //filtered.groupBy { it.isAllDay() || !it.spansSingleDay() }.flatMap { it.value.sortedWith(comparator) }//.sortedBy { it.summary } //.sortedWith(compareBy({ !it.isAllDay() }, { it.occurrence?.startDateTime ?: it.getStart() }, { it.summary }))
        } //.distinctUntilChanged()

    }

    override suspend fun prefetchEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ) {

        if (::selectedCalendarIds.isInitialized) { // TODO
            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            // expand recurrences for locally stored events
            expandDbEventsUntil(toDate, timeZoneId, false)

            // fetch from API
            if (prefetchedFrom == null || fromDate.isBefore(prefetchedFrom)) {
                when (fetchEventsUseCase.execute(selectedCalendarIds, fromDate, toDate, timeZoneId)) {
                    UseCase.Result.Success -> prefetchedFrom = fromDate
                }
            } else if (prefetchedTo == null || toDate.isAfter(prefetchedTo)) {

                when (fetchEventsUseCase.execute(selectedCalendarIds, fromDate, toDate, timeZoneId)) {
                    UseCase.Result.Success -> prefetchedTo = toDate
                }
            }

            fetchingState.value = CalendarsRepository.FetchingState.Finished
        }

    }

    private suspend fun expandDbEventsUntil(toDate: LocalDate, timeZoneId: String, force: Boolean) {

        synchronized(this) { // TODO
            TimberLogger.v("xxx expanding local occurrences until ${toDate}")

            if (force || eventsExpandedUntil == null || (eventsExpandedUntil != null && toDate.isAfter(eventsExpandedUntil))) {

                events.value = dbEvents.flatMap {event ->
                    if (event.isRecurring()) {
                        // TODO FIXME java.util.ConcurrentModificationException
                        val expandedOccurrences = ICalUtils.expandOccurrencesWithSingleEdits(event, dbEvents.filter { it.uid == event.uid }, toDate, timeZoneId)!!
                        val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event)

                        filteredByExdates
                    } else if (event.isFromRecurring()) {
                        // Event that is "from recurring" has already been created when expading ^
                        emptyList()
                    } else {
                        listOf(event)
                    }
                }

                TimberLogger.v("xxx expanded local occurrences until ${toDate}: ${events.value.size}")

                // first expand will happen on empty DB Events
                if (dbEvents.isNotEmpty()) {
                    eventsExpandedUntil = toDate
                }

            }
        }





    }

    override fun eventFlow(eventId: String): Flow<Event?> {
        return database.eventsDao().selectByIdFlow(eventId)./*distinctUntilChanged().*/map {
            if (it != null) {
                transformEventUseCase.execute(it)
            } else {
                null
            }
        }
    }

    override suspend fun selectEventEntity(eventId: String): EventEntity? = database.eventsDao().selectByIdFlow(eventId).first() // TODO exception

    override suspend fun selectRootEventEntity(eventUid: String): EventEntity? {
        return database.eventsDao().selectByUid(eventUid).find { it.sharedEvents.any { if (it.isJsonObject) (it.asJsonObject.get("Data").asString.contains("RRULE:")) else false } }
    }

    override suspend fun persistEvents(vararg events: EventEntity) {
        TimberLogger.v("persist Event: ${events.map { it.id + " for calendar " + it.calendarId }}")
        database.eventsDao().insert(*events)
    }

    override suspend fun deleteEventById(id: String) {
        database.eventsDao().deleteById(id)
    }

    override suspend fun selectCalendarKeys(calendarId: String): List<CalendarKeyEntity> {
        return database.calendarKeysDao().select(calendarId)//.distinctUntilChanged()
    }

    override suspend fun persistCalendarKey(calendarKey: CalendarKeyEntity) {
        Timber.d("persisting calendar key: $calendarKey")
        database.calendarKeysDao().insert(calendarKey)
    }

    override suspend fun deleteCalendarKeyById(id: String) {
        database.calendarKeysDao().deleteById(id)
    }

    override suspend fun selectPassphrases(calendarId: String): List<PassphraseEntity> {
        return database.passphrasesDao().select(calendarId)
    }

    override suspend fun persistPassphrase(passphrase: PassphraseEntity) {
        database.passphrasesDao().insert(passphrase)
    }

    override suspend fun deletePassphraseById(id: String) {
        database.passphrasesDao().deleteById(id)
    }

    override suspend fun selectMembers(calendarId: String): List<MemberEntity> {
        return database.membersDao().select(calendarId)
    }

    override suspend fun persistMember(member: MemberEntity) {
        database.membersDao().insert(member)
    }

    override suspend fun deleteMemberById(id: String) {
        database.membersDao().deleteById(id)
    }

    override suspend fun selectCalendarSettings(calendarId: String): CalendarSettingsEntity? {
        return database.calendarSettingsDao().select(calendarId)
    }

    override suspend fun persistCalendarSettings(calendarSettings: CalendarSettingsEntity) {
        database.calendarSettingsDao().insert(calendarSettings)
    }

    override suspend fun deleteCalendarSettingsById(id: String) {
        database.calendarSettingsDao().deleteById(id)
    }

    override suspend fun selectUserSettings(userId: String): UserSettingsEntity? {
        return database.userSettingsDao().select(userId)
    }

    override suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity) {
        userSettings.fkUserId = userId
        database.userSettingsDao().insert(userSettings)
    }

    override suspend fun deleteUserSettingsByUserId(userId: String) {
        database.userSettingsDao().deleteByUserId(userId)
    }

    override suspend fun getDefaultCalendarId(userId: String): String? {
        logger.d("getting default calendars, user ID: ${userId}")
        logger.d("getting default calendars, user settings: ${selectUserSettings(userId)}")
        logger.d("getting default calendars, active calendars: ${getActiveCalendars(userId)}")
        return selectUserSettings(userId)?.defaultCalendarId ?: getActiveCalendars(userId).firstOrNull()?.id
    }

    override suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>> {
        return database.eventAlarmsDao().select(eventId)
    }

    override suspend fun persistEventAlarm(eventAlarm: EventAlarmEntity) {
        database.eventAlarmsDao().insert(eventAlarm)
    }

    override suspend fun deleteEventAlarmById(eventAlarmId: String) {
        database.eventAlarmsDao().deleteById(eventAlarmId)
    }

}
