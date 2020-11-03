package me.proton.android.calendar.data

import androidx.room.Entity
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.filterOutOccurrencesByExdates
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId
import timber.log.Timber
import java.time.*

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

    // decrypted Events existing in database
    private val dbEvents = mutableListOf<Event>()

    // TODO shard by dates -- tree?
    // all Events including expanded
    private val allEvents = MutableStateFlow<List<Event>>(emptyList())

    // events that should be currently visible // TODO remove when we introduce EventTree
    private val displayedEvents = MutableStateFlow<List<Event>>(emptyList())
    private val displayedEventsMutex = Mutex()

    private var prefetchedFrom: LocalDate? = null
    private var prefetchedTo: LocalDate? = null

    override val fetchingState = MutableStateFlow<CalendarsRepository.FetchingState>(CalendarsRepository.FetchingState.NotNeeded)

    private lateinit var selectedCalendarIds: List<String>

    private var eventsExpandedUntil: ZonedDateTime = ZonedDateTime.now()
    private val expandEventsMutex = Mutex()
    private val expandEventsToDate = MutableStateFlow<ZonedDateTime>(eventsExpandedUntil)

    private val visibleCalendars = MutableStateFlow<List<CalendarEntity>>(emptyList())

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val DEBOUNCE_EXPANDING_EVENTS_ON_FETCH = Duration.ofMillis(1000)
    private val DEBOUNCE_CALENDARS_UPDATE = Duration.ofMillis(500)

    init {

        // cold init, fetch all needed entities straight from database
        coroutineScope.launch {
            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            // Events
            val eventEntities = database.eventsDao().selectEvents()
            val transformedEvents = eventEntities.mapNotNull { transformEventUseCase.execute(it) }
            dbEvents.addAll(transformedEvents)

            // Calendars
            visibleCalendars.value = database.calendarsDao().selectCalendars().filterVisible()

            // force expanding Events after cold init is done
            expandEventsMutex.withLock {
                expandEventsToDate.value = ZonedDateTime.now().plusMonths(1)
            }

            fetchingState.value = CalendarsRepository.FetchingState.Finished
        }

        coroutineScope.launch {
            expandEventsToDate.debounce(DEBOUNCE_EXPANDING_EVENTS_ON_FETCH.toMillis()).collect {
                expandDbEventsUntil(it)
            }
        }

        coroutineScope.launch {
            database.calendarsDao().flowCalendars().debounce(DEBOUNCE_CALENDARS_UPDATE.toMillis()).collect { calendarEntities ->
                visibleCalendars.value = calendarEntities.filterVisible()
            }
        }

        coroutineScope.launch {
            visibleCalendars.collect {
                showEventsInVisibleCalendars()
            }
        }

        coroutineScope.launch {
            allEvents.collect { calendarEntities ->
                showEventsInVisibleCalendars()
            }
        }
    }

    private fun List<CalendarEntity>.filterVisible(): List<CalendarEntity> {
        return this.filter {
            it.display == 1 && (it.isActive || it.isDisabled)
        }
    }

    private suspend fun showEventsInVisibleCalendars() {
        displayedEvents.value = displayedEventsMutex.withLock {
            allEvents.value.filter { event ->
                visibleCalendars.value.find { it.id == event.calendar.id } != null
            }
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

    override suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean {
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

        return displayedEvents.map {

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

    override suspend fun fetchEvents(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ) {

        val expandUntilDateTime = ZonedDateTime.of(LocalDateTime.of(toDate, LocalTime.MIDNIGHT), ZoneId.of(timeZoneId))
        expandEventsMutex.withLock {
            if (expandUntilDateTime.isAfter(eventsExpandedUntil)) {
                expandEventsToDate.value = expandUntilDateTime
            }
        }

        if (::selectedCalendarIds.isInitialized) { // TODO
            // TODO FIXME WE DONT INITIALIZE THIS SO WE DON'T FETCH
            fetchingState.value = CalendarsRepository.FetchingState.Fetching



            // TODO cache timestamps of this and don't fetch each time we scroll
            // fetch from API
            if (prefetchedFrom == null || fromDate.isBefore(prefetchedFrom)) {
                when (fetchEventsUseCase.execute(userId, selectedCalendarIds, fromDate, toDate, timeZoneId)) {
                    UseCase.Result.Success -> prefetchedFrom = fromDate
                }
            } else if (prefetchedTo == null || toDate.isAfter(prefetchedTo)) {

                when (fetchEventsUseCase.execute(userId, selectedCalendarIds, fromDate, toDate, timeZoneId)) {
                    UseCase.Result.Success -> prefetchedTo = toDate
                }
            }

            fetchingState.value = CalendarsRepository.FetchingState.Finished
        }

    }

    override suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean = database.eventsDao().hasEvent(eventId, calendarId)

    override suspend fun hasCalendar(calendarId: String, ): Boolean = database.calendarsDao().hasCalendar(calendarId)

    private suspend fun expandDbEventsUntil(toDateTime: ZonedDateTime) {

        expandEventsMutex.withLock {
            TimberLogger.v("expanding local occurrences until ${toDateTime.toLocalDate()}")

                allEvents.value = dbEvents.flatMap { event ->
                    if (event.isRecurring()) {
                        // TODO FIXME java.util.ConcurrentModificationException
                        val expandedOccurrences = ICalUtils.expandOccurrencesWithSingleEdits(event, dbEvents.filter { it.uid == event.uid }, toDateTime.toLocalDate(), toDateTime.zone.id)!!
                        val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event)

                        filteredByExdates
                    } else if (event.isSingleEdit()) {
                        // single edits are already generated when expanding above ^
                        //  however, orphaned single edits (without original recurring event)
                        //  have to be added to the list manually
                        if (dbEvents.find { it.uid == event.uid && it.isRecurring() } != null) {
                            emptyList()
                        } else {
                            listOf(event)
                        }
                    } else {
                        listOf(event)
                    }
                }

                TimberLogger.v("expanded local occurrences until ${toDateTime}: ${allEvents.value.size}")

                if (dbEvents.isNotEmpty()) {
                    eventsExpandedUntil = toDateTime
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

    override suspend fun persistEvents(vararg events: EventEntity) {
        TimberLogger.v("persist Event: ${events.map { it.id + " for calendar " + it.calendarId }}")
        database.eventsDao().updateOrInsert(*events)
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
        return database.eventAlarmsDao().select(eventId)
    }

    override suspend fun selectEventAlarms(
        timestampSecondsStart: Long,
        timestampSecondsEnd: Long
    ): List<EventAlarmEntity> {
        return database.eventAlarmsDao().select(timestampSecondsStart, timestampSecondsEnd)
    }

    override suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectUpcoming(timestampSeconds)
    }

    override suspend fun persistEventAlarm(eventAlarm: EventAlarmEntity) {
        database.eventAlarmsDao().insert(eventAlarm)
    }

    override suspend fun deleteEventAlarmById(eventAlarmId: String) {
        database.eventAlarmsDao().deleteById(eventAlarmId)
    }

}
