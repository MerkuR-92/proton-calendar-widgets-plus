package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.SyncEventDeleteContainer
import me.proton.android.calendar.data.api.SyncEventsUpdateApiRequest
import me.proton.android.calendar.data.api.UpdateCalendarApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import java.time.LocalDate
import java.time.ZoneId

class UpdateCalendarUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val valueStoreProvider: ValueStoreProvider
): UseCase {

    //Update Calendar Display in DB and refresh events
    suspend fun executeDbUpdate(calendarId: String, display: Int) : UseCase.Result {

        val calendarEntity = calendarsRepository.selectCalendar(calendarId) ?: return UseCase.Result.InvalidParams("event $calendarId doesn't exist in DB")

        val newCalendarEntity = CalendarEntity(
            calendarId,
            calendarEntity.name,
            calendarEntity.description,
            calendarEntity.color,
            display,
            calendarEntity.flags)
        newCalendarEntity.fkUserId = calendarEntity.fkUserId

        database.calendarsDao().update(newCalendarEntity)


        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        //Refresh events
        val timeZoneId = ZoneId.of(calendarsRepository.selectUserSettings(TODOuserID)?.primaryTimezone!!)
        val firstDayOfTheMonth = LocalDate.now(timeZoneId).withDayOfMonth(1).plusMonths(1)
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
        val selectedCalendarIds = calendarsRepository.getActiveCalendars(TODOuserID).filter { it.display == 1 }.map { it.id }.toList()
        calendarsRepository.refreshEvents(selectedCalendarIds, TODOuserID, toDate, timeZoneId.id)

        return UseCase.Result.Success
    }

    //Update Calendar Display on Server
    suspend fun executeServerUpdate(calendarId: String, display: Int) : UseCase.Result {
        val updateCalendarApiRequest = UpdateCalendarApiRequest(
            display = display
        )

        val result = when (val updateCalendarResponse = calendarsApi.updateCalendar(calendarId, updateCalendarApiRequest)) {
            is ApiResponse.Success -> {
                //Nothing to do, changes have already been registered
                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error(updateCalendarResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(updateCalendarResponse.exception.message ?: "(no exception message)")
        }

        if (result is UseCase.Result.Error) {
            //Revert changes made to DB
            executeDbUpdate(calendarId, if (display == 1) 0 else 1)
        }

        return result
    }
}