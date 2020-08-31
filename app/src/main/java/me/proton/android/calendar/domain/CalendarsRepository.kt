package me.proton.android.calendar.domain

import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Event
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Manages all Calendars, Events, Members, Passphrases etc.
 */
// TODO move to separate package?
interface CalendarsRepository {

    // calendars
    suspend fun selectCalendar(calendarId: String): CalendarEntity?

    suspend fun selectCalendars(userId: String): List<CalendarEntity>

    fun flowCalendars(userId: String): Flow<List<CalendarEntity>>

    suspend fun persistCalendar(userId: String, calendar: CalendarEntity)

    suspend fun updateCalendar(userId: String, calendar: CalendarEntity)

    suspend fun deleteCalendarById(id: String)

    suspend fun getActiveCalendars(userId: String): List<CalendarEntity>


    // TODO create FLOW methods taking "event" selections according to "views" like monthly, weekly...

    // events
    suspend fun eventsFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Flow<List<Event>>

    suspend fun prefetchEvents(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    )

    fun eventFlow(eventId: String): Flow<Event?> // TODO separate Flow<> from normal DB queries?

    suspend fun selectEventEntity(eventId: String): EventEntity?

    /**
     * Root Event is the original recurring event for single-edited event with RECURRENCE-ID. May be the event itself
     * if there is only one event with this UID.
     */
    suspend fun selectRootEventEntity(eventUid: String): EventEntity?

    suspend fun persistEvents(vararg events: EventEntity)

    suspend fun deleteEventById(id: String)

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

    // user settings
    suspend fun selectUserSettings(userId: String): UserSettingsEntity?

    suspend fun persistUserSettings(userId: String, userSettings: UserSettingsEntity)

    suspend fun deleteUserSettingsByUserId(id: String)

    suspend fun getDefaultCalendarId(userId: String): String?

    // event alarms
    suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>>

    suspend fun persistEventAlarm(eventAlarm: EventAlarmEntity) // eventId is already there

    suspend fun deleteEventAlarmById(id: String)

    suspend fun init(calendarIds: List<String>, toDate: LocalDate, timeZoneId: String)
//    suspend fun init(calendarIds: List<String>, toDate: LocalDate, timeZoneId: String)

    val fetchingState: Flow<FetchingState>

    sealed class FetchingState {
        object NotNeeded : FetchingState()
        object Fetching : FetchingState()
        object Finished : FetchingState()
    }
}
