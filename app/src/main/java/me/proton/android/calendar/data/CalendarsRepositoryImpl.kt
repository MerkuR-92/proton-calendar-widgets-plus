package me.proton.android.calendar.data

import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.VisibleForTesting
import biweekly.property.RecurrenceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getFullyOverlappingWindow
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstRealOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.overlapsWithFullDayRange
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutBySearchTerm
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutDuplicatesInSubscribedCalendars
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.utils.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.data.entity.SkeletonEventEntity
import me.proton.android.calendar.data.entity.toSkeletonEvent
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.IndexEventForSearchUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.toInt
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.ceil

@FlowPreview
@ExperimentalCoroutinesApi
class CalendarsRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val transformEventUseCase: TransformEventUseCase,
    private val logger: Logger,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val calendarsApi: CalendarsApi,
    private val json: Json,
    private val widgetRefresher: WidgetRefresher,
    private val eventDecryptor: EventDecryptor,
    private val searchDatabase: SearchDatabase,
    private val indexEventForSearchUseCase: IndexEventForSearchUseCase,
    private val userManager: UserManager,
    private val accountManager: AccountManager
) : CalendarsRepository {

    private val DEBOUNCE_EXPANDING_EVENTS_ON_FETCH = Duration.ofMillis(1000)
    private val DEBOUNCE_CALENDARS_UPDATE = Duration.ofMillis(1000)
    private val DEBOUNCE_EVENTS_UPDATE = Duration.ofMillis(1000)

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

    /**
     * Min and Max FetchWindow requested in the lifetime of Repository. This means user has visited the views
     * corresponding to these windows, but not necessarily fetched events from backend.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var minRequestedWindowToFetch: FetchWindow? = null
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var maxRequestedWindowToFetch: FetchWindow? = null

    private var eventsExpandedUntil: ZonedDateTime = ZonedDateTime.now()
    private val expandEventsToDateFlow = MutableStateFlow<ZonedDateTime>(eventsExpandedUntil)
    private var expandEventsToDateChannel = Channel<ZonedDateTime>()

    private val dbCalendarEntities = MutableStateFlow<List<CalendarEntity>>(emptyList())
    private val visibleCalendarEntities = MutableStateFlow<List<CalendarEntity>>(emptyList())

    private var coroutineScope = CoroutineScope(Dispatchers.Default)
    private var scopeEventFetching = CoroutineScope(Dispatchers.Default)

    // caches already calculated Events for EventsWindow to quickly show them when resubscribing to flow
    private val eventsCache = mutableMapOf<CalendarsRepository.EventsWindow, List<Event>>()
    private val eventsCacheMutex = Mutex()

    // cache for SkeletonEvents
    private val skeletonEventsCache = mutableMapOf<CalendarsRepository.EventsWindow, List<SkeletonEvent>>()
    private val skeletonEventsCacheMutex = Mutex()

    private val allCalendarsFlow =
        database.calendarsDao().flowCalendars().joinToCalendars(database, json).debounce(DEBOUNCE_CALENDARS_UPDATE.toMillis())
            .distinctUntilChanged().shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1).onEach {
                eventDecryptor.setCalendars(it)
            }

    private val visibleCalendarsFlow =
        allCalendarsFlow.map { it.filterVisibleCalendars() }.distinctUntilChanged()
            .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1)

    private val visibleSkeletonEventsFlow =
        database.eventsDao().skeletonEventCountFlow().debounce(DEBOUNCE_EVENTS_UPDATE.toMillis())
            .combineTransform<Int, List<Calendar>, List<SkeletonEvent>>(visibleCalendarsFlow) { _, calendars ->
                // in order to prevent too large cursors, we subscribe to overall COUNT and then select data in a paginated way, manually
                val pageSize = 100
                val skeletonEventEntities = calendars.flatMap { calendar ->
                    val allEventsInCalendarCount = database.eventsDao().count(calendar.id)

                    (0 until ceil(allEventsInCalendarCount / pageSize.toDouble()).toInt()).flatMap { page ->
                        database.eventsDao().selectSkeletonEventsInCalendarPaginated(calendar.id, pageSize, pageSize * page)
                    }
                }

                val existingAddressIds = mutableMapOf<String, Boolean>() // AddressId -> exists/doesn't exist

                val skeletonEvents = skeletonEventEntities.mapNotNull { skeletonEventEntity ->
                    val calendar = calendars.firstOrNull { it.id == skeletonEventEntity.calendarId }

                    // cache information if Address exists locally or not
                    if ((skeletonEventEntity.addressId != null) && existingAddressIds.contains(skeletonEventEntity.addressId).not()) {
                        existingAddressIds[skeletonEventEntity.addressId] = database.addressDao().getByAddressId(AddressId(skeletonEventEntity.addressId)) != null
                    }

                    if ((skeletonEventEntity.addressId != null) && existingAddressIds[skeletonEventEntity.addressId] == false) {
                        // if it's an auto-added invite and I don't have the Address to decrypt it, filter it out
                        null
                    } else calendar?.run { skeletonEventEntity.toSkeletonEvent(json, this.color, this.type) }
                }

                emit(skeletonEvents)

            }.shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 1).distinctUntilChanged()

    private fun List<Calendar>.filterVisibleCalendars(): List<Calendar> {
        return this.filter {
            it.display && (it.isActive || it.isDisabled)
        }
    }

    private suspend fun showEventsInVisibleCalendars() {

        // out of all events, filter out invisible ones (because of hidden calendar)
        val events = eventsMutex.withLock {
            allEvents.value.filter { event ->
                visibleCalendarEntities.value.find { it.id == event.calendar.id } != null
            }
        }

        displayedEventsMutex.withLock {
            displayedEvents.value = events
        }
    }

    override suspend fun refreshCalendarsFlags(userId: UserId) {
        val remoteMembers = calendarsApi.getAllMembers(userId).valueOrNullAndLogErrors(logger)

        remoteMembers?.members?.forEach {
            if (hasCalendar(it.calendarId)) {
                persistMember(it)
            } else {
                logger.i("refreshCalendarsFlags: Member's Calendar doesn't exist locally")
            }
        }
    }

    private fun removeDisabledFlag(flags: Int, dbCalendar: Calendar): Int {
        // status at 1 means the address is active

        // if the calendar is inactive we keep the same flags but remove the disabled flag
        // we also check if the calendar is simply disabled or super owner disabled to correctly update it
        return if (dbCalendar.isInactive && !dbCalendar.isSuperOwnerDisabled) {
            flags - MemberEntity.CalendarFlags.DISABLED.value
        } else if (dbCalendar.isInactive && dbCalendar.isSuperOwnerDisabled) {
            flags - MemberEntity.CalendarFlags.SUPER_OWNER_DISABLED.value
        } else {
            // if the calendar is simply disabled we set the flags at active
            MemberEntity.CalendarFlags.ACTIVE.value
        }
    }

    private fun addDisabledFlag(flags: Int, dbCalendar: Calendar): Int {
        // status at 0 means the address is disabled

        // if the calendar is inactive we keep the same flags but add the disabled flag
        return if (dbCalendar.isInactive) {
            flags + MemberEntity.CalendarFlags.DISABLED.value
        } else {
            // if the calendar is simply active we set the flags at disabled
            MemberEntity.CalendarFlags.DISABLED.value
        }
    }

    override suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<CalendarsRepository.InitingState> {

        val flow = MutableStateFlow<CalendarsRepository.InitingState>(CalendarsRepository.InitingState.Initing)

        // TODO make sure we also migrate the calendar fetching for new event decryption
        scopeEventFetching.launch {
            fetchEventsChannel.consumeEach {

                minRequestedWindowToFetch = if (minRequestedWindowToFetch != null) {
                    listOf(it, minRequestedWindowToFetch).minByOrNull { it!!.fromDate }
                } else it

                maxRequestedWindowToFetch = if (maxRequestedWindowToFetch != null) {
                    listOf(it, maxRequestedWindowToFetch).maxByOrNull { it!!.toDate }
                } else it

                logger.v("consuming: $it")
                fetchEventsInWindow(it)
            }
        }

        flow.value = CalendarsRepository.InitingState.Finished
        return flow
    }

    private suspend fun fetchEventsInWindow(fetchWindow: FetchWindow) {

        if (!fetchedWindows.contains(fetchWindow)) {
            logger.d("fetching events: ${fetchWindow.fromDate} = ${fetchWindow.toDate}")

            fetchingState.value = CalendarsRepository.FetchingState.Fetching

            // fetch from API
            val fetchEventsResult = fetchEventsUseCase.splitFetchEvents(
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

        // cancel any ongoing Event fetching
        scopeEventFetching.cancel()
        scopeEventFetching = CoroutineScope(Dispatchers.Default)

        minRequestedWindowToFetch = null
        maxRequestedWindowToFetch = null

        fetchedWindows.clear()
        fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        fetchingState.value = CalendarsRepository.FetchingState.Finished

        skeletonEventsCacheMutex.withLock {
            skeletonEventsCache.clear()
        }

        eventsCacheMutex.withLock {
            eventsCache.clear()
        }
    }

    override suspend fun countCalendars(): Int {
        return database.calendarsDao().countCalendars()
    }

    override suspend fun clearSearchDatabase() {
        withContext(Dispatchers.IO) {
            searchDatabase.searchDao().deleteAllSearchEvents()
        }
    }

    override suspend fun selectCalendarEntity(calendarId: String): CalendarEntity? {
        return database.calendarsDao().selectById(calendarId)
    }

    override suspend fun selectCalendar(calendarId: String): Calendar? {
        return database.calendarsDao().selectById(calendarId)?.joinToCalendar(database, json)
    }

    override suspend fun selectCalendarEntities(userId: String): List<CalendarEntity> {
        return database.calendarsDao().selectCalendars(userId)
    }

    override suspend fun selectUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json)
    }

    override suspend fun selectUserPersonalCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter {
            it.isOwner
        }
    }

    override suspend fun selectOtherCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectCalendars(userId).joinToCalendars(database, json).filter {
            !it.isOwner
        }
    }

    override suspend fun selectAllCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectCalendars(userId).joinToCalendars(database, json)
    }

    override suspend fun selectActiveUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter { it.isActive }
    }

    override suspend fun selectDisabledUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter { it.isDisabled }
    }

    override suspend fun selectInactiveUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter { it.isInactive }
    }

    override suspend fun selectSubscribedCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectSubscribedCalendars(userId).joinToCalendars(database, json)
    }

    override fun flowActiveUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).transform<List<Calendar>, List<Calendar>> { it.filter { it.isActive } }.distinctUntilChanged()
    }

    override fun flowDisabledUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).transform<List<Calendar>, List<Calendar>> { it.filter { it.isDisabled } }.distinctUntilChanged()
    }

    override fun flowInactiveUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).transform<List<Calendar>, List<Calendar>> { it.filter { it.isInactive } }.distinctUntilChanged()
    }

    override fun flowUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).distinctUntilChanged()
    }

    override fun flowUserPersonalCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).map { userCalendars ->
            userCalendars.filter { it.isOwner }
        }.distinctUntilChanged()
    }

    override fun flowSharedCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).map { userCalendars ->
            userCalendars.filter { !it.isOwner }
        }.distinctUntilChanged()
    }

    override fun flowSubscribedCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowSubscribedCalendars(userId).joinToCalendars(database, json).distinctUntilChanged()
    }

    override fun flowHolidayCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowHolidayCalendars(userId).joinToCalendars(database, json).distinctUntilChanged()
    }

    override suspend fun persistCalendar(userId: String, calendar: CalendarEntity) {
        //  TODO make sure we have "flags" set!!!!!
        database.calendarsDao().updateOrInsert(calendar.copy(fkUserId = userId))
    }

    override suspend fun deleteCalendarById(id: String) {
        database.calendarsDao().deleteById(id)

        database.calendarsDao().selectCalendarUserId(id)?.let {
            deleteAllSearchEventsInCalendar(it, id)
        }
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

    override suspend fun refreshCalendars(userId: UserId, calendarIds: List<String>) {
        val calendars = arrayListOf<CalendarEntity>()
        calendarIds.forEach {
            val calendarResponse = calendarsApi.getCalendar(userId, it)
            if (calendarResponse !is ApiResponse.Success) {
                // We log but ignore the error
                logger.e("error getting calendar from API in CalendarsRepositoryImpl")
            } else {
                calendars.add(calendarResponse.data.calendar)
            }
        }
        calendars.forEach {
            // TODO do boostrap for subscribed calendars to get calendar subscription extra properties
            persistCalendar(userId.id, it)
        }
    }

    override suspend fun fetchCalendars(userId: UserId): List<Calendar>? {
        return fetchCalendarEntities(userId)?.map {
            Calendar.from(
                it,
                fetchMembers(userId, it.id)?.firstOrNull() ?: return null,
                fetchCalendarSettings(userId, it.id) ?: return null,
                json)
        }
    }

    override suspend fun fetchCalendarEntities(userId: UserId): List<CalendarEntity>? = calendarsApi.getCalendars(userId).valueOrNullAndLogErrors(logger)?.calendars

    override suspend fun fetchMembersToCalendarEntities(
        userId: UserId,
        calendarEntities: List<CalendarEntity>
    ): List<Calendar> {
        val calendars = arrayListOf<Calendar>()
        coroutineScope {
            calendarEntities.map {
                async {
                    val members = fetchMembers(userId, it.id)?.firstOrNull() ?: run {
                        logger.e("fetchMembersToCalendarEntities: Failed to fetch members")
                        return@async null
                    }
                    val calendarSettings = fetchCalendarSettings(userId, it.id) ?: run {
                        logger.e("fetchMembersToCalendarEntities: Failed to fetch calendar settings")
                        return@async null
                    }
                    val calendar = Calendar.from(
                        it,
                        members,
                        calendarSettings,
                        json
                    )
                    calendars.add(calendar)
                }
            }.awaitAll()
        }
        return calendars
    }

    override suspend fun fetchMembers(userId: UserId, calendarId: String): List<MemberEntity>? {
        val membersResponse = calendarsApi.getMemberList(userId, calendarId)
        return if (membersResponse !is ApiResponse.Success) {
            logger.e("error getting members from API in CalendarsRepositoryImpl")
            null
        } else {
            membersResponse.data.members
        }
    }

    private suspend fun fetchCalendarSettings(userId: UserId, calendarId: String): CalendarSettingsEntity? {
        val settingsResponse = calendarsApi.getCalendarSettings(userId, calendarId)
        return if (settingsResponse !is ApiResponse.Success) {
            logger.e("error getting CalendarSettings from API in CalendarsRepositoryImpl")
            null
        } else {
            settingsResponse.data.calendarSettings
        }
    }

    override suspend fun fetchCalendar(userId: UserId, calendarId: String): Calendar? {

        val fetchedCalendar = calendarsApi.getCalendar(userId, calendarId).valueOrNullAndLogErrors(logger)?.calendar ?: return null
        val fetchedMember = calendarsApi.getMemberList(userId, calendarId).valueOrNullAndLogErrors(logger)?.members?.firstOrNull() ?: return null
        val fetchedCalendarSettings = calendarsApi.getCalendarSettings(userId, calendarId).valueOrNullAndLogErrors(logger)?.calendarSettings ?: return null

        return Calendar.from(fetchedCalendar, fetchedMember, fetchedCalendarSettings, json)
    }

    override suspend fun fetchCalendarEntity(userId: UserId, calendarId: String): CalendarEntity? =
        calendarsApi.getCalendar(userId, calendarId).valueOrNullAndLogErrors(logger)?.calendar

    override suspend fun fetchManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? =
        calendarsApi.getManagedHolidayCalendars(userId).valueOrNullAndLogErrors(logger)?.calendars

    override suspend fun getManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? =
        database.managedHolidayCalendarDao().selectAll()

    override suspend fun getManagedHolidayCalendar(userId: UserId, calendarId: String): ManagedHolidayCalendarEntity? =
        database.managedHolidayCalendarDao().selectById(calendarId)

    /**
     * Fetches the managed holiday calendar list from BE, persists it in DB if non null.
     * @return the fetched list of managed holiday calendar.
     */
    override suspend fun refreshManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? {
        val managedHolidayCalendars = calendarsApi.getManagedHolidayCalendars(userId).valueOrNullAndLogErrors(logger)?.calendars
        managedHolidayCalendars?.forEach {
            database.managedHolidayCalendarDao().updateOrInsert(it.copy(fkUserId = userId.id))
        }
        return managedHolidayCalendars
    }

    override suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean {
        val member = selectCalendarMembers(calendarId).firstOrNull()
        return if (member != null) member.display == newDisplay else false
    }

    override suspend fun updateCalendarDisplay(calendarId: String, display: Boolean) {
        database.membersDao().selectCalendarMembers(calendarId).firstOrNull()?.let {
            database.membersDao().updateDisplay(calendarId, display.toInt())
            widgetRefresher.refreshEventList()
        }
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

    override fun getUiEventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<CalendarsRepository.GetEventsResult<UiEvent>> {

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        return visibleSkeletonEventsFlow.map<List<SkeletonEvent>, CalendarsRepository.GetEventsResult<UiEvent>> { visibleSkeletonEvents ->

            val userAddresses = accountManager.getPrimaryUserId().firstOrNull()?.let { userManager.getAddresses(it) }
                ?: return@map CalendarsRepository.GetEventsResult.Exception(Exception("could not get user addresses in getUiEventsFlow"))

            val userEmails = userAddresses.map { it.email }

            logger.v("getUiEventsFlow flow for ${eventsWindow.fromDate} - ${eventsWindow.toDate} we have ${visibleSkeletonEvents.size} skeletons to expand")

            // TODO hack for hiding duplicated events from subscribed Calendars
            val (uniqueEventSkeletons, duplicatedEventSkeletons) = visibleSkeletonEvents.filterOutDuplicatesInSubscribedCalendars()

            if (visibleSkeletonEvents.size != uniqueEventSkeletons.size) {
                logger.i("found duplicates in filterOutDuplicatesInSubscribedCalendars, deleting / 100: ${duplicatedEventSkeletons.size / 100}")

                // delete duplicated subscribed events from local DB
                duplicatedEventSkeletons.chunked(50).forEach {
                    database.eventsDao().deleteByIds(it.map { it.id })
                }
            }

            coroutineScope {

                val transformedEvents = uniqueEventSkeletons.distinct().map { skeleton ->
                    async {
                        val transformedEvent = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                            // get Event from cache based on metadata, or select entire EventEntity and decrypt it in case of cache miss
                            eventDecryptor.getFromCache(skeleton.id, skeleton.calendar.id, skeleton.modifyTime) ?: database.eventsDao().selectById(skeleton.id)?.let { eventDecryptor.decrypt(it) }
                        } else {
                            database.eventsDao().selectById(skeleton.id)?.let { transformEventUseCase.execute(it) }
                        }

                        transformedEvent// TODO cache what we were able to transform? maybe not because decryptor caches it anyway?
                    }
                }.awaitAll().filterNotNull()

                // TODO let's see if we have less memory issues without Repo's eventsCache
//                eventsCacheMutex.withLock {
//                    eventsCache[eventsWindow] = transformedEvents
//                }

                val events = transformedEvents.map { event ->
                    expandSkeletonEventsAndFilterInWindowToUiEvents(
                        event,
                        transformedEvents,
                        eventsWindow,
                        userEmails
                    )
                }.flatten()

                CalendarsRepository.GetEventsResult.Success(events)

            }

        }.onStart {
            emit(CalendarsRepository.GetEventsResult.InProgress)
        }.flowOn(Dispatchers.Default).distinctUntilChanged()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    data class FetchWindow(
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

    override suspend fun transformAllowingApiCall(eventId: String, calendarId: String): Event? {
        return database.eventsDao().selectEvent(eventId, calendarId)?.let { eventDecryptor.decryptAllowingApiCall(it) }
    }

    private fun createEventsFlow(eventsWindow: CalendarsRepository.EventsWindow, allowCached: Boolean): Flow<CalendarsRepository.GetEventsResult<Event>> {

        return createSkeletonsFlow(eventsWindow).transform<List<SkeletonEvent>, CalendarsRepository.GetEventsResult<Event>> { eventSkeletons ->

            // TODO hack for hiding duplicated events from subscribed Calendars
            val (uniqueEventSkeletons, duplicatedEventSkeletons) = eventSkeletons.filterOutDuplicatesInSubscribedCalendars()

            if (eventSkeletons.size != uniqueEventSkeletons.size) {
                logger.i("found duplicates in filterOutDuplicatesInSubscribedCalendars, deleting / 100: ${duplicatedEventSkeletons.size / 100}")

                // delete duplicated subscribed events from local DB
                duplicatedEventSkeletons.chunked(50).forEach {
                    database.eventsDao().deleteByIds(it.map { it.id })
                }
            }

            logger.v("events flow: createEventsFlow for ${eventsWindow.fromDate} - ${eventsWindow.toDate}")

            coroutineScope {

                val transformedEvents = uniqueEventSkeletons.distinct().map { skeleton ->
                    async {
                        val transformedEvent = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                            // get Event from cache based on metadata, or select entire EventEntity and decrypt it in case of cache miss
                            eventDecryptor.getFromCache(skeleton.id, skeleton.calendar.id, skeleton.modifyTime) ?: database.eventsDao().selectById(skeleton.id)?.let { eventDecryptor.decrypt(it) }
                        } else {
                            database.eventsDao().selectById(skeleton.id)?.let { transformEventUseCase.execute(it) }
                        }

                        val skeletons = uniqueEventSkeletons.filter { it.id == skeleton.id }

                        // Skeleton Events already have correct Occurrence & DTSTART/DTEND applied,
                        // all we need to do is decrypt EventEntity and return full Events with correct occurrences

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

    override suspend fun getEvents(userId: String, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): List<Event> {

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        val visibleCalendars = selectAllCalendars(userId).filterVisibleCalendars()

        val existingAddressIds = mutableMapOf<String, Boolean>() // AddressId -> exists/doesn't exist

        val visibleSkeletons = visibleCalendars.map { calendar ->
            database.eventsDao().selectSkeletonEvents(calendar.id).mapNotNull { skeletonEventEntity ->

                // cache information if Address exists locally or not
                if ((skeletonEventEntity.addressId != null) && existingAddressIds.contains(skeletonEventEntity.addressId).not()) {
                    existingAddressIds[skeletonEventEntity.addressId] = database.addressDao().getByAddressId(AddressId(skeletonEventEntity.addressId)) != null
                }

                if ((skeletonEventEntity.addressId != null) && existingAddressIds[skeletonEventEntity.addressId] == false) {
                    // if it's an auto-added invite and I don't have the Address to decrypt it, filter it out
                    null
                } else calendar.run { skeletonEventEntity.toSkeletonEvent(json, this.color, this.type) }
            }.filterOutDuplicatesInSubscribedCalendars().first
        }

        val skeletonsInWindow = visibleSkeletons.map { skeletons ->
            skeletons.map { skeletonEvent ->
                expandSkeletonEventsAndFilterInWindow(
                    skeletonEvent,
                    skeletons,
                    eventsWindow
                )
            }.flatten()
        }.flatten()

        logger.v("getEvents [List<Event>] for ${eventsWindow.fromDate} - ${eventsWindow.toDate}")

        return coroutineScope {

            // Skeleton Events already have correct Occurrence & DTSTART/DTEND applied,
            // all we need to do is decrypt EventEntity and return full Events with correct occurrences

            val eventIds = skeletonsInWindow.map { it.id }.distinct()
            val chunkedEventIds = eventIds.chunked(20)
            val eventEntities = chunkedEventIds.flatMap {
                database.eventsDao().selectAllById(it)
            }

            val transformedEvents = eventEntities.map { eventEntity ->
                async {
                    val transformedEvent = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                        eventDecryptor.decrypt(eventEntity)
                    } else {
                        transformEventUseCase.execute(eventEntity)
                    }

                    val skeletons = skeletonsInWindow.filter { it.id == eventEntity.id }

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

            transformedEvents
        }
    }

    override fun getEventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        allowCached: Boolean
    ): Flow<CalendarsRepository.GetEventsResult<Event>> {

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        return createEventsFlow(eventsWindow, allowCached)
    }

    override fun getSearchEvents(userId: String, searchTerm: String): Flow<CalendarsRepository.GetEventsResult<Event>> =
        searchDatabase.searchDao().flowSearchEvents(userId).distinctUntilChanged().transform<List<SearchEventEntity>, CalendarsRepository.GetEventsResult<Event>> { searchEventEntities ->
            val containSearchTerm = searchEventEntities.filterOutBySearchTerm(searchTerm)

            val deduplicated = containSearchTerm.mapNotNull { searchEventEntity ->
                database.eventsDao().selectEvent(searchEventEntity.eventId, searchEventEntity.calendarId)?.let { eventDecryptor.decrypt(it) }
            }.filterOutDuplicatesInSubscribedCalendars().first

            emit(CalendarsRepository.GetEventsResult.Success(deduplicated))
        }.onStart {
            emit(CalendarsRepository.GetEventsResult.InProgress)
        }.catch {
            emit(CalendarsRepository.GetEventsResult.Exception(it))
        }.distinctUntilChanged().cancellable()

    override suspend fun deleteAllSearchEvents(userId: String) {
        searchDatabase.searchDao().deleteAll(userId)
    }

    override suspend fun deleteAllSearchEventsInCalendar(userId: String, calendarId: String) {
        searchDatabase.searchDao().deleteAllInCalendar(userId, calendarId)
    }

    override suspend fun deleteSearchEventsForEvents(userId: String, calendarId: String, eventIds: List<String>) {
        searchDatabase.searchDao().deleteSearchEventsForEvents(userId, calendarId, eventIds)
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

        }.flowOn(Dispatchers.Default).distinctUntilChanged()

    }

    /**
     * Get Skeleton Events with correct Calendar Colors.
     */
    override suspend fun getSkeletonEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): List<SkeletonEvent> {

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        return visibleSkeletonEventsFlow.map { visibleSkeletonEvents ->

            logger.v("events flow: getSkeletonEvents for ${eventsWindow.fromDate} - ${eventsWindow.toDate}")

            visibleSkeletonEvents.map { skeletonEvent ->
                expandSkeletonEventsAndFilterInWindow(
                    skeletonEvent,
                    visibleSkeletonEvents,
                    eventsWindow
                )
            }.flatten()

        }.first()
    }

    /**
     * Get Skeleton Events with correct Calendar Colors.
     */
    override fun getSkeletonEventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<CalendarsRepository.GetEventsResult<SkeletonEvent>> {

        logger.v("calling getSkeletonEventsForIndicators $fromDate - $toDate")

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

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

    override suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean =
        database.eventsDao().hasEvent(eventId, calendarId)

    override suspend fun eventExistsOnServer(userId: UserId, eventId: String, calendarId: String): Boolean? {
        return when (val result = calendarsApi.getEvent(userId, calendarId, eventId)) {
            is ApiResponse.Success-> true
            is ApiResponse.Error -> {
                if (result.isNotFound()) return false
                else null
            }
            is ApiResponse.Exception -> null
        }
    }

    override suspend fun shouldFetchEvent(metadata: ServerEvent.EventEntityMetadata): Boolean {

        val dbEventEntity = database.eventsDao().selectById(metadata.id)
        val isDbEventUpToDate = dbEventEntity?.modifyTime == metadata.modifyTime
        if (isDbEventUpToDate) return false

        if (metadata.rRule == null) { // non-recurring event

            val now = Instant.now()
            val startInstant = Instant.ofEpochSecond(metadata.startTime)
            val endInstant = Instant.ofEpochSecond(metadata.endTime)

            val isEventRecent = when {
                now.minus(60, ChronoUnit.DAYS).isAfter(endInstant) -> false
                now.plus(120, ChronoUnit.DAYS).isBefore(startInstant) -> false
                else -> true
            }

            val minWindowStart = minRequestedWindowToFetch?.fromDate?.atStartOfDay(ZoneId.of(minRequestedWindowToFetch?.timeZoneId))
            val maxWindowEnd = maxRequestedWindowToFetch?.toDate?.plusDays(1)?.atStartOfDay(ZoneId.of(maxRequestedWindowToFetch?.timeZoneId))

            // event is within all requested FetchWindows, it means that user would have displayed it in one of the views
            val isInsideRequestedFetchingWindows = when {
                minWindowStart?.toInstant()?.isAfter(endInstant) == true -> false
                maxWindowEnd?.toInstant()?.isBefore(startInstant) == true -> false
                else -> minWindowStart != null && maxWindowEnd != null
            }

            return isEventRecent || isInsideRequestedFetchingWindows
        } else { // recurring event, we always assume "should fetch" for simplicity
            return true
        }

    }

    override suspend fun hasCalendar(calendarId: String, ): Boolean = database.calendarsDao().hasCalendar(calendarId)

    /**
     * Calling this method requires obtained lock on 'allEvents'!
     */
    override fun expandDbEvent(event: Event, allEvents: List<Event>, toDateTime: ZonedDateTime): List<Event> {
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

    private fun expandSkeletonEventsAndFilterInWindowToUiEvents(event: Event, allEvents: List<Event>, eventsWindow: CalendarsRepository.EventsWindow, userEmails: List<String>): List<UiEvent> {

        return if (event.isRecurring()) {
            // occurrences are already filtered for time window
            ICalUtilsImpl.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                event,
                allEvents.filter { it.uid == event.uid },
                eventsWindow.fromDate,
                eventsWindow.toDate,
                eventsWindow.timeZoneId,
                userEmails
            )!!
        } else if (event.isSingleEdit()) {
            // single edits are already generated when expanding above ^
            //  however, orphaned single edits (without original recurring event)
            //  have to be added to the list manually
            if (allEvents.find { it.uid == event.uid && it.isRecurring() } != null) {
                emptyList()
            } else {
                if (event.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId)) listOf(event.toUiEvent(userEmails, eventsWindow.timeZoneId)) else emptyList()
            }
        } else {
            if (event.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId)) listOf(event.toUiEvent(userEmails, eventsWindow.timeZoneId)) else emptyList()
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
                val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(it)
                } else {
                    transformEventUseCase.execute(it)
                }
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
                val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(it)
                } else {
                    transformEventUseCase.execute(it)
                }
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
                iCal?.events?.firstOrNull()?.recurrenceId == null
            }
        } else null
    }

    /**
     * Main chain has no other occurrences left and event is the only single edit
     */
    override suspend fun isStandaloneSingleEdit(userId: UserId, eventUid: String, eventRecurrenceId: RecurrenceId, timeZoneId: String): Boolean? {
        // Check if single edit is the only occurrence of a recurring event
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {

            val skeletonEvents = eventsSharingUidResponse.data.events.mapNotNull { eventEntity ->
                val skeletonEventEntity = SkeletonEventEntity(eventEntity.id, eventEntity.calendarId, eventEntity.sharedEvents, eventEntity.modifyTime, eventEntity.addressId)
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

    override suspend fun selectEventsByUid(eventUid: String): List<Event> {
        return database.eventsDao().selectByUid(formatUidForICal(eventUid)).mapNotNull {
            eventDecryptor.decrypt(it)
        }
    }

    override suspend fun fetchEventById(userId: UserId, calendarId: String, eventId: String): ApiResponse<EventApiResponse> {
        return calendarsApi.getEvent(userId, calendarId, eventId)
    }

    override suspend fun persistEvents(vararg events: EventEntity) {
        val eventsByCalendar = events.groupBy { it.calendarId }
        database.inTransaction {
            eventsByCalendar.forEach {
                val calendarUserId = database.calendarsDao().selectCalendarUserId(it.key)
                if (calendarUserId != null /* Calendar exists */) {
                    try {
                        it.value.forEach {
                            // don't overwrite Event it we already have newer one in DB
                            if (!database.eventsDao().hasEventWithHigherModifyTime(it.id, it.calendarId, it.modifyTime)) {
                                database.eventsDao().updateOrInsert(it)
                            }

                            indexEventForSearchUseCase.execute(calendarUserId, listOf(it))
                        }
                    } catch (e: SQLiteConstraintException) {
                        // hack for different SQLite implementations formatting message differently
                        if (e.message?.contains("787") == true
                            && e.message?.contains("foreign", ignoreCase = true) == true
                            && e.message?.contains("constraint", ignoreCase = true) == true
                        ) {
                            // ignore, it means this Event's Calendar doesn't exist
                            logger.e("persistEvents couldn't insert because ${e.message}", e)
                        } else throw e
                    }
                } else {
                    logger.i("persistEvents couldn't insert because calendar doesn't exist")
                }
            }
        }
    }
    override suspend fun deleteEventsById(calendarId: String, ids: List<String>) {
        database.eventsDao().deleteByIds(ids)

        database.calendarsDao().selectCalendarUserId(calendarId)?.let {
            deleteSearchEventsForEvents(it, calendarId, ids)
        }
    }

    override suspend fun deleteAllEvents() {
        database.eventsDao().deleteAll()
    }

    override suspend fun deleteAllEvents(calendarId: String) {
        database.eventsDao().deleteAll(calendarId)

        database.calendarsDao().selectCalendarUserId(calendarId)?.let {
            deleteAllSearchEventsInCalendar(it, calendarId)
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

    override suspend fun selectCalendarMembers(calendarId: String): List<MemberEntity> {
        return database.membersDao().selectCalendarMembers(calendarId)
    }

    override suspend fun selectMemberById(memberId: String): MemberEntity? {
        return database.membersDao().selectById(memberId)
    }

    override suspend fun persistMember(member: MemberEntity) {
        database.membersDao().updateOrInsert(member)
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

    override suspend fun selectCalendarSubscriptions(calendarId: String): List<CalendarSubscriptionEntity> {
        return database.calendarSubscriptionDao().selectCalendarSubscriptions()
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

    override suspend fun selectCalendarUserSettingsDisplayWeekNumber(userId: String): Int? {
        return database.calendarUserSettingsDao().selectCalendarUserSettingsDisplayWeekNumber(userId)
    }

    override fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsDisplayWeekNumber(userId).distinctUntilChanged()
    }

    override fun flowCalendarUserSettingsAutoImportInvite(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsAutoImportInvite(userId).distinctUntilChanged()
    }

    override suspend fun updateCalendarUserDefaultCalendarId(userId: String, defaultCalendarId: String) {
        return database.calendarUserSettingsDao().updateDefaultCalendarId(userId, defaultCalendarId)
    }

    override suspend fun updateCalendarUserAutoImportInvite(userId: String, autoImportInvite: Boolean) {
        return database.calendarUserSettingsDao().updateAutoImportInvite(userId, autoImportInvite.toInt())
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

    override suspend fun getDefaultCalendarIdOrFirstActiveId(userId: String): String? {
        return getDefaultCalendarId(userId) ?: selectActiveUserCalendars(userId).firstOrNull()?.id
    }

    override suspend fun getDefaultCalendarId(userId: String): String? {
        return database.calendarUserSettingsDao().selectCalendarUserDefaultCalendarId(userId)
    }

    override suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>> {
        return database.eventAlarmsDao().selectByEventId(eventId)
    }

    override suspend fun selectEventAlarm(eventAlarmId: String): EventAlarmEntity? {
        return database.eventAlarmsDao().select(eventAlarmId)
    }

    override suspend fun deleteAllEventAlarms(calendarId: String) {
        database.eventAlarmsDao().deleteAll(calendarId)
    }

    override suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectUpcomingInclusive(timestampSeconds)
    }

    override suspend fun selectAllEventAlarmsBetween(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectAllBetweenInclusive(timestampSecondsFrom, timestampSecondsTo)
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

    override suspend fun getAddressForMember(
        userId: UserId,
        member: MemberEntity,
        addresses: List<UserAddress>?,
        refresh: Boolean
    ): UserAddress? {
        val address = (addresses ?: userManager.getAddressesOrNull(userId, refresh))?.firstOrNull {
            if (!member.addressId.isNullOrEmpty()) {
                it.addressId.id.equalsNoCase(member.addressId)
            } else {
                canonicalizeProtonEmail(it.email, forceCanonicalization = true)
                    .equalsNoCase(member.canonicalEmail)
            }
        }
        if (address != null && member.addressId.isNullOrEmpty()) {
            // Update AddressId in member if a match was found and field was not already persisted
            database.membersDao().updateMemberAddressId(member.id, address.addressId.id)
        }
        return address
    }

    /**
     * Find member that belongs to user
     */
    override fun getUserMember(userAddresses: List<UserAddress>, members: List<MemberEntity>): MemberEntity? {
        return members.firstOrNull { member ->
            userAddresses.any { userAddress ->
                if (!member.addressId.isNullOrEmpty()) {
                    member.addressId.equalsNoCase(userAddress.addressId.id)
                } else {
                    member.canonicalEmail.equalsNoCase(
                        canonicalizeProtonEmail(userAddress.email, forceCanonicalization = true)
                    )
                }
            }
        }
    }
}

/**
 * Joins [CalendarEntity] with [MemberEntity] to [Calendar] object.
 */
fun Flow<List<CalendarEntity>>.joinToCalendars(database: AppDatabase, json: Json): Flow<List<Calendar>> {
    return combine(
        this.distinctUntilChanged(),
        database.membersDao().flowMembers().distinctUntilChanged(),
        database.calendarSettingsDao().flowCalendarSettings().distinctUntilChanged()
    ) { calendars, members, calendarSettingsList ->
        if (calendars.isNotEmpty()) {
            // Get user addresses so we can find the calendar member for current user
            val userAddresses = database.addressDao().getByUserId(UserId(calendars.first().fkUserId))
            calendars.mapNotNull { calendarEntity ->
                // Find the calendar settings for that calendar
                val calendarSettings = calendarSettingsList.firstOrNull { it.calendarId == calendarEntity.id } ?: return@mapNotNull null
                // Find the member that belongs to the current user
                val userMember = members.filter { it.calendarId == calendarEntity.id }.getUserMember(userAddresses)
                // Map to Calendar
                userMember?.let { Calendar.from(calendarEntity, it, calendarSettings, json) }
            }
        } else emptyList()
    }.distinctUntilChanged()
}

/**
 * Joins [CalendarEntity] with [MemberEntity] to [Calendar] object.
 */
suspend fun List<CalendarEntity>.joinToCalendars(database: AppDatabase, json: Json): List<Calendar> {
    if (this.isEmpty()) return emptyList()
    // Get user addresses so we can find the calendar member for current user
    val userAddresses = database.addressDao().getByUserId(UserId(this.first().fkUserId))
    return this.mapNotNull { calendarEntity ->
        val calendarSettings = database.calendarSettingsDao().select(calendarEntity.id) ?: return@mapNotNull null
        // Get all members for that calendar
        val calendarMembers = database.membersDao().selectCalendarMembers(calendarEntity.id)
        // Find the member that belongs to the current user
        val userMember = calendarMembers.getUserMember(userAddresses)
        // Map to Calendar
        userMember?.let { Calendar.from(calendarEntity, it, calendarSettings, json) }
    }
}

/**
 * Joins [CalendarEntity] with [MemberEntity] to [Calendar] object.
 */
suspend fun CalendarEntity?.joinToCalendar(database: AppDatabase, json: Json): Calendar? {
    return if (this == null) {
        null
    } else {
        // Get user addresses so we can find the calendar member for current user
        val userAddresses = database.addressDao().getByUserId(UserId(this.fkUserId))
        val calendarSettings = database.calendarSettingsDao().select(this.id) ?: return null
        // Get all members for that calendar
        val calendarMembers = database.membersDao().selectCalendarMembers(this.id)
        // Find the member that belongs to the current user
        val userMember = calendarMembers.getUserMember(userAddresses)
        // Map to Calendar
        userMember?.let { Calendar.from(this, it, calendarSettings, json) }
    }
}

/**
 * Find member that belongs to user
 */
private fun List<MemberEntity>.getUserMember(userAddresses: List<AddressEntity>): MemberEntity? {
    return this.firstOrNull { member ->
        userAddresses.any { userAddress ->
            if (!member.addressId.isNullOrEmpty()) {
                member.addressId.equalsNoCase(userAddress.addressId.id)
            } else {
                member.canonicalEmail.equalsNoCase(
                    canonicalizeProtonEmail(userAddress.email, forceCanonicalization = true)
                )
            }
        }
    }
}
