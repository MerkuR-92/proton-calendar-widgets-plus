package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.delay
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId

class UpdateParticipationStatusUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PARTICIPATION_STATUS"
    }

    suspend fun execute(userId: UserId, calendarId: String, eventId: String, attendeeId: String, status: Int): UseCase.Result {
        return when (val updateParticipationStatusResponse =
            calendarsApi.updateParticipationStatus(userId, calendarId, eventId, attendeeId, status)
        ) {
            is ApiResponse.Success -> {
                // TODO Handle success and update DB or wait for server event ?
                //  To update event entity, check if we can update EventEntity.Attendees json by finding the attendeeId and just changing value of property Status
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                logger.e("api error updating participation status: $updateParticipationStatusResponse")
                UseCase.Result.Error(updateParticipationStatusResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating participation status: $updateParticipationStatusResponse")
                UseCase.Result.Error(updateParticipationStatusResponse.exception.message ?: "(no exception message)")
            }
        }
    }

}
