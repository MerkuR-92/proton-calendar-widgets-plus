package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId
import java.lang.Exception

class SyncServerEventsUseCase(
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val serverEventsApi: ServerEventsApi,
    private val handleServerEventsUseCase: HandleServerEventsUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "SYNC_SERVER_EVENTS"
        const val WORKER_PERIODIC_ID = "SYNC_SERVER_EVENTS_PERIODIC"
    }

    suspend fun execute(userId: UserId): UseCase.Result {

        logger.v("executing SyncServerEventsUseCase for $userId")

        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        var lastProtonEventId = valueStore.getString(ValueKey.LAST_SERVER_EVENT_ID)
            ?: return UseCase.Result.InvalidParams("SyncServerEventsUseCase: no last server event id")

        do {
            var moreEvents = false

            // TODO we need to properly authorize all API requests for specific users!!!
            when (val eventsReponse = serverEventsApi.getServerEvents(userId, lastProtonEventId)) {
                is ApiResponse.Success -> {
                    logger.v("fetched Server Events for ID: $lastProtonEventId")
                    // TODO handle eventsReponse.data.refresh, I think it's "force wipe database"?????
                    moreEvents = eventsReponse.data.more == 1 // TODO parse as boolean
                    logger.v("moreEvents: $moreEvents")

                    val handleServerEventsResult = when (val result =
                        handleServerEventsUseCase.execute(eventsReponse.data, userId)) {
                        UseCase.Result.Success -> {
                            logger.v("correctly handled Proton Events $lastProtonEventId")
                            lastProtonEventId = eventsReponse.data.eventId
                            valueStore.putString(
                                ValueKey.LAST_SERVER_EVENT_ID,
                                eventsReponse.data.eventId
                            )
                            logger.v("next Proton Events ID is saved as $lastProtonEventId")
                            UseCase.Result.Success
                        }
                        is UseCase.Result.InvalidParams -> {
                            UseCase.Result.InvalidParams("SyncServerEventsUseCase: invalid params handling server events: ${result.message}")
                        }
                        is UseCase.Result.Error -> {
                            UseCase.Result.Error("SyncServerEventsUseCase: error handling server events: ${result.message}, ${result.error}")
                        }
                    }

                    if (handleServerEventsResult !is UseCase.Result.Success) {
                        return handleServerEventsResult
                    }

                }
                is ApiResponse.Error -> {
                    return UseCase.Result.Error("SyncServerEventsUseCase: api error getting server events: $eventsReponse")
                }
                is ApiResponse.Exception -> {
                    return UseCase.Result.Error("SyncServerEventsUseCase: exception getting server events: $eventsReponse")
                }
            }

        } while (moreEvents)

        logger.v("success syncing Proton Events, ID saved for later is $lastProtonEventId")

        return UseCase.Result.Success
    }

}
