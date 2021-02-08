package me.proton.android.calendar.domain.usecase

import android.provider.CalendarContract
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UpdateCalendarApiRequest
import me.proton.android.calendar.data.api.UpdateCalendarDisplayApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId

class UpdateCalendarUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
): UseCase {

    companion object {
        const val WORKER_ID = "UPDATE_CALENDAR"
        const val WORKER_LIST_ID = "UPDATE_CALENDAR_LIST"
    }

    //TODO Handle other Calendar parameters

    //Update Single Calendar on Server
    suspend fun executeUpdate(userId: UserId, calendarId: String) : UseCase.Result {
        val dbCalendar = database.calendarsDao().selectById(calendarId) ?: return UseCase.Result.Error("UpdateCalendarUseCase: DB Calendar was null")
        val updateCalendarApiRequest = UpdateCalendarApiRequest(
            name = dbCalendar.name,
            description = dbCalendar.description,
            color = dbCalendar.color,
            display = dbCalendar.display)

        return when (val updateCalendarResponse = calendarsApi.updateCalendar(userId, calendarId, updateCalendarApiRequest)) {
            is ApiResponse.Success -> {
                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error("UpdateCalendarUseCase: error in update calendar: ${updateCalendarResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("UpdateCalendarUseCase: error in update calendar: ${updateCalendarResponse.exception.message ?: "(no exception message)"}")
        }
    }

    //Update Calendars Display values on Server
    suspend fun executeUpdateList(userId: UserId) : UseCase.Result {
        val dbCalendars = database.calendarsDao().selectCalendars(userId.id)
        return when (val calendarsResponse = calendarsApi.getCalendars(userId)) {
            is ApiResponse.Success -> {
                val serverCalendarsMap = HashMap<String, Int>()
                calendarsResponse.data.calendars.forEach {
                    serverCalendarsMap[it.id] = it.display
                }

                val dbCalendarsMap = HashMap<String, Int>()
                dbCalendars.forEach {
                    dbCalendarsMap[it.id] = it.display
                }

                val updateCalendarDisplayResponses = arrayListOf<UseCase.Result>()

                dbCalendarsMap.forEach {
                    if (it.value != serverCalendarsMap[it.key]) {
                        updateCalendarDisplayResponses.add(
                            when (val updateCalendarDisplayResponse = calendarsApi.updateCalendarDisplay(userId, it.key, UpdateCalendarDisplayApiRequest(display = it.value))) {
                                is ApiResponse.Success -> {
                                    UseCase.Result.Success
                                }
                                is ApiResponse.Error ->
                                    UseCase.Result.Error("UpdateCalendarUseCase: error in update calendar display: ${updateCalendarDisplayResponse.error}")
                                is ApiResponse.Exception ->
                                    UseCase.Result.Error("UpdateCalendarUseCase: error in update calendar display: ${updateCalendarDisplayResponse.exception.message ?: "(no exception message)"}")
                            }
                        )
                    }
                }

                updateCalendarDisplayResponses.forEach {
                    if (it != UseCase.Result.Success) return it
                }

                return UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error("UpdateCalendarUseCase: error in fetch calendars: ${calendarsResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("UpdateCalendarUseCase: error in fetch calendars: ${calendarsResponse.exception.message ?: "(no exception message)"}")
        }
    }
}
