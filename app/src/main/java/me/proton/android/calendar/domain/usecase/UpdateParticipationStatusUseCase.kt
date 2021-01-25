package me.proton.android.calendar.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.delay
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import java.time.Instant

class UpdateParticipationStatusUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PARTICIPATION_STATUS"
    }

    suspend fun execute(userId: UserId, calendarId: String, eventId: String, attendeeId: String, status: Int): UseCase.Result {
        return when (val updateParticipationStatusResponse =
            calendarsApi.updateParticipationStatus(userId, calendarId, eventId, attendeeId, status)
        ) {
            is ApiResponse.Success -> {

                // TODO notify organizer by sending updated ics

                when (val eventResponse = calendarsApi.getEvent(userId, calendarId, eventId)) {
                    is ApiResponse.Success -> {
                        calendarsRepository.persistEvents(eventResponse.data.event)
                    }
                    is ApiResponse.Error -> {
                        logger.e("api error fetching event by id: $eventResponse")
                    }
                    is ApiResponse.Exception -> {
                        logger.e("api error fetching event by id: $eventResponse")
                    }
                }

                // If getEvent failed we still return success and will receive updated event in next server event loop
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
