package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Handles all the updates to Events in proton-event-loop.
 */
class HandleEventsMetadataUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val calendarsRepository: CalendarsRepository,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
) : UseCase {

    val now = Instant.now()

    @Throws(java.lang.Exception::class)
    suspend fun execute(
        userId: UserId,
        eventMetadata: List<ServerEvent.EventsApiResponse>
    ): UseCase.Result {

        logger.v("executing HandleEventsMetadataUseCase")

        // no additional logic when deleting Events
        eventMetadata.filter { it.action == ServerEvent.Action.DELETE.value }.let { events ->
            calendarsRepository.deleteEventsById(events.map { it.id })
        }

        eventMetadata.filter { it.action == ServerEvent.Action.CREATE.value || it.action == ServerEvent.Action.UPDATE.value }
            .let { events ->
                events.filter { it.event != null && shouldFetchEvent(it.event) }.let {

                    val responses = coroutineScope {
                        it.map { async { calendarsApi.getEvent(userId, it.event!!.calendarId, it.event.id) } }
                            .awaitAll()
                    }

                    responses.mapNotNull { if (it is ApiResponse.Success) it.data.event else null }
                        .let { eventEntities ->
                            calendarsRepository.persistEvents(*eventEntities.toTypedArray())

                            updateAlarmsUseCase.execute(userId.id, eventEntities.map { it.id })
                            fetchPublicKeysUseCase.execute(userId, eventEntities)
                        }

                    var errorOccurred = false

                    responses.mapNotNull { if (it is ApiResponse.Error) it else null }.forEach {
                        if (it.httpCode == 404) {
                            logger.e("404 fetching event in HandleEventsMetadataUseCase: $it")
                        } else {
                            errorOccurred = true
                            logger.e("error fetching event in HandleEventsMetadataUseCase: $it")
                        }
                    }

                    responses.mapNotNull { if (it is ApiResponse.Exception) it.exception else null }.forEach {
                        errorOccurred = true
                        logger.e("exception fetching event in HandleEventsMetadataUseCase", it)
                    }

                    if (errorOccurred) {
                        throw Exception("error fetching events in HandleEventsMetadataUseCase")
                    }
                }
            }

        return UseCase.Result.Success
    }

    /**
     * Events outside sensible range should not be fetched automatically.
     */
    private fun shouldFetchEvent(metadata: ServerEvent.EventEntityMetadata): Boolean {

        val startInstant = Instant.ofEpochSecond(metadata.startTime)
        val endInstant = Instant.ofEpochSecond(metadata.endTime)

        if (metadata.rRule == null) {

            return when {
                now.minus(60, ChronoUnit.DAYS).isAfter(endInstant) -> false
                now.plus(120, ChronoUnit.DAYS).isBefore(startInstant) -> false
                else -> true
            }

        }

        return true
    }

}
