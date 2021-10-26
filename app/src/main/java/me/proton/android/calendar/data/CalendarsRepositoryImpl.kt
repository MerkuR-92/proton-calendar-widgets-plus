package me.proton.android.calendar.data

import biweekly.property.RecurrenceId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.DateTimeUtilsImpl.getFullyOverlappingWindow
import me.proton.android.calendar.common.EventUtilsImpl.addExceptionDate
import me.proton.android.calendar.common.EventUtilsImpl.generateFirstRealOccurrenceSince
import me.proton.android.calendar.common.EventUtilsImpl.overlapsWithFullDayRange
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.ICalUtilsImpl
import me.proton.android.calendar.common.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.domain.entity.UserId
import me.proton.core.util.kotlin.toBoolean
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.collections.ArrayList

@FlowPreview
@ExperimentalCoroutinesApi
class CalendarsRepositoryImpl(
    private val database: AppDatabase,
    private val transformEventUseCase: TransformEventUseCase,
    private val logger: Logger,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val calendarsApi: CalendarsApi,
    private val json: Json,
    private val widgetRefresher: WidgetRefresher
) : CalendarsRepository {

    private val DEBOUNCE_EXPANDING_EVENTS_ON_FETCH = Duration.ofMillis(1000)
    private val DEBOUNCE_CALENDARS_UPDATE = Duration.ofMillis(500)
    private val DEBOUNCE_EVENTS_UPDATE = Duration.ofMillis(200)

    private val eventsMutex = Mutex()

    // decrypted Events existing in database
    private val dbEvents = mutableListOf<Event>()

    // all Events including expanded
    private val allEvents = MutableStateFlow<List<Event>>(emptyList())

    // events that should be currently visible
    private val displayedEvents = MutableStateFlow<List<Event>?>(null)
    private val displayedEventsMutex = Mutex()

    override val fetchingState =
        MutableStateFlow<CalendarsRepository.FetchingState>(CalendarsRepository.FetchingState.NotNeeded)

    private var fetchedWindows = mutableSetOf<FetchWindow>()
    private var fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var eventsExpandedUntil: ZonedDateTime = ZonedDateTime.now()
    private val expandEventsToDateFlow = MutableStateFlow<ZonedDateTime>(eventsExpandedUntil)
    private var expandEventsToDateChannel = Channel<ZonedDateTime>()

    private val dbCalendars = MutableStateFlow<List<CalendarEntity>>(emptyList())
    private val visibleCalendars = MutableStateFlow<List<CalendarEntity>>(emptyList())

    private var coroutineScope = CoroutineScope(Dispatchers.Default)

    // caches already calculated Events for EventsWindow to quickly show them when resubscribing to flow
    private val eventsCache = mutableMapOf<CalendarsRepository.EventsWindow, List<Event>>()
    private val eventsCacheMutex = Mutex()

    // cache for SkeletonEvents
    private val skeletonEventsCache = mutableMapOf<CalendarsRepository.EventsWindow, List<SkeletonEvent>>()
    private val skeletonEventsCacheMutex = Mutex()

    private val visibleCalendarEntitiesFlow =
        database.calendarsDao().flowCalendars().debounce(DEBOUNCE_CALENDARS_UPDATE.toMillis())
            .map { it.filterVisible() }.distinctUntilChanged()
            .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1)

    private val visibleSkeletonEventsFlow =
        database.eventsDao().selectSkeletonEventsFlow().debounce(DEBOUNCE_EVENTS_UPDATE.toMillis())
            .combineTransform(visibleCalendarEntitiesFlow) { skeletonEventEntities, calendarEntities ->

                val skeletonEvents = skeletonEventEntities.mapNotNull { skeletonEventEntity ->
                    val calendar = calendarEntities.firstOrNull { it.id == skeletonEventEntity.calendarId }
                    calendar?.run { skeletonEventEntity.toSkeletonEvent(json, this.color) }
                }

                emit(skeletonEvents)

            }.shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1)

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

    override suspend fun refreshCalendarsFlagsForAddress(address: String, enabled: Boolean, userId: String) {
        // Members objects are used to link an Address and the Calendars that are part of it
        val members = database.membersDao().selectByAddress(address)
        val calendarIds = ArrayList<String>()
        members.forEach { calendarIds.add(it.calendarId) }
        calendarIds.forEach {
            selectCalendar(it)?.let { dbCalendar ->
                var flags = dbCalendar.flags
                if (!enabled && !dbCalendar.isDisabled) {
                    flags = addDisabledFlag(flags, dbCalendar)
                } else if (enabled && dbCalendar.isDisabled) {
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
        return if (dbCalendar.isInactive) {
            flags + CalendarFlags.DISABLED.value
        } else {
            // if the calendar is simply active we set the flags at disabled
            CalendarFlags.DISABLED.value
        }
    }

    override suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<CalendarsRepository.InitingState> {

        val flow = MutableStateFlow<CalendarsRepository.InitingState>(CalendarsRepository.InitingState.Initing)

        // TODO make sure we also migrate the calendar fetching for new event decryption
        coroutineScope.launch {
            fetchEventsChannel.consumeEach {
                logger.v("consuming: $it")
                fetchEventsInWindow(it)
            }
        }

        if (FeatureFlag.NEW_EVENT_DECRYPTION) {
            flow.value = CalendarsRepository.InitingState.Finished
            return flow
        }

        // TODO temporary solution for being stuck on expandEventsToDateChannel.send
        shutdown()

        logger.v("initForUser $userId")

        eventsExpandedUntil = ZonedDateTime.now(timeZoneId)

        if (coroutineScope.isActive) {
            logger.v("scope active, cancelling")
            coroutineScope.cancel()
        }

        coroutineScope = CoroutineScope(Dispatchers.Default)

        // cold init, fetch all needed entities straight from database
        coroutineScope.launch {
            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            logger.v("getting events from db")

            // Events
            val eventEntities = database.eventsDao().selectEvents()
            val transformedEvents = eventEntities.map {
                async {
                    transformEventUseCase.execute(it)
                }
            }.awaitAll().filterNotNull()

            eventsMutex.withLock {
                // we just selected all events from DB so let's clear all that might have been added
                // by fetching, user actions or
                dbEvents.clear()
                dbEvents.addAll(transformedEvents)
                logger.v("transformed and inserted into db events: ${transformedEvents.size}")
            }

            // Calendars
            dbCalendars.value = database.calendarsDao().selectCalendars()
            visibleCalendars.value = dbCalendars.value.filterVisible()

            // force expanding Events after cold init is done
            eventsExpandedUntil = eventsExpandedUntil.plusMonths(2).withDayOfMonth(1)
            expandEventsToDateChannel.send(eventsExpandedUntil)

            flow.value = CalendarsRepository.InitingState.Finished

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
                logger.v("leaving consume expandeventschannel")
            }
        }

        coroutineScope.launch {
            database.calendarsDao().flowCalendars().debounce(DEBOUNCE_CALENDARS_UPDATE.toMillis())
                .collect { calendarEntities ->
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

        return flow
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

            if (fetchEventsResult.first is UseCase.Result.Success<*>) {
                if (fetchEventsResult.second == null) {
                    logger.e("fetchEventsResult: null event list when Sucess")
                }

                fetchEventsResult.second?.let {
                    logger.v("fetchEventsResult success: ${it.size}")
                    persistEvents(*it.toTypedArray())
                    updateAlarmsUseCase.execute(fetchWindow.userId.id, it.map { it.id })
                    fetchedWindows.add(fetchWindow)
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

        fetchedWindows.clear()
        fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        fetchingState.value = CalendarsRepository.FetchingState.Finished

        skeletonEventsCacheMutex.withLock {
            skeletonEventsCache.clear()
        }

        eventsCacheMutex.withLock {
            eventsCache.clear()
        }

        if (FeatureFlag.NEW_EVENT_DECRYPTION) {
            return
        }

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

    override suspend fun selectUserCalendars(userId: String): List<CalendarEntity> {
        return database.calendarsDao().selectUserCalendars(userId)
    }

    override fun flowActiveUserCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowActiveUserCalendars(userId).distinctUntilChanged()
    }

    override fun flowDisabledUserCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowDisabledUserCalendars(userId).distinctUntilChanged()
    }

    override fun flowInactiveUserCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowInactiveUserCalendars(userId).distinctUntilChanged()
    }

    override fun flowUserCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowUserCalendars(userId).distinctUntilChanged()
    }

    override fun flowSubscribedCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowSubscribedCalendars(userId).distinctUntilChanged()
    }

    override suspend fun persistCalendar(userId: String, calendar: CalendarEntity) {
        //  TODO make sure we have "flags" set!!!!!
        database.calendarsDao().updateOrInsert(calendar.copy(fkUserId = userId))
    }

    override suspend fun updateCalendar(userId: String, calendar: CalendarEntity) {
        database.calendarsDao().update(calendar.copy(fkUserId = userId))
    }

    override suspend fun deleteCalendarById(id: String) {
        database.calendarsDao().deleteById(id)
    }

    override suspend fun refreshCalendars(userId: UserId): Boolean {
        val calendarsResponse = calendarsApi.getCalendars(userId)
        return if (calendarsResponse !is ApiResponse.Success) {
            logger.e("error getting calendars from API in CalendarsRepositoryImpl")
            false
        } else {
            calendarsResponse.data.calendars.forEach {
                // TODO do boostrap for subscribed calendars to get calendar subscription extra properties
                persistCalendar(userId.id, it)
            }
            true
        }
    }

    override suspend fun fetchCalendars(userId: UserId): List<CalendarEntity>? {
        val calendarsResponse = calendarsApi.getCalendars(userId)
        return if (calendarsResponse !is ApiResponse.Success) {
            logger.e("error getting calendars from API in CalendarsRepositoryImpl")
            null
        } else {
            calendarsResponse.data.calendars
        }
    }

    override suspend fun getActiveUserCalendars(userId: String): List<CalendarEntity> {
        return selectUserCalendars(userId).filter { it.isActive }
    }

    override suspend fun getDisabledUserCalendars(userId: String): List<CalendarEntity> {
        return selectUserCalendars(userId).filter { it.isDisabled }
    }

    override suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean {
        val calendar = selectCalendar(calendarId)
        return if (calendar != null) calendar.display == newDisplay else false
    }

    override suspend fun updateCalendarDisplay(calendarId: String, display: Int) {
        database.calendarsDao().updateCalendarDisplay(calendarId, display)
        widgetRefresher.refresh()
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
            result.addAll(
                filtered.get(true)?.sortedWith(compareBy({ it.getOccurrenceStart(timeZoneId) }, { it.summary }))
                    ?: emptyList()
            )
            result.addAll(
                filtered.get(false)?.sortedWith(compareBy({ it.getOccurrenceStart(timeZoneId) }, { it.summary }))
                    ?: emptyList()
            )
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

//        val expandUntilDateTime = ZonedDateTime.of(LocalDateTime.of(toDate, LocalTime.MIDNIGHT), ZoneId.of(timeZoneId))
//        expandEventsToDateChannel.send(expandUntilDateTime)

        val calendarIds = database.calendarsDao().selectCalendars(userId.id).map { it.id }

        fetchEventsChannel.send(FetchWindow(userId, calendarIds, fromDate, toDate, timeZoneId))

    }

    private fun createEventsFlow(eventsWindow: CalendarsRepository.EventsWindow, allowCached: Boolean): Flow<CalendarsRepository.GetEventsResult<Event>> {

        return createSkeletonsFlow(eventsWindow).transform<List<SkeletonEvent>, CalendarsRepository.GetEventsResult<Event>> { eventSkeletons ->

            logger.v("events flow: createEventsFlow for ${eventsWindow.fromDate} - ${eventsWindow.fromDate}")

            coroutineScope {

                // Skeleton Events already have correct Occurrence & DTSTART/DTEND applied,
                // all we need to do is decrypt EventEntity and return full Events with correct occurrences

                val eventEntities = database.eventsDao().selectAllById(eventSkeletons.map { it.id })

                val transformedEvents = eventEntities.map { eventEntity ->
                    async {
                        val transformedEvent = transformEventUseCase.execute(eventEntity)

                        val skeletons = eventSkeletons.filter { it.id == eventEntity.id }

                        if (transformedEvent != null) {
                            skeletons.map {
                                if (it.occurrence == null) { // non-recurring event
                                    transformedEvent
                                } else if (it.isSingleEdit()) { // single edits, copy occurrence it replaces
                                    transformedEvent.occurrence = it.occurrence
                                    transformedEvent
                                } else { // recurring event, apply occurrence
                                    Event.withOccurrence(transformedEvent, it.occurrence!!)
                                }
                            }
                        } else null
                    }

                }.awaitAll().filterNotNull().flatten()

                emit(CalendarsRepository.GetEventsResult.Success(transformedEvents))

                eventsCacheMutex.withLock {
                    eventsCache[eventsWindow] = transformedEvents
                }

            }

        }.onStart {

            if (allowCached) {
                eventsCacheMutex.withLock {

                    val overlappingWindow = eventsCache.keys.getFullyOverlappingWindow(eventsWindow)

                    if (overlappingWindow != null) {
                        val overlappingEvents = eventsCache[overlappingWindow]?.filter { it.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId) }
                        if (overlappingEvents == null) {
                            logger.v("events not found in cache")
                            emit(CalendarsRepository.GetEventsResult.InProgress)
                        } else {
                            logger.v("returning skeleton events from cache ($eventsWindow): ${overlappingEvents.size} in total")
                            emit(CalendarsRepository.GetEventsResult.Success(overlappingEvents))
                        }
                    } else {
                        emit(CalendarsRepository.GetEventsResult.InProgress)
                    }
                }
            } else {
                emit(CalendarsRepository.GetEventsResult.InProgress)
            }

        }.retry(1) {
            logger.e("retrying in createEventsFlow because of exception $it"); true
        }.catch {
            logger.e("Exception in createEventsFlow while transforming", it)
            emit(CalendarsRepository.GetEventsResult.Exception(it))
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    }

    override fun getEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        allowCached: Boolean
    ): Flow<CalendarsRepository.GetEventsResult<Event>> {

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        return createEventsFlow(eventsWindow, allowCached)
    }

    private fun createSkeletonsFlow(
        eventsWindow: CalendarsRepository.EventsWindow
    ): Flow<List<SkeletonEvent>> {

        return visibleSkeletonEventsFlow.map { visibleSkeletonEvents ->

            logger.v("events flow: createSkeletonsFlow for ${eventsWindow.fromDate} - ${eventsWindow.toDate}")

            visibleSkeletonEvents.map { skeletonEvent ->
                expandSkeletonEventsAndFilterInWindow(
                    skeletonEvent,
                    visibleSkeletonEvents,
                    eventsWindow
                )
            }.flatten()

        }.flowOn(Dispatchers.Default)

    }

    private fun getSkeletonEvents(
        eventsWindow: CalendarsRepository.EventsWindow
    ): Flow<CalendarsRepository.GetEventsResult<SkeletonEvent>> {

        return createSkeletonsFlow(eventsWindow).transform<List<SkeletonEvent>, CalendarsRepository.GetEventsResult<SkeletonEvent>> { eventSkeletons ->

            logger.v("getSkeletonEvents for ${eventsWindow.fromDate} - ${eventsWindow.toDate}")

            emit(CalendarsRepository.GetEventsResult.Success(eventSkeletons))

            skeletonEventsCacheMutex.withLock {
                skeletonEventsCache[eventsWindow] = eventSkeletons
            }

        }.onStart {

            skeletonEventsCacheMutex.withLock {

                val overlappingWindow = skeletonEventsCache.keys.getFullyOverlappingWindow(eventsWindow)

                if (overlappingWindow != null) {
                    val overlappingEvents = skeletonEventsCache[overlappingWindow]?.filter { it.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId) }
                    if (overlappingEvents == null) {
                        logger.e("skeletonEvents not found in cache")
                        emit(CalendarsRepository.GetEventsResult.InProgress)
                    } else {
                        logger.v("returning skeleton events from cache ($eventsWindow): ${overlappingEvents.size} in total")
                        emit(CalendarsRepository.GetEventsResult.Success(overlappingEvents))
                    }
                } else {
                    emit(CalendarsRepository.GetEventsResult.InProgress)
                }
            }

        }.retry(1) {
            logger.e("retrying getSkeletonEvents events because of exception $it"); true
        }.catch {
            logger.e("Exception in getSkeletonEvents() while transforming", it)
            emit(CalendarsRepository.GetEventsResult.Exception(it))
        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    }

    /**
     * Get Skeleton Events with correct Calendar Colors.
     */
    override fun getSkeletonEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<CalendarsRepository.GetEventsResult<SkeletonEvent>> {

        logger.v("calling getSkeletonEventsForIndicators $fromDate - $toDate")

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        return getSkeletonEvents(eventsWindow)
    }

    override suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean =
        database.eventsDao().hasEvent(eventId, calendarId)

    override suspend fun hasCalendar(calendarId: String, ): Boolean = database.calendarsDao().hasCalendar(calendarId)

    private suspend fun expandDbEventsUntil(toDateTime: ZonedDateTime) {

        logger.v("expandDbEventsUntil ${toDateTime.toLocalDate()}")

        fetchingState.value = CalendarsRepository.FetchingState.Fetching

        eventsMutex.withLock {

            allEvents.value = dbEvents.flatMap { expandDbEvent(it, dbEvents, toDateTime) }

            logger.v("expanded total count: ${allEvents.value.size}")

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
            val expandedOccurrences = ICalUtilsImpl.expandOccurrencesWithSingleEdits(
                event,
                allEvents.filter { it.uid == event.uid },
                toDateTime.toLocalDate(),
                toDateTime.zone.id
            )!!
            val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event, toDateTime.zone.id)

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

    private fun expandSkeletonEventsAndFilterInWindow(event: Event, allEvents: List<Event>, eventsWindow: CalendarsRepository.EventsWindow): List<Event> {

        return if (event.isRecurring()) {
            // occurrences are already filtered for time window
            val expandedOccurrences = ICalUtilsImpl.expandOccurrencesWithSingleEdits(
                event,
                allEvents.filter { it.uid == event.uid },
                eventsWindow.fromDate,
                eventsWindow.toDate,
                eventsWindow.timeZoneId
            )!!
            val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event, eventsWindow.timeZoneId)

            filteredByExdates
        } else if (event.isSingleEdit()) {
            // single edits are already generated when expanding above ^
            //  however, orphaned single edits (without original recurring event)
            //  have to be added to the list manually
            if (allEvents.find { it.uid == event.uid && it.isRecurring() } != null) {
                emptyList()
            } else {
                if (event.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId)) listOf(event) else emptyList()
            }
        } else {
            if (event.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId)) listOf(event) else emptyList()
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

    override suspend fun selectEventEntity(eventId: String): EventEntity? =
        database.eventsDao().selectById(eventId)

    override suspend fun selectRootEventEntity(eventUid: String): EventEntity? {
        val formattedUid = formatUidForICal(eventUid)
        return database.eventsDao().selectByUid(formattedUid).find { eventEntity ->
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

    override suspend fun hasSingleEdits(userId: UserId, eventUid: String): Boolean? {
        // Return null for failed API calls
        val formattedUid = formatUidForICal(eventUid)
        val hasSingleEditsInDb = database.eventsDao().countByUid(formattedUid) > 1
        if (hasSingleEditsInDb) return true
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {
            eventsSharingUidResponse.data.events.forEach {
                val event = transformEventUseCase.execute(it)
                if (event?.iCalEvent?.recurrenceId != null) return true
            }
            false
        } else null
    }

    override suspend fun getSingleEdits(userId: UserId, eventUid: String, stopAfter: ZonedDateTime?, timeZoneId: String?): List<Event>? {
        // Return null for failed API calls
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {
            val events = arrayListOf<Event>()
            eventsSharingUidResponse.data.events.forEach {
                val event = transformEventUseCase.execute(it)
                if (stopAfter != null && timeZoneId != null && event?.getStart(timeZoneId)?.isAfter(stopAfter) == true) {
                    events.add(event)
                    return events
                }
                if (event?.iCalEvent?.recurrenceId != null) events.add(event)
            }
            events
        } else return null
    }

    /**
     * User is invited to only one occurrence of a recurring event
     */
    override suspend fun isOrphanSingleEdit(userId: UserId, eventUid: String): Boolean? {
        // Check if single edit is the only occurrence of a recurring event
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {
            // If an event with the same UID has no recurrenceId then we have occurrence(s) of the main series
            eventsSharingUidResponse.data.events.none { eventEntity ->
                val sharedEvents = eventEntity.sharedEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Shared>(it)
                }
                val iCal = ICalUtilsImpl.parseICalString(sharedEvents.first { !it.isEncrypted }.data)
                iCal?.events?.first()?.recurrenceId == null
            }
        } else null
    }

    /**
     * Main chain has no other occurrences left and event is the only single edit
     */
    override suspend fun isStandaloneSingleEdit(userId: UserId, eventUid: String, eventRecurrenceId: RecurrenceId, timeZoneId: String, occurrenceNumber: Int): Boolean? {
        // Check if single edit is the only occurrence of a recurring event
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {

            val skeletonEvents = eventsSharingUidResponse.data.events.mapNotNull { eventEntity ->
                val skeletonEventEntity = SkeletonEventEntity(eventEntity.id, eventEntity.calendarId, eventEntity.sharedEvents)
                skeletonEventEntity.toSkeletonEvent(json)
            }

            val singleEdits = skeletonEvents.filter { skeletonEvent ->
                skeletonEvent.iCalEvent.recurrenceId != null
            }
            if (singleEdits.size > 1) return false

            val rootEvent = skeletonEvents.firstOrNull { it.iCalEvent.recurrenceId == null } ?: return false // If it has no root event then it is an orphan single edit

            val isStandaloneSingleEdit = rootEvent.generateFirstRealOccurrenceSince(skeletonEvents, rootEvent.getStart(timeZoneId)) == null &&
                    (singleEdits.isNullOrEmpty() || singleEdits.firstOrNull()?.iCalEvent?.recurrenceId == eventRecurrenceId)
            isStandaloneSingleEdit
        } else null
    }

    override suspend fun getEventsByUid(userId: UserId, eventUid: String): ApiResponse<EventsByUidApiResponse> {
        return calendarsApi.getEventsByUid(userId, eventUid, 0, 100)
    }

    override suspend fun fetchEventById(userId: UserId, calendarId: String, eventId: String): ApiResponse<EventApiResponse> {
        return calendarsApi.getEvent(userId, calendarId, eventId)
    }

    override suspend fun persistEvents(vararg events: EventEntity) {

        // update local database first
        database.eventsDao().updateOrInsert(*events)

        if (FeatureFlag.NEW_EVENT_DECRYPTION) return // TODO REMOVE EVERYTHING BELOW, BECAUSE WE DON'T USE THE EXPANDED CACHE ANYMORE

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

            val newlyExpandedAffectedEvents =
                affectedEvents.flatMap { expandDbEvent(it, affectedEvents, eventsExpandedUntil) }

            logger.v("expanded to replace: ")
            newlyExpandedAffectedEvents.forEach {
                logger.v("${it.summary}")
            }
            logger.v("=========")

            // 1. remove all related events because we just expanded them again along with new ones
            // 2. add newly expanded (new + related) events
            allEvents.value = allEvents.value.filterNot { event ->
                relatedEvents.find { it.id == event.id } != null
            } + newlyExpandedAffectedEvents

            // replace (raw, not expanded) events in dbEvents, they are read when scrolling & expanding
            dbEvents.removeAll { event -> affectedEvents.find { event.id == it.id } != null }
            logger.v("persistEvents dbEvents: ${dbEvents.size} adding ${affectedEvents.size}")
            dbEvents.addAll(affectedEvents)

            fetchingState.value = CalendarsRepository.FetchingState.Finished

        }

    }

    override suspend fun deleteEventsById(ids: List<String>) {

        database.eventsDao().deleteByIds(ids)

        if (FeatureFlag.NEW_EVENT_DECRYPTION) return // TODO REMOVE EVERYTHING BELOW, BECAUSE WE DON'T USE THE EXPANDED CACHE ANYMORE

        // TODO deleting alarms makes no sense, because they have just been deleted by foreign key on Event

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

    override suspend fun updateCalendarSettings(calendarSettings: CalendarSettingsEntity) {
        database.calendarSettingsDao().updateOrInsert(calendarSettings)
    }

    override suspend fun deleteCalendarSettingsById(id: String) {
        database.calendarSettingsDao().deleteById(id)
    }

    override suspend fun selectCalendarSubscription(calendarId: String): CalendarSubscriptionEntity? {
        return database.calendarSubscriptionDao().select(calendarId)
    }

    override fun flowCalendarSubscriptions(): Flow<List<CalendarSubscriptionEntity>> {
        return database.calendarSubscriptionDao().flowCalendarSubscriptions().distinctUntilChanged()
    }

    override suspend fun persistCalendarSubscription(calendarSubscription: CalendarSubscriptionEntity) {
        database.calendarSubscriptionDao().updateOrInsert(calendarSubscription)
    }

    override suspend fun deleteCalendarSubscriptionById(id: String) {
        database.calendarSubscriptionDao().deleteById(id)
    }

    override suspend fun selectCalendarUserSettings(userId: String): CalendarUserSettingsEntity? {
        return database.calendarUserSettingsDao().select(userId)
    }

    override suspend fun updateCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String, autoDetectPrimaryTimezone: Int) {
        return database.calendarUserSettingsDao().updateAutoDetectPrimaryTimezone(userId, autoDetectPrimaryTimezone)
    }

    override suspend fun selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Int? {
        return database.calendarUserSettingsDao().selectAutoDetectPrimaryTimezone(userId)
    }

    override fun flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId).distinctUntilChanged()
    }

    override suspend fun updateCalendarUserSettingsDisplayWeekNumber(userId: String, displayWeekNumber: Int) {
        return database.calendarUserSettingsDao().updateDisplayWeekNumber(userId, displayWeekNumber)
    }

    override fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsDisplayWeekNumber(userId).distinctUntilChanged()
    }

    override suspend fun updateCalendarUserDefaultCalendarId(userId: String, defaultCalendarId: String) {
        return database.calendarUserSettingsDao().updateDefaultCalendarId(userId, defaultCalendarId)
    }

    override fun flowCalendarUserDefaultCalendarId(userId: String): Flow<String?> {
        return database.calendarUserSettingsDao().flowCalendarUserDefaultCalendarId(userId).distinctUntilChanged()
    }

    override suspend fun selectCalendarUserSettingsPrimaryTimezone(userId: String): String? {
        return database.calendarUserSettingsDao().selectCalendarUserSettingsPrimaryTimezone(userId)
    }

    override fun flowCalendarUserSettingsPrimaryTimezone(userId: String): Flow<String?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsPrimaryTimezone(userId).distinctUntilChanged()
    }

    override suspend fun persistCalendarUserSettings(userId: String, calendarUserSettings: CalendarUserSettingsEntity) {
        database.calendarUserSettingsDao().updateOrInsert(calendarUserSettings.copy(fkUserId = userId))
    }

    override suspend fun deleteCalendarUserSettingsByUserId(userId: String) {
        database.calendarUserSettingsDao().deleteByUserId(userId)
    }

    override suspend fun getDefaultCalendarId(userId: String): String? {
        return selectCalendarUserSettings(userId)?.defaultCalendarId ?: getActiveUserCalendars(userId).firstOrNull()?.id
    }

    override suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>> {
        return database.eventAlarmsDao().selectByEventId(eventId)
    }

    override suspend fun selectEventAlarm(eventAlarmId: String): EventAlarmEntity? {
        return database.eventAlarmsDao().select(eventAlarmId)
    }

    override suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectUpcomingInclusive(timestampSeconds)
    }

    override suspend fun selectAllEventAlarmsBetween(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity> {
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
