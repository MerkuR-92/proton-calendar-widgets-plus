package me.proton.android.calendar.data

import com.google.gson.Gson
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import kotlinx.coroutines.flow.*
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.filterOccurencesByRecurrenceId
import timber.log.Timber
import java.time.LocalDate

// TODO better name? move to separate package?
class CalendarsRepositoryImpl(private val gson: Gson, private val database: AppDatabase, private val transformEventUseCase: TransformEventUseCase, private val crypto: Crypto) : CalendarsRepository {

    override suspend fun selectCalendar(calendarId: String): CalendarEntity? {
        return database.calendarsDao().selectById(calendarId)
    }

    override suspend fun selectCalendars(userId: String): List<CalendarEntity> {
        return database.calendarsDao().selectCalendars(userId)
    }

    override fun flowCalendars(userId: String): Flow<List<CalendarEntity>> {
        return database.calendarsDao().flowCalendars(userId)
    }

//    override fun flowCalendars(userId: String): Flow<List<CalendarEntity>> {
//        return database.calendarsDao().flowCalendars(userId)./*distinctUntilChanged().*/map {
//            TimberLogger.d("mapping calendar list in thread ${Thread.currentThread().name}")
//            // repository & flow doesn't care from where it's called
//            it
//        }.also { /*fire API request if online*/ }
//    }

    override suspend fun persistCalendar(userId: String, calendar: CalendarEntity) {
        calendar.fkUserId = userId
        //  TODO make sure we have "flags" set!!!!!
        database.calendarsDao().insert(calendar)
    }

    override suspend fun deleteCalendarById(id: String) {
        database.calendarsDao().deleteById(id)
    }

    override fun eventsFlow(calendarIds: List<String>, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Flow<List<Event>> {
        TimberLogger.d("eventsFlow: ${fromDate} - ${toDate}")

//        val sharedEventsFieldSubstring = "DTSTART;VALUE=DATE:${fromDateTime.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)}"

        return database.eventsDao().flowEvents(calendarIds).distinctUntilChanged().map {
            it
                .mapNotNull { transformEventUseCase.execute(it) }
                .filter {
                    // TODO optimise and select events that are within correct window
                    //  not only starttime, but also overlapping

                    if (it.isRecurring()) {
                        val occurrences = it.generateOccurrencesInFullDayRange(fromDate, toDate, timeZoneId)
                        if (occurrences != null && occurrences.size > 0) {
                            // TODO we have metadata in Occurrence, use it
                            it.occurence = occurrences.first() // TODO in theory, there may be more occcurrences in given range (MINUTELY?)
                            true
                        } else false
                    } else {
                        it.overlapsWithFullDayRange(fromDate, toDate, timeZoneId)
                    }
                }.filterOccurencesByRecurrenceId()
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

    override suspend fun selectSettings(calendarId: String): SettingsEntity? {
        return database.settingsDao().select(calendarId)
    }

    override suspend fun persistSettings(settings: SettingsEntity) {
        database.settingsDao().insert(settings)
    }

    override suspend fun deleteSettingsById(id: String) {
        database.settingsDao().deleteById(id)
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
