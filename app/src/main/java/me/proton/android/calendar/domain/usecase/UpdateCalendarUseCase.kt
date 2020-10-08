package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
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
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID = "UPDATE_SERVER_CALENDAR"
    }

    //TODO Handle other Calendar parameters

    //Update Calendar Display on Server
    suspend fun executeServerUpdate(calendarId: String, name: String?, description: String?, color: String?, displayValue: Int) : UseCase.Result {
        val display = if (displayValue != -1) displayValue else null
        val updateCalendarApiRequest = UpdateCalendarApiRequest(
            name = name,
            description = description,
            color = color,
            display = display)

        return when (val updateCalendarResponse = calendarsApi.updateCalendar(calendarId, updateCalendarApiRequest)) {
            is ApiResponse.Success -> {
                //Update value in DB
                val calendarEntity = calendarsRepository.selectCalendar(calendarId) ?: return UseCase.Result.InvalidParams("event $calendarId doesn't exist in DB")

                val newCalendarEntity = calendarEntity.copy(
                    name = name?: calendarEntity.name,
                    description = description?: calendarEntity.description,
                    color = color?: calendarEntity.color,
                    display = display?: calendarEntity.display)

                newCalendarEntity.fkUserId = calendarEntity.fkUserId

                database.calendarsDao().update(newCalendarEntity)

                calendarsRepository.refreshEvents(arrayListOf(calendarId))

                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error(updateCalendarResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(updateCalendarResponse.exception.message ?: "(no exception message)")
        }
    }
}
