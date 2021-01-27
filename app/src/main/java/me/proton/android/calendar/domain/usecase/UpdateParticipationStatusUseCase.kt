package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId

class UpdateParticipationStatusUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val updatePersonalPartUseCase: UpdatePersonalPartUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PARTICIPATION_STATUS"
    }

    suspend fun execute(userId: UserId, calendarId: String, eventId: String, attendeeId: String, status: Int, personalPartICalString: String?): UseCase.Result {
        return when (val updateParticipationStatusResponse =
            calendarsApi.updateParticipationStatus(userId, calendarId, eventId, attendeeId, status)
        ) {
            is ApiResponse.Success -> {

                // TODO notify organizer by sending updated ics

                // personalPartICalString == null ignore alarms update, personalPartICalString == "" clear alarms, else update event with new alarms
                personalPartICalString?.let {
                    // TODO Ignore update alarms errors or display snack ?
                    val updatePersonalPartUseCaseUseCaseResult = updatePersonalPartUseCase.execute(userId, calendarId, eventId, personalPartICalString)
                    updatePersonalPartUseCaseUseCaseResult.ifSuccessAndLogErrors(logger) { }
                }

                when (val eventResponse = calendarsApi.getEvent(userId, calendarId, eventId)) {
                    is ApiResponse.Success -> {
                        calendarsRepository.persistEvents(eventResponse.data.event)
                    }
                    is ApiResponse.Error -> {
                        logger.e("api error fetching event by id: $eventResponse")
                    }
                    is ApiResponse.Exception -> {
                        logger.e("api error fetching event by id: ${eventResponse.exception.message ?: "(no exception message)"}")
                    }
                }

                // If getEvent failed we still return success and will receive updated event in next server event loop
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error("api error updating participation status: ${updateParticipationStatusResponse.error}")
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error("api error updating participation status: ${updateParticipationStatusResponse.exception.message ?: "(no exception message)"}")
            }
        }
    }
}
