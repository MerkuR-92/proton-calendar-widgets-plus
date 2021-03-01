package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId

class SyncServerEventsUseCase(
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val serverEventsApi: ServerEventsApi,
    private val database: AppDatabase,
    private val handleServerEventsUseCase: HandleServerEventsUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "SYNC_SERVER_EVENTS"
    }

    suspend fun execute(userId: UserId): UseCase.Result {

        logger.v("executing SyncServerEventsUseCase for $userId")

        val valueStore = valueStoreProvider.provideValueStore(userId.id)

        val syncResults = mutableListOf<UseCase.Result>()

        // sync core proton events
        syncResults.add(sync(userId))

        // sync each calendar
        database.calendarsDao().selectCalendars(userId.id).forEach { calendarEntity ->

            if (valueStore.getStringFromSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarEntity.id) == null) {
                val latestEventIdResponse = serverEventsApi.getLatestServerCalendarEvent(userId, calendarEntity.id)
                if (latestEventIdResponse is ApiResponse.Success) {
                    valueStore.putStringInSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarEntity.id, latestEventIdResponse.data.calendarEventId)
                } else {
                    logger.e("could not get latest calendar server event ID response in SyncServerEventsUseCase")
                }
            }

            syncResults.add(sync(userId, calendarEntity.id))
        }

        return syncResults.firstOrNull { it !is UseCase.Result.Success<*> } ?: UseCase.Result.Success<Unit>()
    }

    /**
     * Sync "core" or "calendar" server events.
     */
    private suspend fun sync(userId: UserId, calendarId: String? = null): UseCase.Result {

        logger.v("sync for calendarId: $calendarId")

        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        var lastServerEventId = if (calendarId != null) {
            valueStore.getStringFromSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarId)
                ?: return UseCase.Result.InvalidParams("SyncServerEventsUseCase: no last server calendar event id")
        } else {
            valueStore.getString(ValueKey.LAST_SERVER_EVENT_ID)
                ?: return UseCase.Result.InvalidParams("SyncServerEventsUseCase: no last server event id")
        }

        logger.v("with lastServerEventId: $lastServerEventId")

        do {
            var moreEvents = false

            val eventsReponse = if (calendarId != null) {
                serverEventsApi.getServerCalendarEventsSince(userId, lastServerEventId, calendarId)
            } else {
                serverEventsApi.getServerCoreEventsSince(userId, lastServerEventId)
            }

            when (eventsReponse) {
                is ApiResponse.Success -> {
                    logger.v("fetched Server Events for ID: $lastServerEventId")
                    moreEvents = eventsReponse.data.more == 1
                    logger.v("moreEvents: $moreEvents")

                    val handleServerEventsResult = when (val result =
                        handleServerEventsUseCase.execute(eventsReponse.data, userId)) {
                        is UseCase.Result.Success<*> -> {
                            logger.v("correctly handled Server Events $lastServerEventId")
                            lastServerEventId = eventsReponse.data.eventId

                            if (calendarId != null) {
                                valueStore.putStringInSet(
                                    ValueSet.LAST_SERVER_CALENDAR_EVENT_ID,
                                    calendarId,
                                    eventsReponse.data.eventId
                                )
                            } else {
                                valueStore.putString(
                                    ValueKey.LAST_SERVER_EVENT_ID,
                                    eventsReponse.data.eventId
                                )
                            }

                            logger.v("next Server Events ID for calendar $calendarId is saved as $lastServerEventId")
                            UseCase.Result.Success<Unit>()
                        }
                        is UseCase.Result.InvalidParams -> {
                            logger.e("SyncServerEventsUseCase: invalid params handling server events: ${result.message}")
                            UseCase.Result.InvalidParams("invalid params handling server events: ${result.message}")
                        }
                        is UseCase.Result.Error -> {
                            logger.e("SyncServerEventsUseCase: handleServerEventsResult error ${result.message}, ${result.error}")
                            UseCase.Result.Error("error handling server events: ${result.message}, ${result.error}")
                        }
                    }

                    if (handleServerEventsResult !is UseCase.Result.Success<*>) {
                        return handleServerEventsResult
                    }

                }
                is ApiResponse.Error -> {
                    logger.e("error in SyncServerEvents: ${eventsReponse}")
                    return UseCase.Result.Error("api error getting server events: $eventsReponse")
                }
                is ApiResponse.Exception -> {
                    logger.e("Exception in SyncServerEvents: ${eventsReponse}")
                    return UseCase.Result.Error("exception getting server events: $eventsReponse")
                }
            }

        } while (moreEvents)

        logger.v("success syncing Server Events, ID saved for later is $lastServerEventId")

        return UseCase.Result.Success<Unit>()

    }

}
