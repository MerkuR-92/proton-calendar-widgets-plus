package me.proton.android.calendar.domain

import biweekly.property.RecurrenceId
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Manages all Calendars, Events, Members, Passphrases etc.
 */
// TODO move to separate package?
interface CalendarsRepository {

    data class EventsWindow(
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val timeZoneId: String
    )

    suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<InitingState>

    suspend fun shutdown()

    // calendars
    suspend fun selectCalendarEntity(calendarId: String): CalendarEntity?

    suspend fun selectCalendar(calendarId: String): Calendar?

    suspend fun selectCalendars(userId: String): List<CalendarEntity>

    suspend fun selectUserCalendars(userId: String): List<Calendar>

    suspend fun selectActiveUserCalendars(userId: String): List<Calendar>

    suspend fun selectDisabledUserCalendars(userId: String): List<Calendar>

    suspend fun selectInactiveUserCalendars(userId: String): List<Calendar>

    suspend fun selectSubscribedCalendars(userId: String): List<CalendarEntity>

    fun flowActiveUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowDisabledUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowInactiveUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowSubscribedCalendars(userId: String): Flow<List<Calendar>>

    suspend fun persistCalendar(userId: String, calendar: CalendarEntity)

    suspend fun deleteCalendarById(id: String)

    suspend fun refreshCalendars(userId: UserId): Boolean

    suspend fun fetchCalendars(userId: UserId): List<Calendar>?

    suspend fun fetchCalendarEntities(userId: UserId): List<CalendarEntity>?

    /**
     * Fetches and combines MemberEntity with supplied CalendarEntities
     */
    suspend fun fetchMembersToCalendarEntities(userId: UserId, calendars: List<CalendarEntity>): List<Calendar>?

    suspend fun fetchMembers(userId: UserId, calendarId: String): List<MemberEntity>?

    suspend fun fetchCalendar(userId: UserId, calendarId: String): Calendar?

    suspend fun fetchCalendarEntity(userId: UserId, calendarId: String): CalendarEntity?

    suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean

    suspend fun updateCalendarDisplay(calendarId: String, display: Boolean)

    // TODO create FLOW methods taking "event" selections according to "views" like monthly, weekly...

    // events
    fun eventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<List<Event>?>

    /**
     * Request Events to be pushed to observers and also fetched from API if possible.
     */
    suspend fun fetchEvents(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    )

    sealed class GetEventsResult<out T> {
        object InProgress: GetEventsResult<Nothing>()
        data class Success<T>(val events: List<T>): GetEventsResult<T>()
        data class Exception(val throwable: Throwable): GetEventsResult<Nothing>()
    }

    suspend fun transformAllowingApiCall(eventId: String, calendarId: String): Event?

    /**
     * @return Transformed Events.
     */
    fun getEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        allowCached: Boolean
    ): Flow<GetEventsResult<Event>>

    /**
     * @return SkeletonEvents with correct Calendar Color.
     */
    fun getSkeletonEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<GetEventsResult<SkeletonEvent>>

    suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean

    suspend fun eventExistsOnServer(userId: UserId, eventId: String, calendarId: String): Boolean?

    suspend fun shouldFetchEvent(metadata: ServerEvent.EventEntityMetadata): Boolean

    suspend fun hasCalendar(calendarId: String, ): Boolean

    suspend fun selectEventEntity(eventId: String): EventEntity?

    suspend fun refreshCalendarsFlags(userId: UserId)

    /**
     * Root Event is the original recurring event for single-edited event with RECURRENCE-ID. May be the event itself
     * if there is only one event with this UID.
     */
    suspend fun selectRootEventEntity(eventUid: String): EventEntity?

    suspend fun hasSingleEdits(userId: UserId, eventUid: String): Boolean?

    suspend fun getSingleEdits(userId: UserId, eventUid: String, stopAfter: ZonedDateTime? = null,  timeZoneId: String? = null): List<Event>?

    suspend fun isOrphanSingleEdit(userId: UserId, eventUid: String): Boolean?

    suspend fun isStandaloneSingleEdit(userId: UserId, eventUid: String, eventRecurrenceId: RecurrenceId, timeZoneId: String): Boolean?

    suspend fun persistEvents(vararg events: EventEntity)

    suspend fun deleteEventsById(ids: List<String>)

    suspend fun deleteAllEvents(calendarId: String)

    suspend fun getEventsByUid(userId: UserId, eventUid: String): ApiResponse<EventsByUidApiResponse>

    suspend fun fetchEventById(userId: UserId, calendarId: String, eventId: String): ApiResponse<EventApiResponse>

    // calendar keys
    suspend fun selectCalendarKeys(calendarId: String): List<CalendarKeyEntity>

    suspend fun persistCalendarKey(calendarKey: CalendarKeyEntity) // calendarId is already there

    suspend fun deleteCalendarKeyById(id: String)

    // passphrases
    suspend fun selectPassphrases(calendarId: String): List<PassphraseEntity>

    suspend fun persistPassphrase(passphrase: PassphraseEntity) // calendarId is already there

    suspend fun deletePassphraseById(id: String)

    // members
    suspend fun selectMembers(calendarId: String): List<MemberEntity>

    suspend fun persistMember(member: MemberEntity) // calendarId is already there

    suspend fun deleteMemberById(id: String)

    // calendar settings
    suspend fun selectCalendarSettings(calendarId: String): CalendarSettingsEntity?

    suspend fun persistCalendarSettings(calendarSettings: CalendarSettingsEntity) // calendarId is already there

    suspend fun updateCalendarSettings(calendarSettings: CalendarSettingsEntity) // calendarId is already there

    suspend fun deleteCalendarSettingsById(id: String)

    // calendar subscription
    suspend fun selectCalendarSubscription(calendarId: String): CalendarSubscriptionEntity?

    suspend fun selectCalendarSubscriptions(calendarId: String): List<CalendarSubscriptionEntity>

    fun flowCalendarSubscriptions(): Flow<List<CalendarSubscriptionEntity>>

    suspend fun persistCalendarSubscription(calendarSubscription: CalendarSubscriptionEntity) // calendarId is already there

    suspend fun deleteCalendarSubscriptionById(id: String)

    // calendar user settings
    suspend fun selectCalendarUserSettings(userId: String): CalendarUserSettingsEntity?

    suspend fun updateCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String, autoDetectPrimaryTimezone: Int)

    suspend fun selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Int?

    fun flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Flow<Int?>

    suspend fun updateCalendarUserSettingsDisplayWeekNumber(userId: String, displayWeekNumber: Int)

    suspend fun selectCalendarUserSettingsDisplayWeekNumber(userId: String): Int?

    fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int?>

    fun flowCalendarUserSettingsAutoImportInvite(userId: String): Flow<Int?>

    suspend fun updateCalendarUserDefaultCalendarId(userId: String, defaultCalendarId: String)

    suspend fun updateCalendarUserAutoImportInvite(userId: String, autoImportInvite: Boolean)

    fun flowCalendarUserDefaultCalendarId(userId: String): Flow<String?>

    suspend fun selectCalendarUserSettingsPrimaryTimezone(userId: String): String?

    fun flowCalendarUserSettingsPrimaryTimezone(userId: String): Flow<String?>

    suspend fun persistCalendarUserSettings(userId: String, calendarUserSettings: CalendarUserSettingsEntity)

    suspend fun deleteCalendarUserSettingsByUserId(userId: String)

    suspend fun getDefaultCalendarIdOrFirstActiveId(userId: String): String?

    suspend fun getDefaultCalendarId(userId: String): String?

    // event alarms
    suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>>

    // event alarms
    suspend fun selectEventAlarm(eventAlarmId: String): EventAlarmEntity?

    /**
     * Selects upcoming EventAlarms that should be shown at [timestampSeconds] or the nearest possible timestamp.
     */
    suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity>

    /**
     * Selects EventAlarms that should be shown between [timestampSecondsFrom] and [timestampSecondsTo] inclusive.
     */
    suspend fun selectAllEventAlarmsBetween(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity>

    suspend fun persistEventAlarm(logger: Logger, eventAlarm: EventAlarmEntity)

    suspend fun deleteEventAlarmById(id: String)

    suspend fun deleteEventAlarmsForEvent(eventId: String)

    suspend fun deleteEventAlarmsByEventIdAndOccurrence(eventId: String, occurrence: Long)

    suspend fun deleteAllEventAlarms(calendarId: String)

    val fetchingState: Flow<FetchingState>

    sealed class FetchingState {
        object NotNeeded : FetchingState()
        object Fetching : FetchingState()
        object Finished : FetchingState()
    }

    sealed class InitingState {
        object Initing : InitingState()
        object Finished : InitingState()
        object Error : InitingState()
    }
}
