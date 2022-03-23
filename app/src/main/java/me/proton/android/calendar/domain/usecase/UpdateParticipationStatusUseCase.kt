package me.proton.android.calendar.domain.usecase

import biweekly.parameter.ParticipationStatus
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.toParticipationStatus
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class UpdateParticipationStatusUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val updatePersonalPartUseCase: UpdatePersonalPartUseCase,
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PARTICIPATION_STATUS"
        const val WORKER_ID_SINGLE_EDIT = "WORKER_ID_UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String,
        eventId: String,
        attendeeId: String,
        status: Int,
        personalPartICalString: String?,
        updateTime: Int? = null
    ): UseCase.Result {
        return when (val updateParticipationStatusResponse =
            calendarsApi.updateParticipationStatus(userId, calendarId, eventId, attendeeId, status, updateTime)
        ) {
            is ApiResponse.Success -> {

                // personalPartICalString == null ignore alarms update, personalPartICalString == "" clear alarms, else update event with new alarms
                if (personalPartICalString != null) {
                    // TODO Ignore update alarms errors or display snack ?
                    val updatePersonalPartUseCaseUseCaseResult = updatePersonalPartUseCase.execute(userId, calendarId, eventId, personalPartICalString)
                    updatePersonalPartUseCaseUseCaseResult.ifSuccessAndLogErrors(logger) { }

                    // Fetch updated event after updating its alarms
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
                } else {
                    // Use EventEntity returned by updateParticipationStatus route if we don't need to update alarms
                    calendarsRepository.persistEvents(updateParticipationStatusResponse.data.event)
                }

                // If getEvent failed we still return success and will receive updated event in next server event loop
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                updateParticipationStatusResponse.logErrorIfNeeded("api error updating participation status", logger)
                UseCase.Result.Error("api error updating participation status")
            }
            is ApiResponse.Exception -> {
                updateParticipationStatusResponse.logErrorIfNeeded("api exception updating participation status", logger)
                UseCase.Result.Error("api exception updating participation status")
            }
        }
    }

    suspend fun executeClearSingleEdits(userId: UserId, calendarId: String, eventUid: String, userEmails: List<String>, mainChainStatus: Int): UseCase.Result {

        val singleEdits = calendarsRepository.getSingleEdits(userId, eventUid)

        var singleEditsClearedSuccessfully = true
        singleEdits?.forEach { event ->
            val mainChanParticipationStatus = mainChainStatus.toParticipationStatus()
            if (event.currentUserAttendeeId == null || event.getParticipationStatus(userEmails) == mainChanParticipationStatus) return@forEach
            when (val updateParticipationStatusResponse =
                calendarsApi.updateParticipationStatus(userId, calendarId, event.id, event.currentUserAttendeeId, ParticipationStatus.NEEDS_ACTION.toInt()) // 0 == NEEDS_ACTION
            ) {
                is ApiResponse.Success -> {
                    // Clear alarms
                    // TODO Ignore update alarms errors or display snack ?
                    val updatePersonalPartUseCaseUseCaseResult = updatePersonalPartUseCase.execute(userId, calendarId, event.id, "")
                    updatePersonalPartUseCaseUseCaseResult.ifSuccessAndLogErrors(logger) { }
                }
                is ApiResponse.Error -> {
                    singleEditsClearedSuccessfully = false
                    logger.e("api error updating single edit participation status: ${updateParticipationStatusResponse.error}")
                }
                is ApiResponse.Exception -> {
                    singleEditsClearedSuccessfully = false
                    logger.e("api error updating single edit participation status: ${updateParticipationStatusResponse.exception.message ?: "(no exception message)"}")
                }
            }
        }

        when (val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100)) {
            is ApiResponse.Success -> {
                calendarsRepository.persistEvents(*eventsSharingUidResponse.data.events.toTypedArray())
            }
            is ApiResponse.Error -> {
                logger.e("api error fetching events by uid: ${eventsSharingUidResponse.error}")
            }
            is ApiResponse.Exception -> {
                logger.e("api error fetching events by uid: ${eventsSharingUidResponse.exception.message ?: "(no exception message)"}")
            }
        }

        return if (singleEditsClearedSuccessfully) UseCase.Result.Success<Unit>()
        else UseCase.Result.Error("Failed to update participation status for one or more single edits")
    }
}
