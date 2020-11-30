package me.proton.android.calendar.data

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.ICalUtils.filterOutOccurrencesByExdates
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId
import java.time.*
import java.time.temporal.TemporalAdjusters

@FlowPreview
@ExperimentalCoroutinesApi
class CalendarsRepositoryImpl(
    private val gson: Gson,
    private val database: AppDatabase,
    private val transformEventUseCase: TransformEventUseCase,
    private val logger: Logger,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase) : CalendarsRepository {

    private val eventsMutex = Mutex()

    // decrypted Events existing in database
    private val dbEvents = mutableListOf<Event>()
    // all Events including expanded
    private val allEvents = MutableStateFlow<List<Event>>(emptyList())

    // events that should be currently visible
    private val displayedEvents = MutableStateFlow<List<Event>?>(null)
    private val displayedEventsMutex = Mutex()

    override val fetchingState = MutableStateFlow<CalendarsRepository.FetchingState>(CalendarsRepository.FetchingState.NotNeeded)

    private var fetchedWindows = mutableSetOf<FetchWindow>()
    private var fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var eventsExpandedUntil: ZonedDateTime = ZonedDateTime.now()
    private val expandEventsToDateFlow = MutableStateFlow<ZonedDateTime>(eventsExpandedUntil)
    private var expandEventsToDateChannel = Channel<ZonedDateTime>()

    private val dbCalendars = MutableStateFlow<List<CalendarEntity>>(emptyList())
    private val visibleCalendars = MutableStateFlow<List<CalendarEntity>>(emptyList())

    private var coroutineScope = CoroutineScope(Dispatchers.Default)

    private val DEBOUNCE_EXPANDING_EVENTS_ON_FETCH = Duration.ofMillis(1000)
    private val DEBOUNCE_CALENDARS_UPDATE = Duration.ofMillis(500)

    private fun List<CalendarEntity>.filterVisible(): List<CalendarEntity> {
        return this.filter {
            it.display == 1 && (it.isActive || it.isDisabled)
        }
    }

    private suspend fun showEventsInVisibleCalendars() {

        // out of all events, filter out invisible ones (because of hidden calendar)
        val events = eventsMutex.withLock {
            allEvents.value.filter { event ->
                visibleCalendars.value.find { it.id == event.calendar.id } != null
            }
        }

        displayedEventsMutex.withLock {
            displayedEvents.value = events
        }
    }

    override suspend fun refreshCalendarsFlagsForAddress(address: String, status: Int, userId: String) {
        // Members objects are used to link an Address and the Calendars that are part of it
        val members = database.membersDao().selectByAddress(address)
        val calendarIds = ArrayList<String>()
        members.forEach { calendarIds.add(it.calendarId) }
        calendarIds.forEach {
            selectCalendar(it)?.let { dbCalendar ->
                var flags = dbCalendar.flags
                if (status == AddressStatus.DISABLED.value && !dbCalendar.isDisabled) {
                    flags = addDisabledFlag(flags, dbCalendar)
                } else if (status == AddressStatus.ENABLED.value && dbCalendar.isDisabled) {
                    flags = removeDisabledFlag(flags, dbCalendar)
                }
                database.calendarsDao().updateCalendarFlags(dbCalendar.id, flags)
            }
        }
    }

    private fun removeDisabledFlag(flags: Int, dbCalendar: CalendarEntity): Int {
        // status at 1 means the address is active

        // if the calendar is inactive we keep the same flags but remove the disabled flag
        // we also check if the calendar is simply disabled or super owner disabled to correctly update it
        return if (dbCalendar.isInactive && !dbCalendar.isSuperOwnerDisabled) {
            flags - CalendarFlags.DISABLED.value
        } else if (dbCalendar.isInactive && dbCalendar.isSuperOwnerDisabled) {
            flags - CalendarFlags.SUPER_OWNER_DISABLED.value
        } else {
            // if the calendar is simply disabled we set the flags at active
            CalendarFlags.ACTIVE.value
        }
    }

    private fun addDisabledFlag(flags: Int, dbCalendar: CalendarEntity): Int {
        // status at 0 means the address is disabled

        // if the calendar is inactive we keep the same flags but add the disabled flag
        return if (dbCalendar.isInactive)  {
            flags + CalendarFlags.DISABLED.value
        } else  {
            // if the calendar is simply active we set the flags at disabled
            CalendarFlags.DISABLED.value
        }
    }

    override suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<CalendarsRepository.InitingState> {

        logger.d("initForUser $userId")

        eventsExpandedUntil = ZonedDateTime.now(timeZoneId)

        if (coroutineScope.isActive) {
            logger.v("scope active, cancelling")
            coroutineScope.cancel()
        }

        coroutineScope = CoroutineScope(Dispatchers.Default)

        // cold init, fetch all needed entities straight from database
        coroutineScope.launch {
            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            // Events
            val eventEntities = database.eventsDao().selectEvents()
            val transformedEvents = eventEntities.mapNotNull { transformEventUseCase.execute(it) }
            eventsMutex.withLock {
                // we just selected all events from DB so let's clear all that might have been added
                // by fetching, user actions or
                dbEvents.clear()
                dbEvents.addAll(transformedEvents)
            }

            // Calendars
            dbCalendars.value = database.calendarsDao().selectCalendars()
            visibleCalendars.value = dbCalendars.value.filterVisible()

            // force expanding Events after cold init is done
            expandEventsToDateChannel.send(eventsExpandedUntil.plusMonths(2).withDayOfMonth(1))

            fetchingState.value = CalendarsRepository.FetchingState.Finished
        }

        coroutineScope.launch {
            expandEventsToDateFlow.debounce(DEBOUNCE_EXPANDING_EVENTS_ON_FETCH.toMillis()).collect {
                expandDbEventsUntil(it)
            }
        }

        coroutineScope.launch {
            expandEventsToDateChannel.consumeEach {
                logger.v("expandEventsToDateChannel consuming: $it")
                // get max of currently requested and already expanded timestamps
                listOf(it, eventsExpandedUntil).maxByOrNull { it.toEpochSecond() }?.let {
                    logger.v("expandEventsToDateChannel but publishing in flow: $it")
                    expandEventsToDateFlow.value = it
                }
            }
        }

        coroutineScope.launch {
            database.calendarsDao().flowCalendars().debounce(DEBOUNCE_CALENDARS_UPDATE.toMillis()).collect { calendarEntities ->
                dbCalendars.value = calendarEntities
                visibleCalendars.value = calendarEntities.filterVisible()
            }
        }

        coroutineScope.launch {
            visibleCalendars.collect {
                showEventsInVisibleCalendars()
            }
        }

        coroutineScope.launch {
            dbCalendars.collect {
                // TODO do we do antything here?
            }
        }

        coroutineScope.launch {
            allEvents.collect { calendarEntities ->
                showEventsInVisibleCalendars()
            }
        }

        coroutineScope.launch {
            fetchEventsChannel.consumeEach { fetchEventsInWindow(it) }
        }

        return flow {
            emit(CalendarsRepository.InitingState.Initing)

            // if user has no Events in the database, perform cold init
            val coldInitNeeded = database.calendarsDao().selectCalendars(userId).map { it.id }.all { database.eventsDao().count(it) == 0 }
            if (coldInitNeeded) {
                emit(CalendarsRepository.InitingState.ColdIniting)
                coldInit(userId)
            }

            emit(CalendarsRepository.InitingState.Finished)
        }
    }

    private suspend fun fetchEventsInWindow(fetchWindow: FetchWindow) {

        if (!fetchedWindows.contains(fetchWindow)) {
            logger.d("fetching events: ${fetchWindow.fromDate} = ${fetchWindow.toDate}")

            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            // fetch from API
            val fetchEventsResult = fetchEventsUseCase.execute(
                fetchWindow.userId,
                fetchWindow.calendarIds,
                fetchWindow.fromDate,
                fetchWindow.toDate,
                fetchWindow.timeZoneId
            )

            if (fetchEventsResult.first is UseCase.Result.Success) {
                if (fetchEventsResult.second == null) {
                    logger.e("fetchEventsResult: null event list when Sucess")
                }

                fetchEventsResult.second?.let {
                    persistEvents(*it.toTypedArray())
                    fetchedWindows.add(fetchWindow)
                    handleAlarmsUseCase.execute(fetchWindow.userId)
                }
            }

            fetchingState.value = CalendarsRepository.FetchingState.Finished

        } else {
            logger.v("no need to fetch events: ${fetchWindow.fromDate} = ${fetchWindow.toDate}")
        }

    }

    /**
     * Synchronously fetch current month worth of Events.
     */
    private suspend fun coldInit(userId: String) {

        val now = ZonedDateTime.now()
        val calendarIds = database.calendarsDao().selectCalendars(userId).map { it.id }
        val primaryTimeZone = database.calendarUserSettingsDao().select(userId)?.primaryTimezone

        val timeZoneId = if (primaryTimeZone == null) {
            logger.e("could not retrieve user's primaryTimeZone in coldInitIfNeeded, using UTC")
            ZoneId.of("UTC").id
        } else {
            primaryTimeZone
        }

        logger.v("cold initing and fetching events for ${timeZoneId}")

        // we are fetching synchronously instead of via channel
        fetchEventsInWindow(
            FetchWindow(
                UserId(userId),
                calendarIds,
                now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate(),
                now.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate(),
                timeZoneId
            )
        )

        logger.v("finished cold fetching events for ${timeZoneId}")
    }

    override suspend fun shutdown() {
        logger.v("shutdown calendarepository")

        if (coroutineScope.isActive) {
            logger.v("scope active, cancelling")
            coroutineScope.cancel()
        }

        fetchingState.value = CalendarsRepository.FetchingState.Finished

        displayedEventsMutex.withLock {
            displayedEvents.value = null
        }

        eventsMutex.withLock {
            dbEvents.clear()
            allEvents.value = emptyList()

            fetchedWindows.clear()
            fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

            eventsExpandedUntil = ZonedDateTime.now() // TODO maybe set it to null or far in the past
            expandEventsToDateFlow.value = eventsExpandedUntil
            expandEventsToDateChannel = Channel<ZonedDateTime>()

            dbCalendars.value = emptyList()
            visibleCalendars.value = emptyList()
        }
    }

    override suspend fun selectCalendar(calendarId: String): CalendarEntity? {
        return database.calendarsDao().selectById(calendarId)
    }

    override suspend fun selectCalendars(userId: String): List<CalendarEntity> {
        return database.calendarsDao().selectCalendars(userId)
    }

    override fun flowCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowCalendars(userId).distinctUntilChanged()
    }

    override suspend fun persistCalendar(userId: String, calendar: CalendarEntity) {
        calendar.fkUserId = userId
        //  TODO make sure we have "flags" set!!!!!
        database.calendarsDao().updateOrInsert(calendar)
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

    override suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean {
        val calendar = selectCalendar(calendarId)
        return if (calendar != null) calendar.display == newDisplay else false
    }

    override suspend fun updateCalendarDisplay(calendarId: String, display: Int) {
        database.calendarsDao().updateCalendarDisplay(calendarId, display)
    }

    override fun eventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<List<Event>?> {

        /* unused for now, please don't delete the code
        fun isAllDayPrio(a: Event, b: Event): Boolean {
            // If a is an all day event,
            // b is a part day event,
            // and the all day event starts on the same day that b ends and (b does not span multiple days)
            // The last check is needed because a part day event can span on 2 days without being seen
            // as an all day event
            return a.isAllDay() &&
                    !b.isAllDay() &&
                    ((a.getActualStart(ZoneId.systemDefault().id))?.toLocalDate())?.isEqual((b.getActualEnd(ZoneId.systemDefault().id))?.toLocalDate()) == true &&
                    b.spansSingleDay(timeZoneId = timeZoneId)
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
        }*/

        logger.v("create EventsFlow: ${fromDate} - ${toDate}: ${timeZoneId}")

//        val notFetchedYet = (fetchedWindows.find {
//                (timeZoneId == it.timeZoneId) &&
//                (fromDate.isEqual(it.fromDate) || fromDate.isAfter(it.fromDate)) &&
//                (toDate.isEqual(it.toDate) || toDate.isBefore(it.toDate))
//            } == null)

        return displayedEvents.map {

            // we don't know if there are events for this particular date range
            if (it == null) {
                return@map null
            }

            // we haven't expanded the events for this date range yet
            if (ZonedDateTime.of(fromDate, LocalTime.MIDNIGHT, ZoneId.of(timeZoneId)).isAfter(eventsExpandedUntil)) {
                return@map null
            }

            logger.v("flow filtering for full day range: ${fromDate} - ${toDate}: ${timeZoneId}, ${it.size} items total")

            val filtered = it.filter {
                it.overlapsWithFullDayRange(fromDate, toDate, timeZoneId)
            }.groupBy { it.isAllDay() || !it.spansSingleDay(timeZoneId = timeZoneId) }

            val result = mutableListOf<Event>()
            result.addAll(filtered.get(true)?.sortedWith(compareBy({ it.getActualStart(timeZoneId) }, { it.summary })) ?: emptyList())
            result.addAll(filtered.get(false)?.sortedWith(compareBy({ it.getActualStart(timeZoneId) }, { it.summary })) ?: emptyList())
            result

            //filtered.groupBy { it.isAllDay() || !it.spansSingleDay() }.flatMap { it.value.sortedWith(comparator) }//.sortedBy { it.summary } //.sortedWith(compareBy({ !it.isAllDay() }, { it.occurrence?.startDateTime ?: it.getStart() }, { it.summary }))
        }.distinctUntilChanged()

    }

    private data class FetchWindow(
        val userId: UserId,
        val calendarIds: List<String>,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val timeZoneId: String
    )


    override suspend fun fetchEvents(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ) {

        val expandUntilDateTime = ZonedDateTime.of(LocalDateTime.of(toDate, LocalTime.MIDNIGHT), ZoneId.of(timeZoneId))
        expandEventsToDateChannel.send(expandUntilDateTime)

        val calendarIds = dbCalendars.value.filter { it.fkUserId == userId.id }.map { it.id }

        fetchEventsChannel.send(FetchWindow(userId, calendarIds, fromDate, toDate, timeZoneId))

    }

    override suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean = database.eventsDao().hasEvent(eventId, calendarId)

    override suspend fun hasCalendar(calendarId: String, ): Boolean = database.calendarsDao().hasCalendar(calendarId)

    private suspend fun expandDbEventsUntil(toDateTime: ZonedDateTime) {

        logger.v("expandDbEventsUntil ${toDateTime.toLocalDate()}")

        fetchingState.value = CalendarsRepository.FetchingState.Fetching

        eventsMutex.withLock {

            allEvents.value = dbEvents.flatMap { expandDbEvent(it, dbEvents, toDateTime) }

            logger.v("expanded count: ${allEvents.value.size}")

            if (dbEvents.isNotEmpty()) {
                eventsExpandedUntil = toDateTime
            }

        }

        fetchingState.value = CalendarsRepository.FetchingState.Finished
    }

    /**
     * Calling this method requires obtained lock on 'allEvents'!
     */
    private fun expandDbEvent(event: Event, allEvents: List<Event>, toDateTime: ZonedDateTime): List<Event> {
        return if (event.isRecurring()) {
            val expandedOccurrences = ICalUtils.expandOccurrencesWithSingleEdits(event, allEvents.filter { it.uid == event.uid }, toDateTime.toLocalDate(), toDateTime.zone.id)!!
            val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event)

            filteredByExdates
        } else if (event.isSingleEdit()) {
            // single edits are already generated when expanding above ^
            //  however, orphaned single edits (without original recurring event)
            //  have to be added to the list manually
            if (allEvents.find { it.uid == event.uid && it.isRecurring() } != null) {
                emptyList()
            } else {
                listOf(event)
            }
        } else {
            listOf(event)
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
        return database.eventsDao().selectByUid(eventUid).find { eventEntity ->
            eventEntity.sharedEvents.any {
                try {
                    // only root event contains RRULE
                    it.jsonObject.get("Data")?.jsonPrimitive?.content?.contains("RRULE:") == true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
        }
    }

    override suspend fun hasSingleEdits(eventUid: String): Boolean {
        return database.eventsDao().countByUid(eventUid) > 1
    }

    override suspend fun persistEvents(vararg events: EventEntity) {
        logger.v("persist Event: ")
        events.forEach { logger.v("${it.id}") }

        // update local database first
        database.eventsDao().updateOrInsert(*events)

        // now update local events cache

        // gather all new events along with all events in their chains
        val newEvents = events.mapNotNull { transformEventUseCase.execute(it) }
        val newEventsUids = newEvents.map { it.uid }

        logger.v("newEventsUids: $newEventsUids")

        eventsMutex.withLock {

            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            // extract all events in allEvents related to newly changed events
            val relatedEvents = allEvents.value.filter { newEventsUids.contains(it.uid) }

            logger.v("relatedEvents: ")
            relatedEvents.forEach {
                logger.v("${it.id}")
            }

            // 'distinct' because each event is 'related' to iself
            val affectedEvents = (newEvents + relatedEvents).distinctBy { it.id }

            logger.v("all affected events:")
            affectedEvents.forEach {
                logger.v("${it.id}")
            }

            val newlyExpandedAffectedEvents = affectedEvents.flatMap { expandDbEvent(it, affectedEvents, eventsExpandedUntil) }

            logger.v("expanded to replace: ")
            newlyExpandedAffectedEvents.forEach {
                logger.v("${it.summary}")
            }
            logger.v("=========")

            // 1. remove all related events because we just expanded them again along with new ones
            // 2. add newly expanded (new + related) events
            allEvents.value = allEvents.value.filterNot {
                    event -> relatedEvents.find { it.id == event.id } != null
            } + newlyExpandedAffectedEvents

            updateAlarmsForAffectedEvents(newlyExpandedAffectedEvents, eventsExpandedUntil.zone.id)

            // replace (raw, not expanded) events in dbEvents, they are read when scrolling & expanding
            dbEvents.removeAll { event -> affectedEvents.find { event.id == it.id } != null }
            logger.v("persistEvents dbEvents: ${dbEvents.size} adding ${affectedEvents.size}")
            dbEvents.addAll(affectedEvents)

            fetchingState.value = CalendarsRepository.FetchingState.Finished

        }

    }

    private suspend fun updateAlarmsForAffectedEvents(events: List<Event>, timeZoneId: String) {

        logger.v("updateAlarmsForAffectedEvents:")

        val now = ZonedDateTime.now()

        val upcomingOccurrences = events.filter {
            if (it.isAllDay()) {
                it.getActualStart(timeZoneId)?.toLocalDate()?.isBefore(now.toLocalDate()) == false
            } else {
                it.getActualStart(timeZoneId)?.isBefore(now) == false
            }
        }.groupBy { it.id }.map { it.value.first() }

        upcomingOccurrences.forEach {
            logger.v("alarms for upcoming: $it")

            val alarms = ICalUtils.calculateAlarmEntities(it, timeZoneId, "TODO")

            logger.v("deleting all alarms for `${it.summary}` and creating new ones")

            deleteEventAlarmsForEvent(it.id)
            alarms.forEach {
                logger.v("at: ${Instant.ofEpochSecond(it.occurrence)}")
                persistEventAlarm(it)
            }
        }

    }

    override suspend fun deleteEventsById(ids: List<String>) {

        database.eventsDao().deleteByIds(ids)
        ids.forEach {
            database.eventAlarmsDao().deleteAllByEventId(it)
        }

        eventsMutex.withLock {

            // TODO I think this is all we have to do, we don't need to expand
            //  related events because the only way they might have changed was
            //  deleting single-occurrence, but that adds EXDATE in original event
            //  so it will be handled by persistEvents()

            allEvents.value = allEvents.value.filterNot { ids.contains(it.id) }

            dbEvents.removeAll { ids.contains(it.id) }

        }

    }

    override suspend fun selectCalendarKeys(calendarId: String): List<CalendarKeyEntity> {
        return database.calendarKeysDao().select(calendarId)//.distinctUntilChanged()
    }

    override suspend fun persistCalendarKey(calendarKey: CalendarKeyEntity) {
        logger.d("persisting calendar key: $calendarKey")
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

    override suspend fun selectCalendarUserSettings(userId: String): CalendarUserSettingsEntity? {
        return database.calendarUserSettingsDao().select(userId)
    }

    override suspend fun persistCalendarUserSettings(userId: String, calendarUserSettings: CalendarUserSettingsEntity) {
        calendarUserSettings.fkUserId = userId
        database.calendarUserSettingsDao().updateOrInsert(calendarUserSettings)
    }

    override suspend fun deleteCalendarUserSettingsByUserId(userId: String) {
        database.calendarUserSettingsDao().deleteByUserId(userId)
    }

    override suspend fun getDefaultCalendarId(userId: String): String? {
        logger.d("getting default calendars, user ID: ${userId}")
        logger.d("getting default calendars, calendar user settings: ${selectCalendarUserSettings(userId)}")
        logger.d("getting default calendars, active calendars: ${getActiveCalendars(userId)}")
        return selectCalendarUserSettings(userId)?.defaultCalendarId ?: getActiveCalendars(userId).firstOrNull()?.id
    }

    override suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>> {
        return database.eventAlarmsDao().selectByEventId(eventId)
    }

    override suspend fun selectEventAlarms(
        timestampSecondsStart: Long,
        timestampSecondsEnd: Long
    ): List<EventAlarmEntity> {
        return database.eventAlarmsDao().select(timestampSecondsStart, timestampSecondsEnd)
    }

    override suspend fun selectEventAlarm(eventAlarmId: String): EventAlarmEntity? {
        return database.eventAlarmsDao().select(eventAlarmId)
    }

    override suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectUpcoming(timestampSeconds)
    }

    override suspend fun selectAllBetweenInclusive(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectAllBetweenInclusive(timestampSecondsFrom, timestampSecondsTo)
    }

    override suspend fun persistEventAlarm(eventAlarm: EventAlarmEntity) {
        database.eventAlarmsDao().updateOrInsert(eventAlarm)
    }

    override suspend fun deleteEventAlarmById(id: String) {
        database.eventAlarmsDao().deleteById(id)
    }

    override suspend fun deleteEventAlarmsForEvent(eventId: String) {
        database.eventAlarmsDao().deleteAllByEventId(eventId)
    }

    override suspend fun deleteEventAlarmsByEventIdAndOccurrence(eventId: String, occurrence: Long) {
        database.eventAlarmsDao().deleteAllByEventIdAndOccurrence(eventId, occurrence)
    }

}
