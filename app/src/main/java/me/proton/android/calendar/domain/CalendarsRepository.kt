package me.proton.android.calendar.domain

import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Event
import kotlinx.coroutines.flow.Flow
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

    suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<InitingState>

    suspend fun shutdown()

    // calendars
    suspend fun selectCalendar(calendarId: String): CalendarEntity?

    suspend fun selectCalendars(userId: String): List<CalendarEntity>

    fun flowActiveCalendars(userId: String): Flow<List<CalendarEntity>>

    fun flowDisabledCalendars(userId: String): Flow<List<CalendarEntity>>

    fun flowInactiveCalendars(userId: String): Flow<List<CalendarEntity>>

    suspend fun persistCalendar(userId: String, calendar: CalendarEntity)

    suspend fun updateCalendar(userId: String, calendar: CalendarEntity)

    suspend fun deleteCalendarById(id: String)

    suspend fun refreshCalendars(userId: UserId): Boolean

    suspend fun fetchCalendars(userId: UserId): List<CalendarEntity>?

    suspend fun getActiveCalendars(userId: String): List<CalendarEntity>

    suspend fun getDisabledCalendars(userId: String): List<CalendarEntity>

    suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean

    suspend fun updateCalendarDisplay(calendarId: String, display: Int)

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

    /**
     * @return Transformed Events.
     */
    fun getEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<GetEventsResult<Event>>

    /**
     * @return SkeletonEvents with correct Calendar Color.
     */
    fun getSkeletonEventsForIndicators(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<GetEventsResult<SkeletonEvent>>

    suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean

    suspend fun hasCalendar(calendarId: String, ): Boolean

    fun eventFlow(eventId: String): Flow<Event?> // TODO separate Flow<> from normal DB queries?

    suspend fun selectEventEntity(eventId: String): EventEntity?

    suspend fun refreshCalendarsFlagsForAddress(address: String, status: Int, userId: String)

    /**
     * Root Event is the original recurring event for single-edited event with RECURRENCE-ID. May be the event itself
     * if there is only one event with this UID.
     */
    suspend fun selectRootEventEntity(eventUid: String): EventEntity?

    suspend fun hasSingleEdits(userId: UserId, eventUid: String): Boolean?

    suspend fun getSingleEdits(userId: UserId, eventUid: String, stopAfter: ZonedDateTime? = null,  timeZoneId: String? = null): List<Event>?

    suspend fun isStandaloneSingleEdit(userId: UserId, eventUid: String): Boolean?

    suspend fun persistEvents(vararg events: EventEntity)

    suspend fun deleteEventsById(ids: List<String>)

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

    suspend fun deleteCalendarSettingsById(id: String)

    // calendar user settings
    suspend fun selectCalendarUserSettings(userId: String): CalendarUserSettingsEntity?

    suspend fun updateCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String, autoDetectPrimaryTimezone: Int)

    suspend fun selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Int?

    fun flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Flow<Int?>

    suspend fun updateCalendarUserSettingsDisplayWeekNumber(userId: String, displayWeekNumber: Int)

    fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int?>

    fun flowCalendarUserSettingsPrimaryTimezone(userId: String): Flow<String?>

    suspend fun persistCalendarUserSettings(userId: String, calendarUserSettings: CalendarUserSettingsEntity)

    suspend fun deleteCalendarUserSettingsByUserId(userId: String)

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
    suspend fun selectAllBetweenInclusive(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity>

    suspend fun selectEventAlarms(timestampSecondsStart: Long, timestampSecondsEnd: Long): List<EventAlarmEntity>

    suspend fun persistEventAlarm(eventAlarm: EventAlarmEntity)

    suspend fun deleteEventAlarmById(id: String)

    suspend fun deleteEventAlarmsForEvent(eventId: String)

    suspend fun deleteEventAlarmsByEventIdAndOccurrence(eventId: String, occurrence: Long)

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
