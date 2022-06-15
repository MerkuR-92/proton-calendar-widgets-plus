package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UpdateCalendarApiRequest
import me.proton.android.calendar.data.api.UpdateCalendarDisplayApiRequest
import me.proton.android.calendar.data.api.UpdateMemberApiRequest
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class UpdateCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val database: AppDatabase,
): UseCase {

    companion object {
        const val WORKER_ID = "UPDATE_CALENDAR"
        const val WORKER_LIST_ID = "UPDATE_CALENDAR_LIST"
    }

    // Update Single Calendar on Server
    suspend fun executeUpdateFromDb(userId: UserId, calendarId: String) : UseCase.Result {

        val dbCalendar = database.calendarsDao().selectById(calendarId) ?: return UseCase.Result.Error("UpdateCalendarUseCase: executeUpdateFromDb DB Calendar was null")
        val dbMember = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.Error("UpdateCalendarUseCase: executeUpdateFromDb DB Member was null")

        val updateCalendarApiRequest = UpdateCalendarApiRequest(
            name = dbCalendar.name,
            description = dbCalendar.description
        )

        val updateMemberApiRequest = UpdateMemberApiRequest(
            color = dbMember.color,
            display = dbMember.display
        )

        return updateSingleCalendar(userId, calendarId, updateCalendarApiRequest, dbMember.id, updateMemberApiRequest)

    }

    suspend fun executeUpdate(userId: UserId, calendarId: String, description: String? = null, name: String? = null, color: String? = null, display: Int? = null) : UseCase.Result {
        val updateCalendarApiRequest = UpdateCalendarApiRequest(
            name = name,
            description = description
        )

        val updateMemberApiRequest = UpdateMemberApiRequest(
            color = color,
            display = display
        )

        val dbMember = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate DB Member was null")

        return updateSingleCalendar(userId, calendarId, updateCalendarApiRequest, dbMember.id, updateMemberApiRequest)
    }

    private suspend fun updateSingleCalendar(
        userId: UserId,
        calendarId: String,
        updateCalendarApiRequest: UpdateCalendarApiRequest,
        memberId: String,
        updateMemberApiRequest: UpdateMemberApiRequest
    ): UseCase.Result {
        val updateCalendarApiResponse = when (val updateCalendarResponse =
            calendarsApi.updateCalendar(userId, calendarId, updateCalendarApiRequest)) {
            is ApiResponse.Success -> {
                calendarsRepository.updateCalendar(userId.id, updateCalendarResponse.data.calendar)
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update calendar: ${updateCalendarResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update calendar: ${updateCalendarResponse.exception.message ?: "(no exception message)"}")
        }

        val updateMemberApiResponse = when (val updateCalendarResponse =
            calendarsApi.updateMember(userId, calendarId, memberId, updateMemberApiRequest)) {
            is ApiResponse.Success -> {
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update member: ${updateCalendarResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update member: ${updateCalendarResponse.exception.message ?: "(no exception message)"}")
        }

        val responses = listOf(updateCalendarApiResponse, updateMemberApiResponse)

        return if (responses.all { it is UseCase.Result.Success<*> }) {
            UseCase.Result.Success<Unit>()
        } else {
            responses.first { it !is UseCase.Result.Success<*> }
        }
    }

    //Update Calendars Display values on Server
    suspend fun updateAllCalendarsDisplay(userId: UserId) : UseCase.Result {
        val dbCalendarMembers = database.membersDao().selectByUserId(userId.id).groupBy { it.calendarId }

        // Map<CalendarId, Pair<MemberId, DisplayInt>>
        val serverCalendarMembers = dbCalendarMembers.mapValues {
            calendarsApi.getMemberList(userId, it.key).valueOrNullAndLogErrors(logger)?.members
        }

        val membersToUpdateDisplay = dbCalendarMembers.filter {
            it.value.first().display != serverCalendarMembers[it.key]?.firstOrNull()?.display
        }

        val updateCalendarDisplayResponses = arrayListOf<UseCase.Result>()
        membersToUpdateDisplay.forEach {
            updateCalendarDisplayResponses.add(
                when (val updateCalendarDisplayResponse = calendarsApi.updateCalendarDisplay(userId, calendarId = it.key, memberId = it.value.first().id, UpdateCalendarDisplayApiRequest(display = it.value.first().display))) {
                    is ApiResponse.Success -> {
                        UseCase.Result.Success<Unit>()
                    }
                    is ApiResponse.Error ->
                        UseCase.Result.Error("UpdateCalendarUseCase: executeUpdateList error in update calendar display: ${updateCalendarDisplayResponse.error}")
                    is ApiResponse.Exception ->
                        UseCase.Result.Error("UpdateCalendarUseCase: executeUpdateList error in update calendar display: ${updateCalendarDisplayResponse.exception.message ?: "(no exception message)"}")
                }
            )
        }

        updateCalendarDisplayResponses.forEach {
            if (it !is UseCase.Result.Success<*>) return it
        }

        return UseCase.Result.Success<Unit>()
    }
}
