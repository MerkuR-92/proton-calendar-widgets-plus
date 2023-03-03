package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class LeaveCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val database: AppDatabase
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_LEAVE_CALENDAR"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        val member = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.Error("LeaveCalendarUseCase: Member was null")

        return when (val leaveCalendarResponse =
            calendarsApi.leaveCalendar(userId, calendarId, member.id)
        ) {
            is ApiResponse.Success -> {
                // Delete Calendar from DB
                calendarsRepository.deleteCalendarById(calendarId)
                return UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error join calendar: $leaveCalendarResponse")
                UseCase.Result.Error(leaveCalendarResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error join calendar: $leaveCalendarResponse")
                UseCase.Result.Error(leaveCalendarResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}