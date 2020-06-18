package me.proton.android.calendar.domain

import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Event
import kotlinx.coroutines.flow.Flow

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

    suspend fun deleteCalendarById(id: String)


    // TODO create FLOW methods taking "event" selections according to "views" like monthly, weekly...

    // events
    fun eventsFlow(calendarId: String): Flow<List<Event>>

    fun event(eventId: String): Flow<Event?> // TODO separate Flow<> from normal DB queries?

    suspend fun selectEventEntity(eventId: String): EventEntity?

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
    suspend fun selectSettings(calendarId: String): SettingsEntity?

    suspend fun persistSettings(settings: SettingsEntity) // calendarId is already there

    suspend fun deleteSettingsById(id: String)

    // event alarms
    suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>>

    suspend fun persistEventAlarm(eventAlarm: EventAlarmEntity) // eventId is already there

    suspend fun deleteEventAlarmById(id: String)

}
